//go:build linux

package fastpath

import "time"

func zeroDeadline() time.Time { return time.Time{} }
