package throughput

import "testing"

func TestParseBitrate(t *testing.T) {
	cases := map[string]uint64{"10K": 10_000, "2.5M": 2_500_000, "1G": 1_000_000_000, "1234": 1234}
	for in, want := range cases {
		got, err := ParseBitrate(in)
		if err != nil || got != want {
			t.Fatalf("ParseBitrate(%q)=%d,%v want %d", in, got, err, want)
		}
	}
	if _, err := ParseBitrate("0"); err == nil {
		t.Fatal("expected zero bitrate rejection")
	}
}
