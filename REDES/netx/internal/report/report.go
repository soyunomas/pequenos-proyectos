package report

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func WriteJSON(w io.Writer, v any) error {
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(v)
}

func WriteTCPNDJSON(w io.Writer, result protocol.TCPTestResult) error {
	enc := json.NewEncoder(w)
	summary := result
	summary.IdleLatency.Samples = nil
	summary.Stages = make([]protocol.StageResult, len(result.Stages))
	for i, stage := range result.Stages {
		summary.Stages[i] = stripStageSamples(stage)
	}
	summary.SingleStream = stripStageSamples(result.SingleStream)
	summary.Aggregate = stripStageSamples(result.Aggregate)
	if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "summary", "result": summary}); err != nil {
		return err
	}
	for stageIndex, stage := range result.Stages {
		if stage.Upload != nil {
			if err := writeDirectionSamples(enc, result.SchemaVersion, stageIndex, stage.Streams, "upload", stage.Upload.Samples); err != nil {
				return err
			}
		}
		if stage.Download != nil {
			if err := writeDirectionSamples(enc, result.SchemaVersion, stageIndex, stage.Streams, "download", stage.Download.Samples); err != nil {
				return err
			}
		}
		for _, sample := range stage.LoadedLatency.Samples {
			if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "latency_sample", "phase": "loaded", "stage": stageIndex, "streams": stage.Streams, "sample": sample}); err != nil {
				return err
			}
		}
	}
	for _, sample := range result.IdleLatency.Samples {
		if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "latency_sample", "phase": "idle", "sample": sample}); err != nil {
			return err
		}
	}
	return nil
}

func WriteUDPNDJSON(w io.Writer, result protocol.UDPResult) error {
	enc := json.NewEncoder(w)
	summary := result
	summary.Throughput.Samples = nil
	summary.IdleLatency.Samples = nil
	summary.LoadedLatency.Samples = nil
	if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "summary", "result": summary}); err != nil {
		return err
	}
	for _, sample := range result.Throughput.Samples {
		if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "throughput_sample", "transport": "udp", "direction": "upload", "sample": sample}); err != nil {
			return err
		}
	}
	for _, sample := range result.LoadedLatency.Samples {
		if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "latency_sample", "phase": "loaded", "sample": sample}); err != nil {
			return err
		}
	}
	for _, sample := range result.IdleLatency.Samples {
		if err := enc.Encode(map[string]any{"schema_version": result.SchemaVersion, "type": "latency_sample", "phase": "idle", "sample": sample}); err != nil {
			return err
		}
	}
	return nil
}

func PrintTCPHuman(w io.Writer, result protocol.TCPTestResult) {
	fmt.Fprintf(w, "TCP %s | idle RTT p50 %.2f ms p95 %.2f ms\n", result.Direction, result.IdleLatency.Summary.P50MS, result.IdleLatency.Summary.P95MS)
	for i, stage := range result.Stages {
		fmt.Fprintf(w, "stage %d | %d stream(s)", i+1, stage.Streams)
		if stage.Upload != nil {
			fmt.Fprintf(w, " | upload %.2f Mbit/s", stage.Upload.MegabitsPerSec)
		}
		if stage.Download != nil {
			fmt.Fprintf(w, " | download %.2f Mbit/s", stage.Download.MegabitsPerSec)
		}
		fmt.Fprintf(w, " | loaded RTT p50 %.2f ms p95 %.2f ms p99 %.2f ms\n", stage.LoadedLatency.Summary.P50MS, stage.LoadedLatency.Summary.P95MS, stage.LoadedLatency.Summary.P99MS)
	}
	fmt.Fprintf(w, "selected aggregate: %d stream(s) | %s\n", result.Aggregate.Streams, result.Adaptive.StopReason)
}

func PrintUDPHuman(w io.Writer, result protocol.UDPResult) {
	fmt.Fprintf(w, "UDP upload | target %.2f Mbit/s | received %.2f Mbit/s | loss %.3f%% | reorder %d | jitter %.3f ms\n",
		float64(result.RateBitsPerSec)/1_000_000, result.Throughput.MegabitsPerSec, result.LossPercent, result.PacketsReordered, result.JitterMS)
	fmt.Fprintf(w, "RTT idle p50 %.2f ms p95 %.2f ms | loaded p50 %.2f ms p95 %.2f ms p99 %.2f ms\n",
		result.IdleLatency.Summary.P50MS, result.IdleLatency.Summary.P95MS, result.LoadedLatency.Summary.P50MS, result.LoadedLatency.Summary.P95MS, result.LoadedLatency.Summary.P99MS)
}

func stripStageSamples(stage protocol.StageResult) protocol.StageResult {
	out := stage
	out.LoadedLatency.Samples = nil
	if stage.Upload != nil {
		upload := *stage.Upload
		upload.Samples = nil
		out.Upload = &upload
	}
	if stage.Download != nil {
		download := *stage.Download
		download.Samples = nil
		out.Download = &download
	}
	return out
}

func writeDirectionSamples(enc *json.Encoder, schema, stage, streams int, direction string, samples []protocol.ThroughputSample) error {
	for _, sample := range samples {
		if err := enc.Encode(map[string]any{"schema_version": schema, "type": "throughput_sample", "stage": stage, "streams": streams, "direction": direction, "sample": sample}); err != nil {
			return err
		}
	}
	return nil
}
