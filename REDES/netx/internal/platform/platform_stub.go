//go:build !linux

package platform

import (
	"errors"
)

func detect() Capabilities {
	return Capabilities{
		Affinity:     false,
		AFXDPEnabled: false,
		AFXDPReason:  "AF_XDP is a Linux-only optional backend and is not enabled",
	}
}

func applyAffinity(cpu, numaNode int) (AffinityResult, error) {
	if cpu < 0 && numaNode < 0 {
		return AffinityResult{}, nil
	}
	return AffinityResult{}, errors.New("CPU/NUMA affinity is only supported on Linux")
}
