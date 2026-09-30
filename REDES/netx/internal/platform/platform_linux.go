//go:build linux

package platform

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"golang.org/x/sys/unix"
)

func detect() Capabilities {
	return Capabilities{
		NUMANodes:                     detectNUMA(),
		Affinity:                      true,
		UDPBatching:                   true,
		UDPGSO:                        probeUDPSockopt(unix.UDP_SEGMENT, 1200),
		UDPGRO:                        probeUDPSockopt(unix.UDP_GRO, 1),
		KernelSoftwareTimestamping:    probeSocketTimestamp(unix.SO_TIMESTAMPNS, 1),
		HardwareTimestampingSocketAPI: probeHardwareTimestampSocketAPI(),
		HardwareTimestampingNIC:       false,
		AFXDPEnabled:                  false,
		AFXDPReason:                   "not enabled: no AF_XDP backend is implemented; standard sockets are used",
	}
}

func probeUDPSockopt(opt, value int) bool {
	fd, err := unix.Socket(unix.AF_INET, unix.SOCK_DGRAM|unix.SOCK_CLOEXEC, unix.IPPROTO_UDP)
	if err != nil {
		return false
	}
	defer unix.Close(fd)
	return unix.SetsockoptInt(fd, unix.IPPROTO_UDP, opt, value) == nil
}

func probeSocketTimestamp(opt, value int) bool {
	fd, err := unix.Socket(unix.AF_INET, unix.SOCK_DGRAM|unix.SOCK_CLOEXEC, unix.IPPROTO_UDP)
	if err != nil {
		return false
	}
	defer unix.Close(fd)
	return unix.SetsockoptInt(fd, unix.SOL_SOCKET, opt, value) == nil
}

func probeHardwareTimestampSocketAPI() bool {
	flags := unix.SOF_TIMESTAMPING_RX_HARDWARE |
		unix.SOF_TIMESTAMPING_RAW_HARDWARE |
		unix.SOF_TIMESTAMPING_RX_SOFTWARE |
		unix.SOF_TIMESTAMPING_SOFTWARE
	return probeSocketTimestamp(unix.SO_TIMESTAMPING, flags)
}

func applyAffinity(cpu, numaNode int) (AffinityResult, error) {
	if cpu >= 0 && numaNode >= 0 {
		return AffinityResult{}, errors.New("--cpu and --numa-node are mutually exclusive")
	}
	if cpu < 0 && numaNode < 0 {
		return AffinityResult{}, nil
	}
	var cpus []int
	if cpu >= 0 {
		cpus = []int{cpu}
	} else {
		nodes := detectNUMA()
		for _, node := range nodes {
			if node.Node == numaNode {
				cpus = append(cpus, node.CPUs...)
				break
			}
		}
		if len(cpus) == 0 {
			return AffinityResult{}, fmt.Errorf("NUMA node %d has no discoverable CPUs", numaNode)
		}
	}
	var set unix.CPUSet
	set.Zero()
	for _, c := range cpus {
		if c < 0 || c >= 1024 {
			return AffinityResult{}, fmt.Errorf("CPU %d outside supported affinity mask 0..1023", c)
		}
		set.Set(c)
	}
	tasks, err := os.ReadDir("/proc/self/task")
	if err != nil {
		return AffinityResult{}, fmt.Errorf("read process threads: %w", err)
	}
	applied := 0
	for _, task := range tasks {
		tid, err := strconv.Atoi(task.Name())
		if err != nil {
			continue
		}
		if err := unix.SchedSetaffinity(tid, &set); err != nil {
			return AffinityResult{}, fmt.Errorf("set affinity tid=%d: %w", tid, err)
		}
		applied++
	}
	sort.Ints(cpus)
	return AffinityResult{RequestedCPU: cpu, RequestedNUMA: numaNode, AppliedCPUs: cpus, Threads: applied}, nil
}

func detectNUMA() []NUMANode {
	paths, err := filepath.Glob("/sys/devices/system/node/node*/cpulist")
	if err != nil {
		return nil
	}
	var out []NUMANode
	for _, path := range paths {
		base := filepath.Base(filepath.Dir(path))
		if !strings.HasPrefix(base, "node") {
			continue
		}
		id, err := strconv.Atoi(strings.TrimPrefix(base, "node"))
		if err != nil {
			continue
		}
		b, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		cpus, err := parseCPUList(strings.TrimSpace(string(b)))
		if err != nil {
			continue
		}
		out = append(out, NUMANode{Node: id, CPUs: cpus})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Node < out[j].Node })
	return out
}

func parseCPUList(s string) ([]int, error) {
	if s == "" {
		return nil, errors.New("empty CPU list")
	}
	var out []int
	seen := map[int]bool{}
	for _, field := range strings.Split(s, ",") {
		field = strings.TrimSpace(field)
		if field == "" {
			continue
		}
		if strings.Contains(field, "-") {
			parts := strings.SplitN(field, "-", 2)
			start, err1 := strconv.Atoi(parts[0])
			end, err2 := strconv.Atoi(parts[1])
			if err1 != nil || err2 != nil || start < 0 || end < start {
				return nil, fmt.Errorf("invalid CPU range %q", field)
			}
			for c := start; c <= end; c++ {
				if !seen[c] {
					out = append(out, c)
					seen[c] = true
				}
			}
			continue
		}
		c, err := strconv.Atoi(field)
		if err != nil || c < 0 {
			return nil, fmt.Errorf("invalid CPU %q", field)
		}
		if !seen[c] {
			out = append(out, c)
			seen[c] = true
		}
	}
	sort.Ints(out)
	return out, nil
}
