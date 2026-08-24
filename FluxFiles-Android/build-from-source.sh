#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DAV="$ROOT/third_party/dav4jvm"
APP="$ROOT/source"
(cd "$DAV" && GIT_COMMIT=02fe1a95e6 ./gradlew publish --stacktrace)
rm -rf "$APP/local-maven"
mkdir -p "$APP/local-maven"
cp -R "$DAV/build/repo/"* "$APP/local-maven/"
(cd "$APP" && ./gradlew assembleDebug lintVitalRelease --stacktrace)
echo "APK: $APP/app/build/outputs/apk/debug/app-debug.apk"
