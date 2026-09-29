package server

import (
	"bufio"
	"context"
	"crypto/subtle"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"sync"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
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
	_ = control.SetDeadline(time.Now().Add(24*time.Hour + 15*time.Minute))
	reader := bufio.NewReaderSize(control, 4096)

	var req protocol.Request
	if err := protocol.ReadJSONLine(reader, &req); err != nil {
		return err
	}
	if err := validateRequest(req); err != nil {
		_ = protocol.WriteJSONLine(control, map[string]string{"error": err.Error()})
		return err
	}

	token, err := throughput.NewToken()
	if err != nil {
		return err
	}
	dataLn, err := net.Listen("tcp", net.JoinHostPort(s.cfg.ListenHost, "0"))
	if err != nil {
		return fmt.Errorf("listen data: %w", err)
	}
	defer dataLn.Close()

	port := dataLn.Addr().(*net.TCPAddr).Port
	if err := protocol.WriteJSONLine(control, protocol.Offer{DataPort: port, Token: token}); err != nil {
		return err
	}

	if tcpLn, ok := dataLn.(*net.TCPListener); ok {
		_ = tcpLn.SetDeadline(time.Now().Add(10 * time.Second))
	}
	dataConn, err := dataLn.Accept()
	if err != nil {
		return fmt.Errorf("accept data: %w", err)
	}
	defer dataConn.Close()

	dataReader := bufio.NewReaderSize(dataConn, 4096)
	gotToken, err := protocol.ReadTokenLine(dataReader)
	if err != nil {
		return fmt.Errorf("read data token: %w", err)
	}
	if subtle.ConstantTimeCompare([]byte(gotToken), []byte(token)) != 1 {
		return errors.New("invalid data token")
	}

	if err := protocol.WriteJSONLine(control, map[string]bool{"ready": true}); err != nil {
		return err
	}

	result, err := measureTCPUpload(ctx, dataReader, req)
	if err != nil {
		return err
	}
	return protocol.WriteJSONLine(control, result)
}

func measureTCPUpload(ctx context.Context, r io.Reader, req protocol.Request) (protocol.Result, error) {
	warmup := time.Duration(req.WarmupMS) * time.Millisecond
	duration := time.Duration(req.DurationMS) * time.Millisecond
	buf := make([]byte, req.BufferSize)
	start := time.Now()
	measureStart := start.Add(warmup)
	measureEnd := measureStart.Add(duration)
	var measured uint64

	for {
		n, err := r.Read(buf)
		now := time.Now()
		if n > 0 && !now.Before(measureStart) && now.Before(measureEnd) {
			measured += uint64(n)
		}
		if !now.Before(measureEnd) {
			break
		}
		select {
		case <-ctx.Done():
			return protocol.Result{}, ctx.Err()
		default:
		}
		if err != nil {
			if errors.Is(err, io.EOF) {
				break
			}
			return protocol.Result{}, fmt.Errorf("read data: %w", err)
		}
	}

	seconds := duration.Seconds()
	bps := float64(measured*8) / seconds
	return protocol.Result{
		Mode:            "tcp-upload",
		Bytes:           measured,
		DurationMS:      req.DurationMS,
		BitsPerSecond:   bps,
		MegabitsPerSec:  bps / 1_000_000,
		MebibytesPerSec: float64(measured) / seconds / (1 << 20),
	}, nil
}

func validateRequest(req protocol.Request) error {
	if req.Mode != "tcp-upload" {
		return fmt.Errorf("unsupported mode %q", req.Mode)
	}
	if req.DurationMS < 100 || req.DurationMS > int64((24*time.Hour)/time.Millisecond) {
		return errors.New("duration out of range")
	}
	if req.WarmupMS < 0 || req.WarmupMS > int64((10*time.Minute)/time.Millisecond) {
		return errors.New("warmup out of range")
	}
	if req.BufferSize < 4<<10 || req.BufferSize > 16<<20 {
		return errors.New("buffer size out of range")
	}
	return nil
}
