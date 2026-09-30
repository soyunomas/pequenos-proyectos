package report

import (
	"bytes"
	"encoding/json"
	"errors"
	"strings"
	"testing"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func TestMachineOutputStaysPlainWhenColorIsForced(t *testing.T) {
	t.Setenv("NETX_COLOR", "always")
	t.Setenv("NETX_COLOR_THEME", "dark")
	t.Setenv("NO_COLOR", "")
	r := protocol.TCPTestResult{SchemaVersion: protocol.ResultSchemaVersion, Transport: "tcp"}
	var out bytes.Buffer
	if err := WriteJSON(&out, r); err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(out.Bytes(), []byte{0x1b}) || !json.Valid(out.Bytes()) {
		t.Fatal("JSON contains ANSI escapes or invalid JSON")
	}
	out.Reset()
	if err := WriteTCPNDJSON(&out, r); err != nil {
		t.Fatal(err)
	}
	for _, line := range strings.Split(strings.TrimSpace(out.String()), "\n") {
		if strings.ContainsRune(line, 0x1b) || !json.Valid([]byte(line)) {
			t.Fatal("NDJSON contains ANSI escapes or invalid JSON")
		}
	}
}

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
