package throughput

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/diagnose"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/latency"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/sockopt"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/tcpinfo"
)

type ClientConfig struct {
	Host              string
	Port              int
	Direction         string
	Duration          time.Duration
	Warmup            time.Duration
	BufferSize        int
	DialTimeout       time.Duration
	SampleInterval    time.Duration
	ProbeInterval     time.Duration
	Streams           int
	Adaptive          bool
	MaxStreams        int
	ConvergencePct    float64
	Diagnostics       bool
	CongestionControl string
}

type TCPStream struct {
	Conn   net.Conn
	Reader io.Reader
}

func RunTCPSuite(ctx context.Context, cfg ClientConfig) (protocol.TCPTestResult, error) {
	if err := validateClientConfig(cfg); err != nil {
		return protocol.TCPTestResult{}, err
	}
	testID, err := NewToken()
	if err != nil {
		return protocol.TCPTestResult{}, err
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
		return protocol.TCPTestResult{}, fmt.Errorf("idle latency: %w", err)
	}

	stageStreams := []int{1}
	if cfg.Adaptive {
		for n := 2; n <= cfg.MaxStreams; n *= 2 {
			stageStreams = append(stageStreams, n)
		}
		if stageStreams[len(stageStreams)-1] != cfg.MaxStreams && cfg.MaxStreams > 1 {
			stageStreams = append(stageStreams, cfg.MaxStreams)
		}
	} else if cfg.Streams > 1 {
		stageStreams = append(stageStreams, cfg.Streams)
	}

	result := protocol.TCPTestResult{
		SchemaVersion: protocol.ResultSchemaVersion, ProtocolVersion: protocol.Version,
		TestID: testID, Transport: "tcp", MeasurementKind: "transport_goodput", CongestionControl: cfg.CongestionControl, Direction: cfg.Direction,
		DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(), SampleMS: cfg.SampleInterval.Milliseconds(),
		IdleLatency: idle, DiagnosticsEnabled: cfg.Diagnostics,
		Adaptive: protocol.AdaptiveResult{Enabled: cfg.Adaptive, SelectedStreams: 1, ConvergencePct: cfg.ConvergencePct, StopReason: "fixed stream count"},
	}

	var previousScore float64
	var bestScore float64
	var bestStage protocol.StageResult
	for i, streams := range stageStreams {
		stage, err := RunTCPStage(ctx, cfg, streams)
		if err != nil {
			return protocol.TCPTestResult{}, fmt.Errorf("tcp stage streams=%d: %w", streams, err)
		}
		result.Stages = append(result.Stages, stage)
		if i == 0 {
			result.SingleStream = stage
		}
		score := stageScore(stage)
		if i == 0 || score > bestScore {
			bestScore = score
			bestStage = stage
			result.Aggregate = stage
			result.Adaptive.SelectedStreams = streams
		}
		if cfg.Adaptive && i > 0 && previousScore > 0 {
			gain := (score - previousScore) / previousScore * 100
			if gain < cfg.ConvergencePct {
				result.Aggregate = bestStage
				result.Adaptive.SelectedStreams = bestStage.Streams
				result.Adaptive.StopReason = fmt.Sprintf("converged: %.2f%% marginal gain < %.2f%%", gain, cfg.ConvergencePct)
				if cfg.Diagnostics {
					result.Diagnostics = diagnose.EvaluateTCP(result)
				}
				return result, nil
			}
		}
		previousScore = score
	}
	if cfg.Adaptive {
		result.Adaptive.StopReason = "maximum stream count reached"
	}
	if cfg.Diagnostics {
		result.Diagnostics = diagnose.EvaluateTCP(result)
	}
	return result, nil
}

func RunTCPStage(ctx context.Context, cfg ClientConfig, streams int) (protocol.StageResult, error) {
	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	controlAddr := net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
	control, err := dialer.DialContext(ctx, "tcp", controlAddr)
	if err != nil {
		return protocol.StageResult{}, fmt.Errorf("dial control %s: %w", controlAddr, err)
	}
	defer control.Close()
	deadline := time.Now().Add(cfg.Warmup + cfg.Duration + 20*time.Second)
	_ = control.SetDeadline(deadline)
	reader := bufio.NewReaderSize(control, 4096)

	req := protocol.Request{Mode: "tcp-" + cfg.Direction, DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(), SampleIntervalMS: cfg.SampleInterval.Milliseconds(), BufferSize: cfg.BufferSize, Streams: streams, Diagnostics: cfg.Diagnostics, CongestionControl: cfg.CongestionControl}
	if err := protocol.WriteJSONLine(control, req); err != nil {
		return protocol.StageResult{}, fmt.Errorf("send request: %w", err)
	}
	var offer protocol.Offer
	if err := protocol.ReadJSONLine(reader, &offer); err != nil {
		return protocol.StageResult{}, fmt.Errorf("read offer: %w", err)
	}
	if offer.Transport != "tcp" || offer.DataPort <= 0 || offer.Token == "" {
		return protocol.StageResult{}, errors.New("invalid TCP data offer")
	}

	data := make([]net.Conn, streams)
	for i := 0; i < streams; i++ {
		addr := net.JoinHostPort(cfg.Host, strconv.Itoa(offer.DataPort))
		conn, err := dialer.DialContext(ctx, "tcp", addr)
		if err != nil {
			closeConns(data)
			return protocol.StageResult{}, fmt.Errorf("dial data stream %d: %w", i, err)
		}
		if cfg.CongestionControl != "" {
			if err := sockopt.SetCongestionControl(conn, cfg.CongestionControl); err != nil {
				_ = conn.Close()
				closeConns(data)
				return protocol.StageResult{}, fmt.Errorf("set congestion control stream %d: %w", i, err)
			}
		}
		data[i] = conn
		_ = conn.SetDeadline(deadline)
		if err := protocol.WriteJSONLine(conn, protocol.DataHello{Token: offer.Token, Stream: i}); err != nil {
			closeConns(data)
			return protocol.StageResult{}, fmt.Errorf("send data hello stream %d: %w", i, err)
		}
	}
	defer closeConns(data)

	var ready protocol.Ready
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		return protocol.StageResult{}, fmt.Errorf("read ready: %w", err)
	}
	if !ready.Ready {
		return protocol.StageResult{}, errors.New("server did not enter ready state")
	}
	startAt := time.Now().Add(time.Duration(ready.StartDelayMS) * time.Millisecond)
	measureStart := startAt.Add(cfg.Warmup)
	measureEnd := measureStart.Add(cfg.Duration)

	telemetryCh := make(chan struct {
		result protocol.EndpointTelemetry
		err    error
	}, 1)
	localRole := roleForDirection(cfg.Direction, true)
	go func() {
		r, err := tcpinfo.CaptureWindow(ctx, data, localRole, measureStart, measureEnd, cfg.Diagnostics)
		telemetryCh <- struct {
			result protocol.EndpointTelemetry
			err    error
		}{r, err}
	}()

	latencyCh := make(chan struct {
		result protocol.LatencyResult
		err    error
	}, 1)
	go func() {
		r, err := latency.MeasureBetween(ctx, latency.Config{Host: cfg.Host, Port: cfg.Port, DialTimeout: cfg.DialTimeout, Interval: cfg.ProbeInterval}, measureStart, measureEnd)
		latencyCh <- struct {
			result protocol.LatencyResult
			err    error
		}{r, err}
	}()

	streamReaders := make([]TCPStream, streams)
	for i, conn := range data {
		streamReaders[i] = TCPStream{Conn: conn, Reader: conn}
	}
	stage := protocol.StageResult{Streams: streams}
	var localDownload protocol.DirectionResult
	var sendErr, receiveErr error

	switch cfg.Direction {
	case "upload":
		sendErr = SendTCP(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.BufferSize, data)
	case "download":
		localDownload, receiveErr = ReceiveTCP(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.SampleInterval, cfg.BufferSize, streamReaders)
	case "bidir":
		sendDone := make(chan error, 1)
		go func() { sendDone <- SendTCP(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.BufferSize, data) }()
		localDownload, receiveErr = ReceiveTCP(ctx, startAt, cfg.Warmup, cfg.Duration, cfg.SampleInterval, cfg.BufferSize, streamReaders)
		sendErr = <-sendDone
	default:
		return protocol.StageResult{}, fmt.Errorf("unsupported TCP direction %q", cfg.Direction)
	}
	if sendErr != nil {
		return protocol.StageResult{}, sendErr
	}
	if receiveErr != nil {
		return protocol.StageResult{}, receiveErr
	}
	tele := <-telemetryCh
	if tele.err != nil {
		return protocol.StageResult{}, fmt.Errorf("local telemetry: %w", tele.err)
	}
	if cfg.Diagnostics {
		stage.LocalTelemetry = &tele.result
	}
	// The receiver stops exactly at the measurement boundary. Closing data sockets here
	// prevents the peer's guard traffic from blocking on a socket that is no longer read.
	closeConns(data)

	var remote protocol.SessionResult
	if err := protocol.ReadJSONLine(reader, &remote); err != nil {
		return protocol.StageResult{}, fmt.Errorf("read session result: %w", err)
	}
	if cfg.Diagnostics && remote.Telemetry != nil {
		stage.RemoteTelemetry = remote.Telemetry
	}
	if cfg.Direction == "upload" || cfg.Direction == "bidir" {
		if remote.Upload == nil {
			return protocol.StageResult{}, errors.New("server omitted upload result")
		}
		stage.Upload = remote.Upload
	}
	if cfg.Direction == "download" || cfg.Direction == "bidir" {
		stage.Download = &localDownload
	}
	lat := <-latencyCh
	if lat.err != nil {
		return protocol.StageResult{}, fmt.Errorf("loaded latency: %w", lat.err)
	}
	stage.LoadedLatency = lat.result
	return stage, nil
}

func ReceiveTCP(ctx context.Context, startAt time.Time, warmup, duration, sampleInterval time.Duration, bufferSize int, streams []TCPStream) (protocol.DirectionResult, error) {
	measureStart := startAt.Add(warmup)
	measureEnd := measureStart.Add(duration)
	counters := make([]atomic.Uint64, len(streams))
	counterPtrs := make([]*atomic.Uint64, len(streams))
	for i := range counters {
		counterPtrs[i] = &counters[i]
	}

	samplesCh := make(chan []protocol.ThroughputSample, 1)
	go func() {
		samplesCh <- metrics.SampleCounters(ctx, measureStart, measureEnd, sampleInterval, counterPtrs)
	}()

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
			_ = streams[i].Conn.SetReadDeadline(measureEnd.Add(protocol.DefaultGuardTime))
			buf := make([]byte, bufferSize)
			for {
				n, err := streams[i].Reader.Read(buf)
				now := time.Now()
				if n > 0 && metrics.InWindow(now, measureStart, measureEnd) {
					counters[i].Add(uint64(n))
				}
				if !now.Before(measureEnd) {
					return
				}
				if err != nil {
					if errors.Is(err, io.EOF) || isExpectedNetClose(err) {
						if !now.Before(measureEnd) {
							return
						}
					}
					errCh <- fmt.Errorf("read stream %d: %w", i, err)
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
	samples := <-samplesCh
	return metrics.BuildDirection(duration, counterPtrs, samples), nil
}

func SendTCP(ctx context.Context, startAt time.Time, warmup, duration time.Duration, bufferSize int, conns []net.Conn) error {
	measureEnd := startAt.Add(warmup).Add(duration)
	stopAt := measureEnd.Add(protocol.DefaultGuardTime)
	var wg sync.WaitGroup
	errCh := make(chan error, len(conns))
	for i, conn := range conns {
		i, conn := i, conn
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := metrics.WaitUntil(ctx, startAt); err != nil {
				errCh <- err
				return
			}
			_ = conn.SetWriteDeadline(stopAt.Add(protocol.DefaultGuardTime))
			buf := make([]byte, bufferSize)
			for time.Now().Before(stopAt) {
				if _, err := conn.Write(buf); err != nil {
					now := time.Now()
					if isExpectedNetClose(err) && !now.Before(measureEnd) {
						return
					}
					errCh <- fmt.Errorf("write stream %d: %w", i, err)
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

func NewToken() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("random token: %w", err)
	}
	return hex.EncodeToString(b[:]), nil
}

func validateClientConfig(cfg ClientConfig) error {
	if cfg.Host == "" {
		return errors.New("host is required")
	}
	if cfg.Port < 1 || cfg.Port > 65535 {
		return errors.New("port out of range")
	}
	if cfg.Direction != "upload" && cfg.Direction != "download" && cfg.Direction != "bidir" {
		return errors.New("direction must be upload, download or bidir")
	}
	if cfg.Duration < 100*time.Millisecond || cfg.Duration > 24*time.Hour {
		return errors.New("duration must be between 100ms and 24h")
	}
	if cfg.Warmup < 0 || cfg.Warmup > 10*time.Minute {
		return errors.New("warmup must be between 0 and 10m")
	}
	if cfg.BufferSize < 4<<10 || cfg.BufferSize > 16<<20 {
		return errors.New("buffer must be between 4KiB and 16MiB")
	}
	if cfg.SampleInterval < 50*time.Millisecond || cfg.SampleInterval > 5*time.Second {
		return errors.New("sample interval must be between 50ms and 5s")
	}
	if cfg.Duration/cfg.SampleInterval > 2000 {
		return errors.New("too many samples; increase sample interval")
	}
	if cfg.ProbeInterval < 10*time.Millisecond || cfg.ProbeInterval > 5*time.Second {
		return errors.New("probe interval must be between 10ms and 5s")
	}
	if cfg.Streams < 1 || cfg.Streams > protocol.MaxStreams || cfg.MaxStreams < 1 || cfg.MaxStreams > protocol.MaxStreams {
		return fmt.Errorf("stream counts must be between 1 and %d", protocol.MaxStreams)
	}
	maxUsed := cfg.Streams
	if cfg.Adaptive && cfg.MaxStreams > maxUsed {
		maxUsed = cfg.MaxStreams
	}
	if int64(maxUsed)*int64(cfg.Duration/cfg.SampleInterval) > 20_000 {
		return errors.New("sample/stream matrix too large; increase sample interval or reduce streams")
	}
	if cfg.ConvergencePct < 0 || cfg.ConvergencePct > 100 {
		return errors.New("convergence percent must be between 0 and 100")
	}
	if cfg.DialTimeout <= 0 {
		return errors.New("dial timeout must be positive")
	}
	return nil
}

func roleForDirection(direction string, local bool) string {
	switch direction {
	case "upload":
		if local {
			return "sender"
		}
		return "receiver"
	case "download":
		if local {
			return "receiver"
		}
		return "sender"
	case "bidir":
		return "bidirectional"
	default:
		return "unknown"
	}
}

func stageScore(stage protocol.StageResult) float64 {
	var score float64
	if stage.Upload != nil {
		score += stage.Upload.BitsPerSecond
	}
	if stage.Download != nil {
		score += stage.Download.BitsPerSecond
	}
	return score
}

func closeConns(conns []net.Conn) {
	for _, conn := range conns {
		if conn != nil {
			_ = conn.Close()
		}
	}
}

func isExpectedNetClose(err error) bool {
	var ne net.Error
	return errors.As(err, &ne) && ne.Timeout() || errors.Is(err, net.ErrClosed) || errors.Is(err, io.EOF) || errors.Is(err, syscall.EPIPE) || errors.Is(err, syscall.ECONNRESET)
}
