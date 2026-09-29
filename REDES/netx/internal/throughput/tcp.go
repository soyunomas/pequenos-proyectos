package throughput

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"syscall"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

type ClientConfig struct {
	Host        string
	Port        int
	Duration    time.Duration
	Warmup      time.Duration
	BufferSize  int
	DialTimeout time.Duration
}

func RunTCPUpload(ctx context.Context, cfg ClientConfig) (protocol.Result, error) {
	if err := validateClientConfig(cfg); err != nil {
		return protocol.Result{}, err
	}

	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	controlAddr := net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
	control, err := dialer.DialContext(ctx, "tcp", controlAddr)
	if err != nil {
		return protocol.Result{}, fmt.Errorf("dial control %s: %w", controlAddr, err)
	}
	defer control.Close()

	deadline := time.Now().Add(cfg.Warmup + cfg.Duration + 15*time.Second)
	_ = control.SetDeadline(deadline)
	reader := bufio.NewReaderSize(control, 4096)

	req := protocol.Request{
		Mode:       "tcp-upload",
		DurationMS: cfg.Duration.Milliseconds(),
		WarmupMS:   cfg.Warmup.Milliseconds(),
		BufferSize: cfg.BufferSize,
	}
	if err := protocol.WriteJSONLine(control, req); err != nil {
		return protocol.Result{}, fmt.Errorf("send request: %w", err)
	}

	var offer protocol.Offer
	if err := protocol.ReadJSONLine(reader, &offer); err != nil {
		return protocol.Result{}, fmt.Errorf("read offer: %w", err)
	}
	if offer.DataPort <= 0 || offer.Token == "" {
		return protocol.Result{}, errors.New("invalid data offer")
	}

	dataAddr := net.JoinHostPort(cfg.Host, strconv.Itoa(offer.DataPort))
	dataConn, err := dialer.DialContext(ctx, "tcp", dataAddr)
	if err != nil {
		return protocol.Result{}, fmt.Errorf("dial data %s: %w", dataAddr, err)
	}
	defer dataConn.Close()
	_ = dataConn.SetDeadline(deadline)

	if _, err := io.WriteString(dataConn, offer.Token+"\n"); err != nil {
		return protocol.Result{}, fmt.Errorf("send data token: %w", err)
	}

	var ready struct {
		Ready bool `json:"ready"`
	}
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		return protocol.Result{}, fmt.Errorf("read ready: %w", err)
	}
	if !ready.Ready {
		return protocol.Result{}, errors.New("server did not enter ready state")
	}

	buf := make([]byte, cfg.BufferSize)
	streamStart := time.Now()
	measureDoneAt := streamStart.Add(cfg.Warmup + cfg.Duration)
	stopAt := measureDoneAt.Add(protocol.DefaultGuardTime)
	for time.Now().Before(stopAt) {
		if _, err := dataConn.Write(buf); err != nil {
			var ne net.Error
			if errors.As(err, &ne) && ne.Timeout() {
				break
			}
			expectedClose := errors.Is(err, net.ErrClosed) || errors.Is(err, io.EOF) ||
				errors.Is(err, syscall.EPIPE) || errors.Is(err, syscall.ECONNRESET)
			if expectedClose && !time.Now().Before(measureDoneAt) {
				break
			}
			return protocol.Result{}, fmt.Errorf("write data: %w", err)
		}
		select {
		case <-ctx.Done():
			return protocol.Result{}, ctx.Err()
		default:
		}
	}
	_ = dataConn.Close()

	var result protocol.Result
	if err := protocol.ReadJSONLine(reader, &result); err != nil {
		return protocol.Result{}, fmt.Errorf("read result: %w", err)
	}
	return result, nil
}

func NewToken() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("random token: %w", err)
	}
	return hex.EncodeToString(b[:]), nil
}

func validateClientConfig(cfg ClientConfig) error {
	if cfg.Host == "" {
		return errors.New("host is required")
	}
	if cfg.Port < 1 || cfg.Port > 65535 {
		return errors.New("port out of range")
	}
	if cfg.Duration < 100*time.Millisecond || cfg.Duration > 24*time.Hour {
		return errors.New("duration must be between 100ms and 24h")
	}
	if cfg.Warmup < 0 || cfg.Warmup > 10*time.Minute {
		return errors.New("warmup must be between 0 and 10m")
	}
	if cfg.BufferSize < 4<<10 || cfg.BufferSize > 16<<20 {
		return errors.New("buffer must be between 4KiB and 16MiB")
	}
	if cfg.DialTimeout <= 0 {
		return errors.New("dial timeout must be positive")
	}
	return nil
}
