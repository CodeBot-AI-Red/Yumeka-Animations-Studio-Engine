#!/usr/bin/env bash
# Chamado pelo Gradle (tarefa buildNativeAi, antes de preBuild).
# Baixa o codigo oficial e compila as .so somente se fontes/versoes mudaram.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HERE/../app/src/main/jniLibs/arm64-v8a"
STAMP="$HERE/build/.stamp"
HASH="$(cat "$HERE/versions.env" "$HERE"/common/* "$HERE"/sd/* "$HERE"/vlm/* "$HERE"/scripts/build-android.sh | sha256sum | cut -d' ' -f1)"
if [ -f "$OUT/libyumeka_sd.so" ] && [ -f "$OUT/libyumeka_vlm.so" ] && [ "$(cat "$STAMP" 2>/dev/null)" = "$HASH" ]; then
  echo "[native-ai] bibliotecas atualizadas, nada a compilar"; exit 0
fi
if [ -z "${ANDROID_NDK:-}" ]; then
  for c in "${ANDROID_NDK_LATEST_HOME:-}" "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" $(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/ndk/* 2>/dev/null | sort -V -r); do
    if [ -n "$c" ] && [ -f "$c/build/cmake/android.toolchain.cmake" ]; then export ANDROID_NDK="$c"; break; fi
  done
fi
"$HERE/scripts/fetch-sources.sh"
"$HERE/scripts/build-android.sh"
mkdir -p "$(dirname "$STAMP")"; echo "$HASH" > "$STAMP"
