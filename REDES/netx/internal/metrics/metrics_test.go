package metrics

import (
	"testing"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func TestMeasurementWindowBoundaries(t *testing.T) {
	start := time.Unix(0, 1_000_000_000)
	end := start.Add(time.Second)
	cases := []struct {
		at   time.Time
		want bool
	}{
		{start.Add(-time.Nanosecond), false},
		{start, true},
		{end.Add(-time.Nanosecond), true},
		{end, false},
	}
	for _, tc := range cases {
		if got := InWindow(tc.at, start, end); got != tc.want {
			t.Fatalf("InWindow(%v)=%v want %v", tc.at, got, tc.want)
		}
	}
}

func TestLatencySummaryPercentilesAndMAD(t *testing.T) {
	samples := []protocol.LatencySample{{RTTMS: 1}, {RTTMS: 2}, {RTTMS: 3}, {RTTMS: 4}, {RTTMS: 100}}
	s := SummarizeLatency(samples)
	if s.Count != 5 || s.P50MS != 3 || s.MinMS != 1 || s.MaxMS != 100 || s.MADMS != 1 {
		t.Fatalf("unexpected summary: %+v", s)
	}
	if s.P95MS <= s.P90MS {
		t.Fatalf("expected p95 > p90: %+v", s)
	}
}
