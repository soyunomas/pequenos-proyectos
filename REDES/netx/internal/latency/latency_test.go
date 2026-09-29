package latency

import (
	"context"
	"testing"
	"time"
)

func TestMeasureBetweenRejectsBadWindow(t *testing.T) {
	now := time.Now()
	_, err := MeasureBetween(context.Background(), Config{Host: "127.0.0.1", Port: 1, Interval: time.Millisecond}, now, now)
	if err == nil {
		t.Fatal("expected invalid window error")
	}
}
