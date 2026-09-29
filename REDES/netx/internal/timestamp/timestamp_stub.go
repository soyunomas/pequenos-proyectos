//go:build !linux

package timestamp

import (
	"fmt"
	"net"
	"time"
)

func configure(_ *net.UDPConn, mode string) error {
	if mode == Userspace {
		return nil
	}
	return fmt.Errorf("%s timestamping is only supported on Linux", mode)
}

func read(conn *net.UDPConn, _ string, buf []byte) (int, *net.UDPAddr, time.Time, string, error) {
	n, addr, err := conn.ReadFromUDP(buf)
	return n, addr, time.Now(), Userspace, err
}
