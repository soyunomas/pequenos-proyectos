#!/bin/sh
set -eu

BIN=${BIN:-./bin/netx}
PORT=${PORT:-25232}
TMP=${TMPDIR:-/tmp}/netx-diag-overhead-$$
LOG=$TMP.server.log
mkdir -p "$TMP"

cleanup() {
    if [ -n "${SERVER_PID:-}" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -rf "$TMP"
}
trap cleanup EXIT INT TERM

"$BIN" server --listen 127.0.0.1 --port "$PORT" >"$LOG" 2>&1 &
SERVER_PID=$!
sleep 0.15

measure() {
    diag=$1
    if command -v timeout >/dev/null 2>&1; then
        RUN="timeout 8s"
    else
        RUN=""
    fi
    $RUN "$BIN" throughput --port "$PORT" --duration 1200ms --warmup 200ms --sample 300ms --probe-interval 100ms --diagnostics="$diag" 127.0.0.1 |
        awk '/^stage 1/{for(i=1;i<=NF;i++) if($i=="upload"){print $(i+1); exit}}'
}

: >"$TMP/ratios"
i=1
while [ "$i" -le 5 ]; do
    off=$(measure false)
    on=$(measure true)
    awk -v off="$off" -v on="$on" 'BEGIN { if (off <= 0 || on <= 0) exit 2; print ((off-on)/off)*100 }' >>"$TMP/ratios"
    i=$((i+1))
done

median=$(sort -n "$TMP/ratios" | sed -n '3p')
printf 'diagnostics overhead pair degradations (%%): %s\n' "$(tr '\n' ' ' < "$TMP/ratios")"
printf 'diagnostics overhead median degradation: %.3f%%\n' "$median"
awk -v d="$median" 'BEGIN { if (d >= 1.0) { printf "diagnostics overhead gate failed: %.3f%% >= 1%%\n", d > "/dev/stderr"; exit 1 } }'
printf '%s\n' 'diagnostics A/B: PASS (<1% median loopback degradation on this host)'
