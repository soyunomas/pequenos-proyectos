package protocol

import (
	"bufio"
	"bytes"
	"strings"
	"testing"
)

func TestJSONLineRoundTrip(t *testing.T) {
	var buf bytes.Buffer
	want := Request{Mode: "tcp-upload", DurationMS: 1000, WarmupMS: 200, BufferSize: 65536}
	if err := WriteJSONLine(&buf, want); err != nil {
		t.Fatal(err)
	}
	var got Request
	if err := ReadJSONLine(bufio.NewReader(&buf), &got); err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestReadJSONLineRejectsOversize(t *testing.T) {
	input := strings.Repeat("x", MaxControlFrame+1) + "\n"
	var dst map[string]any
	if err := ReadJSONLine(bufio.NewReader(strings.NewReader(input)), &dst); err == nil {
		t.Fatal("expected oversized frame error")
	}
}
