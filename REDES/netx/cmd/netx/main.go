package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/buildinfo"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
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
	case "version":
		fmt.Printf("netx %s (%s) protocol=%d\n", buildinfo.Version, buildinfo.Commit, protocol.Version)
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
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	fmt.Printf("netx server listening on %s:%d\n", *listen, *port)
	return server.New(server.Config{ListenHost: *listen, Port: *port}).Run(ctx)
}

func runThroughput(args []string) error {
	fs := flag.NewFlagSet("throughput", flag.ContinueOnError)
	port := fs.Int("port", protocol.DefaultPort, "server control port")
	duration := fs.Duration("duration", protocol.DefaultDuration, "measurement window")
	warmup := fs.Duration("warmup", protocol.DefaultWarmup, "warm-up before measurement")
	buffer := fs.Int("buffer", protocol.DefaultBuffer, "per-connection userspace buffer")
	dialTimeout := fs.Duration("dial-timeout", protocol.DefaultDial, "connection timeout")
	jsonOut := fs.Bool("json", false, "emit machine-readable JSON")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: netx throughput [flags] HOST")
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	result, err := throughput.RunTCPUpload(ctx, throughput.ClientConfig{
		Host: fs.Arg(0), Port: *port, Duration: *duration, Warmup: *warmup,
		BufferSize: *buffer, DialTimeout: *dialTimeout,
	})
	if err != nil {
		return err
	}
	if *jsonOut {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		return enc.Encode(result)
	}
	fmt.Printf("TCP upload  %8.2f Mbit/s  %8.2f MiB/s  (%d bytes / %s)\n",
		result.MegabitsPerSec, result.MebibytesPerSec, result.Bytes,
		time.Duration(result.DurationMS)*time.Millisecond)
	return nil
}

func usage() {
	fmt.Print(`netx - network performance measurement for real diagnostics

Usage:
  netx server [--listen ADDR] [--port PORT]
  netx throughput [flags] HOST
  netx version

Phase 1 implements a TCP upload baseline with a separate control and data plane.
Run 'make help' in the source tree for build, test and OpenWrt targets.
`)
}
