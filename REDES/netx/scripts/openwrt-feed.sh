#!/bin/sh
set -eu

ARCH=${OPENWRT_ARCH:-}
RELEASE_DIR=${RELEASE_DIR:-dist/release}
OUT=${OPENWRT_FEED_DIR:-dist/openwrt-feed/netx}

if [ -z "$ARCH" ]; then
  echo 'OPENWRT_ARCH is required: amd64|386|armv5|armv6|armv7|arm64|mips|mipsle|mips64|riscv64' >&2
  exit 2
fi

case "$ARCH" in
  amd64|386|armv5|armv6|armv7|arm64|riscv64) bin="netx-linux-$ARCH" ;;
  mips) bin="netx-linux-mips-softfloat" ;;
  mipsle) bin="netx-linux-mipsle-softfloat" ;;
  mips64) bin="netx-linux-mips64-softfloat" ;;
  *) echo "unsupported OPENWRT_ARCH=$ARCH" >&2; exit 2 ;;
esac

[ -x "$RELEASE_DIR/$bin" ] || {
  echo "$RELEASE_DIR/$bin missing; run 'make release' first" >&2
  exit 1
}

rm -rf "$OUT"
mkdir -p "$OUT/files"
cp packaging/openwrt/Makefile "$OUT/Makefile"
cp packaging/openwrt/files/netx.init "$OUT/files/netx.init"
cp packaging/openwrt/files/netx.config "$OUT/files/netx.config"
cp "$RELEASE_DIR/$bin" "$OUT/files/netx"
chmod 0755 "$OUT/files/netx" "$OUT/files/netx.init"

printf 'OpenWrt feed package staged at %s for %s\n' "$OUT" "$ARCH"
printf '%s\n' 'Copy that directory to <openwrt-sdk>/package/netx and run: make package/netx/compile V=s'
