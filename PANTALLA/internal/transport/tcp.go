// ------ internal/transport/tcp.go ----
package transport

import (
	"crypto/tls"
	"net"

	"github.com/usuario/gokvm/internal/protocol"
)

// SendPacket usa el protocolo binario custom
func SendPacket(conn net.Conn, pkt protocol.Packet) error {
	return pkt.Encode(conn)
}

// ReadPacket usa el protocolo binario custom
func ReadPacket(conn net.Conn, pkt *protocol.Packet) error {
	return pkt.Decode(conn)
}

// GetLocalIP utilidad para ver la IP
func GetLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return ""
	}
	for _, address := range addrs {
		if ipnet, ok := address.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ipnet.IP.To4() != nil {
				return ipnet.IP.String()
			}
		}
	}
	return "localhost"
}

// DialSecure conecta al cliente usando TLS
func DialSecure(address string) (net.Conn, error) {
	// InsecureSkipVerify: true necesario para certificados auto-firmados
	conf := &tls.Config{
		InsecureSkipVerify: true,
	}
	// tls.Dial usa net.Dialer por debajo, que tiene KeepAlive y NoDelay activados por defecto
	return tls.Dial("tcp", address, conf)
}
