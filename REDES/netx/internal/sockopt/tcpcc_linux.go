//go:build linux

package sockopt

import (
	"fmt"
	"net"
	"syscall"
)

func setCongestionControl(conn net.Conn, name string) error {
	tcp, ok := conn.(*net.TCPConn)
	if !ok {
		return fmt.Errorf("congestion control requires TCP connection")
	}
	raw, err := tcp.SyscallConn()
	if err != nil {
		return err
	}
	var controlErr error
	if err := raw.Control(func(fd uintptr) {
		controlErr = syscall.SetsockoptString(int(fd), syscall.IPPROTO_TCP, syscall.TCP_CONGESTION, name)
	}); err != nil {
		return err
	}
	if controlErr != nil {
		return fmt.Errorf("set TCP_CONGESTION=%q: %w", name, controlErr)
	}
	return nil
}
