package advanced

import (
	"bufio"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	quic "github.com/quic-go/quic-go"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/latency"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

const quicALPN = "netx/5"

type QUICConfig struct {
	Host           string
	Port           int
	Direction      string
	Duration       time.Duration
	Warmup         time.Duration
	BufferSize     int
	DialTimeout    time.Duration
	SampleInterval time.Duration
	ProbeInterval  time.Duration
	Streams        int
}

type qstream struct {
	stream *quic.Stream
	reader io.Reader
}

func RunQUICSuite(ctx context.Context, cfg QUICConfig) (protocol.QUICTestResult, error) {
	if err := validateQUICConfig(cfg); err != nil {
		return protocol.QUICTestResult{}, err
	}
	testID, err := throughput.NewToken()
	if err != nil {
		return protocol.QUICTestResult{}, err
	}
	idleDuration := 5 * cfg.ProbeInterval
	if idleDuration < 250*time.Millisecond {
		idleDuration = 250 * time.Millisecond
	}
	if idleDuration > time.Second {
		idleDuration = time.Second
	}
	idle, err := latency.MeasureDuration(ctx, latency.Config{Host: cfg.Host, Port: cfg.Port, DialTimeout: cfg.DialTimeout, Interval: cfg.ProbeInterval}, idleDuration)
	if err != nil {
		return protocol.QUICTestResult{}, fmt.Errorf("idle latency: %w", err)
	}
	result := protocol.QUICTestResult{
		SchemaVersion: protocol.ResultSchemaVersion, ProtocolVersion: protocol.Version, TestID: testID,
		MeasurementKind: "transport_goodput", Transport: "quic", Direction: cfg.Direction,
		DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(), SampleMS: cfg.SampleInterval.Milliseconds(),
		IdleLatency: idle,
	}
	stageStreams := []int{1}
	if cfg.Streams > 1 {
		stageStreams = append(stageStreams, cfg.Streams)
	}
	var best float64
	for i, n := range stageStreams {
		stage, err := runQUICStage(ctx, cfg, n)
		if err != nil {
			return protocol.QUICTestResult{}, fmt.Errorf("quic stage streams=%d: %w", n, err)
		}
		result.Stages = append(result.Stages, stage)
		if i == 0 {
			result.SingleStream = stage
		}
		score := stageGoodput(stage)
		if i == 0 || score > best {
			best = score
			result.Aggregate = stage
		}
	}
	return result, nil
}

func runQUICStage(ctx context.Context, cfg QUICConfig, streams int) (protocol.StageResult, error) {
	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	control, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port)))
	if err != nil {
		return protocol.StageResult{}, err
	}
	defer control.Close()
	_ = control.SetDeadline(time.Now().Add(cfg.Warmup + cfg.Duration + 20*time.Second))
	reader := bufio.NewReaderSize(control, 4096)
	req := protocol.Request{
		Mode: "quic-" + cfg.Direction, DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(),
		SampleIntervalMS: cfg.SampleInterval.Milliseconds(), BufferSize: cfg.BufferSize, Streams: streams,
	}
	if err := protocol.WriteJSONLine(control, req); err != nil {
		return protocol.StageResult{}, err
	}
	var offer protocol.Offer
	if err := protocol.ReadJSONLine(reader, &offer); err != nil {
		return protocol.StageResult{}, err
	}
	if offer.Transport != "quic" || offer.DataPort <= 0 || offer.Token == "" {
		return protocol.StageResult{}, errors.New("invalid QUIC offer")
	}
	tlsConf := &tls.Config{InsecureSkipVerify: true, NextProtos: []string{quicALPN}, MinVersion: tls.VersionTLS13} // ephemeral test certificate; session token authenticates the data channel to control.
	qconn, err := quic.DialAddr(ctx, net.JoinHostPort(cfg.Host, strconv.Itoa(offer.DataPort)), tlsConf, &quic.Config{
		HandshakeIdleTimeout: cfg.DialTimeout, MaxIdleTimeout: cfg.Warmup + cfg.Duration + 10*time.Second,
	})
	if err != nil {
		return protocol.StageResult{}, fmt.Errorf("dial QUIC: %w", err)
	}
	defer qconn.CloseWithError(0, "done")
	qstreams := make([]qstream, streams)
	for i := 0; i < streams; i++ {
		st, err := qconn.OpenStreamSync(ctx)
		if err != nil {
			return protocol.StageResult{}, err
		}
		if err := protocol.WriteJSONLine(st, protocol.DataHello{Token: offer.Token, Stream: i}); err != nil {
			return protocol.StageResult{}, err
		}
		qstreams[i] = qstream{stream: st, reader: st}
	}
	var ready protocol.Ready
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		return protocol.StageResult{}, err
	}
	if !ready.Ready {
		return protocol.StageResult{}, errors.New("QUIC server did not enter ready state")
	}
	startAt := time.Now().Add(time.Duration(ready.StartDelayMS) * time.Millisecond)
	measureStart := startAt.Add(cfg.Warmup)
	measureEnd := measureStart.Add(cfg.Duration)
	latCh := make(chan struct {
		r protocol.LatencyResult
		e error
	}, 1)
	go func() {
		r, e := latency.MeasureBetween(ctx, latency.Config{Host: cfg.Host, Port: cfg.Port, DialTimeout: cfg.DialTimeout, Interval: cfg.ProbeInterval}, measureStart, measureEnd)
		latCh <- struct {
			r protocol.LatencyResult
			e error
		}{r, e}
	}()
	stage := protocol.StageResult{Streams: streams}
	var localDown protocol.DirectionResult
	switch cfg.Direction {
	case "upload":
		err = sendQUIC(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.BufferSize, qstreams)
	case "download":
		localDown, err = receiveQUIC(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.SampleInterval, cfg.BufferSize, qstreams)
	case "bidir":
		done := make(chan error, 1)
		go func() { done <- sendQUIC(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.BufferSize, qstreams) }()
		localDown, err = receiveQUIC(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.SampleInterval, cfg.BufferSize, qstreams)
		if sendErr := <-done; err == nil {
			err = sendErr
		}
	}
	if err != nil {
		return protocol.StageResult{}, err
	}
	for _, st := range qstreams {
		st.stream.CancelRead(0)
		_ = st.stream.Close()
	}
	var remote protocol.SessionResult
	if err := protocol.ReadJSONLine(reader, &remote); err != nil {
		return protocol.StageResult{}, fmt.Errorf("read QUIC session result: %w", err)
	}
	if cfg.Direction == "upload" || cfg.Direction == "bidir" {
		if remote.Upload == nil {
			return protocol.StageResult{}, errors.New("QUIC server omitted upload result")
		}
		stage.Upload = remote.Upload
	}
	if cfg.Direction == "download" || cfg.Direction == "bidir" {
		stage.Download = &localDown
	}
	lat := <-latCh
	if lat.e != nil {
		return protocol.StageResult{}, lat.e
	}
	stage.LoadedLatency = lat.r
	return stage, nil
}

func handleQUICServer(ctx context.Context, control net.Conn, listenHost string, req protocol.Request) error {
	if err := validateQUICRequest(req); err != nil {
		return err
	}
	token, err := throughput.NewToken()
	if err != nil {
		return err
	}
	tlsConf, err := makeQUICServerTLS()
	if err != nil {
		return err
	}
	listener, err := quic.ListenAddr(net.JoinHostPort(listenHost, "0"), tlsConf, &quic.Config{
		MaxIncomingStreams: int64(protocol.MaxStreams + 4), HandshakeIdleTimeout: protocol.DefaultDial,
		MaxIdleTimeout: time.Duration(req.WarmupMS+req.DurationMS)*time.Millisecond + 10*time.Second,
	})
	if err != nil {
		return fmt.Errorf("listen QUIC: %w", err)
	}
	defer listener.Close()
	_, portText, err := net.SplitHostPort(listener.Addr().String())
	if err != nil {
		return err
	}
	port, _ := strconv.Atoi(portText)
	if err := protocol.WriteJSONLine(control, protocol.Offer{Transport: "quic", DataPort: port, Token: token}); err != nil {
		return err
	}
	acceptCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	qconn, err := listener.Accept(acceptCtx)
	if err != nil {
		return err
	}
	defer qconn.CloseWithError(0, "done")
	streams := make([]qstream, req.Streams)
	seen := make([]bool, req.Streams)
	for accepted := 0; accepted < req.Streams; accepted++ {
		st, err := qconn.AcceptStream(acceptCtx)
		if err != nil {
			return err
		}
		br := bufio.NewReaderSize(st, 4096)
		var hello protocol.DataHello
		if err := protocol.ReadJSONLine(br, &hello); err != nil {
			return err
		}
		if hello.Token != token || hello.Stream < 0 || hello.Stream >= req.Streams || seen[hello.Stream] {
			return errors.New("invalid QUIC stream authentication/index")
		}
		seen[hello.Stream] = true
		streams[hello.Stream] = qstream{stream: st, reader: br}
	}
	if err := protocol.WriteJSONLine(control, protocol.Ready{Ready: true, StartDelayMS: protocol.DefaultStartDelay.Milliseconds()}); err != nil {
		return err
	}
	startAt := time.Now().Add(protocol.DefaultStartDelay)
	warmup := time.Duration(req.WarmupMS) * time.Millisecond
	duration := time.Duration(req.DurationMS) * time.Millisecond
	sample := time.Duration(req.SampleIntervalMS) * time.Millisecond
	result := protocol.SessionResult{}
	switch req.Mode {
	case "quic-upload":
		upload, err := receiveQUIC(ctx, startAt, warmup, duration, sample, req.BufferSize, streams)
		if err != nil {
			return err
		}
		result.Upload = &upload
	case "quic-download":
		if err := sendQUIC(ctx, startAt, warmup, duration, req.BufferSize, streams); err != nil {
			return err
		}
	case "quic-bidir":
		done := make(chan error, 1)
		go func() { done <- sendQUIC(ctx, startAt, warmup, duration, req.BufferSize, streams) }()
		upload, recvErr := receiveQUIC(ctx, startAt, warmup, duration, sample, req.BufferSize, streams)
		sendErr := <-done
		if recvErr != nil {
			return recvErr
		}
		if sendErr != nil {
			return sendErr
		}
		result.Upload = &upload
	default:
		return fmt.Errorf("unsupported QUIC mode %q", req.Mode)
	}
	for _, st := range streams {
		st.stream.CancelRead(0)
		_ = st.stream.Close()
	}
	return protocol.WriteJSONLine(control, result)
}

func receiveQUIC(ctx context.Context, startAt time.Time, warmup, duration, sampleInterval time.Duration, bufferSize int, streams []qstream) (protocol.DirectionResult, error) {
	measureStart := startAt.Add(warmup)
	measureEnd := measureStart.Add(duration)
	counters := make([]atomic.Uint64, len(streams))
	ptrs := make([]*atomic.Uint64, len(streams))
	for i := range counters {
		ptrs[i] = &counters[i]
	}
	samplesCh := make(chan []protocol.ThroughputSample, 1)
	go func() { samplesCh <- metrics.SampleCounters(ctx, measureStart, measureEnd, sampleInterval, ptrs) }()
	var wg sync.WaitGroup
	errCh := make(chan error, len(streams))
	for i := range streams {
		i := i
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := metrics.WaitUntil(ctx, startAt); err != nil {
				errCh <- err
				return
			}
			_ = streams[i].stream.SetReadDeadline(measureEnd.Add(protocol.DefaultGuardTime))
			buf := make([]byte, bufferSize)
			for {
				n, err := streams[i].reader.Read(buf)
				now := time.Now()
				if n > 0 && metrics.InWindow(now, measureStart, measureEnd) {
					counters[i].Add(uint64(n))
				}
				if !now.Before(measureEnd) {
					return
				}
				if err != nil {
					if errors.Is(err, io.EOF) && !now.Before(measureEnd) {
						return
					}
					errCh <- err
					return
				}
				select {
				case <-ctx.Done():
					errCh <- ctx.Err()
					return
				default:
				}
			}
		}()
	}
	wg.Wait()
	close(errCh)
	for err := range errCh {
		if err != nil {
			return protocol.DirectionResult{}, err
		}
	}
	return metrics.BuildDirection(duration, ptrs, <-samplesCh), nil
}

func sendQUIC(ctx context.Context, startAt time.Time, warmup, duration time.Duration, bufferSize int, streams []qstream) error {
	stopAt := startAt.Add(warmup).Add(duration)
	var wg sync.WaitGroup
	errCh := make(chan error, len(streams))
	for i := range streams {
		i := i
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := metrics.WaitUntil(ctx, startAt); err != nil {
				errCh <- err
				return
			}
			_ = streams[i].stream.SetWriteDeadline(stopAt.Add(protocol.DefaultGuardTime))
			buf := make([]byte, bufferSize)
			for time.Now().Before(stopAt) {
				if _, err := streams[i].stream.Write(buf); err != nil {
					if !time.Now().Before(stopAt) {
						return
					}
					errCh <- err
					return
				}
				select {
				case <-ctx.Done():
					errCh <- ctx.Err()
					return
				default:
				}
			}
		}()
	}
	wg.Wait()
	close(errCh)
	for err := range errCh {
		if err != nil {
			return err
		}
	}
	return nil
}

func makeQUICServerTLS() (*tls.Config, error) {
	_, key, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 120))
	if err != nil {
		return nil, err
	}
	now := time.Now()
	template := x509.Certificate{
		SerialNumber: serial, NotBefore: now.Add(-time.Minute), NotAfter: now.Add(24 * time.Hour),
		KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, &template, &template, key.Public(), key)
	if err != nil {
		return nil, err
	}
	return &tls.Config{
		MinVersion: tls.VersionTLS13, NextProtos: []string{quicALPN},
		Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}},
	}, nil
}

func validateQUICConfig(cfg QUICConfig) error {
	if cfg.Host == "" || cfg.Port < 1 || cfg.Port > 65535 {
		return errors.New("valid host and port are required")
	}
	req := protocol.Request{
		Mode: "quic-" + cfg.Direction, DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(),
		SampleIntervalMS: cfg.SampleInterval.Milliseconds(), BufferSize: cfg.BufferSize, Streams: cfg.Streams,
	}
	if err := validateQUICRequest(req); err != nil {
		return err
	}
	if cfg.ProbeInterval < 10*time.Millisecond || cfg.ProbeInterval > 5*time.Second {
		return errors.New("probe interval must be between 10ms and 5s")
	}
	if cfg.DialTimeout <= 0 {
		return errors.New("dial timeout must be positive")
	}
	return nil
}

func validateQUICRequest(req protocol.Request) error {
	if req.Mode != "quic-upload" && req.Mode != "quic-download" && req.Mode != "quic-bidir" {
		return errors.New("QUIC direction must be upload, download or bidir")
	}
	if req.DurationMS < 100 || req.DurationMS > int64((24*time.Hour)/time.Millisecond) || req.WarmupMS < 0 {
		return errors.New("invalid QUIC duration/warmup")
	}
	if req.SampleIntervalMS < 50 || req.SampleIntervalMS > 5000 || req.DurationMS/req.SampleIntervalMS > 2000 {
		return errors.New("invalid QUIC sample interval")
	}
	if req.BufferSize < 4<<10 || req.BufferSize > 16<<20 {
		return errors.New("QUIC buffer size out of range")
	}
	if req.Streams < 1 || req.Streams > protocol.MaxStreams {
		return errors.New("QUIC stream count out of range")
	}
	return nil
}

func stageGoodput(stage protocol.StageResult) float64 {
	var n float64
	if stage.Upload != nil {
		n += stage.Upload.BitsPerSecond
	}
	if stage.Download != nil {
		n += stage.Download.BitsPerSecond
	}
	return n
}
