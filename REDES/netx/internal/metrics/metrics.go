package metrics

import (
	"context"
	"math"
	"sort"
	"sync/atomic"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func InWindow(now, start, end time.Time) bool {
	return !now.Before(start) && now.Before(end)
}

func WaitUntil(ctx context.Context, target time.Time) error {
	d := time.Until(target)
	if d <= 0 {
		return nil
	}
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-t.C:
		return nil
	}
}

func SampleCounters(ctx context.Context, start, end time.Time, interval time.Duration, counters []*atomic.Uint64) []protocol.ThroughputSample {
	if interval <= 0 || !end.After(start) {
		return nil
	}
	if err := WaitUntil(ctx, start); err != nil {
		return nil
	}
	previous := snapshot(counters)
	previousAt := start
	next := start.Add(interval)
	var samples []protocol.ThroughputSample
	for next.Before(end) {
		if err := WaitUntil(ctx, next); err != nil {
			return samples
		}
		now := time.Now()
		current := snapshot(counters)
		samples = append(samples, buildSample(start, previousAt, now, previous, current))
		previous, previousAt = current, now
		next = next.Add(interval)
	}
	if err := WaitUntil(ctx, end); err == nil {
		current := snapshot(counters)
		samples = append(samples, buildSample(start, previousAt, end, previous, current))
	}
	return samples
}

func BuildDirection(duration time.Duration, counters []*atomic.Uint64, samples []protocol.ThroughputSample) protocol.DirectionResult {
	seconds := duration.Seconds()
	streams := make([]protocol.StreamResult, len(counters))
	var total uint64
	for i, c := range counters {
		b := c.Load()
		total += b
		streams[i] = protocol.StreamResult{Stream: i, Bytes: b, BitsPerSecond: float64(b*8) / seconds}
	}
	bps := float64(total*8) / seconds
	return protocol.DirectionResult{
		Bytes: total, DurationMS: duration.Milliseconds(), BitsPerSecond: bps,
		MegabitsPerSec: bps / 1_000_000, MebibytesPerSec: float64(total) / seconds / (1 << 20),
		Streams: streams, Samples: samples,
	}
}

func SummarizeLatency(samples []protocol.LatencySample) protocol.LatencySummary {
	if len(samples) == 0 {
		return protocol.LatencySummary{}
	}
	values := make([]float64, len(samples))
	for i := range samples {
		values[i] = samples[i].RTTMS
	}
	sort.Float64s(values)
	median := percentile(values, 0.50)
	dev := make([]float64, len(values))
	for i, v := range values {
		dev[i] = math.Abs(v - median)
	}
	sort.Float64s(dev)
	return protocol.LatencySummary{
		Count: len(values), MinMS: values[0], P50MS: median, P90MS: percentile(values, 0.90),
		P95MS: percentile(values, 0.95), P99MS: percentile(values, 0.99), MaxMS: values[len(values)-1],
		MADMS: percentile(dev, 0.50),
	}
}

func snapshot(counters []*atomic.Uint64) []uint64 {
	out := make([]uint64, len(counters))
	for i, c := range counters {
		out[i] = c.Load()
	}
	return out
}

func buildSample(origin, previousAt, now time.Time, previous, current []uint64) protocol.ThroughputSample {
	seconds := now.Sub(previousAt).Seconds()
	if seconds <= 0 {
		seconds = 1e-9
	}
	per := make([]float64, len(current))
	var bytes uint64
	for i := range current {
		delta := current[i] - previous[i]
		bytes += delta
		per[i] = float64(delta*8) / seconds
	}
	return protocol.ThroughputSample{
		OffsetMS: now.Sub(origin).Milliseconds(), IntervalMS: now.Sub(previousAt).Milliseconds(),
		Bytes: bytes, BitsPerSecond: float64(bytes*8) / seconds, PerStreamBPS: per,
	}
}

func percentile(sorted []float64, q float64) float64 {
	if len(sorted) == 0 {
		return 0
	}
	if len(sorted) == 1 {
		return sorted[0]
	}
	pos := q * float64(len(sorted)-1)
	lo := int(math.Floor(pos))
	hi := int(math.Ceil(pos))
	if lo == hi {
		return sorted[lo]
	}
	frac := pos - float64(lo)
	return sorted[lo]*(1-frac) + sorted[hi]*frac
}
