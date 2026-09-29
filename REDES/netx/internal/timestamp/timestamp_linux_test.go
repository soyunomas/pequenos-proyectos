//go:build linux

package timestamp

import (
	"net"
	"strings"
	"testing"
	"time"
)

func TestKernelSoftwareTimestamp(t *testing.T) {
	recv, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	defer recv.Close()
	reader, err := NewReader(recv, Kernel)
	if err != nil {
		t.Fatal(err)
	}
	send, err := net.DialUDP("udp4", nil, recv.LocalAddr().(*net.UDPAddr))
	if err != nil {
		t.Fatal(err)
	}
	defer send.Close()
	if _, err := send.Write([]byte("netx")); err != nil {
		t.Fatal(err)
	}
	_ = recv.SetReadDeadline(time.Now().Add(time.Second))
	buf := make([]byte, 64)
	n, _, at, source, err := reader.Read(buf)
	if err != nil {
		t.Fatal(err)
	}
	if n != 4 || at.IsZero() {
		t.Fatalf("invalid timestamped read n=%d at=%v", n, at)
	}
	if !strings.Contains(source, "kernel-software") {
		t.Fatalf("unexpected timestamp source %q", source)
	}
}
