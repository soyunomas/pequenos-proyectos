package diagnose

import (
	"math"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

const (
	queueMinMS            = 5.0
	queueRelativeIncrease = 0.50
	retransBytesPct       = 0.10
	limitedTimePct        = 5.0
	cpuNormalizedPct      = 90.0
	singleFlowGainPct     = 15.0
)

func EvaluateTCP(result protocol.TCPTestResult) []protocol.DiagnosticFinding {
	var out []protocol.DiagnosticFinding
	if q, ok := queueing(result); ok {
		out = append(out, q)
	}
	if s, ok := singleFlow(result); ok {
		out = append(out, s)
	}
	for _, endpoint := range senderEndpoints(result.Aggregate) {
		if r, ok := retransmission(endpoint); ok {
			out = append(out, r)
		}
		if r, ok := rwndLimited(endpoint); ok {
			out = append(out, r)
		}
		if r, ok := sndbufLimited(endpoint); ok {
			out = append(out, r)
		}
		if r, ok := cpuLimited(endpoint, result.Aggregate.Streams); ok {
			out = append(out, r)
		}
		if r, ok := ecnCE(endpoint); ok {
			out = append(out, r)
		}
	}
	return dedupe(out)
}

func queueing(r protocol.TCPTestResult) (protocol.DiagnosticFinding, bool) {
	idle := r.IdleLatency.Summary.P95MS
	loaded := r.Aggregate.LoadedLatency.Summary.P95MS
	if idle <= 0 || loaded <= idle {
		return protocol.DiagnosticFinding{}, false
	}
	increase := loaded - idle
	threshold := math.Max(queueMinMS, idle*queueRelativeIncrease)
	if increase < threshold {
		return protocol.DiagnosticFinding{}, false
	}
	return finding("queueing-under-load", "La latencia aumenta de forma material durante la carga.",
		ev("loaded_minus_idle_p95", increase, threshold, "ms"), ev("idle_p95", idle, 0, "ms"), ev("loaded_p95", loaded, 0, "ms")), true
}

func singleFlow(r protocol.TCPTestResult) (protocol.DiagnosticFinding, bool) {
	if r.Aggregate.Streams <= 1 {
		return protocol.DiagnosticFinding{}, false
	}
	single := stageBPS(r.SingleStream)
	agg := stageBPS(r.Aggregate)
	if single <= 0 || agg <= single {
		return protocol.DiagnosticFinding{}, false
	}
	gain := (agg - single) / single * 100
	if gain < singleFlowGainPct {
		return protocol.DiagnosticFinding{}, false
	}
	return finding("single-flow-limited", "Varios flujos obtienen bastante más goodput que un único flujo.",
		ev("aggregate_gain_over_single", gain, singleFlowGainPct, "%"), ev("single_goodput", single/1e6, 0, "Mbit/s"), ev("aggregate_goodput", agg/1e6, 0, "Mbit/s")), true
}

func retransmission(ep protocol.EndpointTelemetry) (protocol.DiagnosticFinding, bool) {
	var sent, retrans uint64
	var count uint32
	for _, s := range ep.TCP {
		sent += s.Delta.BytesSent
		retrans += s.Delta.BytesRetrans
		count += s.Delta.TotalRetrans
	}
	if sent == 0 {
		return protocol.DiagnosticFinding{}, false
	}
	pct := float64(retrans) / float64(sent) * 100
	if pct < retransBytesPct {
		return protocol.DiagnosticFinding{}, false
	}
	return finding("loss-retransmission-limited", "El emisor registra retransmisiones TCP durante la ventana medida.",
		ev("bytes_retrans_percent", pct, retransBytesPct, "%"), ev("total_retransmissions", float64(count), 3, "segments")), true
}

func rwndLimited(ep protocol.EndpointTelemetry) (protocol.DiagnosticFinding, bool) {
	pct := limitedPercent(ep, func(d protocol.TCPDelta) uint64 { return d.RwndLimitedUsec })
	if pct < limitedTimePct {
		return protocol.DiagnosticFinding{}, false
	}
	return finding("receiver-window-limited", "El emisor pasa una fracción relevante del tiempo limitado por la ventana anunciada del receptor.", ev("rwnd_limited_busy_time", pct, limitedTimePct, "%")), true
}

func sndbufLimited(ep protocol.EndpointTelemetry) (protocol.DiagnosticFinding, bool) {
	pct := limitedPercent(ep, func(d protocol.TCPDelta) uint64 { return d.SndbufLimitedUsec })
	if pct < limitedTimePct {
		return protocol.DiagnosticFinding{}, false
	}
	return finding("sender-buffer-limited", "El emisor pasa una fracción relevante del tiempo limitado por su send buffer.", ev("sndbuf_limited_busy_time", pct, limitedTimePct, "%")), true
}

func cpuLimited(ep protocol.EndpointTelemetry, streams int) (protocol.DiagnosticFinding, bool) {
	if !ep.Host.Supported {
		return protocol.DiagnosticFinding{}, false
	}
	if ep.Host.ProcessCPUPercentNormalized >= cpuNormalizedPct {
		return finding("host-cpu-limited", "El proceso de netx consume casi toda la capacidad total de CPU disponible del host durante la medida.",
			ev("process_cpu_normalized", ep.Host.ProcessCPUPercentNormalized, cpuNormalizedPct, "%"), ev("system_cpu", ep.Host.SystemCPUPercent, 0, "%")), true
	}
	if streams == 1 && ep.Host.ProcessCPUPercentOneCore >= 90 {
		return finding("host-cpu-limited", "El test single-flow consume aproximadamente un core completo de CPU; el goodput puede estar limitado por ejecución local.",
			ev("process_cpu_one_core", ep.Host.ProcessCPUPercentOneCore, 90, "%"), ev("process_cpu_normalized", ep.Host.ProcessCPUPercentNormalized, 0, "%")), true
	}
	return protocol.DiagnosticFinding{}, false
}

func ecnCE(ep protocol.EndpointTelemetry) (protocol.DiagnosticFinding, bool) {
	var delivered, ce uint32
	var negotiated bool
	for _, s := range ep.TCP {
		delivered += s.Delta.Delivered
		ce += s.Delta.DeliveredCE
		negotiated = negotiated || s.End.ECNNegotiated
	}
	if !negotiated || ce == 0 {
		return protocol.DiagnosticFinding{}, false
	}
	pct := 0.0
	if delivered > 0 {
		pct = float64(ce) / float64(delivered) * 100
	}
	return finding("ecn-congestion-signaled", "TCP negoció ECN y el emisor observó entregas marcadas CE.", ev("delivered_ce", float64(ce), 0, "segments"), ev("delivered_ce_percent", pct, 0, "%")), true
}

func senderEndpoints(stage protocol.StageResult) []protocol.EndpointTelemetry {
	var out []protocol.EndpointTelemetry
	for _, ep := range []*protocol.EndpointTelemetry{stage.LocalTelemetry, stage.RemoteTelemetry} {
		if ep != nil && ep.Supported && (ep.Role == "sender" || ep.Role == "bidirectional") {
			out = append(out, *ep)
		}
	}
	return out
}

func limitedPercent(ep protocol.EndpointTelemetry, pick func(protocol.TCPDelta) uint64) float64 {
	var limited, busy uint64
	for _, s := range ep.TCP {
		limited += pick(s.Delta)
		busy += s.Delta.BusyTimeUsec
	}
	if busy == 0 {
		return 0
	}
	return float64(limited) / float64(busy) * 100
}

func stageBPS(s protocol.StageResult) float64 {
	var v float64
	if s.Upload != nil {
		v += s.Upload.BitsPerSecond
	}
	if s.Download != nil {
		v += s.Download.BitsPerSecond
	}
	return v
}

func ev(metric string, value, threshold float64, unit string) protocol.DiagnosticEvidence {
	return protocol.DiagnosticEvidence{Metric: metric, Value: value, Threshold: threshold, Unit: unit}
}
func finding(code, summary string, e ...protocol.DiagnosticEvidence) protocol.DiagnosticFinding {
	return protocol.DiagnosticFinding{Code: code, Summary: summary, Evidence: e}
}
func dedupe(in []protocol.DiagnosticFinding) []protocol.DiagnosticFinding {
	seen := map[string]bool{}
	out := make([]protocol.DiagnosticFinding, 0, len(in))
	for _, f := range in {
		if !seen[f.Code] {
			seen[f.Code] = true
			out = append(out, f)
		}
	}
	return out
}
