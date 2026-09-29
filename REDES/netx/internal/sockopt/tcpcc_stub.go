//go:build !linux

package sockopt

import "net"

func setCongestionControl(conn net.Conn, name string) error {
	return ErrUnsupported
}
