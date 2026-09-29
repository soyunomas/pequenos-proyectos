package hostmetrics

import "github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/protocol"

type Snapshot struct {
	Supported               bool
	ProcessCPUSeconds       float64
	SystemTotal             uint64
	SystemIdle              uint64
	RSSBytes                uint64
	NumCPU                  int
	CPUPressureSomeAvg10    float64
	MemoryPressureSomeAvg10 float64
}

func Capture() Snapshot { return capture() }

func Delta(a, b Snapshot, wallSeconds float64) protocol.HostTelemetry {
	if !a.Supported || !b.Supported || wallSeconds <= 0 {
		return protocol.HostTelemetry{Supported: false}
	}
	proc := (b.ProcessCPUSeconds - a.ProcessCPUSeconds) / wallSeconds * 100
	numCPU := b.NumCPU
	if numCPU < 1 {
		numCPU = 1
	}
	normalized := proc / float64(numCPU)
	var system float64
	if b.SystemTotal > a.SystemTotal {
		total := b.SystemTotal - a.SystemTotal
		idle := uint64(0)
		if b.SystemIdle >= a.SystemIdle {
			idle = b.SystemIdle - a.SystemIdle
		}
		if idle > total {
			idle = total
		}
		system = float64(total-idle) / float64(total) * 100
	}
	return protocol.HostTelemetry{
		Supported: true, NumCPU: numCPU, ProcessCPUPercentOneCore: proc,
		ProcessCPUPercentNormalized: normalized, SystemCPUPercent: system,
		RSSBytes: b.RSSBytes, CPUPressureSomeAvg10: b.CPUPressureSomeAvg10,
		MemoryPressureSomeAvg10: b.MemoryPressureSomeAvg10,
	}
}
