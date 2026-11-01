#!/usr/bin/env bash
# 编 compose_arkui_utils 静态库（鸿蒙目标）。产物给 ohos/ohosApp 的 -include-binary 用。
# 用法：./build.sh [arm64-v8a|x86_64]
set -euo pipefail

ABI="${1:-arm64-v8a}"
SDK="${DEVECO_SDK_HOME:-/Applications/DevEco-Studio.app/Contents/sdk}"
NATIVE="$SDK/default/openharmony/native"
CMAKE="$NATIVE/build-tools/cmake/bin/cmake"
NINJA="$NATIVE/build-tools/cmake/bin/ninja"
DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$DIR/build/$ABI"
OUT="$DIR/lib/$ABI"

[ -x "$CMAKE" ] || { echo "找不到 cmake：$CMAKE（设 DEVECO_SDK_HOME 指向 sdk 目录）" >&2; exit 1; }

# libskikobridge.so 只在链接期需要（为了把 DT_NEEDED 写进 libnexioohos.so），
# 来源是 HAR，不进版本库 —— 缺了就从本地那份 HAR 里取，保证换机也能一键编出来。
HAR="$DIR/../../../harmonyApp/third_party/cpf-compose-1.9.2-1.2.0.har"
SKIKO_OUT="$DIR/skikobridge/lib/arm64-v8a/libskikobridge.so"
if [ ! -f "$SKIKO_OUT" ]; then
    [ -f "$HAR" ] || { echo "缺 HAR：$HAR" >&2; exit 1; }
    mkdir -p "$(dirname "$SKIKO_OUT")"
    TMP="$(mktemp -d)"
    tar -xzf "$HAR" -C "$TMP"
    cp "$TMP/package/libs/arm64-v8a/libskikobridge.so" "$SKIKO_OUT"
    rm -rf "$TMP"
    echo "已从 HAR 取出 libskikobridge.so"
fi

"$CMAKE" -S "$DIR" -B "$BUILD" -GNinja \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NATIVE/build/cmake/ohos.toolchain.cmake" \
    -DOHOS_ARCH="$ABI" \
    -DOHOS_PLATFORM=OHOS \
    -DCMAKE_BUILD_TYPE=Release
"$CMAKE" --build "$BUILD" --target compose_arkui_utils -j 8

mkdir -p "$OUT"
cp "$BUILD/libcompose_arkui_utils.a" "$OUT/"
echo "OK -> $OUT/libcompose_arkui_utils.a"
