package advanced

import (
	"context"
	"errors"
	"math"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

type ResponsivenessConfig struct {
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

func RunResponsiveness(ctx context.Context, cfg ResponsivenessConfig) (protocol.ResponsivenessResult, error) {
	if cfg.Direction == "" {
		cfg.Direction = "bidir"
	}
	if cfg.Streams < 1 {
		cfg.Streams = 4
	}
	tcp, err := throughput.RunTCPSuite(ctx, throughput.ClientConfig{
		Host: cfg.Host, Port: cfg.Port, Direction: cfg.Direction, Duration: cfg.Duration, Warmup: cfg.Warmup,
		BufferSize: cfg.BufferSize, DialTimeout: cfg.DialTimeout, SampleInterval: cfg.SampleInterval,
		ProbeInterval: cfg.ProbeInterval, Streams: cfg.Streams, MaxStreams: cfg.Streams, ConvergencePct: 5,
		Diagnostics: true,
	})
	if err != nil {
		return protocol.ResponsivenessResult{}, err
	}
	if tcp.Aggregate.LoadedLatency.Summary.Count == 0 {
		return protocol.ResponsivenessResult{}, errors.New("no working-latency probes completed")
	}
	working := tcp.Aggregate.LoadedLatency.Summary.P95MS
	rpm := 0.0
	if working > 0 && !math.IsNaN(working) {
		rpm = 60000 / working
	}
	result := protocol.ResponsivenessResult{
		SchemaVersion: protocol.ResultSchemaVersion, ProtocolVersion: protocol.Version, TestID: tcp.TestID,
		MeasurementKind: "responsiveness_under_working_conditions",
		Method: "netx-concurrent-application-echo-v1",
		Reference: "draft-ietf-ippm-responsiveness-09",
		DraftConformant: false,
		NonConformanceNote: "Uses independent netx application echo and p95 working RTT; it does not implement the draft's HTTP foreign/self probes, moving-average stability rule, or trimmed-mean aggregation. RPM is therefore explicitly approximate.",
		Direction: cfg.Direction, Streams: tcp.Aggregate.Streams, IdleLatency: tcp.IdleLatency,
		WorkingLatency: tcp.Aggregate.LoadedLatency, WorkingRTTMS: working, WorkingRPMApprox: rpm,
	}
	if tcp.Aggregate.Upload != nil {
		result.UploadBPS = tcp.Aggregate.Upload.BitsPerSecond
	}
	if tcp.Aggregate.Download != nil {
		result.DownloadBPS = tcp.Aggregate.Download.BitsPerSecond
	}
	return result, nil
}
