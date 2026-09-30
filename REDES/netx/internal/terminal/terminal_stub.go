//go:build !linux

package terminal

// Linux/OpenWrt is the supported baseline. Other targets default to plain text;
// users can explicitly request ANSI styling with NETX_COLOR=always.
func isTerminal(fd uintptr) bool { return false }
