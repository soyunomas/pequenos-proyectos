package advanced

import (
	"bufio"
	"context"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"math"
	"net"
	"sort"
	"strconv"
	"sync"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

const (
	availableMagic        = "NXA4"
	availableHeaderSize   = 34
	availableGapThreshold = 1.25
	availableMinGap       = 50 * time.Microsecond
)

type AvailableConfig struct {
	Host         string
	Port         int
	DialTimeout  time.Duration
	PacketSize   int
	Chirps       int
	ChirpPackets int
	ChirpGap     time.Duration
	MinRateBPS   uint64
	MaxRateBPS   uint64
}

type availablePacket struct {
	sendNS  uint64
	arrival time.Time
}

func RunAvailable(ctx context.Context, cfg AvailableConfig) (protocol.AvailableResult, error) {
	if err := validateAvailableConfig(cfg); err != nil {
		return protocol.AvailableResult{}, err
	}
	testID, err := throughput.NewToken()
	if err != nil {
		return protocol.AvailableResult{}, err
	}
	dialer := net.Dialer{Timeout: cfg.DialTimeout}
	control, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port)))
	if err != nil {
		return protocol.AvailableResult{}, fmt.Errorf("dial available control: %w", err)
	}
	defer control.Close()
	reader := bufio.NewReaderSize(control, 4096)
	req := protocol.Request{
		Mode: "available", PacketSize: cfg.PacketSize, Chirps: cfg.Chirps, ChirpPackets: cfg.ChirpPackets,
		ChirpGapMS: cfg.ChirpGap.Milliseconds(), MinRateBitsPerSec: cfg.MinRateBPS, MaxRateBitsPerSec: cfg.MaxRateBPS,
	}
	if err := protocol.WriteJSONLine(control, req); err != nil {
		return protocol.AvailableResult{}, err
	}
	var offer protocol.Offer
	if err := protocol.ReadJSONLine(reader, &offer); err != nil {
		return protocol.AvailableResult{}, fmt.Errorf("read available offer: %w", err)
	}
	if offer.Transport != "udp-chirp" || offer.DataPort <= 0 || offer.Token == "" {
		return protocol.AvailableResult{}, errors.New("invalid available-bandwidth offer")
	}
	addr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(cfg.Host, strconv.Itoa(offer.DataPort)))
	if err != nil {
		return protocol.AvailableResult{}, err
	}
	conn, err := net.DialUDP("udp", nil, addr)
	if err != nil {
		return protocol.AvailableResult{}, err
	}
	defer conn.Close()
	var ready protocol.Ready
	if err := protocol.ReadJSONLine(reader, &ready); err != nil {
		return protocol.AvailableResult{}, fmt.Errorf("read available ready: %w", err)
	}
	if !ready.Ready {
		return protocol.AvailableResult{}, errors.New("available server did not enter ready state")
	}
	if err := metrics.WaitUntil(ctx, time.Now().Add(time.Duration(ready.StartDelayMS)*time.Millisecond)); err != nil {
		return protocol.AvailableResult{}, err
	}
	if err := sendChirps(ctx, conn, offer.Token, cfg); err != nil {
		return protocol.AvailableResult{}, err
	}
	if err := protocol.WriteJSONLine(control, protocol.AvailableDone{Done: true}); err != nil {
		return protocol.AvailableResult{}, err
	}
	var result protocol.AvailableResult
	if err := protocol.ReadJSONLine(reader, &result); err != nil {
		return protocol.AvailableResult{}, fmt.Errorf("read available result: %w", err)
	}
	result.SchemaVersion = protocol.ResultSchemaVersion
	result.ProtocolVersion = protocol.Version
	result.TestID = testID
	return result, nil
}

func handleAvailableServer(ctx context.Context, control net.Conn, reader *bufio.Reader, listenHost string, req protocol.Request) error {
	cfg := AvailableConfig{
		PacketSize: req.PacketSize, Chirps: req.Chirps, ChirpPackets: req.ChirpPackets,
		ChirpGap:   time.Duration(req.ChirpGapMS) * time.Millisecond,
		MinRateBPS: req.MinRateBitsPerSec, MaxRateBPS: req.MaxRateBitsPerSec,
		DialTimeout: protocol.DefaultDial, Host: listenHost, Port: protocol.DefaultPort,
	}
	if err := validateAvailableConfig(cfg); err != nil {
		_ = protocol.WriteJSONLine(control, map[string]string{"error": err.Error()})
		return err
	}
	token, err := throughput.NewToken()
	if err != nil {
		return err
	}
	addr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(listenHost, "0"))
	if err != nil {
		return err
	}
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return err
	}
	defer conn.Close()
	port := conn.LocalAddr().(*net.UDPAddr).Port
	if err := protocol.WriteJSONLine(control, protocol.Offer{Transport: "udp-chirp", DataPort: port, Token: token}); err != nil {
		return err
	}
	if err := protocol.WriteJSONLine(control, protocol.Ready{Ready: true, StartDelayMS: protocol.DefaultStartDelay.Milliseconds()}); err != nil {
		return err
	}
	tokenBytes, _ := hex.DecodeString(token)
	type doneResult struct{ err error }
	doneCh := make(chan doneResult, 1)
	go func() {
		var done protocol.AvailableDone
		err := protocol.ReadJSONLine(reader, &done)
		if err == nil && !done.Done {
			err = errors.New("invalid available completion")
		}
		doneCh <- doneResult{err: err}
	}()
	packets := make([][]availablePacket, cfg.Chirps)
	seen := make([][]bool, cfg.Chirps)
	for i := range packets {
		packets[i] = make([]availablePacket, cfg.ChirpPackets)
		seen[i] = make([]bool, cfg.ChirpPackets)
	}
	buf := make([]byte, 65535)
	hardDeadline := time.Now().Add(30 * time.Second)
	done := false
	for {
		if time.Now().After(hardDeadline) {
			return errors.New("available-bandwidth receive deadline exceeded")
		}
		_ = conn.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
		n, _, readErr := conn.ReadFromUDP(buf)
		now := time.Now()
		if readErr != nil {
			var ne net.Error
			if errors.As(readErr, &ne) && ne.Timeout() {
				select {
				case d := <-doneCh:
					if d.err != nil {
						return d.err
					}
					done = true
				default:
				}
				if done {
					break
				}
				select {
				case <-ctx.Done():
					return ctx.Err()
				default:
				}
				continue
			}
			return readErr
		}
		if n < availableHeaderSize || string(buf[:4]) != availableMagic || !equalToken(buf[4:20], tokenBytes) {
			continue
		}
		chirp := int(binary.BigEndian.Uint32(buf[20:24]))
		idx := int(binary.BigEndian.Uint16(buf[24:26]))
		if chirp < 0 || chirp >= cfg.Chirps || idx < 0 || idx >= cfg.ChirpPackets {
			continue
		}
		packets[chirp][idx] = availablePacket{sendNS: binary.BigEndian.Uint64(buf[26:34]), arrival: now}
		seen[chirp][idx] = true
	}
	result := analyzeChirps(cfg, packets, seen)
	return protocol.WriteJSONLine(control, result)
}

func sendChirps(ctx context.Context, conn *net.UDPConn, tokenHex string, cfg AvailableConfig) error {
	token, err := hex.DecodeString(tokenHex)
	if err != nil || len(token) != 16 {
		return errors.New("invalid chirp token")
	}
	packet := make([]byte, cfg.PacketSize)
	copy(packet[:4], availableMagic)
	copy(packet[4:20], token)
	bits := float64(cfg.PacketSize * 8)
	pairs := cfg.ChirpPackets - 1
	for chirp := 0; chirp < cfg.Chirps; chirp++ {
		chirpStart := time.Now()
		var due time.Duration
		for idx := 0; idx < cfg.ChirpPackets; idx++ {
			if idx > 0 {
				pair := idx - 1
				fraction := 0.0
				if pairs > 1 {
					fraction = float64(pair) / float64(pairs-1)
				}
				rate := float64(cfg.MinRateBPS) * math.Pow(float64(cfg.MaxRateBPS)/float64(cfg.MinRateBPS), fraction)
				gap := time.Duration(bits / rate * float64(time.Second))
				if gap < availableMinGap {
					gap = availableMinGap
				}
				due += gap
				if err := metrics.WaitUntil(ctx, chirpStart.Add(due)); err != nil {
					return err
				}
			}
			sent := time.Now()
			binary.BigEndian.PutUint32(packet[20:24], uint32(chirp))
			binary.BigEndian.PutUint16(packet[24:26], uint16(idx))
			binary.BigEndian.PutUint64(packet[26:34], uint64(sent.Sub(chirpStart).Nanoseconds()))
			if _, err := conn.Write(packet); err != nil {
				return fmt.Errorf("write chirp %d packet %d: %w", chirp, idx, err)
			}
		}
		if chirp+1 < cfg.Chirps {
			if err := metrics.WaitUntil(ctx, time.Now().Add(cfg.ChirpGap)); err != nil {
				return err
			}
		}
	}
	return nil
}

func analyzeChirps(cfg AvailableConfig, packets [][]availablePacket, seen [][]bool) protocol.AvailableResult {
	result := protocol.AvailableResult{
		SchemaVersion: protocol.ResultSchemaVersion, ProtocolVersion: protocol.Version,
		MeasurementKind: "available_bandwidth_estimate", Method: "chirp-gap-dispersion-v1", Transport: "udp",
		Units: "bits_per_second", PacketSize: cfg.PacketSize, Chirps: cfg.Chirps, ChirpPackets: cfg.ChirpPackets,
		RequestedMinBPS: cfg.MinRateBPS, RequestedMaxBPS: cfg.MaxRateBPS, ConfidenceLevel: 0.95,
	}
	estimates := make([]float64, 0, cfg.Chirps)
	rightCensored := 0
	for chirp := 0; chirp < cfg.Chirps; chirp++ {
		ce := protocol.ChirpEstimate{Chirp: chirp, PacketsExpected: cfg.ChirpPackets}
		for _, ok := range seen[chirp] {
			if ok {
				ce.PacketsReceived++
			}
		}
		ce.LossPercent = float64(cfg.ChirpPackets-ce.PacketsReceived) / float64(cfg.ChirpPackets) * 100
		var points []protocol.AvailablePoint
		for i := 1; i < cfg.ChirpPackets; i++ {
			if !seen[chirp][i-1] || !seen[chirp][i] {
				continue
			}
			sendGap := packets[chirp][i].sendNS - packets[chirp][i-1].sendNS
			if sendGap == 0 {
				continue
			}
			arrivalGap := packets[chirp][i].arrival.Sub(packets[chirp][i-1].arrival)
			if arrivalGap <= 0 {
				continue
			}
			input := float64(cfg.PacketSize*8) * 1e9 / float64(sendGap)
			if input > result.MaxObservedInputBPS {
				result.MaxObservedInputBPS = input
			}
			points = append(points, protocol.AvailablePoint{
				PairIndex: i - 1, SendGapUS: float64(sendGap) / 1e3, ArrivalGapUS: float64(arrivalGap) / float64(time.Microsecond),
				InputBPS: input, GapRatio: float64(arrivalGap) / float64(time.Duration(sendGap)),
			})
		}
		ce.Points = points
		if ce.LossPercent > 20 || len(points) < 4 {
			result.Samples = append(result.Samples, ce)
			continue
		}
		cross := -1
		for i := 1; i < len(points); i++ {
			if points[i-1].GapRatio >= availableGapThreshold && points[i].GapRatio >= availableGapThreshold {
				cross = i - 1
				break
			}
		}
		switch {
		case cross < 0:
			ce.Censored = "right"
			rightCensored++
		case cross == 0:
			ce.Censored = "left"
		default:
			ce.CrossingIndex = points[cross].PairIndex
			ce.LowerBPS = points[cross-1].InputBPS
			ce.UpperBPS = points[cross].InputBPS
			if ce.UpperBPS < ce.LowerBPS {
				ce.LowerBPS, ce.UpperBPS = ce.UpperBPS, ce.LowerBPS
			}
			ce.EstimateBPS = math.Sqrt(ce.LowerBPS * ce.UpperBPS)
			ce.Valid = true
			estimates = append(estimates, ce.EstimateBPS)
		}
		result.Samples = append(result.Samples, ce)
	}
	result.ValidChirps = len(estimates)
	requiredValid := maxInt(3, cfg.Chirps/2+1)
	if len(estimates) < requiredValid {
		if rightCensored >= cfg.Chirps/2+1 {
			result.RejectionReason = "probe ceiling below apparent available bandwidth; increase max rate"
		} else {
			result.RejectionReason = "insufficient valid chirps"
		}
		return result
	}
	sort.Float64s(estimates)
	result.EstimateBPS = median(estimates)
	mean, sd := meanStd(estimates)
	half := 1.96 * sd / math.Sqrt(float64(len(estimates)))
	result.LowerBPS = math.Max(0, mean-half)
	result.UpperBPS = mean + half
	if result.EstimateBPS > 0 {
		result.RelativeWidth = (result.UpperBPS - result.LowerBPS) / result.EstimateBPS
	}
	result.Stable = result.RelativeWidth <= 0.35
	result.EstimateValid = result.Stable
	if !result.Stable {
		result.RejectionReason = "95% confidence interval too wide; estimate is unstable"
	}
	return result
}

func validateAvailableConfig(cfg AvailableConfig) error {
	if cfg.PacketSize < availableHeaderSize+1 || cfg.PacketSize > 65507 {
		return fmt.Errorf("packet size must be between %d and 65507", availableHeaderSize+1)
	}
	if cfg.Chirps < 3 || cfg.Chirps > 64 || cfg.ChirpPackets < 8 || cfg.ChirpPackets > 128 {
		return errors.New("chirps must be 3..64 and chirp-packets 8..128")
	}
	if cfg.MinRateBPS == 0 || cfg.MaxRateBPS <= cfg.MinRateBPS || cfg.MaxRateBPS > 10_000_000_000 {
		return errors.New("available bandwidth rates require 0 < min < max <= 10 Gbit/s")
	}
	fastestGap := time.Duration(float64(cfg.PacketSize*8) / float64(cfg.MaxRateBPS) * float64(time.Second))
	if fastestGap < availableMinGap {
		return fmt.Errorf("max rate requires %s packet gaps, below portable pacing floor %s; reduce --max-rate", fastestGap, availableMinGap)
	}
	if cfg.ChirpGap < 10*time.Millisecond || cfg.ChirpGap > 5*time.Second {
		return errors.New("chirp gap must be between 10ms and 5s")
	}
	if cfg.DialTimeout <= 0 {
		cfg.DialTimeout = protocol.DefaultDial
	}
	return nil
}

func meanStd(v []float64) (float64, float64) {
	if len(v) == 0 {
		return 0, 0
	}
	var sum float64
	for _, x := range v {
		sum += x
	}
	mean := sum / float64(len(v))
	if len(v) == 1 {
		return mean, 0
	}
	var ss float64
	for _, x := range v {
		d := x - mean
		ss += d * d
	}
	return mean, math.Sqrt(ss / float64(len(v)-1))
}

func median(v []float64) float64 {
	if len(v) == 0 {
		return 0
	}
	n := len(v)
	if n%2 == 1 {
		return v[n/2]
	}
	return (v[n/2-1] + v[n/2]) / 2
}

func equalToken(a, b []byte) bool {
	if len(a) != 16 || len(b) != 16 {
		return false
	}
	var x byte
	for i := range a {
		x |= a[i] ^ b[i]
	}
	return x == 0
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

var _ = sync.Once{}