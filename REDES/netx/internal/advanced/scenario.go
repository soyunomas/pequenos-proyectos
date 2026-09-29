package advanced

import (
	"bufio"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

type ScenarioConfig struct {
	Host           string
	Port           int
	DialTimeout    time.Duration
	Duration       time.Duration
	Profile        string
	MessageSize    int
	BurstMessages  int
	BurstPause     time.Duration
	RateBitsPerSec uint64
}

func RunScenario(ctx context.Context, cfg ScenarioConfig) (protocol.ScenarioResult, error) {
	if err := validateScenarioConfig(cfg); err != nil {
		return protocol.ScenarioResult{}, err
	}
	testID, err := throughput.NewToken()
	if err != nil {
		return protocol.ScenarioResult{}, err
	}
	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	control, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port)))
	if err != nil {
		return protocol.ScenarioResult{}, err
	}
	defer control.Close()
	_ = control.SetDeadline(time.Now().Add(cfg.Duration + 20*time.Second))
	reader := bufio.NewReaderSize(control, 4096)
	req := protocol.Request{
		Mode: "scenario", Profile: cfg.Profile, DurationMS: cfg.Duration.Milliseconds(), MessageSize: cfg.MessageSize,
		BurstMessages: cfg.BurstMessages, BurstPauseMS: cfg.BurstPause.Milliseconds(), ScenarioRateBPS: cfg.RateBitsPerSec,
	}
	if err := protocol.WriteJSONLine(control, req); err != nil {
		return protocol.ScenarioResult{}, err
	}
	var offer protocol.Offer
	if err := protocol.ReadJSONLine(reader, &offer); err != nil {
		return protocol.ScenarioResult{}, err
	}
	if offer.Transport != "tcp-scenario" || offer.DataPort <= 0 || offer.Token == "" {
		return protocol.ScenarioResult{}, errors.New("invalid scenario offer")
	}
	data, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(cfg.Host, strconv.Itoa(offer.DataPort)))
	if err != nil {
		return protocol.ScenarioResult{}, err
	}
	defer data.Close()
	if err := protocol.WriteJSONLine(data, protocol.DataHello{Token: offer.Token, Stream: 0}); err != nil {
		return protocol.ScenarioResult{}, err
	}
	var ready protocol.Ready
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		return protocol.ScenarioResult{}, err
	}
	if !ready.Ready {
		return protocol.ScenarioResult{}, errors.New("scenario server did not enter ready state")
	}
	startAt := time.Now().Add(time.Duration(ready.StartDelayMS) * time.Millisecond)
	local, err := runScenarioClient(ctx, data, startAt, cfg)
	if err != nil {
		return protocol.ScenarioResult{}, err
	}
	_ = data.Close()
	var remote protocol.ScenarioResult
	if err := protocol.ReadJSONLine(reader, &remote); err != nil {
		return protocol.ScenarioResult{}, fmt.Errorf("read scenario result: %w", err)
	}
	local.SchemaVersion = protocol.ResultSchemaVersion
	local.ProtocolVersion = protocol.Version
	local.TestID = testID
	local.MeasurementKind = "application_scenario"
	local.Transport = "tcp"
	if cfg.Profile == "streaming" {
		local.PayloadBytesReceived = remote.PayloadBytesReceived
		local.PayloadBitsPerSecond = float64(remote.PayloadBytesReceived*8) / cfg.Duration.Seconds()
	}
	return local, nil
}

func handleScenarioServer(ctx context.Context, control net.Conn, listenHost string, req protocol.Request) error {
	cfg := ScenarioConfig{
		Duration: time.Duration(req.DurationMS) * time.Millisecond, Profile: req.Profile, MessageSize: req.MessageSize,
		BurstMessages: req.BurstMessages, BurstPause: time.Duration(req.BurstPauseMS) * time.Millisecond,
		RateBitsPerSec: req.ScenarioRateBPS, DialTimeout: protocol.DefaultDial, Host: listenHost, Port: protocol.DefaultPort,
	}
	if err := validateScenarioConfig(cfg); err != nil {
		return err
	}
	token, err := throughput.NewToken()
	if err != nil {
		return err
	}
	ln, err := net.Listen("tcp", net.JoinHostPort(listenHost, "0"))
	if err != nil {
		return err
	}
	defer ln.Close()
	port := ln.Addr().(*net.TCPAddr).Port
	if err := protocol.WriteJSONLine(control, protocol.Offer{Transport: "tcp-scenario", DataPort: port, Token: token}); err != nil {
		return err
	}
	if tcpLn, ok := ln.(*net.TCPListener); ok {
		_ = tcpLn.SetDeadline(time.Now().Add(15 * time.Second))
	}
	data, err := ln.Accept()
	if err != nil {
		return err
	}
	defer data.Close()
	br := bufio.NewReaderSize(data, 4096)
	var hello protocol.DataHello
	if err := protocol.ReadJSONLine(br, &hello); err != nil {
		return err
	}
	if hello.Token != token || hello.Stream != 0 {
		return errors.New("invalid scenario data authentication")
	}
	if err := protocol.WriteJSONLine(control, protocol.Ready{Ready: true, StartDelayMS: protocol.DefaultStartDelay.Milliseconds()}); err != nil {
		return err
	}
	startAt := time.Now().Add(protocol.DefaultStartDelay)
	if err := metrics.WaitUntil(ctx, startAt); err != nil {
		return err
	}
	result, err := runScenarioServer(ctx, data, br, cfg)
	if err != nil {
		return err
	}
	result.SchemaVersion = protocol.ResultSchemaVersion
	result.ProtocolVersion = protocol.Version
	result.MeasurementKind = "application_scenario"
	result.Transport = "tcp"
	return protocol.WriteJSONLine(control, result)
}

func runScenarioClient(ctx context.Context, conn net.Conn, startAt time.Time, cfg ScenarioConfig) (protocol.ScenarioResult, error) {
	if err := metrics.WaitUntil(ctx, startAt); err != nil {
		return protocol.ScenarioResult{}, err
	}
	stopAt := startAt.Add(cfg.Duration)
	_ = conn.SetDeadline(stopAt.Add(2 * time.Second))
	payload := make([]byte, cfg.MessageSize)
	result := protocol.ScenarioResult{Profile: cfg.Profile, DurationMS: cfg.Duration.Milliseconds(), MessageSize: cfg.MessageSize, TargetRateBPS: cfg.RateBitsPerSec}
	switch cfg.Profile {
	case "request-response", "small-message":
		for time.Now().Before(stopAt) {
			start := time.Now()
			if err := writeFrame(conn, payload); err != nil {
				return result, err
			}
			reply, err := readFrame(bufio.NewReaderSize(conn, cfg.MessageSize+16), cfg.MessageSize)
			if err != nil {
				return result, err
			}
			if len(reply) != cfg.MessageSize {
				return result, errors.New("scenario echo length mismatch")
			}
			result.Requests++
			result.PayloadBytesSent += uint64(cfg.MessageSize)
			result.PayloadBytesReceived += uint64(len(reply))
			result.Samples = append(result.Samples, protocol.ScenarioSample{OffsetMS: time.Since(startAt).Milliseconds(), LatencyMS: float64(time.Since(start)) / float64(time.Millisecond), Bytes: cfg.MessageSize})
		}
	case "bursty":
		br := bufio.NewReaderSize(conn, maxInt(4096, cfg.MessageSize+16))
		for time.Now().Before(stopAt) {
			burstStart := time.Now()
			sent := 0
			for i := 0; i < cfg.BurstMessages && time.Now().Before(stopAt); i++ {
				if err := writeFrame(conn, payload); err != nil {
					return result, err
				}
				sent++
				result.PayloadBytesSent += uint64(cfg.MessageSize)
			}
			for i := 0; i < sent; i++ {
				reply, err := readFrame(br, cfg.MessageSize)
				if err != nil {
					return result, err
				}
				result.PayloadBytesReceived += uint64(len(reply))
				result.Requests++
			}
			if sent > 0 {
				result.Samples = append(result.Samples, protocol.ScenarioSample{OffsetMS: time.Since(startAt).Milliseconds(), LatencyMS: float64(time.Since(burstStart)) / float64(time.Millisecond), Bytes: sent * cfg.MessageSize})
			}
			if cfg.BurstPause > 0 {
				if err := metrics.WaitUntil(ctx, time.Now().Add(cfg.BurstPause)); err != nil {
					return result, err
				}
			}
		}
	case "streaming":
		if err := runStreamingClient(ctx, conn, startAt, stopAt, payload, cfg.RateBitsPerSec, &result); err != nil {
			return result, err
		}
	default:
		return result, fmt.Errorf("unsupported scenario profile %q", cfg.Profile)
	}
	result.OperationsPerSecond = float64(result.Requests) / cfg.Duration.Seconds()
	if cfg.Profile != "streaming" {
		result.PayloadBitsPerSecond = float64(result.PayloadBytesReceived*8) / cfg.Duration.Seconds()
		result.Latency = summarizeScenarioLatency(result.Samples)
	}
	return result, nil
}

func runScenarioServer(ctx context.Context, conn net.Conn, br *bufio.Reader, cfg ScenarioConfig) (protocol.ScenarioResult, error) {
	result := protocol.ScenarioResult{Profile: cfg.Profile, DurationMS: cfg.Duration.Milliseconds(), MessageSize: cfg.MessageSize, TargetRateBPS: cfg.RateBitsPerSec}
	_ = conn.SetDeadline(time.Now().Add(cfg.Duration + 5*time.Second))
	for {
		frame, err := readFrame(br, cfg.MessageSize)
		if err != nil {
			if errors.Is(err, io.EOF) || errors.Is(err, net.ErrClosed) {
				break
			}
			var ne net.Error
			if errors.As(err, &ne) && ne.Timeout() {
				break
			}
			return result, err
		}
		result.Requests++
		result.PayloadBytesReceived += uint64(len(frame))
		if cfg.Profile != "streaming" {
			if err := writeFrame(conn, frame); err != nil {
				return result, err
			}
			result.PayloadBytesSent += uint64(len(frame))
		}
		select {
		case <-ctx.Done():
			return result, ctx.Err()
		default:
		}
	}
	if cfg.Duration > 0 {
		result.OperationsPerSecond = float64(result.Requests) / cfg.Duration.Seconds()
		result.PayloadBitsPerSecond = float64(result.PayloadBytesReceived*8) / cfg.Duration.Seconds()
	}
	return result, nil
}

func runStreamingClient(ctx context.Context, conn net.Conn, startAt, stopAt time.Time, payload []byte, rate uint64, result *protocol.ScenarioResult) error {
	frameBits := uint64((len(payload) + 4) * 8)
	if rate == 0 {
		return errors.New("streaming target rate must be positive")
	}
	interval := time.Duration(float64(frameBits) / float64(rate) * float64(time.Second))
	if interval < 100*time.Microsecond {
		interval = 100 * time.Microsecond
	}
	next := startAt
	for time.Now().Before(stopAt) {
		if err := metrics.WaitUntil(ctx, next); err != nil {
			return err
		}
		if err := writeFrame(conn, payload); err != nil {
			return err
		}
		result.Requests++
		result.PayloadBytesSent += uint64(len(payload))
		next = next.Add(interval)
		if next.Before(time.Now().Add(-interval)) {
			next = time.Now().Add(interval)
		}
	}
	result.PayloadBitsPerSecond = float64(result.PayloadBytesSent*8) / stopAt.Sub(startAt).Seconds()
	return nil
}

func writeFrame(w io.Writer, payload []byte) error {
	var header [4]byte
	binary.BigEndian.PutUint32(header[:], uint32(len(payload)))
	if _, err := w.Write(header[:]); err != nil {
		return err
	}
	_, err := w.Write(payload)
	return err
}

func readFrame(r io.Reader, maxSize int) ([]byte, error) {
	var header [4]byte
	if _, err := io.ReadFull(r, header[:]); err != nil {
		return nil, err
	}
	n := int(binary.BigEndian.Uint32(header[:]))
	if n < 0 || n > maxSize || n > 1<<20 {
		return nil, fmt.Errorf("scenario frame size %d out of range", n)
	}
	buf := make([]byte, n)
	if _, err := io.ReadFull(r, buf); err != nil {
		return nil, err
	}
	return buf, nil
}

func summarizeScenarioLatency(samples []protocol.ScenarioSample) protocol.LatencyResult {
	lat := make([]protocol.LatencySample, 0, len(samples))
	for _, s := range samples {
		if s.LatencyMS > 0 {
			lat = append(lat, protocol.LatencySample{OffsetMS: s.OffsetMS, RTTMS: s.LatencyMS})
		}
	}
	return protocol.LatencyResult{Summary: metrics.SummarizeLatency(lat), Samples: lat}
}

func validateScenarioConfig(cfg ScenarioConfig) error {
	switch cfg.Profile {
	case "request-response", "small-message", "bursty", "streaming":
	default:
		return errors.New("scenario profile must be request-response, small-message, bursty or streaming")
	}
	if cfg.Duration < 100*time.Millisecond || cfg.Duration > 24*time.Hour {
		return errors.New("scenario duration must be between 100ms and 24h")
	}
	if cfg.MessageSize < 1 || cfg.MessageSize > 1<<20 {
		return errors.New("scenario message size must be 1..1048576")
	}
	if cfg.BurstMessages < 1 || cfg.BurstMessages > 4096 {
		return errors.New("burst messages must be 1..4096")
	}
	if cfg.BurstPause < 0 || cfg.BurstPause > 10*time.Second {
		return errors.New("burst pause out of range")
	}
	if cfg.Profile == "streaming" && (cfg.RateBitsPerSec == 0 || cfg.RateBitsPerSec > 10_000_000_000) {
		return errors.New("streaming rate must be 1 bit/s..10 Gbit/s")
	}
	if cfg.DialTimeout <= 0 {
		return errors.New("dial timeout must be positive")
	}
	return nil
}
