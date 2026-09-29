#!/bin/sh
set -eu

GO=${GO:-go}
OUT=${PROFILE_DIR:-profiles}
mkdir -p "$OUT"

"$GO" test -run '^$' -bench '^BenchmarkUDPWritePortable$' -benchtime=2s   -cpuprofile="$OUT/udp-portable.cpu.prof" -memprofile="$OUT/udp-portable.mem.prof" ./internal/fastpath

"$GO" test -run '^$' -bench '^BenchmarkUDPWriteBatch16$' -benchtime=2s   -cpuprofile="$OUT/udp-batch16.cpu.prof" -memprofile="$OUT/udp-batch16.mem.prof" ./internal/fastpath

printf 'profiles written to %s\n' "$OUT"
printf '%s\n' 'Inspect with: go tool pprof <profile>'
