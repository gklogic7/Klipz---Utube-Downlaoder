#!/usr/bin/env bash
set -euo pipefail

# Cross-compiles LGPL v2.1+ FFmpeg shared libraries for Klipz Android ABIs:
# arm64-v8a, armeabi-v7a, and x86_64.
# Usage: ANDROID_NDK_HOME=/path/to/ndk ./scripts/build_ffmpeg_android.sh [ffmpeg_version]

FFMPEG_VERSION="${1:-7.1}"
API_LEVEL="${API_LEVEL:-24}"
NDK="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your Android NDK path}"
HOST_TAG="linux-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
OUT_BASE="$(pwd)/app/src/main/cpp/prebuilt"
WORK_DIR="$(mktemp -d)"

echo "Downloading FFmpeg ${FFMPEG_VERSION}..."
curl -fsSL "https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz" -o "$WORK_DIR/ffmpeg.tar.xz"
tar -xf "$WORK_DIR/ffmpeg.tar.xz" -C "$WORK_DIR"
SRC_DIR="$WORK_DIR/ffmpeg-${FFMPEG_VERSION}"

build_abi() {
  local ABI="$1"
  local ARCH="$2"
  local TARGET="$3"
  local EXTRA_FLAGS="$4"
  local PREFIX="$OUT_BASE/$ABI"

  echo "=== Building LGPL FFmpeg for $ABI ($TARGET) ==="
  cd "$SRC_DIR"
  make distclean >/dev/null 2>&1 || true

  ./configure \
    --prefix="$PREFIX" \
    --target-os=android \
    --arch="$ARCH" \
    --enable-cross-compile \
    --cc="$TOOLCHAIN/bin/${TARGET}${API_LEVEL}-clang" \
    --cxx="$TOOLCHAIN/bin/${TARGET}${API_LEVEL}-clang++" \
    --ar="$TOOLCHAIN/bin/llvm-ar" \
    --nm="$TOOLCHAIN/bin/llvm-nm" \
    --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
    --strip="$TOOLCHAIN/bin/llvm-strip" \
    --enable-shared \
    --disable-static \
    --disable-programs \
    --disable-doc \
    --disable-gpl \
    --disable-nonfree \
    --enable-small \
    $EXTRA_FLAGS

  make -j"$(nproc)"
  make install
}

build_abi "arm64-v8a" "aarch64" "aarch64-linux-android" ""
build_abi "armeabi-v7a" "arm" "armv7a-linux-androideabi" "--disable-neon"
build_abi "x86_64" "x86_64" "x86_64-linux-android" "--disable-asm"

echo "Built LGPL FFmpeg libraries into $OUT_BASE"
