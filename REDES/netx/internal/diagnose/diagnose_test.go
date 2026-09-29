package diagnose

import (
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"testing"
)

func TestEvaluateTCPFindings(t *testing.T) {
	sender := &protocol.EndpointTelemetry{Role: "sender", Supported: true, Host: protocol.HostTelemetry{Supported: true, ProcessCPUPercentNormalized: 95}, TCP: []protocol.TCPStreamTelemetry{{Delta: protocol.TCPDelta{BytesSent: 1_000_000, BytesRetrans: 20_000, TotalRetrans: 5, BusyTimeUsec: 1_000_000, RwndLimitedUsec: 100_000, Delivered: 1000, DeliveredCE: 10}, End: protocol.TCPSnapshot{ECNNegotiated: true}}}}
	r := protocol.TCPTestResult{
		IdleLatency:  protocol.LatencyResult{Summary: protocol.LatencySummary{P95MS: 10}},
		SingleStream: protocol.StageResult{Streams: 1, Upload: &protocol.DirectionResult{BitsPerSecond: 100e6}},
		Aggregate:    protocol.StageResult{Streams: 4, Upload: &protocol.DirectionResult{BitsPerSecond: 140e6}, LoadedLatency: protocol.LatencyResult{Summary: protocol.LatencySummary{P95MS: 30}}, LocalTelemetry: sender},
	}
	got := EvaluateTCP(r)
	want := map[string]bool{"queueing-under-load": false, "single-flow-limited": false, "loss-retransmission-limited": false, "receiver-window-limited": false, "host-cpu-limited": false, "ecn-congestion-signaled": false}
	for _, f := range got {
		if _, ok := want[f.Code]; ok {
			want[f.Code] = true
		}
	}
	for code, ok := range want {
		if !ok {
			t.Fatalf("missing finding %s: %+v", code, got)
		}
	}
}

func TestNoFindingWithoutEvidence(t *testing.T) {
	if got := EvaluateTCP(protocol.TCPTestResult{}); len(got) != 0 {
		t.Fatalf("unexpected findings: %+v", got)
	}
}
