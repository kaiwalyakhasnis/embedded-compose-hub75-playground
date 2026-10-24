// LED components: ui.led, the toolkit's parts for small LED panels, one per page.
// Marquee, FitOrScroll, LedIcon (LedIcons, ledBitmap), SmallText (a 3x5 font),
// BarGraph, ProgressBar and Blink; the pages slide (SlideTransition) and show
// PageDots. Made for 32 x 8 (Panel: MAX7219); bigger panels get scale 2.
// Right/Left: next/previous page; each page also moves on by itself.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import ui.Alignment
import ui.Arrangement
import ui.Box
import ui.Color
import ui.Column
import ui.Device
import ui.Modifier
import ui.Row
import ui.dp
import ui.fillMaxSize
import ui.fillMaxWidth
import ui.height
import ui.led.BarGraph
import ui.led.Blink
import ui.led.FitOrScroll
import ui.led.LedIcon
import ui.led.LedIcons
import ui.led.Marquee
import ui.led.PageDots
import ui.led.ProgressBar
import ui.led.SlideTransition
import ui.led.SmallText
import ui.led.ledBitmap
import ui.log
import ui.rememberFrameMillis
import ui.width
import kotlin.math.abs
import kotlin.math.sin

private enum class Page(val title: String, val millis: Long) {
    Marquee("Marquee: once across, then the next page", 0),
    Fit("FitOrScroll: still if it fits, else scrolling", 9000),
    Icons("LedIcons and ledBitmap", 6000),
    Small("SmallText: a 3x5 font, 8 characters on 32 LEDs", 5000),
    Bars("BarGraph", 5000),
    Progress("ProgressBar and Blink", 5000),
}

private const val KEY_LEFT = 105
private const val KEY_RIGHT = 106

/** scale 2 on panels at least 16 LEDs high. */
private val scale get() = if (Device.height >= 16) 2 else 1

@Composable
fun App() {
    var index by remember { mutableIntStateOf(0) }
    val pages = Page.entries
    val go = { delta: Int -> index = (index + delta).mod(pages.size) }
    DisposableEffect(Unit) {
        Device.onKey = { code, value ->
            if (value == 1 && code == KEY_RIGHT) go(1)
            if (value == 1 && code == KEY_LEFT) go(-1)
        }
        onDispose { Device.onKey = null }
    }
    LaunchedEffect(index) {
        log("page ${index + 1}/${pages.size}: ${pages[index].title}")
        if (pages[index].millis > 0) {
            delay(pages[index].millis)
            go(1)
        }
    }
    Box(Modifier.fillMaxSize()) {
        SlideTransition(index, Modifier.fillMaxSize(), forward = { from, to -> to == (from + 1) % pages.size }) { i ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (pages[i]) {
                    Page.Marquee -> Marquee("MARQUEE: ONCE ACROSS", Modifier.fillMaxWidth(), Color.Yellow, scale = scale, onDone = { go(1) })
                    Page.Fit -> FitDemo()
                    Page.Icons -> IconsDemo()
                    Page.Small -> SmallDemo()
                    Page.Bars -> BarsDemo()
                    Page.Progress -> ProgressDemo()
                }
            }
        }
        PageDots(index, pages.size)
    }
}

/** A short text (still) and a long one (scrolling), in turn. */
@Composable
private fun FitDemo() {
    var long by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            long = 1 - long
        }
    }
    FitOrScroll(if (long == 1) "THIS ONE IS TOO LONG" else "FITS", Modifier.fillMaxWidth(), Color.Green, scale = scale)
}

private val ICONS = LedIcons.all.values.toList() + ledBitmap(".###. #...# #.#.# #...# .###.")  // your own: ledBitmap

/** The icons, four at a time. */
@Composable
private fun IconsDemo() {
    val millis by rememberFrameMillis(true)
    val first = (millis / 1000 * 4 % ICONS.size).toInt()
    val colors = listOf(Color.White, Color.Red, Color.Green, Color.Yellow)
    Row(horizontalArrangement = Arrangement.spacedBy((scale).dp)) {
        for (k in 0 until 4) LedIcon(ICONS[(first + k) % ICONS.size], color = colors[k], scale = scale)
    }
}

/** A clock in the small font, from 09:41:00. */
@Composable
private fun SmallDemo() {
    val millis by rememberFrameMillis(true)
    val s = 9 * 3600 + 41 * 60 + millis / 1000
    fun two(n: Long) = (n % 60).toString().padStart(2, '0')
    SmallText("${two(s / 3600)}:${two(s / 60)}:${two(s)}", color = Color(0xff4fc3f7), scale = scale)
}

/** A moving level meter. */
@Composable
private fun BarsDemo() {
    val millis by rememberFrameMillis(true)
    val t = millis / 1000f
    val values = List(Device.width) { x -> abs(sin(x * 0.35f + t * 3f) * sin(x * 0.11f - t * 1.3f)) }
    BarGraph(values, Modifier.fillMaxSize(), Color(0xffff7043))
}

/** A bell blinking beside a bar filling up. */
@Composable
private fun ProgressDemo() {
    val millis by rememberFrameMillis(true)
    Row(horizontalArrangement = Arrangement.spacedBy((2 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Blink(onMillis = 400) { LedIcon(LedIcons.Bell, color = Color.Yellow, scale = scale) }
        Column(Modifier.width((Device.width / 2).dp)) {
            SmallText("LOAD", Modifier.height((6 * scale).dp), scale = scale)
            ProgressBar((millis % 4000) / 4000f, Modifier.fillMaxWidth().height(scale.dp), Color.Green, Color(0xff1b3a1b))
        }
    }
}
