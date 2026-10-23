// The wall-clock part of platform.posix, from the browser's clock and time
// zone: time(), clock_gettime(CLOCK_REALTIME) and localtime_r().
@file:Suppress("FunctionName", "ClassName", "PropertyName")
@file:OptIn(ExperimentalWasmJsInterop::class)

package platform.posix

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer

const val CLOCK_REALTIME = 0
const val CLOCK_MONOTONIC = 1

class time_tVar : CPointed() {
    var value = 0L
}

class timespec : CPointed() {
    var tv_sec = 0L
    var tv_nsec = 0L
}

class tm : CPointed() {
    var tm_sec = 0
    var tm_min = 0
    var tm_hour = 0
    var tm_mday = 0
    var tm_mon = 0
    var tm_year = 0
    var tm_wday = 0
    var tm_yday = 0
    var tm_isdst = 0
}

private fun epochMillis(): Double = js("Date.now()")
private fun monotonicMillis(): Double = js("performance.now()")
private fun local(sec: Double, field: Int): Int = js("""
    ((d) => [d.getSeconds(), d.getMinutes(), d.getHours(), d.getDate(), d.getMonth(), d.getFullYear() - 1900, d.getDay(),
             Math.floor((d - new Date(d.getFullYear(), 0, 1)) / 86400000)][field])(new Date(sec * 1000))
""")

fun time(t: CPointer<time_tVar>?): Long {
    val s = (epochMillis() / 1000).toLong()
    t?.pointed?.value = s
    return s
}

fun clock_gettime(clock: Int, ts: CPointer<timespec>?): Int {
    val ms = if (clock == CLOCK_MONOTONIC) monotonicMillis() else epochMillis()
    ts?.pointed?.apply {
        tv_sec = (ms / 1000).toLong()
        tv_nsec = ((ms - tv_sec * 1000.0) * 1_000_000).toLong()
    }
    return 0
}

fun localtime_r(t: CPointer<time_tVar>?, out: CPointer<tm>?): CPointer<tm>? {
    if (t == null || out == null) return null
    val s = t.pointed.value.toDouble()
    out.pointed.apply {
        tm_sec = local(s, 0); tm_min = local(s, 1); tm_hour = local(s, 2); tm_mday = local(s, 3)
        tm_mon = local(s, 4); tm_year = local(s, 5); tm_wday = local(s, 6); tm_yday = local(s, 7)
    }
    return out
}
