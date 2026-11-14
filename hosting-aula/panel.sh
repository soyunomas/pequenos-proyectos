#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "$0")"
if [[ $EUID != 0 ]]; then
    echo "Ejecuta sudo bash panel.sh" >&2
    exit 1
fi
if ! command -v python3 >/dev/null; then
    apt-get update
    apt-get install -y --no-install-recommends python3
fi
exec python3 panel.py "$@"
