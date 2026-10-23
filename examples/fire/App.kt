// Fire: the classic heat-diffusion effect, a value per LED through a palette.
// Up/Down: hotter/cooler.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.*
import kotlin.random.Random

private val PALETTE = IntArray(37) { i ->
    val t = i / 36f
    val r = (t * 3f).coerceAtMost(1f)
    val g = (t * 3f - 1f).coerceIn(0f, 1f)
    val b = (t * 3f - 2f).coerceIn(0f, 1f)
    (0xff shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
}

@Composable
fun App() {
    val w = Device.width
    val h = Device.height
    val heat = remember { IntArray(w * h) }
    var fuel by remember { mutableIntStateOf(36) }
    val millis by rememberFrameMillis(true)
    DisposableEffect(Unit) {
        Device.onKey = { code, value ->
            if (value != 0) when (code) {
                103 -> fuel = (fuel + 4).coerceAtMost(36)  // Up
                108 -> fuel = (fuel - 4).coerceAtLeast(0)  // Down
            }
        }
        onDispose { Device.onKey = null }
    }
    Canvas(Modifier.fillMaxSize()) {
        millis  // a new frame every frame
        for (x in 0 until w) heat[(h - 1) * w + x] = fuel
        for (y in 0 until h - 1) for (x in 0 until w) {
            val r = Random.nextInt(4)
            val dst = (x + r / 2 - 1 + (r and 1)).coerceIn(0, w - 1)  // drifts sideways a little
            heat[y * w + dst] = (heat[(y + 1) * w + x] - r).coerceAtLeast(0)
        }
        for (i in heat.indices) {
            val v = heat[i]
            if (v > 0) drawRect(Color(PALETTE[v]), Offset((i % w).toFloat(), (i / w).toFloat()), Size(1f, 1f))
        }
    }
}
