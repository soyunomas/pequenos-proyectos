package advanced

import (
	"context"
	"strings"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

func CompareCongestionControl(ctx context.Context, base throughput.ClientConfig, algorithms []string) protocol.CCComparisonResult {
	result := protocol.CCComparisonResult{
		SchemaVersion: protocol.ResultSchemaVersion, ProtocolVersion: protocol.Version,
		MeasurementKind: "tcp_congestion_control_comparison", Direction: base.Direction, Streams: base.Streams,
	}
	for _, raw := range algorithms {
		algorithm := strings.TrimSpace(raw)
		if algorithm == "" {
			continue
		}
		cfg := base
		cfg.CongestionControl = algorithm
		cfg.Diagnostics = true
		test, err := throughput.RunTCPSuite(ctx, cfg)
		item := protocol.CCComparisonItem{Algorithm: algorithm}
		if err != nil {
			item.Error = err.Error()
			result.Items = append(result.Items, item)
			continue
		}
		item.Supported = true
		item.GoodputBPS = stageGoodput(test.Aggregate)
		item.LoadedP95MS = test.Aggregate.LoadedLatency.Summary.P95MS
		item.RetransPct = senderRetransPercent(test.Aggregate)
		result.Items = append(result.Items, item)
	}
	return result
}

func senderRetransPercent(stage protocol.StageResult) float64 {
	var retrans, sent uint64
	for _, endpoint := range []*protocol.EndpointTelemetry{stage.LocalTelemetry, stage.RemoteTelemetry} {
		if endpoint == nil || !endpoint.Supported || (endpoint.Role != "sender" && endpoint.Role != "bidirectional") {
			continue
		}
		for _, stream := range endpoint.TCP {
			retrans += stream.Delta.BytesRetrans
			sent += stream.Delta.BytesSent
		}
	}
	if sent == 0 {
		return 0
	}
	return float64(retrans) / float64(sent) * 100
}
