package timestamp

import (
	"errors"
	"net"
	"time"
)

const (
	Userspace = "userspace"
	Kernel    = "kernel"
	Hardware  = "hardware"
)

type Reader struct {
	conn *net.UDPConn
	mode string
}

func NewReader(conn *net.UDPConn, mode string) (*Reader, error) {
	if mode == "" {
		mode = Userspace
	}
	if mode != Userspace && mode != Kernel && mode != Hardware {
		return nil, errors.New("timestamp mode must be userspace, kernel or hardware")
	}
	if err := configure(conn, mode); err != nil {
		return nil, err
	}
	return &Reader{conn: conn, mode: mode}, nil
}

func (r *Reader) Read(buf []byte) (n int, addr *net.UDPAddr, arrival time.Time, source string, err error) {
	return read(r.conn, r.mode, buf)
}
