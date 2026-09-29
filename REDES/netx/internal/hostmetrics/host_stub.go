//go:build !linux

package hostmetrics

func capture() Snapshot { return Snapshot{} }
