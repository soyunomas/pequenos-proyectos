//go:build linux

package tcpinfo

import (
	"context"
	"io"
	"net"
	"testing"
	"time"
)

func TestCaptureWindowOnLiveTCPConnection(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	serverCh := make(chan net.Conn, 1)
	go func() {
		c, e := ln.Accept()
		if e == nil {
			serverCh <- c
		}
	}()
	client, err := net.Dial("tcp", ln.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	server := <-serverCh
	defer server.Close()
	go io.Copy(io.Discard, server)

	start := time.Now().Add(40 * time.Millisecond)
	end := start.Add(180 * time.Millisecond)
	ch := make(chan error, 1)
	go func() {
		if d := time.Until(start); d > 0 {
			time.Sleep(d)
		}
		buf := make([]byte, 32<<10)
		for time.Now().Before(end) {
			if _, e := client.Write(buf); e != nil {
				ch <- e
				return
			}
		}
		ch <- nil
	}()
	tele, err := CaptureWindow(context.Background(), []net.Conn{client}, "sender", start, end, true)
	if err != nil {
		t.Fatal(err)
	}
	if e := <-ch; e != nil {
		t.Fatal(e)
	}
	if !tele.Supported || len(tele.TCP) != 1 {
		t.Fatalf("unexpected telemetry: %+v", tele)
	}
	s := tele.TCP[0]
	if !s.End.Supported || s.End.TCPInfoLength < 104 {
		t.Fatalf("TCP_INFO unavailable/short: %+v", s.End)
	}
	if s.End.CongestionControl == "" {
		t.Fatalf("congestion control missing: %+v", s.End)
	}
	if s.End.RTTUsec == 0 {
		t.Fatalf("RTT missing: %+v", s.End)
	}
	if s.Delta.BytesSent == 0 && s.Delta.BytesAcked == 0 {
		t.Fatalf("traffic counters did not advance: %+v", s.Delta)
	}
	if !tele.Host.Supported {
		t.Fatalf("host telemetry unavailable: %+v", tele.Host)
	}
}

func TestSnapshotCostBudget(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	serverCh := make(chan net.Conn, 1)
	go func() {
		c, e := ln.Accept()
		if e == nil {
			serverCh <- c
		}
	}()
	client, err := net.Dial("tcp", ln.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	server := <-serverCh
	defer server.Close()

	const n = 400
	start := time.Now()
	for i := 0; i < n; i++ {
		s := snapshot(client)
		if !s.Supported {
			t.Fatalf("snapshot unsupported: %+v", s)
		}
	}
	avg := time.Since(start) / n
	// NetX samples TCP_INFO only twice per stream (start/end), not per packet.
	// At MaxStreams=64 this 500us/snapshot ceiling keeps the synchronous syscall
	// budget below 64ms, i.e. <1% of the default 10s measurement window.
	if avg > 500*time.Microsecond {
		t.Fatalf("TCP_INFO snapshot avg %s exceeds 500us budget", avg)
	}
	t.Logf("TCP_INFO snapshot average: %s", avg)
}
