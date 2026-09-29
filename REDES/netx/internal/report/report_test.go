package report

import (
	"errors"
	"testing"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

type failWriter struct{ writes int }

func (w *failWriter) Write(p []byte) (int, error) {
	w.writes++
	if w.writes > 1 {
		return 0, errors.New("blocked sink")
	}
	return len(p), nil
}

func TestNDJSONPropagatesBackpressureAfterMeasurement(t *testing.T) {
	w := &failWriter{}
	result := protocol.TCPTestResult{SchemaVersion: 1, Stages: []protocol.StageResult{{Streams: 1, LoadedLatency: protocol.LatencyResult{Samples: []protocol.LatencySample{{RTTMS: 1}}}}}}
	if err := WriteTCPNDJSON(w, result); err == nil {
		t.Fatal("expected writer error")
	}
}
