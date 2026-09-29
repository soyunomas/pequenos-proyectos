package throughput

import (
	"bufio"
	"context"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"math"
	"net"
	"strconv"
	"strings"
	"sync/atomic"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/latency"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/timestamp"
)

const (
	udpMagic      = "NTX2"
	udpHeaderSize = 36
)

type UDPConfig struct {
	Host           string
	Port           int
	Duration       time.Duration
	Warmup         time.Duration
	DialTimeout    time.Duration
	SampleInterval time.Duration
	ProbeInterval  time.Duration
	RateBitsPerSec uint64
	PacketSize     int
	PacingQuantum  time.Duration
	TimestampMode  string
}

func RunUDP(ctx context.Context, cfg UDPConfig) (protocol.UDPResult, error) {
	if cfg.TimestampMode == "" {
		cfg.TimestampMode = timestamp.Userspace
	}
	if err := validateUDPConfig(cfg); err != nil {
		return protocol.UDPResult{}, err
	}
	testID, err := NewToken()
	if err != nil {
		return protocol.UDPResult{}, err
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
		return protocol.UDPResult{}, fmt.Errorf("idle latency: %w", err)
	}

	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	controlAddr := net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
	control, err := dialer.DialContext(ctx, "tcp", controlAddr)
	if err != nil {
		return protocol.UDPResult{}, fmt.Errorf("dial UDP control: %w", err)
	}
	defer control.Close()
	deadline := time.Now().Add(cfg.Warmup + cfg.Duration + 20*time.Second)
	_ = control.SetDeadline(deadline)
	reader := bufio.NewReaderSize(control, 4096)

	req := protocol.Request{
		Mode: "udp-upload", DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(),
		SampleIntervalMS: cfg.SampleInterval.Milliseconds(), RateBitsPerSec: cfg.RateBitsPerSec,
		PacketSize: cfg.PacketSize, PacingQuantumUS: cfg.PacingQuantum.Microseconds(), TimestampMode: cfg.TimestampMode,
	}
	if err := protocol.WriteJSONLine(control, req); err != nil {
		return protocol.UDPResult{}, fmt.Errorf("send UDP request: %w", err)
	}
	var offer protocol.Offer
	if err := protocol.ReadJSONLine(reader, &offer); err != nil {
		return protocol.UDPResult{}, fmt.Errorf("read UDP offer: %w", err)
	}
	if offer.Transport != "udp" || offer.DataPort <= 0 || offer.Token == "" {
		return protocol.UDPResult{}, errors.New("invalid UDP data offer")
	}
	udpAddr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(cfg.Host, strconv.Itoa(offer.DataPort)))
	if err != nil {
		return protocol.UDPResult{}, err
	}
	conn, err := net.DialUDP("udp", nil, udpAddr)
	if err != nil {
		return protocol.UDPResult{}, fmt.Errorf("dial UDP data: %w", err)
	}
	defer conn.Close()
	_ = conn.SetWriteDeadline(deadline)

	var ready protocol.Ready
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		return protocol.UDPResult{}, fmt.Errorf("read UDP ready: %w", err)
	}
	if !ready.Ready {
		return protocol.UDPResult{}, errors.New("UDP server did not enter ready state")
	}
	startAt := time.Now().Add(time.Duration(ready.StartDelayMS) * time.Millisecond)
	measureStart := startAt.Add(cfg.Warmup)
	measureEnd := measureStart.Add(cfg.Duration)

	latCh := make(chan struct {
		result protocol.LatencyResult
		err    error
	}, 1)
	go func() {
		r, err := latency.MeasureBetween(ctx, latency.Config{Host: cfg.Host, Port: cfg.Port, DialTimeout: cfg.DialTimeout, Interval: cfg.ProbeInterval}, measureStart, measureEnd)
		latCh <- struct {
			result protocol.LatencyResult
			err    error
		}{r, err}
	}()

	done, err := sendUDPPaced(ctx, conn, offer.Token, startAt, cfg)
	if err != nil {
		return protocol.UDPResult{}, err
	}
	if err := protocol.WriteJSONLine(control, done); err != nil {
		return protocol.UDPResult{}, fmt.Errorf("send UDP completion: %w", err)
	}
	var session protocol.SessionResult
	if err := protocol.ReadJSONLine(reader, &session); err != nil {
		return protocol.UDPResult{}, fmt.Errorf("read UDP result: %w", err)
	}
	if session.UDP == nil {
		return protocol.UDPResult{}, errors.New("server omitted UDP result")
	}
	lat := <-latCh
	if lat.err != nil {
		return protocol.UDPResult{}, fmt.Errorf("loaded latency: %w", lat.err)
	}
	result := *session.UDP
	result.SchemaVersion = protocol.ResultSchemaVersion
	result.ProtocolVersion = protocol.Version
	result.TestID = testID
	result.Transport = "udp"
	result.MeasurementKind = "transport_goodput"
	result.Direction = "upload"
	result.RateBitsPerSec = cfg.RateBitsPerSec
	result.PacketSize = cfg.PacketSize
	result.DurationMS = cfg.Duration.Milliseconds()
	result.WarmupMS = cfg.Warmup.Milliseconds()
	result.IdleLatency = idle
	result.LoadedLatency = lat.result
	return result, nil
}

func sendUDPPaced(ctx context.Context, conn *net.UDPConn, tokenHex string, startAt time.Time, cfg UDPConfig) (protocol.UDPDone, error) {
	token, err := hex.DecodeString(tokenHex)
	if err != nil || len(token) != 16 {
		return protocol.UDPDone{}, errors.New("invalid UDP token")
	}
	if err := metrics.WaitUntil(ctx, startAt); err != nil {
		return protocol.UDPDone{}, err
	}
	packet := make([]byte, cfg.PacketSize)
	copy(packet[:4], udpMagic)
	copy(packet[4:20], token)

	threshold := uint64(cfg.PacketSize*8) * 1_000_000_000
	quantumNS := uint64(cfg.PacingQuantum.Nanoseconds())
	stopElapsed := cfg.Warmup + cfg.Duration + protocol.DefaultGuardTime
	measureStartNS := uint64(cfg.Warmup.Nanoseconds())
	measureEndNS := uint64((cfg.Warmup + cfg.Duration).Nanoseconds())
	start := time.Now()
	next := start.Add(cfg.PacingQuantum)
	var credit uint64
	var seq uint64
	var done protocol.UDPDone

	for time.Since(start) < stopElapsed {
		if err := metrics.WaitUntil(ctx, next); err != nil {
			return done, err
		}
		credit += cfg.RateBitsPerSec * quantumNS
		for credit >= threshold {
			credit -= threshold
			seq++
			elapsed := uint64(time.Since(start).Nanoseconds())
			binary.BigEndian.PutUint64(packet[20:28], seq)
			binary.BigEndian.PutUint64(packet[28:36], elapsed)
			if _, err := conn.Write(packet); err != nil {
				return done, fmt.Errorf("write UDP packet: %w", err)
			}
			if elapsed >= measureStartNS && elapsed < measureEndNS {
				if !done.HasMeasurement {
					done.FirstMeasureSeq = seq
					done.HasMeasurement = true
				}
				done.LastMeasureSeq = seq
			}
		}
		next = next.Add(cfg.PacingQuantum)
		if time.Now().After(next.Add(cfg.PacingQuantum)) {
			next = time.Now().Add(cfg.PacingQuantum)
		}
	}
	return done, nil
}

func MeasureUDPServer(ctx context.Context, conn *net.UDPConn, tokenHex string, startAt time.Time, req protocol.Request, control *bufio.Reader) (protocol.UDPResult, error) {
	token, err := hex.DecodeString(tokenHex)
	if err != nil || len(token) != 16 {
		return protocol.UDPResult{}, errors.New("invalid UDP token")
	}
	tsMode := req.TimestampMode
	if tsMode == "" {
		tsMode = timestamp.Userspace
	}
	tsReader, err := timestamp.NewReader(conn, tsMode)
	if err != nil {
		return protocol.UDPResult{}, err
	}
	warmup := time.Duration(req.WarmupMS) * time.Millisecond
	duration := time.Duration(req.DurationMS) * time.Millisecond
	measureStart := startAt.Add(warmup)
	measureEnd := measureStart.Add(duration)
	stopAt := measureEnd.Add(2 * protocol.DefaultGuardTime)
	_ = conn.SetReadDeadline(stopAt)

	var counter atomic.Uint64
	counterPtrs := []*atomic.Uint64{&counter}
	samplesCh := make(chan []protocol.ThroughputSample, 1)
	go func() {
		samplesCh <- metrics.SampleCounters(ctx, measureStart, measureEnd, time.Duration(req.SampleIntervalMS)*time.Millisecond, counterPtrs)
	}()

	buf := make([]byte, 65535)
	measureStartNS := uint64(warmup.Nanoseconds())
	measureEndNS := uint64((warmup + duration).Nanoseconds())
	var received, reordered, highest uint64
	var highestSet bool
	var jitterNS float64
	var prevArrival time.Time
	var prevSendNS uint64
	timestampSource := timestamp.Userspace

	for time.Now().Before(stopAt) {
		n, _, arrival, source, err := tsReader.Read(buf)
		now := time.Now()
		if err != nil {
			var ne net.Error
			if errors.As(err, &ne) && ne.Timeout() {
				break
			}
			select {
			case <-ctx.Done():
				return protocol.UDPResult{}, ctx.Err()
			default:
			}
			return protocol.UDPResult{}, fmt.Errorf("read UDP: %w", err)
		}
		if n < udpHeaderSize || string(buf[:4]) != udpMagic || !equal16(buf[4:20], token) {
			continue
		}
		seq := binary.BigEndian.Uint64(buf[20:28])
		sendNS := binary.BigEndian.Uint64(buf[28:36])
		if sendNS < measureStartNS || sendNS >= measureEndNS {
			continue
		}
		timestampSource = preferTimestampSource(timestampSource, source)
		received++
		payload := n - udpHeaderSize
		counter.Add(uint64(payload))
		if highestSet && seq < highest {
			reordered++
		}
		if !highestSet || seq > highest {
			highest = seq
			highestSet = true
		}
		if !prevArrival.IsZero() && sendNS >= prevSendNS {
			arrivalDelta := float64(arrival.Sub(prevArrival))
			sendDelta := float64(time.Duration(sendNS - prevSendNS))
			d := math.Abs(arrivalDelta - sendDelta)
			jitterNS += (d - jitterNS) / 16
		}
		prevArrival, prevSendNS = arrival, sendNS
		_ = now // window membership remains based on sender monotonic timestamps.
	}

	var done protocol.UDPDone
	if err := protocol.ReadJSONLine(control, &done); err != nil {
		return protocol.UDPResult{}, fmt.Errorf("read UDP completion: %w", err)
	}
	var expected uint64
	if done.HasMeasurement && done.LastMeasureSeq >= done.FirstMeasureSeq {
		expected = done.LastMeasureSeq - done.FirstMeasureSeq + 1
	}
	lost := uint64(0)
	if expected > received {
		lost = expected - received
	}
	lossPct := 0.0
	if expected > 0 {
		lossPct = float64(lost) / float64(expected) * 100
	}
	samples := <-samplesCh
	throughput := metrics.BuildDirection(duration, counterPtrs, samples)
	return protocol.UDPResult{
		Transport: "udp", Direction: "upload", RateBitsPerSec: req.RateBitsPerSec, PacketSize: req.PacketSize,
		DurationMS: req.DurationMS, WarmupMS: req.WarmupMS, Throughput: throughput,
		PacketsExpected: expected, PacketsReceived: received, PacketsLost: lost, PacketsReordered: reordered,
		LossPercent: lossPct, JitterMS: jitterNS / float64(time.Millisecond), TimestampSource: timestampSource,
	}, nil
}

func preferTimestampSource(current, candidate string) string {
	rank := func(s string) int {
		switch s {
		case "kernel-hardware":
			return 4
		case "kernel-software":
			return 3
		case "kernel-software-fallback":
			return 2
		case timestamp.Userspace:
			return 1
		default:
			return 0
		}
	}
	if rank(candidate) > rank(current) {
		return candidate
	}
	if rank(current) == 0 && candidate != "" {
		return candidate
	}
	return current
}

func ParseBitrate(s string) (uint64, error) {
	s = strings.TrimSpace(strings.ToUpper(s))
	if s == "" {
		return 0, errors.New("empty bitrate")
	}
	mult := float64(1)
	switch s[len(s)-1] {
	case 'K':
		mult, s = 1_000, s[:len(s)-1]
	case 'M':
		mult, s = 1_000_000, s[:len(s)-1]
	case 'G':
		mult, s = 1_000_000_000, s[:len(s)-1]
	}
	v, err := strconv.ParseFloat(s, 64)
	if err != nil || v <= 0 {
		return 0, fmt.Errorf("invalid bitrate %q", s)
	}
	out := v * mult
	if out > 100_000_000_000 {
		return 0, errors.New("bitrate exceeds 100 Gbit/s limit")
	}
	return uint64(out), nil
}

func validateUDPConfig(cfg UDPConfig) error {
	if cfg.Host == "" || cfg.Port < 1 || cfg.Port > 65535 {
		return errors.New("valid host and port are required")
	}
	if cfg.Duration < 100*time.Millisecond || cfg.Duration > 24*time.Hour || cfg.Warmup < 0 || cfg.Warmup > 10*time.Minute {
		return errors.New("invalid UDP duration/warmup")
	}
	if cfg.SampleInterval < 50*time.Millisecond || cfg.SampleInterval > 5*time.Second || cfg.ProbeInterval < 10*time.Millisecond || cfg.ProbeInterval > 5*time.Second {
		return errors.New("invalid UDP sample/probe interval")
	}
	if cfg.Duration/cfg.SampleInterval > 2000 {
		return errors.New("too many UDP samples; increase sample interval")
	}
	if cfg.RateBitsPerSec == 0 || cfg.RateBitsPerSec > 100_000_000_000 {
		return errors.New("UDP rate must be between 1 bit/s and 100 Gbit/s")
	}
	if cfg.PacketSize < udpHeaderSize+1 || cfg.PacketSize > 65507 {
		return fmt.Errorf("UDP packet size must be between %d and 65507", udpHeaderSize+1)
	}
	if cfg.PacingQuantum < 100*time.Microsecond || cfg.PacingQuantum > 10*time.Millisecond {
		return errors.New("UDP pacing quantum must be between 100us and 10ms")
	}
	if cfg.TimestampMode != timestamp.Userspace && cfg.TimestampMode != timestamp.Kernel && cfg.TimestampMode != timestamp.Hardware {
		return errors.New("timestamp mode must be userspace, kernel or hardware")
	}
	if cfg.DialTimeout <= 0 {
		return errors.New("dial timeout must be positive")
	}
	return nil
}

func equal16(a, b []byte) bool {
	if len(a) != 16 || len(b) != 16 {
		return false
	}
	var diff byte
	for i := 0; i < 16; i++ {
		diff |= a[i] ^ b[i]
	}
	return diff == 0
}
