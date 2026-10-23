// core/hal/ec_hal.h for the browser, under the names cinterop gives it on
// the device, so the toolkit's sources compile unchanged. Each function is
// the page's ecHal (site/hal.js); its display half is backends/matrix/
// matrix.cpp compiled to wasm (led/build.sh).
@file:OptIn(ExperimentalWasmJsInterop::class)

package hal

const val EC_EV_KEY = 1
const val EC_EV_POINTER_DOWN = 2
const val EC_EV_POINTER_MOVE = 3
const val EC_EV_POINTER_UP = 4

// ---- display
fun ec_display_init(): Int = js("ecHal.display_init()")
fun ec_display_exit(): Unit = js("ecHal.display_exit()")
fun ec_width(): Int = js("ecHal.width()")
fun ec_height(): Int = js("ecHal.height()")
fun ec_density(): Float = js("ecHal.density()")
fun ec_color(r: Int, g: Int, b: Int, a: Int): Unit = js("ecHal.color(r, g, b, a)")
fun ec_clear(): Unit = js("ecHal.clear()")
fun ec_fill(x1: Int, y1: Int, x2: Int, y2: Int): Unit = js("ecHal.fill(x1, y1, x2, y2)")
fun ec_flip(): Unit = js("ecHal.flip()")
fun ec_blank(blank: Int): Unit = js("ecHal.blank(blank)")

fun ec_font_width(): Int = js("ecHal.font_width()")
fun ec_font_height(): Int = js("ecHal.font_height()")
fun ec_text(x: Int, y: Int, s: String, scale: Int, bold: Int): Unit = js("ecHal.text(x, y, s, scale, bold)")

// Handles are C pointers in the wasm module (32-bit there), Long here as on the device.
fun ec_image_load(name: String): Long = imageLoad(name).toLong()
fun ec_image_width(handle: Long): Int = imageWidth(handle.toInt())
fun ec_image_height(handle: Long): Int = imageHeight(handle.toInt())
fun ec_image_draw(handle: Long, x: Int, y: Int) = imageDraw(handle.toInt(), x, y)
fun ec_image_free(handle: Long) = imageFree(handle.toInt())
private fun imageLoad(name: String): Int = js("ecHal.image_load(name)")
private fun imageWidth(h: Int): Int = js("ecHal.image_width(h)")
private fun imageHeight(h: Int): Int = js("ecHal.image_height(h)")
private fun imageDraw(h: Int, x: Int, y: Int): Unit = js("ecHal.image_draw(h, x, y)")
private fun imageFree(h: Int): Unit = js("ecHal.image_free(h)")

// ---- input: queued by the page; ui/WebRuntime.kt takes them between steps
fun ec_input_open(): Int = js("ecHal.input_open()")

// ---- system
fun ec_now_nanos(): Long = (nowMillis() * 1_000_000.0).toLong()
private fun nowMillis(): Double = js("performance.now()")
fun ec_log(msg: String): Unit = js("ecHal.log(msg)")
fun ec_rss_kb(): Long = -1
fun ec_power_off(): Unit = js("ecHal.power_off()")
