//go:build linux

package fastpath

import (
	"net"
	"testing"

	"golang.org/x/net/ipv4"
)

func BenchmarkUDPWritePortable(b *testing.B) {
	recv, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		b.Fatal(err)
	}
	defer recv.Close()
	_ = recv.SetReadBuffer(4 << 20)
	stop := make(chan struct{})
	go drainUDP(recv, stop)
	defer close(stop)

	send, err := net.DialUDP("udp4", nil, recv.LocalAddr().(*net.UDPAddr))
	if err != nil {
		b.Fatal(err)
	}
	defer send.Close()
	payload := make([]byte, 1200)
	b.SetBytes(int64(len(payload)))
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if _, err := send.Write(payload); err != nil {
			b.Fatal(err)
		}
	}
}

func BenchmarkUDPWriteBatch16(b *testing.B) {
	recv, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		b.Fatal(err)
	}
	defer recv.Close()
	_ = recv.SetReadBuffer(4 << 20)
	stop := make(chan struct{})
	go drainUDP(recv, stop)
	defer close(stop)

	send, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		b.Fatal(err)
	}
	defer send.Close()
	pc := ipv4.NewPacketConn(send)

	const batch = 16
	payload := make([]byte, 1200)
	msgs := make([]ipv4.Message, batch)
	dst := recv.LocalAddr()
	for i := range msgs {
		msgs[i] = ipv4.Message{Buffers: [][]byte{payload}, Addr: dst}
	}
	b.SetBytes(int64(len(payload) * batch))
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		n, err := pc.WriteBatch(msgs, 0)
		if err != nil {
			b.Fatal(err)
		}
		if n != batch {
			b.Fatalf("short batch: %d/%d", n, batch)
		}
	}
}

func drainUDP(conn *net.UDPConn, stop <-chan struct{}) {
	buf := make([]byte, 64<<10)
	for {
		select {
		case <-stop:
			return
		default:
			_ = conn.SetReadDeadline(zeroDeadline())
			if _, _, err := conn.ReadFromUDP(buf); err != nil {
				return
			}
		}
	}
}
