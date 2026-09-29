//go:build linux

package hostmetrics

import (
	"bufio"
	"os"
	"runtime"
	"strconv"
	"strings"
	"syscall"
)

func capture() Snapshot {
	var r syscall.Rusage
	if err := syscall.Getrusage(syscall.RUSAGE_SELF, &r); err != nil {
		return Snapshot{}
	}
	total, idle := readCPUStat()
	return Snapshot{
		Supported:         true,
		ProcessCPUSeconds: timevalSeconds(r.Utime) + timevalSeconds(r.Stime),
		SystemTotal:       total, SystemIdle: idle, RSSBytes: readRSS(), NumCPU: runtime.NumCPU(),
		CPUPressureSomeAvg10:    readPSI("/proc/pressure/cpu"),
		MemoryPressureSomeAvg10: readPSI("/proc/pressure/memory"),
	}
}

func timevalSeconds(tv syscall.Timeval) float64 { return float64(tv.Sec) + float64(tv.Usec)/1e6 }

func readCPUStat() (uint64, uint64) {
	f, err := os.Open("/proc/stat")
	if err != nil {
		return 0, 0
	}
	defer f.Close()
	s := bufio.NewScanner(f)
	if !s.Scan() {
		return 0, 0
	}
	fields := strings.Fields(s.Text())
	if len(fields) < 5 || fields[0] != "cpu" {
		return 0, 0
	}
	var vals []uint64
	for _, x := range fields[1:] {
		v, _ := strconv.ParseUint(x, 10, 64)
		vals = append(vals, v)
	}
	var total uint64
	for _, v := range vals {
		total += v
	}
	idle := vals[3]
	if len(vals) > 4 {
		idle += vals[4]
	}
	return total, idle
}

func readRSS() uint64 {
	b, err := os.ReadFile("/proc/self/statm")
	if err != nil {
		return 0
	}
	f := strings.Fields(string(b))
	if len(f) < 2 {
		return 0
	}
	pages, _ := strconv.ParseUint(f[1], 10, 64)
	return pages * uint64(os.Getpagesize())
}

func readPSI(path string) float64 {
	b, err := os.ReadFile(path)
	if err != nil {
		return 0
	}
	for _, line := range strings.Split(string(b), "\n") {
		if !strings.HasPrefix(line, "some ") {
			continue
		}
		for _, field := range strings.Fields(line) {
			if strings.HasPrefix(field, "avg10=") {
				v, _ := strconv.ParseFloat(strings.TrimPrefix(field, "avg10="), 64)
				return v
			}
		}
	}
	return 0
}
