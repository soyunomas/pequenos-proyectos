#!/bin/sh
set -eu

BIN=${BIN:-./bin/netx}
PORT=${PORT:-25212}
NS1=netx-srv-$$
NS2=netx-cli-$$
V1=veth-s-$$
V2=veth-c-$$
LOG=${TMPDIR:-/tmp}/netx-netem-$$.log

cleanup() {
    if [ -n "${SERVER_PID:-}" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    ip netns del "$NS1" 2>/dev/null || true
    ip netns del "$NS2" 2>/dev/null || true
    rm -f "$LOG"
}
trap cleanup EXIT INT TERM

if ! command -v ip >/dev/null 2>&1 || ! command -v tc >/dev/null 2>&1; then
    echo 'netem: SKIP (iproute2/ip or tc not installed)'
    exit 0
fi

# Network namespaces require CAP_SYS_ADMIN/CAP_NET_ADMIN. Probe without leaving state.
if ! ip netns add "$NS1" 2>/dev/null; then
    echo 'netem: SKIP (host lacks permission for Linux network namespaces/CAP_NET_ADMIN)'
    exit 0
fi
ip netns del "$NS1"

ip netns add "$NS1"
ip netns add "$NS2"
ip link add "$V1" type veth peer name "$V2"
ip link set "$V1" netns "$NS1"
ip link set "$V2" netns "$NS2"
ip -n "$NS1" addr add 10.203.0.1/24 dev "$V1"
ip -n "$NS2" addr add 10.203.0.2/24 dev "$V2"
ip -n "$NS1" link set lo up
ip -n "$NS2" link set lo up
ip -n "$NS1" link set "$V1" up
ip -n "$NS2" link set "$V2" up
ip netns exec "$NS2" tc qdisc add dev "$V2" root netem delay 20ms 3ms loss 1% reorder 1% 50%

BIN_ABS=$(cd "$(dirname "$BIN")" && pwd)/$(basename "$BIN")
ip netns exec "$NS1" "$BIN_ABS" server --listen 10.203.0.1 --port "$PORT" >"$LOG" 2>&1 &
SERVER_PID=$!
sleep 0.2

ip netns exec "$NS2" "$BIN_ABS" latency --port "$PORT" --duration 500ms --interval 100ms 10.203.0.1 >/dev/null
ip netns exec "$NS2" "$BIN_ABS" throughput --port "$PORT" --duration 500ms --warmup 100ms --sample 100ms --probe-interval 100ms 10.203.0.1 >/dev/null
ip netns exec "$NS2" "$BIN_ABS" udp --port "$PORT" --duration 600ms --warmup 100ms --rate 5M --sample 100ms --probe-interval 100ms 10.203.0.1 >/dev/null

printf '%s\n' 'netem: namespace test ok (20ms±3ms, 1% loss, 1% reorder configured)'
