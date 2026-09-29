//go:build !linux

package tcpinfo

import (
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"net"
)

func snapshot(conn net.Conn) protocol.TCPSnapshot {
	return protocol.TCPSnapshot{Supported: false, Error: "TCP_INFO unsupported on this platform"}
}
