//go:build linux && 386

package tcpinfo

import (
	"syscall"
	"unsafe"
)

const socketcallGetsockopt = 15

func rawGetsockopt(fd uintptr, level, opt int, buf []byte) (int, error) {
	if len(buf) == 0 {
		return 0, syscall.EINVAL
	}
	length := uint32(len(buf))
	args := [5]uintptr{fd, uintptr(level), uintptr(opt), uintptr(unsafe.Pointer(&buf[0])), uintptr(unsafe.Pointer(&length))}
	_, _, errno := syscall.Syscall(syscall.SYS_SOCKETCALL, uintptr(socketcallGetsockopt), uintptr(unsafe.Pointer(&args[0])), 0)
	if errno != 0 {
		return 0, errno
	}
	if int(length) > len(buf) {
		length = uint32(len(buf))
	}
	return int(length), nil
}
