// Hello: text, state, a timer and keys.
// Enter counts; Left/Right change the colour. Click the panel first for the keyboard.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import ui.*

private const val KEY_ENTER = 28
private const val KEY_SPACE = 57
private const val KEY_LEFT = 105
private const val KEY_RIGHT = 106

private val COLORS = listOf(Color(0xff40c4ff), Color(0xffff4081), Color(0xffffc400), Color(0xff00e676))

@Composable
fun App() {
    var count by remember { mutableIntStateOf(0) }
    var color by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        Device.onKey = { code, value ->
            if (value == 1) when (code) {
                KEY_ENTER, KEY_SPACE -> count++
                KEY_LEFT -> color = (color + COLORS.size - 1) % COLORS.size
                KEY_RIGHT -> color = (color + 1) % COLORS.size
            }
        }
        onDispose { Device.onKey = null }
    }
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            blink = !blink
        }
    }

    Column(Modifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("HELLO", color = COLORS[color], scale = 2)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COUNT $count")
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(3.dp).background(if (blink) Color.Red else Color(0xff300000), CircleShape))
        }
    }
}
