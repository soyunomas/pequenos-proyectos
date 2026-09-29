package protocol

const (
	DefaultAvailableChirps       = 8
	DefaultAvailablePackets      = 24
	DefaultAvailablePacketSize   = 1200
	DefaultAvailableMinRateBPS   = uint64(1_000_000)
	DefaultAvailableMaxRateBPS   = uint64(100_000_000)
	DefaultAvailableChirpGapMS   = int64(100)
	DefaultScenarioMessageSize   = 1024
	DefaultScenarioBurstMessages = 32
	DefaultScenarioBurstPauseMS  = int64(50)
	DefaultScenarioRateBPS       = uint64(10_000_000)
)

type AvailableDone struct {
	Done bool `json:"done"`
}

type AvailablePoint struct {
	PairIndex    int     `json:"pair_index"`
	SendGapUS    float64 `json:"send_gap_us"`
	ArrivalGapUS float64 `json:"arrival_gap_us"`
	InputBPS     float64 `json:"input_bits_per_second"`
	GapRatio     float64 `json:"gap_ratio"`
}

type ChirpEstimate struct {
	Chirp           int              `json:"chirp"`
	Valid           bool             `json:"valid"`
	Censored        string           `json:"censored,omitempty"`
	CrossingIndex   int              `json:"crossing_index,omitempty"`
	LowerBPS        float64          `json:"lower_bits_per_second,omitempty"`
	UpperBPS        float64          `json:"upper_bits_per_second,omitempty"`
	EstimateBPS     float64          `json:"estimate_bits_per_second,omitempty"`
	PacketsExpected int              `json:"packets_expected"`
	PacketsReceived int              `json:"packets_received"`
	LossPercent     float64          `json:"loss_percent"`
	Points          []AvailablePoint `json:"points,omitempty"`
}

type AvailableResult struct {
	SchemaVersion       int             `json:"schema_version"`
	ProtocolVersion     int             `json:"protocol_version"`
	TestID              string          `json:"test_id"`
	MeasurementKind     string          `json:"measurement_kind"`
	Method              string          `json:"method"`
	Transport           string          `json:"transport"`
	Units               string          `json:"units"`
	PacketSize          int             `json:"packet_size"`
	Chirps              int             `json:"chirps"`
	ChirpPackets        int             `json:"chirp_packets"`
	RequestedMinBPS     uint64          `json:"requested_min_bits_per_second"`
	RequestedMaxBPS     uint64          `json:"requested_max_bits_per_second"`
	MaxObservedInputBPS float64         `json:"max_observed_input_bits_per_second"`
	EstimateValid       bool            `json:"estimate_valid"`
	Stable              bool            `json:"stable"`
	ConfidenceLevel     float64         `json:"confidence_level,omitempty"`
	EstimateBPS         float64         `json:"estimate_bits_per_second,omitempty"`
	LowerBPS            float64         `json:"lower_bits_per_second,omitempty"`
	UpperBPS            float64         `json:"upper_bits_per_second,omitempty"`
	RelativeWidth       float64         `json:"relative_width,omitempty"`
	ValidChirps         int             `json:"valid_chirps"`
	RejectionReason     string          `json:"rejection_reason,omitempty"`
	Samples             []ChirpEstimate `json:"samples"`
}

type QUICTestResult struct {
	SchemaVersion   int           `json:"schema_version"`
	ProtocolVersion int           `json:"protocol_version"`
	TestID          string        `json:"test_id"`
	MeasurementKind string        `json:"measurement_kind"`
	Transport       string        `json:"transport"`
	Direction       string        `json:"direction"`
	DurationMS      int64         `json:"duration_ms"`
	WarmupMS        int64         `json:"warmup_ms"`
	SampleMS        int64         `json:"sample_interval_ms"`
	IdleLatency     LatencyResult `json:"idle_latency"`
	Stages          []StageResult `json:"stages"`
	SingleStream    StageResult   `json:"single_stream"`
	Aggregate       StageResult   `json:"aggregate"`
}

type ScenarioSample struct {
	OffsetMS  int64   `json:"offset_ms"`
	LatencyMS float64 `json:"latency_ms,omitempty"`
	Bytes     int     `json:"bytes"`
}

type ScenarioResult struct {
	SchemaVersion        int              `json:"schema_version"`
	ProtocolVersion      int              `json:"protocol_version"`
	TestID               string           `json:"test_id"`
	MeasurementKind      string           `json:"measurement_kind"`
	Profile              string           `json:"profile"`
	Transport            string           `json:"transport"`
	DurationMS           int64            `json:"duration_ms"`
	MessageSize          int              `json:"message_size"`
	TargetRateBPS        uint64           `json:"target_bits_per_second,omitempty"`
	Requests             uint64           `json:"requests"`
	PayloadBytesSent     uint64           `json:"payload_bytes_sent"`
	PayloadBytesReceived uint64           `json:"payload_bytes_received"`
	OperationsPerSecond  float64          `json:"operations_per_second,omitempty"`
	PayloadBitsPerSecond float64          `json:"payload_bits_per_second,omitempty"`
	Latency              LatencyResult    `json:"latency"`
	Samples              []ScenarioSample `json:"samples,omitempty"`
}

type ResponsivenessResult struct {
	SchemaVersion      int           `json:"schema_version"`
	ProtocolVersion    int           `json:"protocol_version"`
	TestID             string        `json:"test_id"`
	MeasurementKind    string        `json:"measurement_kind"`
	Method             string        `json:"method"`
	Reference          string        `json:"reference"`
	DraftConformant    bool          `json:"draft_conformant"`
	NonConformanceNote string        `json:"non_conformance_note"`
	Direction          string        `json:"direction"`
	Streams            int           `json:"streams"`
	UploadBPS          float64       `json:"upload_bits_per_second,omitempty"`
	DownloadBPS        float64       `json:"download_bits_per_second,omitempty"`
	IdleLatency        LatencyResult `json:"idle_latency"`
	WorkingLatency     LatencyResult `json:"working_latency"`
	WorkingRTTMS       float64       `json:"working_rtt_ms"`
	WorkingRPMApprox   float64       `json:"working_rpm_approx,omitempty"`
}

type CCComparisonItem struct {
	Algorithm   string  `json:"algorithm"`
	Supported   bool    `json:"supported"`
	Error       string  `json:"error,omitempty"`
	GoodputBPS  float64 `json:"goodput_bits_per_second,omitempty"`
	LoadedP95MS float64 `json:"loaded_p95_ms,omitempty"`
	RetransPct  float64 `json:"retrans_percent,omitempty"`
}

type CCComparisonResult struct {
	SchemaVersion   int                `json:"schema_version"`
	ProtocolVersion int                `json:"protocol_version"`
	MeasurementKind string             `json:"measurement_kind"`
	Direction       string             `json:"direction"`
	Streams         int                `json:"streams"`
	Items           []CCComparisonItem `json:"items"`
}
