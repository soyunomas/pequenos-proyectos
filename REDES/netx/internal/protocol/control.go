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
	Version               = 5
	ResultSchemaVersion   = 4
	MaxControlFrame       = 2 << 20 // 2 MiB: bounded time-series + diagnostics frame.
	DefaultPort           = 5202
	DefaultBuffer         = 128 << 10
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
	TimestampMode    string `json:"timestamp_mode,omitempty"`
	Diagnostics       bool   `json:"diagnostics,omitempty"`
	CongestionControl string `json:"congestion_control,omitempty"`
	MinRateBitsPerSec uint64 `json:"min_rate_bits_per_second,omitempty"`
	MaxRateBitsPerSec uint64 `json:"max_rate_bits_per_second,omitempty"`
	Chirps            int    `json:"chirps,omitempty"`
	ChirpPackets      int    `json:"chirp_packets,omitempty"`
	ChirpGapMS        int64  `json:"chirp_gap_ms,omitempty"`
	Profile           string `json:"profile,omitempty"`
	MessageSize       int    `json:"message_size,omitempty"`
	BurstMessages     int    `json:"burst_messages,omitempty"`
	BurstPauseMS      int64  `json:"burst_pause_ms,omitempty"`
	ScenarioRateBPS   uint64 `json:"scenario_rate_bits_per_second,omitempty"`
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

type TCPSnapshot struct {
	Supported                bool   `json:"supported"`
	Error                    string `json:"error,omitempty"`
	TCPInfoLength            int    `json:"tcp_info_length,omitempty"`
	CongestionControl        string `json:"congestion_control,omitempty"`
	State                    uint8  `json:"state,omitempty"`
	CAState                  uint8  `json:"ca_state,omitempty"`
	Options                  uint8  `json:"options,omitempty"`
	ECNNegotiated            bool   `json:"ecn_negotiated,omitempty"`
	ECNSeen                  bool   `json:"ecn_seen,omitempty"`
	DeliveryRateAppLimited   bool   `json:"delivery_rate_app_limited,omitempty"`
	RTOUsec                  uint32 `json:"rto_usec,omitempty"`
	RTTUsec                  uint32 `json:"rtt_usec,omitempty"`
	RTTVarUsec               uint32 `json:"rttvar_usec,omitempty"`
	MinRTTUsec               uint32 `json:"min_rtt_usec,omitempty"`
	SndCwnd                  uint32 `json:"snd_cwnd,omitempty"`
	SndSsthresh              uint32 `json:"snd_ssthresh,omitempty"`
	Unacked                  uint32 `json:"unacked,omitempty"`
	Lost                     uint32 `json:"lost,omitempty"`
	Retrans                  uint32 `json:"retrans,omitempty"`
	TotalRetrans             uint32 `json:"total_retrans,omitempty"`
	Reordering               uint32 `json:"reordering,omitempty"`
	PacingRateBytesPerSec    uint64 `json:"pacing_rate_bytes_per_second,omitempty"`
	MaxPacingRateBytesPerSec uint64 `json:"max_pacing_rate_bytes_per_second,omitempty"`
	DeliveryRateBytesPerSec  uint64 `json:"delivery_rate_bytes_per_second,omitempty"`
	BytesAcked               uint64 `json:"bytes_acked,omitempty"`
	BytesReceived            uint64 `json:"bytes_received,omitempty"`
	BytesSent                uint64 `json:"bytes_sent,omitempty"`
	BytesRetrans             uint64 `json:"bytes_retrans,omitempty"`
	SegsOut                  uint32 `json:"segments_out,omitempty"`
	SegsIn                   uint32 `json:"segments_in,omitempty"`
	DataSegsOut              uint32 `json:"data_segments_out,omitempty"`
	DataSegsIn               uint32 `json:"data_segments_in,omitempty"`
	NotSentBytes             uint32 `json:"not_sent_bytes,omitempty"`
	BusyTimeUsec             uint64 `json:"busy_time_usec,omitempty"`
	RwndLimitedUsec          uint64 `json:"rwnd_limited_usec,omitempty"`
	SndbufLimitedUsec        uint64 `json:"sndbuf_limited_usec,omitempty"`
	Delivered                uint32 `json:"delivered,omitempty"`
	DeliveredCE              uint32 `json:"delivered_ce,omitempty"`
	DSACKDups                uint32 `json:"dsack_dups,omitempty"`
	ReordSeen                uint32 `json:"reorder_events_seen,omitempty"`
	RcvOOOPack               uint32 `json:"received_out_of_order_packets,omitempty"`
	SndWnd                   uint32 `json:"send_window_bytes,omitempty"`
	RcvWnd                   uint32 `json:"receive_window_bytes,omitempty"`
	RcvSpace                 uint32 `json:"receive_space_bytes,omitempty"`
}

type TCPDelta struct {
	TotalRetrans      uint32 `json:"total_retrans"`
	BytesRetrans      uint64 `json:"bytes_retrans"`
	BytesAcked        uint64 `json:"bytes_acked"`
	BytesSent         uint64 `json:"bytes_sent"`
	Delivered         uint32 `json:"delivered"`
	DeliveredCE       uint32 `json:"delivered_ce"`
	BusyTimeUsec      uint64 `json:"busy_time_usec"`
	RwndLimitedUsec   uint64 `json:"rwnd_limited_usec"`
	SndbufLimitedUsec uint64 `json:"sndbuf_limited_usec"`
}

type TCPStreamTelemetry struct {
	Stream int         `json:"stream"`
	Start  TCPSnapshot `json:"start"`
	End    TCPSnapshot `json:"end"`
	Delta  TCPDelta    `json:"delta"`
}

type HostTelemetry struct {
	Supported                   bool    `json:"supported"`
	NumCPU                      int     `json:"num_cpu,omitempty"`
	ProcessCPUPercentOneCore    float64 `json:"process_cpu_percent_one_core,omitempty"`
	ProcessCPUPercentNormalized float64 `json:"process_cpu_percent_normalized,omitempty"`
	SystemCPUPercent            float64 `json:"system_cpu_percent,omitempty"`
	RSSBytes                    uint64  `json:"rss_bytes,omitempty"`
	CPUPressureSomeAvg10        float64 `json:"cpu_pressure_some_avg10,omitempty"`
	MemoryPressureSomeAvg10     float64 `json:"memory_pressure_some_avg10,omitempty"`
}

type EndpointTelemetry struct {
	Role      string               `json:"role"`
	Supported bool                 `json:"supported"`
	TCP       []TCPStreamTelemetry `json:"tcp,omitempty"`
	Host      HostTelemetry        `json:"host"`
}

type DiagnosticEvidence struct {
	Metric    string  `json:"metric"`
	Value     float64 `json:"value"`
	Threshold float64 `json:"threshold,omitempty"`
	Unit      string  `json:"unit,omitempty"`
}

type DiagnosticFinding struct {
	Code     string               `json:"code"`
	Summary  string               `json:"summary"`
	Evidence []DiagnosticEvidence `json:"evidence"`
}

type StageResult struct {
	Streams         int                `json:"streams"`
	Upload          *DirectionResult   `json:"upload,omitempty"`
	Download        *DirectionResult   `json:"download,omitempty"`
	LoadedLatency   LatencyResult      `json:"loaded_latency"`
	LocalTelemetry  *EndpointTelemetry `json:"local_telemetry,omitempty"`
	RemoteTelemetry *EndpointTelemetry `json:"remote_telemetry,omitempty"`
}

type AdaptiveResult struct {
	Enabled         bool    `json:"enabled"`
	SelectedStreams int     `json:"selected_streams"`
	ConvergencePct  float64 `json:"convergence_percent"`
	StopReason      string  `json:"stop_reason"`
}

type TCPTestResult struct {
	SchemaVersion      int                 `json:"schema_version"`
	ProtocolVersion    int                 `json:"protocol_version"`
	TestID             string              `json:"test_id"`
	Transport          string              `json:"transport"`
	MeasurementKind    string              `json:"measurement_kind"`
	CongestionControl  string              `json:"congestion_control,omitempty"`
	Direction          string              `json:"direction"`
	DurationMS         int64               `json:"duration_ms"`
	WarmupMS           int64               `json:"warmup_ms"`
	SampleMS           int64               `json:"sample_interval_ms"`
	DiagnosticsEnabled bool                `json:"diagnostics_enabled"`
	IdleLatency        LatencyResult       `json:"idle_latency"`
	Stages             []StageResult       `json:"stages"`
	SingleStream       StageResult         `json:"single_stream"`
	Aggregate          StageResult         `json:"aggregate"`
	Adaptive           AdaptiveResult      `json:"adaptive"`
	Diagnostics        []DiagnosticFinding `json:"diagnostics,omitempty"`
}

type UDPResult struct {
	SchemaVersion    int             `json:"schema_version"`
	ProtocolVersion  int             `json:"protocol_version"`
	TestID           string          `json:"test_id"`
	Transport        string          `json:"transport"`
	MeasurementKind  string          `json:"measurement_kind"`
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
	TimestampSource  string          `json:"timestamp_source,omitempty"`
	IdleLatency      LatencyResult   `json:"idle_latency"`
	LoadedLatency    LatencyResult   `json:"loaded_latency"`
}

type SessionResult struct {
	Upload    *DirectionResult   `json:"upload,omitempty"`
	Download  *DirectionResult   `json:"download,omitempty"`
	UDP       *UDPResult         `json:"udp,omitempty"`
	Telemetry *EndpointTelemetry `json:"telemetry,omitempty"`
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