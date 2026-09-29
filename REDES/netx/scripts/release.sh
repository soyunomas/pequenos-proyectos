#!/bin/sh
set -eu

GO=${GO:-go}
VERSION=${VERSION:-0.5.0}
COMMIT=${COMMIT:-$(git rev-parse --short=12 HEAD 2>/dev/null || printf unknown)}
OUT=${OUT:-dist/release}

case "$VERSION" in
  *[!0-9A-Za-z._+-]*|'') echo "invalid VERSION: $VERSION" >&2; exit 2 ;;
esac

rm -rf "$OUT"
mkdir -p "$OUT"

MODULE=./cmd/netx
PKG=github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/buildinfo
LDFLAGS="-s -w -buildid= -X $PKG.Version=$VERSION -X $PKG.Commit=$COMMIT"

build() {
  name=$1
  goarch=$2
  shift 2
  env CGO_ENABLED=0 GOOS=linux GOARCH="$goarch" "$@" "$GO" build     -trimpath -buildvcs=false -ldflags "$LDFLAGS"     -o "$OUT/netx-linux-$name" "$MODULE"
}

build amd64 amd64
build 386 386
build armv5 arm GOARM=5
build armv6 arm GOARM=6
build armv7 arm GOARM=7
build arm64 arm64
build mips-softfloat mips GOMIPS=softfloat
build mipsle-softfloat mipsle GOMIPS=softfloat
build mips64-softfloat mips64 GOMIPS64=softfloat
build riscv64 riscv64

(
  cd "$OUT"
  LC_ALL=C sha256sum netx-linux-* > SHA256SUMS
  printf 'version=%s\ncommit=%s\ngo=%s\n' "$VERSION" "$COMMIT" "$("$GO" version)" > BUILDINFO
)

printf 'release artifacts: %s\n' "$OUT"
cat "$OUT/SHA256SUMS"
