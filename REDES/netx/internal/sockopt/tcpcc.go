package sockopt

import (
	"errors"
	"net"
)

var ErrUnsupported = errors.New("TCP congestion-control selection unsupported on this platform")

func SetCongestionControl(conn net.Conn, name string) error {
	if name == "" {
		return nil
	}
	return setCongestionControl(conn, name)
}
