package latency

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"net"
	"strconv"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

type Config struct {
	Host        string
	Port        int
	DialTimeout time.Duration
	Interval    time.Duration
}

func MeasureDuration(ctx context.Context, cfg Config, duration time.Duration) (protocol.LatencyResult, error) {
	conn, reader, err := openSession(ctx, cfg, time.Now().Add(duration+10*time.Second))
	if err != nil {
		return protocol.LatencyResult{}, err
	}
	defer conn.Close()
	start := time.Now()
	return measureSession(ctx, conn, reader, cfg.Interval, start, start.Add(duration))
}

func MeasureBetween(ctx context.Context, cfg Config, start, end time.Time) (protocol.LatencyResult, error) {
	if !end.After(start) {
		return protocol.LatencyResult{}, errors.New("latency end must be after start")
	}
	conn, reader, err := openSession(ctx, cfg, end.Add(10*time.Second))
	if err != nil {
		return protocol.LatencyResult{}, err
	}
	defer conn.Close()
	if time.Now().After(end) {
		return protocol.LatencyResult{}, errors.New("latency session setup exceeded measurement window")
	}
	return measureSession(ctx, conn, reader, cfg.Interval, start, end)
}

func openSession(ctx context.Context, cfg Config, deadline time.Time) (net.Conn, *bufio.Reader, error) {
	if cfg.Host == "" {
		return nil, nil, errors.New("latency host is required")
	}
	if cfg.Interval <= 0 {
		return nil, nil, errors.New("latency interval must be positive")
	}
	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	addr := net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
	conn, err := dialer.DialContext(ctx, "tcp", addr)
	if err != nil {
		return nil, nil, fmt.Errorf("dial latency control %s: %w", addr, err)
	}
	_ = conn.SetDeadline(deadline)
	reader := bufio.NewReaderSize(conn, 4096)
	if err := protocol.WriteJSONLine(conn, protocol.Request{Mode: "latency"}); err != nil {
		_ = conn.Close()
		return nil, nil, fmt.Errorf("start latency session: %w", err)
	}
	var ready protocol.Ready
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		_ = conn.Close()
		return nil, nil, fmt.Errorf("read latency ready: %w", err)
	}
	if !ready.Ready {
		_ = conn.Close()
		return nil, nil, errors.New("latency server not ready")
	}
	return conn, reader, nil
}

func measureSession(ctx context.Context, conn net.Conn, reader *bufio.Reader, interval time.Duration, start, end time.Time) (protocol.LatencyResult, error) {
	if err := metrics.WaitUntil(ctx, start); err != nil {
		return protocol.LatencyResult{}, err
	}
	origin := start
	next := start
	var seq uint64
	var samples []protocol.LatencySample
	for next.Before(end) {
		if err := metrics.WaitUntil(ctx, next); err != nil {
			return protocol.LatencyResult{}, err
		}
		t0 := time.Now()
		if !t0.Before(end) {
			break
		}
		seq++
		if err := protocol.WriteJSONLine(conn, protocol.Probe{Seq: seq}); err != nil {
			return protocol.LatencyResult{}, fmt.Errorf("send latency probe: %w", err)
		}
		var reply protocol.ProbeReply
		if err := protocol.ReadJSONLine(reader, &reply); err != nil {
			return protocol.LatencyResult{}, fmt.Errorf("read latency probe: %w", err)
		}
		if reply.Seq != seq {
			return protocol.LatencyResult{}, fmt.Errorf("latency sequence mismatch: got %d want %d", reply.Seq, seq)
		}
		rtt := time.Since(t0)
		samples = append(samples, protocol.LatencySample{OffsetMS: t0.Sub(origin).Milliseconds(), RTTMS: float64(rtt) / float64(time.Millisecond)})
		next = next.Add(interval)
		if time.Now().After(next) {
			next = time.Now().Add(interval)
		}
	}
	return protocol.LatencyResult{Summary: metrics.SummarizeLatency(samples), Samples: samples}, nil
}
