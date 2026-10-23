#!/bin/bash
# Compiles embedded-compose's LED-matrix backend (backends/matrix/matrix.cpp,
# unchanged) with a browser sink into out/web/led.wasm, using wasi-sdk
# (downloaded once into out/deps).
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
ec=$(cd "${EC_DIR:-$here/../embedded-compose}" && pwd)

WASI_SDK_VERSION=25.0
WASI_SDK_SHA256=52640dde13599bf127a95499e61d6d640256119456d1af8897ab6725bcf3d89c
sdk=$here/out/deps/wasi-sdk-$WASI_SDK_VERSION-x86_64-linux
if [ ! -x "$sdk/bin/clang++" ]; then
  mkdir -p "$here/out/deps"
  tgz=$sdk.tar.gz
  echo "fetching wasi-sdk $WASI_SDK_VERSION"
  curl -fL -o "$tgz" "https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${WASI_SDK_VERSION%%.*}/$(basename "$tgz")"
  echo "$WASI_SDK_SHA256  $tgz" | sha256sum -c --quiet
  tar xzf "$tgz" -C "$here/out/deps"
fi

out=$here/out/web
mkdir -p "$out"
exports=(ec_display_init ec_width ec_height ec_density ec_color ec_clear ec_fill ec_flip ec_blank
         ec_font_width ec_font_height ec_text
         ec_image_load ec_image_width ec_image_height ec_image_draw ec_image_free)
"$sdk/bin/clang++" --target=wasm32-wasip1 --sysroot="$sdk/share/wasi-sysroot" \
  -O2 -std=c++17 -fno-exceptions -fno-rtti -mexec-model=reactor \
  -I"$here/led/include" -I"$ec/core/hal" -I"$ec/backends/matrix" \
  "$ec/backends/matrix/matrix.cpp" "$here/led/web_sink.cpp" "$here/led/png_web.cpp" \
  $(printf -- '-Wl,--export=%s ' "${exports[@]}") -Wl,--strip-all \
  -o "$out/led.wasm.tmp"
mv "$out/led.wasm.tmp" "$out/led.wasm"
echo "$out/led.wasm ($(stat -c %s "$out/led.wasm") bytes)"
