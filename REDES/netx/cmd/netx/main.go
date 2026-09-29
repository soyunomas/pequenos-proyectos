package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/advanced"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/buildinfo"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/latency"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/platform"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/report"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/server"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/throughput"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "netx:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		usage()
		return nil
	}
	switch args[0] {
	case "server":
		return runServer(args[1:])
	case "throughput":
		return runThroughput(args[1:])
	case "udp":
		return runUDP(args[1:])
	case "latency":
		return runLatency(args[1:])
	case "available":
		return runAvailable(args[1:])
	case "quic":
		return runQUIC(args[1:])
	case "scenario":
		return runScenario(args[1:])
	case "responsiveness":
		return runResponsiveness(args[1:])
	case "cc-compare":
		return runCCCompare(args[1:])
	case "capabilities":
		return runCapabilities(args[1:])
	case "version":
		fmt.Printf("netx %s (%s) protocol=%d schema=%d\n", buildinfo.Version, buildinfo.Commit, protocol.Version, protocol.ResultSchemaVersion)
		return nil
	case "help", "-h", "--help":
		usage()
		return nil
	default:
		return fmt.Errorf("unknown command %q (try 'netx help')", args[0])
	}
}

func runServer(args []string) error {
	fs := flag.NewFlagSet("server", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	listen := fs.String("listen", "0.0.0.0", "listen address")
	port := fs.Int("port", protocol.DefaultPort, "control port")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if *port < 1 || *port > 65535 {
		return errors.New("port out of range")
	}
	ctx, stop := commandContext()
	defer stop()
	fmt.Printf("netx server listening on %s:%d (protocol %d)\n", *listen, *port, protocol.Version)
	return server.New(server.Config{ListenHost: *listen, Port: *port}).Run(ctx)
}

func runThroughput(args []string) error {
	fs := flag.NewFlagSet("throughput", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	direction := fs.String("direction", "upload", "upload, download or bidir")
	duration := fs.Duration("duration", protocol.DefaultDuration, "measurement window")
	warmup := fs.Duration("warmup", protocol.DefaultWarmup, "warm-up before measurement")
	buffer := fs.Int("buffer", protocol.DefaultBuffer, "per-stream userspace buffer")
	sample := fs.Duration("sample", protocol.DefaultSampleInterval, "throughput sample interval")
	probe := fs.Duration("probe-interval", protocol.DefaultProbeInterval, "RTT probe interval")
	streams := fs.Int("streams", 1, "target parallel streams; single-flow baseline is retained")
	adaptive := fs.Bool("adaptive", false, "increase 1,2,4,... streams until convergence")
	maxStreams := fs.Int("max-streams", 8, "maximum streams for adaptive mode")
	convergence := fs.Float64("convergence", 5, "stop adaptive mode below this marginal gain percent")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit stable JSON result")
	ndjsonOut := fs.Bool("ndjson", false, "emit summary plus post-measurement samples as NDJSON")
	diagnostics := fs.Bool("diagnostics", true, "collect Linux TCP_INFO/host telemetry and deterministic diagnostics")
	cc := fs.String("cc", "", "request a TCP congestion-control algorithm (for example cubic or bbr)")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx throughput [flags] HOST")
	}
	if *jsonOut && *ndjsonOut {
		return errors.New("--json and --ndjson are mutually exclusive")
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := throughput.RunTCPSuite(ctx, throughput.ClientConfig{
		Host: fs.Arg(0), Port: *port, Direction: *direction, Duration: *duration, Warmup: *warmup,
		BufferSize: *buffer, DialTimeout: *dialTimeout, SampleInterval: *sample, ProbeInterval: *probe,
		Streams: *streams, Adaptive: *adaptive, MaxStreams: *maxStreams, ConvergencePct: *convergence, Diagnostics: *diagnostics, CongestionControl: *cc,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	if *ndjsonOut {
		return report.WriteTCPNDJSON(os.Stdout, result)
	}
	report.PrintTCPHuman(os.Stdout, result)
	return nil
}

func runUDP(args []string) error {
	fs := flag.NewFlagSet("udp", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	duration := fs.Duration("duration", protocol.DefaultDuration, "measurement window")
	warmup := fs.Duration("warmup", protocol.DefaultWarmup, "warm-up before measurement")
	rateText := fs.String("rate", "100M", "target rate in bit/s, accepts K/M/G suffix")
	packet := fs.Int("packet-size", protocol.DefaultUDPPacket, "UDP datagram size including netx header")
	quantum := fs.Duration("pacing-quantum", protocol.DefaultPacingQuantum, "token-bucket pacing quantum")
	sample := fs.Duration("sample", protocol.DefaultSampleInterval, "throughput sample interval")
	probe := fs.Duration("probe-interval", protocol.DefaultProbeInterval, "RTT probe interval")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit stable JSON result")
	ndjsonOut := fs.Bool("ndjson", false, "emit summary plus post-measurement samples as NDJSON")
	timestampMode := fs.String("timestamp", "userspace", "UDP receive timestamp source: userspace, kernel or hardware")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx udp [flags] HOST")
	}
	if *jsonOut && *ndjsonOut {
		return errors.New("--json and --ndjson are mutually exclusive")
	}
	rate, err := throughput.ParseBitrate(*rateText)
	if err != nil {
		return err
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := throughput.RunUDP(ctx, throughput.UDPConfig{
		Host: fs.Arg(0), Port: *port, Duration: *duration, Warmup: *warmup, DialTimeout: *dialTimeout,
		SampleInterval: *sample, ProbeInterval: *probe, RateBitsPerSec: rate, PacketSize: *packet, PacingQuantum: *quantum, TimestampMode: *timestampMode,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	if *ndjsonOut {
		return report.WriteUDPNDJSON(os.Stdout, result)
	}
	report.PrintUDPHuman(os.Stdout, result)
	return nil
}

func runLatency(args []string) error {
	fs := flag.NewFlagSet("latency", flag.ContinueOnError)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	duration := fs.Duration("duration", time.Second, "probe duration")
	interval := fs.Duration("interval", protocol.DefaultProbeInterval, "probe interval")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx latency [flags] HOST")
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := latency.MeasureDuration(ctx, latency.Config{Host: fs.Arg(0), Port: *port, DialTimeout: *dialTimeout, Interval: *interval}, *duration)
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	fmt.Printf("RTT p50 %.3f ms p95 %.3f ms p99 %.3f ms MAD %.3f ms (%d probes)\n", result.Summary.P50MS, result.Summary.P95MS, result.Summary.P99MS, result.Summary.MADMS, result.Summary.Count)
	return nil
}

func runAvailable(args []string) error {
	fs := flag.NewFlagSet("available", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	minRateText := fs.String("min-rate", "1M", "lowest chirp input rate")
	maxRateText := fs.String("max-rate", "100M", "highest chirp input rate")
	packet := fs.Int("packet-size", protocol.DefaultAvailablePacketSize, "UDP chirp datagram size")
	chirps := fs.Int("chirps", protocol.DefaultAvailableChirps, "number of chirps")
	chirpPackets := fs.Int("chirp-packets", protocol.DefaultAvailablePackets, "packets per chirp")
	chirpGap := fs.Duration("chirp-gap", time.Duration(protocol.DefaultAvailableChirpGapMS)*time.Millisecond, "quiet gap between chirps")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx available [flags] HOST")
	}
	minRate, err := throughput.ParseBitrate(*minRateText)
	if err != nil {
		return fmt.Errorf("min-rate: %w", err)
	}
	maxRate, err := throughput.ParseBitrate(*maxRateText)
	if err != nil {
		return fmt.Errorf("max-rate: %w", err)
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := advanced.RunAvailable(ctx, advanced.AvailableConfig{
		Host: fs.Arg(0), Port: *port, DialTimeout: *dialTimeout, PacketSize: *packet, Chirps: *chirps,
		ChirpPackets: *chirpPackets, ChirpGap: *chirpGap, MinRateBPS: minRate, MaxRateBPS: maxRate,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	report.PrintAvailableHuman(os.Stdout, result)
	return nil
}

func runQUIC(args []string) error {
	fs := flag.NewFlagSet("quic", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	direction := fs.String("direction", "upload", "upload, download or bidir")
	duration := fs.Duration("duration", protocol.DefaultDuration, "measurement window")
	warmup := fs.Duration("warmup", protocol.DefaultWarmup, "warm-up before measurement")
	buffer := fs.Int("buffer", protocol.DefaultBuffer, "per-stream userspace buffer")
	sample := fs.Duration("sample", protocol.DefaultSampleInterval, "throughput sample interval")
	probe := fs.Duration("probe-interval", protocol.DefaultProbeInterval, "independent RTT probe interval")
	streams := fs.Int("streams", 1, "QUIC streams; a single-stream baseline is retained")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx quic [flags] HOST")
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := advanced.RunQUICSuite(ctx, advanced.QUICConfig{
		Host: fs.Arg(0), Port: *port, Direction: *direction, Duration: *duration, Warmup: *warmup,
		BufferSize: *buffer, DialTimeout: *dialTimeout, SampleInterval: *sample, ProbeInterval: *probe, Streams: *streams,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	report.PrintQUICHuman(os.Stdout, result)
	return nil
}

func runScenario(args []string) error {
	fs := flag.NewFlagSet("scenario", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	profile := fs.String("profile", "request-response", "request-response, small-message, bursty or streaming")
	duration := fs.Duration("duration", protocol.DefaultDuration, "scenario duration")
	messageSize := fs.Int("message-size", protocol.DefaultScenarioMessageSize, "application payload bytes per message")
	burstMessages := fs.Int("burst-messages", protocol.DefaultScenarioBurstMessages, "messages per burst")
	burstPause := fs.Duration("burst-pause", time.Duration(protocol.DefaultScenarioBurstPauseMS)*time.Millisecond, "pause between bursts")
	rateText := fs.String("rate", "10M", "streaming target payload rate")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx scenario [flags] HOST")
	}
	rate, err := throughput.ParseBitrate(*rateText)
	if err != nil {
		return err
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := advanced.RunScenario(ctx, advanced.ScenarioConfig{
		Host: fs.Arg(0), Port: *port, DialTimeout: *dialTimeout, Duration: *duration, Profile: *profile,
		MessageSize: *messageSize, BurstMessages: *burstMessages, BurstPause: *burstPause, RateBitsPerSec: rate,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	report.PrintScenarioHuman(os.Stdout, result)
	return nil
}

func runResponsiveness(args []string) error {
	fs := flag.NewFlagSet("responsiveness", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	direction := fs.String("direction", "bidir", "upload, download or bidir")
	duration := fs.Duration("duration", protocol.DefaultDuration, "working-condition window")
	warmup := fs.Duration("warmup", protocol.DefaultWarmup, "warm-up")
	buffer := fs.Int("buffer", protocol.DefaultBuffer, "per-stream buffer")
	sample := fs.Duration("sample", protocol.DefaultSampleInterval, "throughput sample interval")
	probe := fs.Duration("probe-interval", protocol.DefaultProbeInterval, "working-latency probe interval")
	streams := fs.Int("streams", 4, "load-generating TCP streams")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx responsiveness [flags] HOST")
	}
	ctx, stop := commandContext()
	defer stop()
	result, err := advanced.RunResponsiveness(ctx, advanced.ResponsivenessConfig{
		Host: fs.Arg(0), Port: *port, Direction: *direction, Duration: *duration, Warmup: *warmup,
		BufferSize: *buffer, DialTimeout: *dialTimeout, SampleInterval: *sample, ProbeInterval: *probe, Streams: *streams,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	report.PrintResponsivenessHuman(os.Stdout, result)
	return nil
}

func runCCCompare(args []string) error {
	fs := flag.NewFlagSet("cc-compare", flag.ContinueOnError)
	tuning := addTuningFlags(fs)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	algorithms := fs.String("algorithms", "cubic,bbr,reno", "comma-separated TCP congestion-control algorithms")
	direction := fs.String("direction", "upload", "upload, download or bidir")
	duration := fs.Duration("duration", protocol.DefaultDuration, "measurement window")
	warmup := fs.Duration("warmup", protocol.DefaultWarmup, "warm-up")
	buffer := fs.Int("buffer", protocol.DefaultBuffer, "per-stream buffer")
	sample := fs.Duration("sample", protocol.DefaultSampleInterval, "throughput sample interval")
	probe := fs.Duration("probe-interval", protocol.DefaultProbeInterval, "RTT probe interval")
	streams := fs.Int("streams", 1, "parallel TCP streams")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if err := tuning.apply(); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx cc-compare [flags] HOST")
	}
	ctx, stop := commandContext()
	defer stop()
	result := advanced.CompareCongestionControl(ctx, throughput.ClientConfig{
		Host: fs.Arg(0), Port: *port, Direction: *direction, Duration: *duration, Warmup: *warmup, BufferSize: *buffer,
		DialTimeout: *dialTimeout, SampleInterval: *sample, ProbeInterval: *probe, Streams: *streams, MaxStreams: *streams,
		ConvergencePct: 5, Diagnostics: true,
	}, strings.Split(*algorithms, ","))
	if *jsonOut {
		return report.WriteJSON(os.Stdout, result)
	}
	report.PrintCCComparisonHuman(os.Stdout, result)
	return nil
}

func runCapabilities(args []string) error {
	fs := flag.NewFlagSet("capabilities", flag.ContinueOnError)
	jsonOut := fs.Bool("json", false, "emit JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if fs.NArg() != 0 {
		return errors.New("usage: netx capabilities [--json]")
	}
	caps := platform.Detect()
	if *jsonOut {
		return report.WriteJSON(os.Stdout, caps)
	}
	fmt.Printf("platform %s/%s | cpus=%d | affinity=%t | udp_batch=%t | gso=%t | gro=%t | kernel_ts=%t | hw_ts_api=%t | af_xdp=%t\n",
		caps.OS, caps.Arch, caps.CPUCount, caps.Affinity, caps.UDPBatching, caps.UDPGSO, caps.UDPGRO,
		caps.KernelSoftwareTimestamping, caps.HardwareTimestampingSocketAPI, caps.AFXDPEnabled)
	if len(caps.NUMANodes) > 0 {
		for _, node := range caps.NUMANodes {
			fmt.Printf("numa node %d cpus=%v\n", node.Node, node.CPUs)
		}
	}
	fmt.Printf("AF_XDP: %s\n", caps.AFXDPReason)
	if caps.HardwareTimestampingSocketAPI && !caps.HardwareTimestampingNIC {
		fmt.Println("hardware timestamping: socket API available; NIC/driver capability is not claimed until externally configured and observed")
	}
	return nil
}

type tuningFlags struct {
	cpu  *int
	numa *int
}

func addTuningFlags(fs *flag.FlagSet) tuningFlags {
	return tuningFlags{
		cpu:  fs.Int("cpu", -1, "pin netx process threads to one Linux CPU (-1 disables)"),
		numa: fs.Int("numa-node", -1, "pin netx process threads to CPUs in one Linux NUMA node (-1 disables)"),
	}
}

func (t tuningFlags) apply() error {
	if t.cpu == nil || t.numa == nil || (*t.cpu < 0 && *t.numa < 0) {
		return nil
	}
	res, err := platform.ApplyAffinity(*t.cpu, *t.numa)
	if err != nil {
		return err
	}
	fmt.Fprintf(os.Stderr, "netx: affinity cpus=%v threads=%d\n", res.AppliedCPUs, res.Threads)
	return nil
}

func commandContext() (context.Context, context.CancelFunc) {
	return signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
}

func usage() {
	fmt.Print(`netx - network performance measurement for real diagnostics

Usage:
  netx server [--listen ADDR] [--port PORT]
  netx throughput [--direction upload|download|bidir] [--streams N|--adaptive] [flags] HOST
  netx udp [--rate 100M] [flags] HOST
  netx latency [flags] HOST
  netx available [flags] HOST
  netx quic [--direction upload|download|bidir] [flags] HOST
  netx scenario [--profile request-response|small-message|bursty|streaming] [flags] HOST
  netx responsiveness [flags] HOST
  netx cc-compare [--algorithms cubic,bbr,reno] [flags] HOST
  netx capabilities [--json]
  netx version

Phase 5 adds capability discovery, optional CPU/NUMA affinity, kernel/hardware UDP receive timestamping, reproducible releases and OpenWrt packaging.
TCP diagnostics are enabled by default and can be disabled with --diagnostics=false. Available bandwidth, transport goodput and responsiveness are reported as distinct magnitudes.
Use 'make help' for validation and OpenWrt cross-build targets.
`)
}