// Package terminal styles human-readable output using basic ANSI SGR sequences.
// It does not query the terminal, change its background or affect machine output.
package terminal

import (
	"io"
	"os"
	"regexp"
	"strconv"
	"strings"
)

const reset = "\x1b[0m"

var tokens = regexp.MustCompile(`\b(?:TCP|UDP|QUIC|RTT|AF_XDP|netx|platform|numa|stage|diagnosis|estimate|scenario|responsiveness|magnitude|selected aggregate|true|false|unavailable|rejected|error|[0-9]+(?:\.[0-9]+)*)\b`)

type palette struct {
	accent string
	value  string
	note   string
}

type writer struct {
	out io.Writer
	palette
}

// NewWriter leaves non-terminal output untouched in auto mode. NO_COLOR takes
// precedence even over NETX_COLOR=always. Unknown backgrounds keep the user's
// default foreground; light/dark palettes are opt-in or inferred from COLORFGBG.
func NewWriter(out io.Writer) io.Writer {
	file, ok := out.(*os.File)
	tty := ok && isTerminal(file.Fd())
	if !enabled(tty, os.Getenv) {
		return out
	}
	return &writer{out: out, palette: selectPalette(os.Getenv)}
}

func enabled(tty bool, getenv func(string) string) bool {
	if getenv("NO_COLOR") != "" {
		return false
	}
	switch strings.ToLower(getenv("NETX_COLOR")) {
	case "never":
		return false
	case "always":
		return true
	}
	term := strings.ToLower(getenv("TERM"))
	return tty && term != "" && term != "dumb"
}

func selectPalette(getenv func(string) string) palette {
	theme := strings.ToLower(getenv("NETX_COLOR_THEME"))
	if theme == "" || theme == "auto" {
		parts := strings.Split(getenv("COLORFGBG"), ";")
		bg, err := strconv.Atoi(parts[len(parts)-1])
		// Only infer the conventional black/white backgrounds. Other ANSI
		// indices can represent customized palettes and are ambiguous.
		if err == nil {
			switch bg {
			case 0:
				theme = "dark"
			case 7, 15:
				theme = "light"
			}
		}
	}
	switch theme {
	case "light":
		return palette{accent: "\x1b[34m", value: "\x1b[1m", note: "\x1b[31m"}
	case "dark":
		return palette{accent: "\x1b[1;36m", value: "\x1b[1m", note: "\x1b[1;33m"}
	default:
		return palette{accent: "\x1b[1m", value: "\x1b[1m", note: "\x1b[1;4m"}
	}
}

func (w *writer) Write(p []byte) (int, error) {
	styled := tokens.ReplaceAllStringFunc(string(p), func(token string) string {
		style := w.accent
		if token[0] >= '0' && token[0] <= '9' {
			style = w.value
		} else if token == "false" || token == "unavailable" || token == "rejected" || token == "error" {
			style = w.note
		}
		return style + token + reset
	})
	n, err := io.WriteString(w.out, styled)
	if err != nil {
		return 0, err
	}
	if n != len(styled) {
		return 0, io.ErrShortWrite
	}
	return len(p), nil
}
