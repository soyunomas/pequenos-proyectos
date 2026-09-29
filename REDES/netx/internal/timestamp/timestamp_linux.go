//go:build linux

package timestamp

import (
	"fmt"
	"net"
	"time"
	"unsafe"

	"golang.org/x/sys/unix"
)

func configure(conn *net.UDPConn, mode string) error {
	if mode == Userspace {
		return nil
	}
	raw, err := conn.SyscallConn()
	if err != nil {
		return err
	}
	var controlErr error
	err = raw.Control(func(fd uintptr) {
		switch mode {
		case Kernel:
			controlErr = unix.SetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_TIMESTAMPNS, 1)
		case Hardware:
			flags := unix.SOF_TIMESTAMPING_RX_HARDWARE |
				unix.SOF_TIMESTAMPING_RAW_HARDWARE |
				unix.SOF_TIMESTAMPING_RX_SOFTWARE |
				unix.SOF_TIMESTAMPING_SOFTWARE
			controlErr = unix.SetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_TIMESTAMPING, flags)
		}
	})
	if err != nil {
		return err
	}
	if controlErr != nil {
		return fmt.Errorf("enable %s timestamping: %w", mode, controlErr)
	}
	return nil
}

func read(conn *net.UDPConn, mode string, buf []byte) (int, *net.UDPAddr, time.Time, string, error) {
	if mode == Userspace {
		n, addr, err := conn.ReadFromUDP(buf)
		return n, addr, time.Now(), Userspace, err
	}
	var oob [512]byte
	n, oobn, _, addr, err := conn.ReadMsgUDP(buf, oob[:])
	fallback := time.Now()
	if err != nil {
		return n, addr, fallback, Userspace, err
	}
	msgs, parseErr := unix.ParseSocketControlMessage(oob[:oobn])
	if parseErr != nil {
		return n, addr, fallback, Userspace, nil
	}
	tsSize := int(unsafe.Sizeof(unix.Timespec{}))
	for _, msg := range msgs {
		if msg.Header.Level != unix.SOL_SOCKET {
			continue
		}
		if mode == Kernel && msg.Header.Type == unix.SCM_TIMESTAMPNS && len(msg.Data) >= tsSize {
			ts := *(*unix.Timespec)(unsafe.Pointer(&msg.Data[0]))
			return n, addr, time.Unix(int64(ts.Sec), int64(ts.Nsec)), "kernel-software", nil
		}
		if mode == Hardware && msg.Header.Type == unix.SCM_TIMESTAMPING && len(msg.Data) >= 3*tsSize {
			ts := (*[3]unix.Timespec)(unsafe.Pointer(&msg.Data[0]))
			if ts[2].Sec != 0 || ts[2].Nsec != 0 {
				return n, addr, time.Unix(int64(ts[2].Sec), int64(ts[2].Nsec)), "kernel-hardware", nil
			}
			if ts[0].Sec != 0 || ts[0].Nsec != 0 {
				return n, addr, time.Unix(int64(ts[0].Sec), int64(ts[0].Nsec)), "kernel-software-fallback", nil
			}
		}
	}
	if mode == Hardware {
		return n, addr, fallback, "userspace-hardware-unavailable", nil
	}
	return n, addr, fallback, "userspace-kernel-unavailable", nil
}
