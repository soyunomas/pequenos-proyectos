package tcpinfo

import (
	"context"
	"net"
	"time"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/metrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

func CaptureWindow(ctx context.Context, conns []net.Conn, role string, start, end time.Time, enabled bool) (protocol.EndpointTelemetry, error) {
	if !enabled {
		return protocol.EndpointTelemetry{Role: role, Supported: false}, nil
	}
	if err := metrics.WaitUntil(ctx, start); err != nil {
		return protocol.EndpointTelemetry{}, err
	}
	startHost := captureHost()
	startTCP := make([]protocol.TCPSnapshot, len(conns))
	for i, conn := range conns {
		startTCP[i] = snapshot(conn)
	}
	if err := metrics.WaitUntil(ctx, end); err != nil {
		return protocol.EndpointTelemetry{}, err
	}
	endTCP := make([]protocol.TCPSnapshot, len(conns))
	for i, conn := range conns {
		endTCP[i] = snapshot(conn)
	}
	endHost := captureHost()

	streams := make([]protocol.TCPStreamTelemetry, len(conns))
	supported := false
	for i := range streams {
		streams[i] = protocol.TCPStreamTelemetry{Stream: i, Start: startTCP[i], End: endTCP[i], Delta: delta(startTCP[i], endTCP[i])}
		if startTCP[i].Supported || endTCP[i].Supported {
			supported = true
		}
	}
	host := hostDelta(startHost, endHost, end.Sub(start))
	return protocol.EndpointTelemetry{Role: role, Supported: supported || host.Supported, TCP: streams, Host: host}, nil
}

func delta(a, b protocol.TCPSnapshot) protocol.TCPDelta {
	return protocol.TCPDelta{
		TotalRetrans:      sub32(b.TotalRetrans, a.TotalRetrans),
		BytesRetrans:      sub64(b.BytesRetrans, a.BytesRetrans),
		BytesAcked:        sub64(b.BytesAcked, a.BytesAcked),
		BytesSent:         sub64(b.BytesSent, a.BytesSent),
		Delivered:         sub32(b.Delivered, a.Delivered),
		DeliveredCE:       sub32(b.DeliveredCE, a.DeliveredCE),
		BusyTimeUsec:      sub64(b.BusyTimeUsec, a.BusyTimeUsec),
		RwndLimitedUsec:   sub64(b.RwndLimitedUsec, a.RwndLimitedUsec),
		SndbufLimitedUsec: sub64(b.SndbufLimitedUsec, a.SndbufLimitedUsec),
	}
}

func sub64(b, a uint64) uint64 {
	if b < a {
		return 0
	}
	return b - a
}
func sub32(b, a uint32) uint32 {
	if b < a {
		return 0
	}
	return b - a
}
