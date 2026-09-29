package advanced

import (
	"bufio"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	quic "github.com/quic-go/quic-go"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/latency"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

const quicALPN = "netx/5"

type QUICConfig struct {
	Host           string
	Port           int
	Direction      string
	Duration       time.Duration
	Warmup         time.Duration
	BufferSize     int
	DialTimeout    time.Duration
	SampleInterval time.Duration
	ProbeInterval  time.Duration
	Streams        int
}

type qstream struct {
	stream *quic.Stream
	reader io.Reader
}

func RunQUICSuite(ctx context.Context, cfg QUICConfig) (protocol.QUICTestResult, error) {
	if err := validateQUICConfig(cfg); err != nil {
		return protocol.QUICTestResult{}, err
	}
	testID, err := throughput.NewToken()
	if err != nil {
		return protocol.QUICTestResult{}, err
	}
	idleDuration := 5 * cfg.ProbeInterval
	if idleDuration < 250*time.Millisecond {
		idleDuration = 250 * time.Millisecond
	}
	if idleDuration > time.Second {
		idleDuration = time.Second
	}
	idle, err := latency.MeasureDuration(ctx, latency.Config{Host: cfg.Host, Port: cfg.Port, DialTimeout: cfg.DialTimeout, Interval: cfg.ProbeInterval}, idleDuration)
	if err != nil {
		return protocol.QUICTestResult{}, fmt.Errorf("idle latency: %w", err)
	}
	result := protocol.QUICTestResult{
		SchemaVersion: protocol.ResultSchemaVersion, ProtocolVersion: protocol.Version, TestID: testID,
		MeasurementKind: "transport_goodput", Transport: "quic", Direction: cfg.Direction,
		DurationMS: cfg.Duration.Milliseconds(), WarmupMS: cfg.Warmup.Milliseconds(), SampleMS: cfg.SampleInterval.Milliseconds(),
		IdleLatency: idle,
	}
	stageStreams := []int{1}
	if cfg.Streams > 1 {
		stageStreams = append(stageStreams, cfg.Streams)
	}
	var best float64
	for i, n := range stageStreams {
		stage, err := runQUICStage(ctx, cfg, n)
		if err != nil {