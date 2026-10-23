// ecHal: core/hal/ec_hal.h for the Kotlin toolkit in the page
// (toolkit/src/wasmJsMain/kotlin/hal/Hal.kt calls it). The display half is
// embedded-compose's backends/matrix/matrix.cpp compiled to led.wasm
// (led/build.sh); its frames go to show(). Input is queued here and taken by
// the toolkit's loop (ui/WebRuntime.kt), which schedule() runs.

const EV_KEY = 1, EV_POINTER_DOWN = 2, EV_POINTER_MOVE = 3, EV_POINTER_UP = 4;

// KeyboardEvent.code -> linux KEY_*
const KEYS = {
  Escape: 1, Minus: 12, Equal: 13, Backspace: 14, Tab: 15, BracketLeft: 26, BracketRight: 27,
  Enter: 28, ControlLeft: 29, Semicolon: 39, Quote: 40, Backquote: 41, ShiftLeft: 42, Backslash: 43,
  Comma: 51, Period: 52, Slash: 53, ShiftRight: 54, AltLeft: 56, Space: 57,
  Home: 102, ArrowUp: 103, PageUp: 104, ArrowLeft: 105, ArrowRight: 106, End: 107, ArrowDown: 108,
  PageDown: 109, Insert: 110, Delete: 111, NumpadEnter: 96, ControlRight: 97, AltRight: 100,
};
"1234567890".split("").forEach((d, i) => (KEYS["Digit" + d] = 2 + i));
"QWERTYUIOP".split("").forEach((c, i) => (KEYS["Key" + c] = 16 + i));
"ASDFGHJKL".split("").forEach((c, i) => (KEYS["Key" + c] = 30 + i));
"ZXCVBNM".split("").forEach((c, i) => (KEYS["Key" + c] = 44 + i));
for (let i = 1; i <= 10; i++) KEYS["F" + i] = 58 + i;
export const linuxKey = (code) => KEYS[code];

// Just enough WASI for wasi-libc's start-up in a module with no files.
function wasiStubs(module, mem, log) {
  const ENOSYS = 52, EBADF = 8;
  const known = {
    environ_sizes_get: (count, size) => { const v = new DataView(mem().buffer); v.setUint32(count, 0, true); v.setUint32(size, 0, true); return 0; },
    environ_get: () => 0,
    fd_prestat_get: () => EBADF,
    fd_write: (fd, iovs, n, written) => {
      const v = new DataView(mem().buffer);
      let text = "", total = 0;
      for (let i = 0; i < n; i++) {
        const p = v.getUint32(iovs + i * 8, true), len = v.getUint32(iovs + i * 8 + 4, true);
        text += new TextDecoder().decode(new Uint8Array(mem().buffer, p, len));
        total += len;
      }
      v.setUint32(written, total, true);
      log(`led.wasm fd ${fd}: ${text}`);
      return 0;
    },
    proc_exit: (code) => { throw new Error(`led.wasm exited (${code})`); },
  };
  const wasi = {};
  for (const imp of WebAssembly.Module.imports(module)) {
    if (imp.module === "wasi_snapshot_preview1") wasi[imp.name] = known[imp.name] || (() => ENOSYS);
  }
  return wasi;
}

/**
 * width x height: the panel, in LEDs. show(rgb): a frame (Uint8Array, RGB
 * rows). log(text), crash(text), powerOff(): from the app.
 */
export async function createHal({ width, height, show, log, crash, powerOff }) {
  let memory;
  const mem = () => memory;
  const u8 = () => new Uint8Array(memory.buffer);
  const cstr = (p) => { const b = u8(); let e = p; while (b[e]) e++; return new TextDecoder().decode(b.subarray(p, e)); };

  const module = await WebAssembly.compileStreaming(fetch("build/led.wasm"));
  const instance = await WebAssembly.instantiate(module, {
    sim: {
      panel_size: (wp, hp) => { const v = new DataView(memory.buffer); v.setInt32(wp, width, true); v.setInt32(hp, height, true); return 1; },
      show: (p) => show(u8().slice(p, p + width * height * 3)),
      image_size: () => 0,  // no images in the simulator yet
      image_read: () => {},
    },
    wasi_snapshot_preview1: wasiStubs(module, mem, log),
  });
  const c = instance.exports;
  memory = c.memory;
  c._initialize();
  const strPtr = c.sim_str(), strSize = c.sim_str_size();
  const putStr = (s) => {
    const bytes = new TextEncoder().encode(s).subarray(0, strSize - 1);
    const b = u8();
    b.set(bytes, strPtr);
    b[strPtr + bytes.length] = 0;
    return strPtr;
  };

  const queue = [];
  let timer = null, step = null, dead = false;
  const wake = () => {
    if (!step || dead) return;
    clearTimeout(timer);
    timer = setTimeout(run, 0);
  };
  const run = () => { timer = null; const s = step; step = null; s(); };

  const hal = {
    // display: matrix.cpp
    display_init: () => c.ec_display_init(),
    display_exit: () => {},
    width: () => c.ec_width(),
    height: () => c.ec_height(),
    density: () => c.ec_density(),
    color: (r, g, b, a) => c.ec_color(r, g, b, a),
    clear: () => c.ec_clear(),
    fill: (x1, y1, x2, y2) => c.ec_fill(x1, y1, x2, y2),
    flip: () => c.ec_flip(),
    blank: (b) => c.ec_blank(b),
    font_width: () => c.ec_font_width(),
    font_height: () => c.ec_font_height(),
    text: (x, y, s, scale, bold) => c.ec_text(x, y, putStr(s), scale, bold),
    image_load: (name) => c.ec_image_load(putStr(name)),
    image_width: (h) => c.ec_image_width(h),
    image_height: (h) => c.ec_image_height(h),
    image_draw: (h, x, y) => c.ec_image_draw(h, x, y),
    image_free: (h) => c.ec_image_free(h),

    // input: five ints per event (type, code, value, x, y), taken by poll()
    input_open: () => 1,
    ev: new Int32Array(0),
    poll() {
      const n = queue.length;
      if (hal.ev.length < n * 5) hal.ev = new Int32Array(n * 5 * 2);
      queue.forEach((e, i) => hal.ev.set(e, i * 5));
      queue.length = 0;
      return n;
    },
    schedule(ms, fn) {
      if (dead) return;
      clearTimeout(timer);
      step = fn;
      timer = queue.length ? setTimeout(run, 0) : setTimeout(run, ms);
    },

    // system
    log: (msg) => log(msg),
    crash: (msg) => { dead = true; clearTimeout(timer); crash(msg); },
    power_off: () => { dead = true; clearTimeout(timer); powerOff(); },

    // from the page
    key(code, value) { queue.push([EV_KEY, code, value, 0, 0]); wake(); },
    pointer(kind, x, y) {
      const type = { down: EV_POINTER_DOWN, move: EV_POINTER_MOVE, up: EV_POINTER_UP }[kind];
      queue.push([type, 0, 0, x, y]);
      wake();
    },
  };
  return hal;
}
