#!/bin/sh
set -eu

BIN=${BIN:-./bin/netx}
PORT=${PORT:-25202}
LOG=${TMPDIR:-/tmp}/netx-smoke-$$.log

cleanup() {
    if [ -n "${SERVER_PID:-}" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -f "$LOG"
}
trap cleanup EXIT INT TERM

"$BIN" server --listen 127.0.0.1 --port "$PORT" >"$LOG" 2>&1 &
SERVER_PID=$!

# Avoid external helpers so this runs on minimal developer systems.
i=0
while [ "$i" -lt 50 ]; do
    if "$BIN" throughput --port "$PORT" --warmup 100ms --duration 300ms 127.0.0.1 >/dev/null 2>&1; then
        echo "smoke: ok"
        exit 0
    fi
    i=$((i + 1))
    sleep 0.05
done

echo "smoke: failed" >&2
cat "$LOG" >&2 || true
exit 1
