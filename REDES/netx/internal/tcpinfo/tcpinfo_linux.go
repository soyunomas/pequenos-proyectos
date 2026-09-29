//go:build linux

package tcpinfo

import (
	"encoding/binary"
	"net"
	"strings"
	"syscall"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

const (
	tcpiOptECN     = 8
	tcpiOptECNSeen = 16
)

func snapshot(conn net.Conn) protocol.TCPSnapshot {
	tcp, ok := conn.(*net.TCPConn)
	if !ok {
		return protocol.TCPSnapshot{Supported: false, Error: "not a TCP connection"}
	}
	raw, err := tcp.SyscallConn()
	if err != nil {
		return protocol.TCPSnapshot{Supported: false, Error: err.Error()}
	}
	var out protocol.TCPSnapshot
	var controlErr error
	err = raw.Control(func(fd uintptr) {
		out, controlErr = snapshotFD(fd)
	})
	if err != nil {
		return protocol.TCPSnapshot{Supported: false, Error: err.Error()}
	}
	if controlErr != nil {
		return protocol.TCPSnapshot{Supported: false, Error: controlErr.Error()}
	}
	return out
}

func snapshotFD(fd uintptr) (protocol.TCPSnapshot, error) {
	var buf [256]byte
	length, err := rawGetsockopt(fd, syscall.IPPROTO_TCP, syscall.TCP_INFO, buf[:])
	if err != nil {
		return protocol.TCPSnapshot{}, err
	}
	b := buf[:length]
	out := protocol.TCPSnapshot{Supported: true, TCPInfoLength: int(length)}
	if len(b) > 0 {
		out.State = b[0]
	}
	if len(b) > 1 {
		out.CAState = b[1]
	}
	if len(b) > 5 {
		out.Options = b[5]
		out.ECNNegotiated = b[5]&tcpiOptECN != 0
		out.ECNSeen = b[5]&tcpiOptECNSeen != 0
	}
	if len(b) > 7 {
		out.DeliveryRateAppLimited = b[7]&1 != 0
	}
	out.RTOUsec = u32(b, 8)
	out.Unacked = u32(b, 24)
	out.Lost = u32(b, 32)
	out.Retrans = u32(b, 36)
	out.RTTUsec = u32(b, 68)
	out.RTTVarUsec = u32(b, 72)
	out.SndSsthresh = u32(b, 76)
	out.SndCwnd = u32(b, 80)
	out.Reordering = u32(b, 88)
	out.RcvSpace = u32(b, 96)
	out.TotalRetrans = u32(b, 100)
	out.PacingRateBytesPerSec = u64(b, 104)
	out.MaxPacingRateBytesPerSec = u64(b, 112)
	out.BytesAcked = u64(b, 120)
	out.BytesReceived = u64(b, 128)
	out.SegsOut = u32(b, 136)
	out.SegsIn = u32(b, 140)
	out.NotSentBytes = u32(b, 144)
	out.MinRTTUsec = u32(b, 148)
	out.DataSegsIn = u32(b, 152)
	out.DataSegsOut = u32(b, 156)
	out.DeliveryRateBytesPerSec = u64(b, 160)
	out.BusyTimeUsec = u64(b, 168)
	out.RwndLimitedUsec = u64(b, 176)
	out.SndbufLimitedUsec = u64(b, 184)
	out.Delivered = u32(b, 192)
	out.DeliveredCE = u32(b, 196)
	out.BytesSent = u64(b, 200)
	out.BytesRetrans = u64(b, 208)
	out.DSACKDups = u32(b, 216)
	out.ReordSeen = u32(b, 220)
	out.RcvOOOPack = u32(b, 224)
	out.SndWnd = u32(b, 228)
	out.RcvWnd = u32(b, 232)
	out.CongestionControl = congestionControl(fd)
	return out, nil
}

func congestionControl(fd uintptr) string {
	var b [64]byte
	length, err := rawGetsockopt(fd, syscall.IPPROTO_TCP, syscall.TCP_CONGESTION, b[:])
	if err != nil || length == 0 {
		return ""
	}
	return strings.TrimRight(string(b[:length]), "\x00")
}

func u32(b []byte, off int) uint32 {
	if off+4 > len(b) {
		return 0
	}
	return binary.NativeEndian.Uint32(b[off : off+4])
}
func u64(b []byte, off int) uint64 {
	if off+8 > len(b) {
		return 0
	}
	return binary.NativeEndian.Uint64(b[off : off+8])
}
