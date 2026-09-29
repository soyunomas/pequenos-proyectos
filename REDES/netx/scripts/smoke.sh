#!/bin/sh
set -eu

BIN=${BIN:-./bin/netx}
PORT=${PORT:-25202}
TMP=${TMPDIR:-/tmp}/netx-smoke-$$
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

run() {
    "$@" >/dev/null
}

run "$BIN" latency --port "$PORT" --duration 250ms --interval 50ms 127.0.0.1
run "$BIN" throughput --port "$PORT" --direction upload --duration 300ms --warmup 100ms --sample 100ms --probe-interval 50ms 127.0.0.1
run "$BIN" throughput --port "$PORT" --direction download --duration 300ms --warmup 100ms --sample 100ms --probe-interval 50ms 127.0.0.1
run "$BIN" throughput --port "$PORT" --direction bidir --duration 300ms --warmup 100ms --sample 100ms --probe-interval 50ms 127.0.0.1
run "$BIN" throughput --port "$PORT" --direction upload --streams 2 --duration 250ms --warmup 100ms --sample 100ms --probe-interval 50ms 127.0.0.1
run "$BIN" throughput --port "$PORT" --direction upload --adaptive --max-streams 4 --convergence 100 --duration 250ms --warmup 100ms --sample 100ms --probe-interval 50ms 127.0.0.1
run "$BIN" udp --port "$PORT" --duration 300ms --warmup 100ms --rate 10M --sample 100ms --probe-interval 50ms 127.0.0.1

"$BIN" throughput --port "$PORT" --duration 250ms --warmup 100ms --sample 100ms --probe-interval 50ms --json 127.0.0.1 >"$TMP/tcp.json"
grep -q '"schema_version": 2' "$TMP/tcp.json"
grep -q '"single_stream"' "$TMP/tcp.json"
grep -q '"aggregate"' "$TMP/tcp.json"
grep -q '"idle_latency"' "$TMP/tcp.json"
grep -q '"diagnostics_enabled": true' "$TMP/tcp.json"
grep -q '"congestion_control"' "$TMP/tcp.json"
grep -q '"local_telemetry"' "$TMP/tcp.json"

"$BIN" udp --port "$PORT" --duration 250ms --warmup 100ms --rate 5M --sample 100ms --probe-interval 50ms --json 127.0.0.1 >"$TMP/udp.json"
grep -q '"packets_lost"' "$TMP/udp.json"
grep -q '"jitter_ms"' "$TMP/udp.json"

"$BIN" throughput --port "$PORT" --duration 250ms --warmup 100ms --sample 100ms --probe-interval 50ms --ndjson 127.0.0.1 >"$TMP/tcp.ndjson"
grep -q '"type":"summary"' "$TMP/tcp.ndjson"
grep -q '"type":"throughput_sample"' "$TMP/tcp.ndjson"
grep -q '"type":"latency_sample"' "$TMP/tcp.ndjson"

"$BIN" throughput --port "$PORT" --duration 250ms --warmup 100ms --sample 100ms --probe-interval 50ms --diagnostics=false --json 127.0.0.1 >"$TMP/tcp-nodiag.json"
! grep -q '"local_telemetry"' "$TMP/tcp-nodiag.json"

printf '%s\n' 'smoke: Phase 3 TCP/UDP/latency/telemetry/JSON/NDJSON ok'
