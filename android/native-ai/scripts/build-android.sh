#!/usr/bin/env bash
# Compila libyumeka_sd.so e libyumeka_vlm.so para Android arm64-v8a com o NDK
# e copia para android/app/src/main/jniLibs (empacotadas automaticamente no APK).
#
# Requer: ANDROID_NDK (ou ANDROID_NDK_HOME / ANDROID_NDK_ROOT), cmake >= 3.22 (ninja opcional).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
source "$HERE/versions.env"
NDK="${ANDROID_NDK:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
  echo "ERRO: NDK nao encontrado. Defina ANDROID_NDK." >&2; exit 1
fi
JOBS="${JOBS:-$(nproc)}"
OUT="$HERE/../app/src/main/jniLibs/$ANDROID_ABI"
mkdir -p "$OUT"

build() { # pasta alvo
  local b="$HERE/build/$1-$ANDROID_ABI"
  local gen="Unix Makefiles"; command -v ninja >/dev/null && gen=Ninja
  cmake -S "$HERE/$1" -B "$b" -G "$gen" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="android-$ANDROID_NATIVE_API" \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="-march=armv8.2-a+dotprod+fp16" \
    -DCMAKE_CXX_FLAGS="-march=armv8.2-a+dotprod+fp16"
  cmake --build "$b" --target "$2" -j "$JOBS"
  cp "$b/lib$2.so" "$OUT/"
  "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip --strip-unneeded "$OUT/lib$2.so"
}

build sd yumeka_sd
build vlm yumeka_vlm

echo "=== Bibliotecas geradas ==="
ls -la "$OUT"
for so in "$OUT"/*.so; do
  echo "--- $so"
  command -v file >/dev/null && file "$so" || true
  echo "Simbolos JNI exportados:"
  "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-nm -D --defined-only "$so" | grep ' T Java_' || { echo "ERRO: nenhum simbolo JNI em $so" >&2; exit 1; }
done
