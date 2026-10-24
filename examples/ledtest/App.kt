// LED test: every LED, module numbers, an arrow, sweeps and scrolling text.
// Made to check MAX7219 modules (Panel: MAX7219, 32 x 8); works on any panel.
// Five steps, over and over, each logged to the console; Right or Enter skips to the next one.
//   1 every LED on                  a dark LED is a dead LED or a bad module
//   2 module numbers 1, 2, 3, ...   chain order and orientation: they should
//                                   read left to right, upright, with a dot in
//                                   each module's top-left corner
//   3 an arrow                      it should point right (with a frame round
//                                   the edge on panels 10 or more LEDs high)
//   4 a column, then a row, sweeping left to right, top to bottom
//   5 scrolling text                the whole message, once across
// The arrow and the text are ui.led's LedIcon and Marquee.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import ui.Alignment
import ui.Box
import ui.Canvas
import ui.Color
import ui.Device
import ui.DrawScope
import ui.Modifier
import ui.Offset
import ui.Size
import ui.fillMaxSize
import ui.fillMaxWidth
import ui.led.LedIcon
import ui.led.LedIcons
import ui.led.Marquee
import ui.log
import ui.rememberFrameMillis

private enum class Step(val title: String, val millis: Long) {
    AllOn("every LED on", 3000),
    Numbers("module numbers, left to right, dot top-left", 6000),
    Arrow("an arrow pointing right", 4000),
    Sweep("a column left to right, then a row top to bottom", 4500),
    Text("scrolling text", 0),  // until the message has passed (Marquee's onDone)
}

private const val KEY_ENTER = 28
private const val KEY_RIGHT = 106
private const val MESSAGE = "HELLO FROM EMBEDDED-COMPOSE ON A MAX7219"

@Composable
fun App() = TestApp()

@Composable
private fun TestApp() {
    var index by remember { mutableIntStateOf(0) }
    val step = Step.entries[index]
    val next = { index = (index + 1) % Step.entries.size }
    DisposableEffect(Unit) {
        Device.onKey = { code, value ->
            if (value == 1 && (code == KEY_RIGHT || code == KEY_ENTER)) next()
        }
        onDispose { Device.onKey = null }
    }
    LaunchedEffect(index) {
        log("test ${index + 1}/${Step.entries.size}: ${step.title} (${Device.width}x${Device.height})")
        if (step.millis > 0) {
            delay(step.millis)
            next()
        }
    }
    key(index) { StepView(step, next) }
}

private fun DrawScope.rect(x: Int, y: Int, w: Int, h: Int) =
    drawRect(Color.White, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))

@Composable
private fun StepView(step: Step, next: () -> Unit) {
    when (step) {
        Step.Arrow -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width.toInt()
                val h = size.height.toInt()
                if (h >= 10) { rect(0, 0, w, 1); rect(0, h - 1, w, 1); rect(0, 0, 1, h); rect(w - 1, 0, 1, h) }
            }
            LedIcon(LedIcons.ArrowRight)
        }
        Step.Text -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Marquee(MESSAGE, Modifier.fillMaxWidth(), onDone = next)
        }
        else -> {
            val millis by rememberFrameMillis(step == Step.Sweep)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width.toInt()
                val h = size.height.toInt()
                when (step) {
                    Step.AllOn -> rect(0, 0, w, h)
                    Step.Numbers -> for (m in 0 until w / 8) {
                        rect(m * 8, 0, 1, 1)
                        drawText("${(m + 1) % 10}", Offset(m * 8 + 2f, (h - 7) / 2f), Color.White)
                    }
                    else -> {
                        val column = (millis / 60).toInt()
                        if (column < w) rect(column, 0, 1, h)
                        else rect(0, minOf(((millis - w * 60) / 150).toInt(), h - 1), w, 1)
                    }
                }
            }
        }
    }
}
