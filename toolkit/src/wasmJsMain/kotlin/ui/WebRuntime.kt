@file:OptIn(ExperimentalWasmJsInterop::class)

package ui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import hal.ec_clear
import hal.ec_density
import hal.ec_display_init
import hal.ec_flip
import hal.ec_height
import hal.ec_input_open
import hal.ec_now_nanos
import hal.ec_rss_kb
import hal.ec_width
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// runApp() for a browser page. On the device (Runtime.kt) it is a loop that
// blocks in ec_input_poll() until input, a timer or the next frame; a page
// can't block, so here one pass of that loop is a step(), and the page calls
// the next one after the same timeout, or sooner when input arrives.

private const val FRAME_NS = 16_666_667L

private fun inputCount(): Int = js("ecHal.poll()")
private fun inputField(i: Int): Int = js("ecHal.ev[i]")
private fun schedule(ms: Int, step: () -> Unit): Unit = js("ecHal.schedule(ms, step)")
private fun crashed(msg: String): Unit = js("ecHal.crash(msg)")

/**
 * Runs [content] full-screen on the page's display. Returns at once; the
 * app keeps running in steps scheduled by the page.
 */
fun runApp(background: Color = Color.Black, content: @Composable () -> Unit): Int {
    if (ec_display_init() != 0) {
        log("display init failed")
        return 1
    }
    val width = ec_width()
    val height = ec_height()
    Device.width = width
    Device.height = height
    Density.scale = ec_density()
    log("screen ${width}x$height, density ${Density.scale}, input devices: ${ec_input_open()}")

    var dead = false
    fun crash(e: Throwable) {
        dead = true
        log("uncaught: $e\n${e.stackTraceToString()}")
        crashed(e.toString())
    }

    val loop = MainLoop()
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(loop + clock + Job() + CoroutineExceptionHandler { _, e -> crash(e) })
    val recomposer = Recomposer(scope.coroutineContext)
    scope.launch { recomposer.runRecomposeAndApplyChanges() }

    var dirty = true
    val root = LayoutNode().apply { isRoot = true }
    val composition = Composition(NodeApplier(root) { dirty = true }, recomposer)
    Snapshot.registerApplyObserver { _, _ -> dirty = true }
    composition.setContent(content)

    val input = InputDispatcher(root) { dirty = true }
    var nextFrame = 0L
    var frames = 0
    var totalFrames = 0L
    var renderNs = 0L
    var statsAt = ec_now_nanos()

    /** One pass of the device's loop; returns how long to wait for the next (ms). */
    fun pass(): Int {
        val n = inputCount()
        for (i in 0 until n) {
            val f = i * 5
            input.handle(inputField(f), inputField(f + 1), inputField(f + 2), inputField(f + 3), inputField(f + 4))
        }

        loop.runReady()
        Snapshot.sendApplyNotifications()
        loop.runReady()

        var now = ec_now_nanos()
        if (clock.hasAwaiters && now >= nextFrame) {
            nextFrame = now + FRAME_NS
            clock.sendFrame(now)
            loop.runReady()
            Snapshot.sendApplyNotifications()
            loop.runReady()
        }

        if (dirty) {
            dirty = false
            val t0 = ec_now_nanos()
            root.measure(Constraints.fixed(width, height))
            root.place(0, 0)
            Raster.setColor(background)
            ec_clear()
            root.draw(input.focused)
            ec_flip()
            frames++
            totalFrames++
            renderNs += ec_now_nanos() - t0
            if (Trace.enabled) log("frame $totalFrames")
        }

        now = ec_now_nanos()
        if (frames > 0 && now - statsAt > 5_000_000_000L) {
            log("stats: $frames frames in ${(now - statsAt) / 1_000_000} ms, render avg ${renderNs / frames / 1000} us, rss ${ec_rss_kb()} kB")
            frames = 0
            renderNs = 0
            statsAt = now
        }

        val waits = listOfNotNull(
            if (loop.hasQueued || dirty) 0L else null,
            if (clock.hasAwaiters) ((nextFrame - now) / 1_000_000).coerceAtLeast(0) else null,
            loop.msUntilNextTimer(),
        )
        return waits.minOrNull()?.coerceAtMost(5000)?.toInt() ?: 5000
    }

    fun step() {
        if (dead) return
        val timeout = try {
            pass()
        } catch (e: Throwable) {
            crash(e)
            return
        }
        schedule(timeout, ::step)
    }
    step()
    return 0
}
