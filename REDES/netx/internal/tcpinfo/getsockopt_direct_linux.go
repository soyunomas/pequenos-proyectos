//go:build linux && !386

package tcpinfo

import (
	"syscall"
	"unsafe"
)

func rawGetsockopt(fd uintptr, level, opt int, buf []byte) (int, error) {
	if len(buf) == 0 {
		return 0, syscall.EINVAL
	}
	length := uint32(len(buf))
	_, _, errno := syscall.Syscall6(syscall.SYS_GETSOCKOPT, fd, uintptr(level), uintptr(opt), uintptr(unsafe.Pointer(&buf[0])), uintptr(unsafe.Pointer(&length)), 0)
	if errno != 0 {
		return 0, errno
	}
	if int(length) > len(buf) {
		length = uint32(len(buf))
	}
	return int(length), nil
}
