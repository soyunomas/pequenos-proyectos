package report

import (
	"fmt"
	"io"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func PrintAvailableHuman(w io.Writer, r protocol.AvailableResult) {
	fmt.Fprintf(w, "available bandwidth | method %s | probe %.2f..%.2f Mbit/s | %d/%d valid chirps\n",
		r.Method, float64(r.RequestedMinBPS)/1e6, float64(r.RequestedMaxBPS)/1e6, r.ValidChirps, r.Chirps)
	if r.EstimateValid {
		fmt.Fprintf(w, "estimate %.2f Mbit/s | 95%% CI %.2f..%.2f Mbit/s | relative width %.1f%%\n",
			r.EstimateBPS/1e6, r.LowerBPS/1e6, r.UpperBPS/1e6, r.RelativeWidth*100)
	} else {
		fmt.Fprintf(w, "estimate rejected | %s", r.RejectionReason)
		if r.EstimateBPS > 0 {
			fmt.Fprintf(w, " | provisional %.2f Mbit/s | interval %.2f..%.2f Mbit/s", r.EstimateBPS/1e6, r.LowerBPS/1e6, r.UpperBPS/1e6)
		}
		fmt.Fprintln(w)
	}
	fmt.Fprintln(w, "magnitude: available_bandwidth_estimate (not bottleneck capacity and not TCP/QUIC goodput)")
}

func PrintQUICHuman(w io.Writer, r protocol.QUICTestResult) {
	fmt.Fprintf(w, "QUIC %s goodput | idle RTT p50 %.2f ms p95 %.2f ms\n", r.Direction, r.IdleLatency.Summary.P50MS, r.IdleLatency.Summary.P95MS)
	for i, stage := range r.Stages {
		fmt.Fprintf(w, "stage %d | %d stream(s)", i+1, stage.Streams)
		if stage.Upload != nil {
			fmt.Fprintf(w, " | upload %.2f Mbit/s", stage.Upload.MegabitsPerSec)
		}
		if stage.Download != nil {
			fmt.Fprintf(w, " | download %.2f Mbit/s", stage.Download.MegabitsPerSec)
		}
		fmt.Fprintf(w, " | loaded RTT p95 %.2f ms\n", stage.LoadedLatency.Summary.P95MS)
	}
	fmt.Fprintf(w, "selected aggregate: %d stream(s)\n", r.Aggregate.Streams)
	fmt.Fprintln(w, "magnitude: transport_goodput (QUIC), not path capacity")
}

func PrintScenarioHuman(w io.Writer, r protocol.ScenarioResult) {
	fmt.Fprintf(w, "scenario %s over %s | message %d B | duration %d ms | operations %d | %.2f ops/s\n",
		r.Profile, r.Transport, r.MessageSize, r.DurationMS, r.Requests, r.OperationsPerSecond)
	fmt.Fprintf(w, "payload sent %d B | received %d B | payload rate %.2f Mbit/s\n",
		r.PayloadBytesSent, r.PayloadBytesReceived, r.PayloadBitsPerSecond/1e6)
	if r.Latency.Summary.Count > 0 {
		fmt.Fprintf(w, "operation latency p50 %.3f ms p95 %.3f ms p99 %.3f ms MAD %.3f ms\n",
			r.Latency.Summary.P50MS, r.Latency.Summary.P95MS, r.Latency.Summary.P99MS, r.Latency.Summary.MADMS)
	}
}

func PrintResponsivenessHuman(w io.Writer, r protocol.ResponsivenessResult) {
	fmt.Fprintf(w, "responsiveness under working conditions | %s | %d stream(s)\n", r.Direction, r.Streams)
	if r.UploadBPS > 0 {
		fmt.Fprintf(w, "upload goodput %.2f Mbit/s\n", r.UploadBPS/1e6)
	}
	if r.DownloadBPS > 0 {
		fmt.Fprintf(w, "download goodput %.2f Mbit/s\n", r.DownloadBPS/1e6)
	}
	fmt.Fprintf(w, "idle RTT p50 %.2f ms p95 %.2f ms | working RTT p50 %.2f ms p95 %.2f ms p99 %.2f ms\n",
		r.IdleLatency.Summary.P50MS, r.IdleLatency.Summary.P95MS,
		r.WorkingLatency.Summary.P50MS, r.WorkingLatency.Summary.P95MS, r.WorkingLatency.Summary.P99MS)
	fmt.Fprintf(w, "working RPM approximation %.0f | reference %s | draft-conformant=%t\n", r.WorkingRPMApprox, r.Reference, r.DraftConformant)
	if !r.DraftConformant {
		fmt.Fprintf(w, "note: %s\n", r.NonConformanceNote)
	}
}

func PrintCCComparisonHuman(w io.Writer, r protocol.CCComparisonResult) {
	fmt.Fprintf(w, "TCP congestion-control comparison | %s | %d stream(s)\n", r.Direction, r.Streams)
	for _, item := range r.Items {
		if !item.Supported {
			fmt.Fprintf(w, "%s | unavailable | %s\n", item.Algorithm, item.Error)
			continue
		}
		fmt.Fprintf(w, "%s | goodput %.2f Mbit/s | loaded RTT p95 %.2f ms | retrans %.3f%%\n",
			item.Algorithm, item.GoodputBPS/1e6, item.LoadedP95MS, item.RetransPct)
	}
}
