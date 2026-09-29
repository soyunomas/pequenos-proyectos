package protocol

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"time"
)

const (
	Version               = 2
	ResultSchemaVersion   = 1
	MaxControlFrame       = 2 << 20 // 2 MiB: bounded time-series result frames.
	DefaultPort           = 5202
	DefaultBuffer         = 128 << 10 // 128 KiB
	DefaultDuration       = 10 * time.Second
	DefaultWarmup         = 2 * time.Second
	DefaultDial           = 5 * time.Second
	DefaultGuardTime      = 300 * time.Millisecond
	DefaultStartDelay     = 150 * time.Millisecond
	DefaultSampleInterval = 250 * time.Millisecond
	DefaultProbeInterval  = 100 * time.Millisecond
	DefaultUDPRate        = uint64(100_000_000)
	DefaultUDPPacket      = 1200
	DefaultPacingQuantum  = time.Millisecond
	MaxStreams            = 64
)

type Request struct {
	Mode             string `json:"mode"`
	DurationMS       int64  `json:"duration_ms,omitempty"`
	WarmupMS         int64  `json:"warmup_ms,omitempty"`
	SampleIntervalMS int64  `json:"sample_interval_ms,omitempty"`
	BufferSize       int    `json:"buffer_size,omitempty"`
	Streams          int    `json:"streams,omitempty"`
	RateBitsPerSec   uint64 `json:"rate_bits_per_second,omitempty"`
	PacketSize       int    `json:"packet_size,omitempty"`
	PacingQuantumUS  int64  `json:"pacing_quantum_us,omitempty"`
}

type Offer struct {
	Transport string `json:"transport"`
	DataPort  int    `json:"data_port"`
	Token     string `json:"token"`
}

type Ready struct {
	Ready        bool  `json:"ready"`
	StartDelayMS int64 `json:"start_delay_ms"`
}

type DataHello struct {
	Token  string `json:"token"`
	Stream int    `json:"stream"`
}

type Probe struct {
	Seq uint64 `json:"seq"`
}

type ProbeReply struct {
	Seq uint64 `json:"seq"`
}

type UDPDone struct {
	FirstMeasureSeq uint64 `json:"first_measure_seq"`
	LastMeasureSeq  uint64 `json:"last_measure_seq"`
	HasMeasurement  bool   `json:"has_measurement"`
}

type ThroughputSample struct {
	OffsetMS      int64     `json:"offset_ms"`
	IntervalMS    int64     `json:"interval_ms"`
	Bytes         uint64    `json:"bytes"`
	BitsPerSecond float64   `json:"bits_per_second"`
	PerStreamBPS  []float64 `json:"per_stream_bits_per_second,omitempty"`
}

type StreamResult struct {
	Stream        int     `json:"stream"`
	Bytes         uint64  `json:"bytes"`
	BitsPerSecond float64 `json:"bits_per_second"`
}

type DirectionResult struct {
	Bytes           uint64             `json:"bytes"`
	DurationMS      int64              `json:"duration_ms"`
	BitsPerSecond   float64            `json:"bits_per_second"`
	MegabitsPerSec  float64            `json:"megabits_per_second"`
	MebibytesPerSec float64            `json:"mebibytes_per_second"`
	Streams         []StreamResult     `json:"streams"`
	Samples         []ThroughputSample `json:"samples,omitempty"`
}

type LatencySample struct {
	OffsetMS int64   `json:"offset_ms"`
	RTTMS    float64 `json:"rtt_ms"`
}

type LatencySummary struct {
	Count int     `json:"count"`
	MinMS float64 `json:"min_ms"`
	P50MS float64 `json:"p50_ms"`
	P90MS float64 `json:"p90_ms"`
	P95MS float64 `json:"p95_ms"`
	P99MS float64 `json:"p99_ms"`
	MaxMS float64 `json:"max_ms"`
	MADMS float64 `json:"mad_ms"`
}

type LatencyResult struct {
	Summary LatencySummary  `json:"summary"`
	Samples []LatencySample `json:"samples,omitempty"`
}

type StageResult struct {
	Streams       int              `json:"streams"`
	Upload        *DirectionResult `json:"upload,omitempty"`
	Download      *DirectionResult `json:"download,omitempty"`
	LoadedLatency LatencyResult    `json:"loaded_latency"`
}

type AdaptiveResult struct {
	Enabled         bool    `json:"enabled"`
	SelectedStreams int     `json:"selected_streams"`
	ConvergencePct  float64 `json:"convergence_percent"`
	StopReason      string  `json:"stop_reason"`
}

type TCPTestResult struct {
	SchemaVersion   int            `json:"schema_version"`
	ProtocolVersion int            `json:"protocol_version"`
	TestID          string         `json:"test_id"`
	Transport       string         `json:"transport"`
	Direction       string         `json:"direction"`
	DurationMS      int64          `json:"duration_ms"`
	WarmupMS        int64          `json:"warmup_ms"`
	SampleMS        int64          `json:"sample_interval_ms"`
	IdleLatency     LatencyResult  `json:"idle_latency"`
	Stages          []StageResult  `json:"stages"`
	SingleStream    StageResult    `json:"single_stream"`
	Aggregate       StageResult    `json:"aggregate"`
	Adaptive        AdaptiveResult `json:"adaptive"`
}

type UDPResult struct {
	SchemaVersion    int             `json:"schema_version"`
	ProtocolVersion  int             `json:"protocol_version"`
	TestID           string          `json:"test_id"`
	Transport        string          `json:"transport"`
	Direction        string          `json:"direction"`
	RateBitsPerSec   uint64          `json:"target_bits_per_second"`
	PacketSize       int             `json:"packet_size"`
	DurationMS       int64           `json:"duration_ms"`
	WarmupMS         int64           `json:"warmup_ms"`
	Throughput       DirectionResult `json:"throughput"`
	PacketsExpected  uint64          `json:"packets_expected"`
	PacketsReceived  uint64          `json:"packets_received"`
	PacketsLost      uint64          `json:"packets_lost"`
	PacketsReordered uint64          `json:"packets_reordered"`
	LossPercent      float64         `json:"loss_percent"`
	JitterMS         float64         `json:"jitter_ms"`
	IdleLatency      LatencyResult   `json:"idle_latency"`
	LoadedLatency    LatencyResult   `json:"loaded_latency"`
}

type SessionResult struct {
	Upload   *DirectionResult `json:"upload,omitempty"`
	Download *DirectionResult `json:"download,omitempty"`
	UDP      *UDPResult       `json:"udp,omitempty"`
}

func WriteJSONLine(w io.Writer, v any) error {
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	return enc.Encode(v)
}

func ReadJSONLine(r *bufio.Reader, dst any) error {
	line, err := readBoundedLine(r, MaxControlFrame)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(line, dst); err != nil {
		return fmt.Errorf("decode control frame: %w", err)
	}
	return nil
}

func readBoundedLine(r *bufio.Reader, limit int) ([]byte, error) {
	var out []byte
	for {
		frag, isPrefix, err := r.ReadLine()
		if err != nil {
			return nil, err
		}
		if len(out)+len(frag) > limit {
			return nil, fmt.Errorf("frame exceeds %d bytes", limit)
		}
		out = append(out, frag...)
		if !isPrefix {
			if len(out) == 0 {
				return nil, errors.New("empty frame")
			}
			return out, nil
		}
	}
}
