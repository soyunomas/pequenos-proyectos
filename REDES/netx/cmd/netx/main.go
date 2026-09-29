package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/buildinfo"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/latency"
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
	listen := fs.String("listen", "0.0.0.0", "listen address")
	port := fs.Int("port", protocol.DefaultPort, "control port")
	if err := fs.Parse(args); err != nil {
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
	if err := fs.Parse(args); err != nil {
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
		Streams: *streams, Adaptive: *adaptive, MaxStreams: *maxStreams, ConvergencePct: *convergence, Diagnostics: *diagnostics,
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
	if err := fs.Parse(args); err != nil {
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
		SampleInterval: *sample, ProbeInterval: *probe, RateBitsPerSec: rate, PacketSize: *packet, PacingQuantum: *quantum,
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
  netx version

Phase 3 adds Linux TCP_INFO and host telemetry plus deterministic evidence-based diagnosis.
TCP diagnostics are enabled by default and can be disabled with --diagnostics=false.
Use 'make help' for validation and OpenWrt cross-build targets.
`)
}
