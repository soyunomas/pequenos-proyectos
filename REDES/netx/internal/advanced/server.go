package advanced

import (
	"bufio"
	"context"
	"net"
	"strings"

	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
)

// HandleServer owns Phase-4 modes while keeping the Phase-1..3 server path stable.
// handled=false means the caller should continue with its legacy dispatch.
func HandleServer(ctx context.Context, control net.Conn, reader *bufio.Reader, listenHost string, req protocol.Request) (handled bool, err error) {
	switch {
	case req.Mode == "available":
		return true, handleAvailableServer(ctx, control, reader, listenHost, req)
	case strings.HasPrefix(req.Mode, "quic-"):
		return true, handleQUICServer(ctx, control, listenHost, req)
	case req.Mode == "scenario":
		return true, handleScenarioServer(ctx, control, listenHost, req)
	default:
		return false, nil
	}
}
