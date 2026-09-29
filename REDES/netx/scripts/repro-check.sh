#!/bin/sh
set -eu

VERSION=${VERSION:-0.5.0}
COMMIT=${COMMIT:-reproducible}
BASE=${TMPDIR:-/tmp}/netx-repro-$$
A="$BASE/a"
B="$BASE/b"
trap 'rm -rf "$BASE"' EXIT INT TERM

OUT="$A" VERSION="$VERSION" COMMIT="$COMMIT" ./scripts/release.sh >/dev/null
OUT="$B" VERSION="$VERSION" COMMIT="$COMMIT" ./scripts/release.sh >/dev/null

(
  cd "$A"
  sha256sum netx-linux-* | sed 's#  .*netx-linux-#  netx-linux-#'
) > "$BASE/a.sha"
(
  cd "$B"
  sha256sum netx-linux-* | sed 's#  .*netx-linux-#  netx-linux-#'
) > "$BASE/b.sha"

if ! cmp -s "$BASE/a.sha" "$BASE/b.sha"; then
  echo 'reproducibility check failed' >&2
  diff -u "$BASE/a.sha" "$BASE/b.sha" >&2 || true
  exit 1
fi
printf '%s\n' 'reproducibility: PASS (two independent cross-builds are byte-identical)'
