package terminal

import (
	"bytes"
	"errors"
	"io"
	"regexp"
	"strings"
	"testing"
)

func TestColorPolicy(t *testing.T) {
	for _, tc := range []struct {
		name string
		tty  bool
		env  map[string]string
		want bool
	}{
		{"terminal", true, map[string]string{"TERM": "xterm"}, true},
		{"redirected", false, map[string]string{"TERM": "xterm"}, false},
		{"dumb", true, map[string]string{"TERM": "dumb"}, false},
		{"unknown terminal", true, nil, false},
		{"disabled", true, map[string]string{"TERM": "xterm", "NETX_COLOR": "never"}, false},
		{"forced", false, map[string]string{"NETX_COLOR": "always"}, true},
		{"no color overrides force", true, map[string]string{"TERM": "xterm", "NETX_COLOR": "always", "NO_COLOR": "1"}, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := enabled(tc.tty, func(k string) string { return tc.env[k] }); got != tc.want {
				t.Fatalf("enabled=%t, want %t", got, tc.want)
			}
		})
	}
}

func TestUnknownBackgroundPreservesForeground(t *testing.T) {
	for _, bg := range []string{"", "invalid", "0;4", "0;8", "0;200"} {
		p := selectPalette(func(k string) string {
			if k == "COLORFGBG" {
				return bg
			}
			return ""
		})
		if p.accent != "\x1b[1m" || p.value != "\x1b[1m" || p.note != "\x1b[1;4m" {
			t.Fatalf("ambiguous background %q changed foreground: %+v", bg, p)
		}
	}
}

func TestColorThemes(t *testing.T) {
	get := func(theme, bg string) func(string) string {
		return func(k string) string {
			return map[string]string{"NETX_COLOR_THEME": theme, "COLORFGBG": bg}[k]
		}
	}
	if selectPalette(get("auto", "15;0")) != selectPalette(get("dark", "")) {
		t.Fatal("black background should use dark theme")
	}
	if selectPalette(get("auto", "0;15")) != selectPalette(get("light", "")) {
		t.Fatal("white background should use light theme")
	}
	if selectPalette(get("light", "15;0")) != selectPalette(get("light", "")) {
		t.Fatal("explicit theme should override background inference")
	}
}

func TestStylingPreservesTextAndWriterContract(t *testing.T) {
	t.Setenv("NETX_COLOR", "always")
	t.Setenv("NO_COLOR", "")
	t.Setenv("NETX_COLOR_THEME", "light")
	var out bytes.Buffer
	w := NewWriter(&out)
	text := "TCP upload 9.70 Mbit/s | pérdida 0% | AF_XDP: false\n"
	n, err := w.Write([]byte(text))
	if err != nil || n != len(text) {
		t.Fatalf("Write=%d,%v", n, err)
	}
	if !strings.Contains(out.String(), "\x1b[") {
		t.Fatal("forced styling missing")
	}
	ansi := regexp.MustCompile(`\x1b\[[0-9;]*m`)
	if ansi.ReplaceAllString(out.String(), "") != text {
		t.Fatal("styling changed result text")
	}
	if !strings.HasSuffix(out.String(), reset+"\n") {
		t.Fatal("last styled token must reset terminal attributes")
	}
}

type shortWriter struct{}

func (shortWriter) Write(p []byte) (int, error) { return len(p) / 2, nil }

func TestShortWriteIsReported(t *testing.T) {
	w := &writer{out: shortWriter{}, palette: palette{accent: "\x1b[1m"}}
	if _, err := w.Write([]byte("TCP")); !errors.Is(err, io.ErrShortWrite) {
		t.Fatalf("expected short-write error, got %v", err)
	}
}
