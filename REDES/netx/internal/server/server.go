package server

import (
	"bufio"
	"context"
	"crypto/subtle"
	"errors"
	"fmt"
	"net"
	"strconv"
	"sync"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/advanced"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/sockopt"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/tcpinfo"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

type Config struct {
	ListenHost string
	Port       int
}

type Server struct {
	cfg Config
	wg  sync.WaitGroup
}

func New(cfg Config) *Server { return &Server{cfg: cfg} }

func (s *Server) Run(ctx context.Context) error {
	addr := net.JoinHostPort(s.cfg.ListenHost, strconv.Itoa(s.cfg.Port))
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("listen control %s: %w", addr, err)
	}
	defer ln.Close()
	go func() {
		<-ctx.Done()
		_ = ln.Close()
	}()
	for {
		conn, err := ln.Accept()
		if err != nil {
			if ctx.Err() != nil || errors.Is(err, net.ErrClosed) {
				s.wg.Wait()
				return nil
			}
			return fmt.Errorf("accept control: %w", err)
		}
		s.wg.Add(1)
		go func() {
			defer s.wg.Done()
			_ = s.handleControl(ctx, conn)
		}()
	}
}

func (s *Server) handleControl(ctx context.Context, control net.Conn) error {
	defer control.Close()
	_ = control.SetDeadline(time.Now().Add(24*time.Hour + 30*time.Minute))
	reader := bufio.NewReaderSize(control, 4096)
	var req protocol.Request
	if err := protocol.ReadJSONLine(reader, &req); err != nil {
		return err
	}
	if req.Mode == "latency" {
		return handleLatency(control, reader)
	}
	if handled, err := advanced.HandleServer(ctx, control, reader, s.cfg.ListenHost, req); handled {
		return err
	}
	if err := validateRequest(req); err != nil {
		_ = protocol.WriteJSONLine(control, map[string]string{"error": err.Error()})
		return err
	}
	switch req.Mode {
	case "tcp-upload", "tcp-download", "tcp-bidir":
		return s.handleTCP(ctx, control, req)
	case "udp-upload":
		return s.handleUDP(ctx, control, reader, req)
	default:
		return fmt.Errorf("unsupported mode %q", req.Mode)
	}
}

func handleLatency(control net.Conn, reader *bufio.Reader) error {
	if err := protocol.WriteJSONLine(control, protocol.Ready{Ready: true}); err != nil {
		return err
	}
	for {
		var probe protocol.Probe
		if err := protocol.ReadJSONLine(reader, &probe); err != nil {
			if errors.Is(err, net.ErrClosed) || errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
				return nil
			}
			return err
		}
		if err := protocol.WriteJSONLine(control, protocol.ProbeReply{Seq: probe.Seq}); err != nil {
			return err
		}
	}
}

func (s *Server) handleTCP(ctx context.Context, control net.Conn, req protocol.Request) error {
	token, err := throughput.NewToken()
	if err != nil {
		return err
	}
	ln, err := net.Listen("tcp", net.JoinHostPort(s.cfg.ListenHost, "0"))
	if err != nil {
		return fmt.Errorf("listen TCP data: %w", err)
	}
	defer ln.Close()
	port := ln.Addr().(*net.TCPAddr).Port
	if err := protocol.WriteJSONLine(control, protocol.Offer{Transport: "tcp", DataPort: port, Token: token}); err != nil {
		return err
	}
	if tcpLn, ok := ln.(*net.TCPListener); ok {
		_ = tcpLn.SetDeadline(time.Now().Add(15 * time.Second))
	}

	streams := make([]throughput.TCPStream, req.Streams)
	conns := make([]net.Conn, req.Streams)
	seen := make([]bool, req.Streams)
	for accepted := 0; accepted < req.Streams; accepted++ {
		conn, err := ln.Accept()
		if err != nil {
			closeConns(conns)
			return fmt.Errorf("accept TCP data: %w", err)
		}
		br := bufio.NewReaderSize(conn, 4096)
		var hello protocol.DataHello
		if err := protocol.ReadJSONLine(br, &hello); err != nil {
			_ = conn.Close()
			closeConns(conns)
			return fmt.Errorf("read data hello: %w", err)
		}
		if subtle.ConstantTimeCompare([]byte(hello.Token), []byte(token)) != 1 || hello.Stream < 0 || hello.Stream >= req.Streams || seen[hello.Stream] {
			_ = conn.Close()
			closeConns(conns)
			return errors.New("invalid TCP stream authentication/index")
		}
		if req.CongestionControl != "" {
			if err := sockopt.SetCongestionControl(conn, req.CongestionControl); err != nil {
				_ = conn.Close()
				closeConns(conns)
				return fmt.Errorf("set server congestion control stream %d: %w", hello.Stream, err)
			}
		}
		seen[hello.Stream] = true
		conns[hello.Stream] = conn
		streams[hello.Stream] = throughput.TCPStream{Conn: conn, Reader: br}
	}
	defer closeConns(conns)
	if err := protocol.WriteJSONLine(control, protocol.Ready{Ready: true, StartDelayMS: protocol.DefaultStartDelay.Milliseconds()}); err != nil {
		return err
	}
	startAt := time.Now().Add(protocol.DefaultStartDelay)
	warmup := time.Duration(req.WarmupMS) * time.Millisecond
	duration := time.Duration(req.DurationMS) * time.Millisecond
	sample := time.Duration(req.SampleIntervalMS) * time.Millisecond
	result := protocol.SessionResult{}
	measureStart := startAt.Add(warmup)
	measureEnd := measureStart.Add(duration)
	telemetryCh := make(chan struct {
		result protocol.EndpointTelemetry
		err    error
	}, 1)
	role := "receiver"
	if req.Mode == "tcp-download" {
		role = "sender"
	} else if req.Mode == "tcp-bidir" {
		role = "bidirectional"
	}
	go func() {
		r, err := tcpinfo.CaptureWindow(ctx, conns, role, measureStart, measureEnd, req.Diagnostics)
		telemetryCh <- struct {
			result protocol.EndpointTelemetry
			err    error
		}{r, err}
	}()

	switch req.Mode {
	case "tcp-upload":
		upload, err := throughput.ReceiveTCP(ctx, startAt, warmup, duration, sample, req.BufferSize, streams)
		if err != nil {
			return err
		}
		result.Upload = &upload
	case "tcp-download":
		if err := throughput.SendTCP(ctx, startAt, warmup, duration, req.BufferSize, conns); err != nil {
			return err
		}
	case "tcp-bidir":
		sendDone := make(chan error, 1)
		go func() { sendDone <- throughput.SendTCP(ctx, startAt, warmup, duration, req.BufferSize, conns) }()
		upload, recvErr := throughput.ReceiveTCP(ctx, startAt, warmup, duration, sample, req.BufferSize, streams)
		sendErr := <-sendDone
		if recvErr != nil {
			return recvErr
		}
		if sendErr != nil {
			return sendErr
		}
		result.Upload = &upload
	}
	tele := <-telemetryCh
	if tele.err != nil {
		return fmt.Errorf("server telemetry: %w", tele.err)
	}
	if req.Diagnostics {
		result.Telemetry = &tele.result
	}
	return protocol.WriteJSONLine(control, result)
}

func (s *Server) handleUDP(ctx context.Context, control net.Conn, reader *bufio.Reader, req protocol.Request) error {
	token, err := throughput.NewToken()
	if err != nil {
		return err
	}
	addr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(s.cfg.ListenHost, "0"))
	if err != nil {
		return err
	}
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return fmt.Errorf("listen UDP data: %w", err)
	}
	defer conn.Close()
	port := conn.LocalAddr().(*net.UDPAddr).Port
	if err := protocol.WriteJSONLine(control, protocol.Offer{Transport: "udp", DataPort: port, Token: token}); err != nil {
		return err
	}
	if err := protocol.WriteJSONLine(control, protocol.Ready{Ready: true, StartDelayMS: protocol.DefaultStartDelay.Milliseconds()}); err != nil {
		return err
	}
	startAt := time.Now().Add(protocol.DefaultStartDelay)
	udpResult, err := throughput.MeasureUDPServer(ctx, conn, token, startAt, req, reader)
	if err != nil {
		return err
	}
	return protocol.WriteJSONLine(control, protocol.SessionResult{UDP: &udpResult})
}

func validateRequest(req protocol.Request) error {
	if req.DurationMS < 100 || req.DurationMS > int64((24*time.Hour)/time.Millisecond) {
		return errors.New("duration out of range")
	}
	if req.WarmupMS < 0 || req.WarmupMS > int64((10*time.Minute)/time.Millisecond) {
		return errors.New("warmup out of range")
	}
	if req.SampleIntervalMS < 50 || req.SampleIntervalMS > 5000 {
		return errors.New("sample interval out of range")
	}
	if req.DurationMS/req.SampleIntervalMS > 2000 {
		return errors.New("too many samples; increase sample interval")
	}
	if req.Mode == "udp-upload" {
		if req.RateBitsPerSec == 0 || req.RateBitsPerSec > 100_000_000_000 {
			return errors.New("UDP rate out of range")
		}
		if req.PacketSize < 37 || req.PacketSize > 65507 {
			return errors.New("UDP packet size out of range")
		}
		if req.PacingQuantumUS < 100 || req.PacingQuantumUS > 10_000 {
			return errors.New("UDP pacing quantum out of range")
		}
		if req.TimestampMode != "" && req.TimestampMode != "userspace" && req.TimestampMode != "kernel" && req.TimestampMode != "hardware" {
			return errors.New("UDP timestamp mode must be userspace, kernel or hardware")
		}
		return nil
	}
	if req.Mode != "tcp-upload" && req.Mode != "tcp-download" && req.Mode != "tcp-bidir" {
		return fmt.Errorf("unsupported mode %q", req.Mode)
	}
	if len(req.CongestionControl) > 32 {
		return errors.New("congestion-control name too long")
	}
	for _, r := range req.CongestionControl {
		if !(r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '_' || r == '-') {
			return errors.New("invalid congestion-control name")
		}
	}
	if req.BufferSize < 4<<10 || req.BufferSize > 16<<20 {
		return errors.New("buffer size out of range")
	}
	if req.Streams < 1 || req.Streams > protocol.MaxStreams {
		return errors.New("stream count out of range")
	}
	if int64(req.Streams)*(req.DurationMS/req.SampleIntervalMS) > 20_000 {
		return errors.New("sample/stream matrix too large")
	}
	return nil
}

func closeConns(conns []net.Conn) {
	for _, conn := range conns {
		if conn != nil {
			_ = conn.Close()
		}
	}
}
