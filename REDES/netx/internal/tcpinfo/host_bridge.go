package tcpinfo

import (
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/hostmetrics"
	"github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"
	"time"
)

func captureHost() hostmetrics.Snapshot { return hostmetrics.Capture() }
func hostDelta(a, b hostmetrics.Snapshot, d time.Duration) protocol.HostTelemetry {
	return hostmetrics.Delta(a, b, d.Seconds())
}
