// LED test: every LED, module numbers, an arrow, sweeps and scrolling text.
// Made to check MAX7219 modules (Panel: MAX7219, 32 x 8); works on any panel.
// Five steps, over and over, each logged to the console; Right or Enter skips to the next one.
//   1 every LED on                  a dark LED is a dead LED or a bad module
//   2 module numbers 1, 2, 3, ...   chain order and orientation: they should
//                                   read left to right, upright, with a dot in
//                                   each module's top-left corner
//   3 a frame and an arrow          the arrow should point right
//   4 a column, then a row, sweeping left to right, top to bottom
//   5 scrolling text           the whole message, once across
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import ui.Canvas
import ui.Color
import ui.Device
import ui.DrawScope
import ui.Modifier
import ui.Offset
import ui.Size
import ui.fillMaxSize
import ui.log
import ui.rememberFrameMillis

private enum class Step(val title: String, val millis: Long) {
    AllOn("every LED on", 3000),
    Numbers("module numbers, left to right, dot top-left", 6000),
    Arrow("a frame and an arrow pointing right", 4000),
    Sweep("a column left to right, then a row top to bottom", 4500),
    Text("scrolling text", 0),  // as long as the message takes to pass: textMillis()
}

private const val KEY_ENTER = 28
private const val KEY_RIGHT = 106
private const val MESSAGE = "HELLO FROM EMBEDDED-COMPOSE ON A MAX7219"
private const val CELL = 6    // a character and its gap, in LEDs
private const val SPEED = 20  // LEDs a second

// The text comes in at the right edge and leaves at the left one.
private fun textMillis(width: Int) = (width + MESSAGE.length * CELL) * 1000L / SPEED

@Composable
fun App() = TestApp()

@Composable
private fun TestApp() {
    var index by remember { mutableIntStateOf(0) }
    val step = Step.entries[index]
    DisposableEffect(Unit) {
        Device.onKey = { code, value ->
            if (value == 1 && (code == KEY_RIGHT || code == KEY_ENTER)) index = (index + 1) % Step.entries.size
        }
        onDispose { Device.onKey = null }
    }
    LaunchedEffect(index) {
        log("test ${index + 1}/${Step.entries.size}: ${step.title} (${Device.width}x${Device.height})")
        delay(if (step == Step.Text) textMillis(Device.width) else step.millis)
        index = (index + 1) % Step.entries.size
    }
    key(index) { StepView(step) }
}

private fun DrawScope.rect(x: Int, y: Int, w: Int, h: Int) =
    drawRect(Color.White, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))

@Composable
private fun StepView(step: Step) {
    val millis by rememberFrameMillis(step == Step.Sweep || step == Step.Text)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        when (step) {
            Step.AllOn -> rect(0, 0, w, h)
            Step.Numbers -> for (m in 0 until w / 8) {
                rect(m * 8, 0, 1, 1)
                drawText("${(m + 1) % 10}", Offset(m * 8 + 2f, (h - 7) / 2f), Color.White)
            }
            Step.Arrow -> {
                rect(0, 0, w, 1); rect(0, h - 1, w, 1); rect(0, 0, 1, h); rect(w - 1, 0, 1, h)
                val c = w / 2
                rect(c - 8, h / 2 - 1, 8, 2)
                for (i in 0 until 3) rect(c + i, h / 2 - 3 + i, 1, 6 - 2 * i)
            }
            Step.Sweep -> {
                val column = (millis / 60).toInt()
                if (column < w) rect(column, 0, 1, h)
                else rect(0, minOf(((millis - w * 60) / 150).toInt(), h - 1), w, 1)
            }
            Step.Text -> drawText(MESSAGE, Offset(w - millis * SPEED / 1000f, (h - 7) / 2f), Color.White)
        }
    }
}
