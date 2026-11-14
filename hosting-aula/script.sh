#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ ${1:-} != --help && ${1:-} != -h && $EUID != 0 ]]; then echo 'Ejecuta con sudo.' >&2; exit 1; fi
if ! command -v python3 >/dev/null; then
    apt-get update
    apt-get install -y --no-install-recommends python3
fi
exec python3 ./hosting.py "$@"
