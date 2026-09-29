package advanced

import (
	"math"
	"testing"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func TestAnalyzeChirpsStableCrossing(t *testing.T) {
	cfg := AvailableConfig{PacketSize: 1200, Chirps: 8, ChirpPackets: 12, MinRateBPS: 1_000_000, MaxRateBPS: 10_000_000}
	packets, seen := syntheticChirps(cfg, func(chirp int) float64 { return 5_000_000 })
	r := analyzeChirps(cfg, packets, seen)
	if !r.EstimateValid || !r.Stable {
		t.Fatalf("expected stable valid estimate: %+v", r)
	}
	if r.MeasurementKind != "available_bandwidth_estimate" {
		t.Fatalf("wrong measurement kind: %q", r.MeasurementKind)
	}
	if r.EstimateBPS < 3_000_000 || r.EstimateBPS > 8_000_000 {
		t.Fatalf("estimate outside expected bracket: %.0f", r.EstimateBPS)
	}
}

func TestAnalyzeChirpsRejectsUnstableEstimate(t *testing.T) {
	cfg := AvailableConfig{PacketSize: 1200, Chirps: 8, ChirpPackets: 12, MinRateBPS: 1_000_000, MaxRateBPS: 10_000_000}
	packets, seen := syntheticChirps(cfg, func(chirp int) float64 {
		if chirp%2 == 0 {
			return 2_000_000
		}
		return 9_000_000
	})
	r := analyzeChirps(cfg, packets, seen)
	if r.EstimateValid {
		t.Fatalf("unstable chirps must not yield a valid estimate: %+v", r)
	}
	if r.RejectionReason == "" {
		t.Fatal("expected explicit rejection reason")
	}
}

func TestMeasurementKindsAreNotInterchangeable(t *testing.T) {
	kinds := []string{
		(protocol.TCPTestResult{MeasurementKind: "transport_goodput"}).MeasurementKind,
		(protocol.AvailableResult{MeasurementKind: "available_bandwidth_estimate"}).MeasurementKind,
		(protocol.ResponsivenessResult{MeasurementKind: "responsiveness_under_working_conditions"}).MeasurementKind,
	}
	seen := map[string]bool{}
	for _, kind := range kinds {
		if kind == "" || kind == "capacity" || kind == "path_capacity" {
			t.Fatalf("invalid measurement label %q", kind)
		}
		if seen[kind] {
			t.Fatalf("measurement kinds collapsed: %v", kinds)
		}
		seen[kind] = true
	}
}

func syntheticChirps(cfg AvailableConfig, crossing func(int) float64) ([][]availablePacket, [][]bool) {
	packets := make([][]availablePacket, cfg.Chirps)
	seen := make([][]bool, cfg.Chirps)
	bits := float64(cfg.PacketSize * 8)
	pairs := cfg.ChirpPackets - 1
	for c := 0; c < cfg.Chirps; c++ {
		packets[c] = make([]availablePacket, cfg.ChirpPackets)
		seen[c] = make([]bool, cfg.ChirpPackets)
		sendNS := uint64(0)
		arrival := time.Unix(0, 0)
		packets[c][0] = availablePacket{sendNS: 0, arrival: arrival}
		seen[c][0] = true
		for i := 1; i < cfg.ChirpPackets; i++ {
			f := float64(i-1) / float64(pairs-1)
			rate := float64(cfg.MinRateBPS) * math.Pow(float64(cfg.MaxRateBPS)/float64(cfg.MinRateBPS), f)
			gap := uint64(bits / rate * 1e9)
			if gap == 0 {
				gap = 1
			}
			sendNS += gap
			ratio := 1.0
			if rate >= crossing(c) {
				ratio = 1.5
			}
			arrival = arrival.Add(time.Duration(float64(gap) * ratio))
			packets[c][i] = availablePacket{sendNS: sendNS, arrival: arrival}
			seen[c][i] = true
		}
	}
	return packets, seen
}
