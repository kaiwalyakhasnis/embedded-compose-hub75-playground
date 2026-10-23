// Bounce: balls with gravity, drawn every frame from rememberFrameMillis().
// Click or tap the panel to throw a ball from there; Enter clears.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import ui.*
import kotlin.random.Random

private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color)

private fun randomBall(x: Float, y: Float) = Ball(
    x, y,
    vx = Random.nextFloat() * 40f - 20f,
    vy = -Random.nextFloat() * 30f,
    color = Color(listOf(0xffff1744, 0xff00e5ff, 0xffffea00, 0xff76ff03, 0xffd500f9).random()),
)

@Composable
fun App() {
    val balls = remember { mutableListOf(randomBall(10f, 5f), randomBall(40f, 8f)) }
    val millis by rememberFrameMillis(true)
    val last = remember { longArrayOf(0) }
    DisposableEffect(Unit) {
        Device.onKey = { code, value -> if (code == 28 && value == 1) balls.clear() }
        onDispose { Device.onKey = null }
    }
    Canvas(Modifier.fillMaxSize().pointerInput { e ->
        if (e.type == PointerEventType.Press) balls += randomBall(e.position.x, e.position.y)
    }) {
        val dt = ((millis - last[0]).coerceIn(0, 50)) / 1000f
        last[0] = millis
        for (b in balls) {
            b.vy += 60f * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.x < 1.5f || b.x > size.width - 1.5f) { b.vx = -b.vx; b.x = b.x.coerceIn(1.5f, size.width - 1.5f) }
            if (b.y > size.height - 1.5f) { b.vy = -b.vy * 0.9f; b.y = size.height - 1.5f }
            drawCircle(b.color, 1.6f, Offset(b.x, b.y))
        }
        drawText("${balls.size}", Offset(1f, 1f), Color(0xff606060))
    }
}
