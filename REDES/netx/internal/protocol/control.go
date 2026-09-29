package protocol

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"time"
)

const (
	Version          = 1
	MaxControlFrame  = 64 << 10 // 64 KiB
	MaxTokenLine     = 256
	DefaultPort      = 5202
	DefaultBuffer    = 128 << 10 // 128 KiB
	DefaultDuration  = 10 * time.Second
	DefaultWarmup    = 2 * time.Second
	DefaultDial      = 5 * time.Second
	DefaultGuardTime = 300 * time.Millisecond
)

type Message struct {
	Protocol int         `json:"protocol"`
	Type     string      `json:"type"`
	Payload  interface{} `json:"payload,omitempty"`
	Error    string      `json:"error,omitempty"`
}

type Request struct {
	Mode       string `json:"mode"`
	DurationMS int64  `json:"duration_ms"`
	WarmupMS   int64  `json:"warmup_ms"`
	BufferSize int    `json:"buffer_size"`
}

type Offer struct {
	DataPort int    `json:"data_port"`
	Token    string `json:"token"`
}

type Result struct {
	Mode            string  `json:"mode"`
	Bytes           uint64  `json:"bytes"`
	DurationMS      int64   `json:"duration_ms"`
	BitsPerSecond   float64 `json:"bits_per_second"`
	MegabitsPerSec  float64 `json:"megabits_per_second"`
	MebibytesPerSec float64 `json:"mebibytes_per_second"`
}

func WriteJSONLine(w io.Writer, v any) error {
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	return enc.Encode(v)
}

func ReadJSONLine(r *bufio.Reader, dst any) error {
	line, err := readBoundedLine(r, MaxControlFrame)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(line, dst); err != nil {
		return fmt.Errorf("decode control frame: %w", err)
	}
	return nil
}

func ReadTokenLine(r *bufio.Reader) (string, error) {
	line, err := readBoundedLine(r, MaxTokenLine)
	if err != nil {
		return "", err
	}
	if len(line) == 0 {
		return "", errors.New("empty data token")
	}
	return string(line), nil
}

func readBoundedLine(r *bufio.Reader, limit int) ([]byte, error) {
	var out []byte
	for {
		frag, isPrefix, err := r.ReadLine()
		if err != nil {
			return nil, err
		}
		if len(out)+len(frag) > limit {
			return nil, fmt.Errorf("frame exceeds %d bytes", limit)
		}
		out = append(out, frag...)
		if !isPrefix {
			return out, nil
		}
	}
}
