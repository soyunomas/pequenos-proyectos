package platform

import "runtime"

type NUMANode struct {
	Node int   `json:"node"`
	CPUs []int `json:"cpus"`
}

type Capabilities struct {
	OS                            string     `json:"os"`
	Arch                          string     `json:"arch"`
	CPUCount                      int        `json:"cpu_count"`
	NUMANodes                     []NUMANode `json:"numa_nodes,omitempty"`
	Affinity                      bool       `json:"affinity"`
	UDPBatching                   bool       `json:"udp_batching"`
	UDPGSO                        bool       `json:"udp_gso"`
	UDPGRO                        bool       `json:"udp_gro"`
	KernelSoftwareTimestamping    bool       `json:"kernel_software_timestamping"`
	HardwareTimestampingSocketAPI bool       `json:"hardware_timestamping_socket_api"`
	HardwareTimestampingNIC       bool       `json:"hardware_timestamping_nic_verified"`
	AFXDPEnabled                  bool       `json:"af_xdp_enabled"`
	AFXDPReason                   string     `json:"af_xdp_reason"`
}

type AffinityResult struct {
	RequestedCPU  int   `json:"requested_cpu,omitempty"`
	RequestedNUMA int   `json:"requested_numa,omitempty"`
	AppliedCPUs   []int `json:"applied_cpus,omitempty"`
	Threads       int   `json:"threads,omitempty"`
}

func Detect() Capabilities {
	c := detect()
	c.OS = runtime.GOOS
	c.Arch = runtime.GOARCH
	c.CPUCount = runtime.NumCPU()
	return c
}

func ApplyAffinity(cpu, numaNode int) (AffinityResult, error) {
	return applyAffinity(cpu, numaNode)
}
