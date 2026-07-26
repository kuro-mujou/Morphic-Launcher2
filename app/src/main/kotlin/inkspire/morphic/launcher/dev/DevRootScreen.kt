package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The dev harness screens; the floating chip switches between them. */
private enum class DevScreen(val label: String) {
    Drag("Drag"),
    Pager("Pager"),
    PagerDrag("Pager+Drag"),
    Surface("Surface"),
    ;

    fun next(): DevScreen = entries[(ordinal + 1) % entries.size]
}

/**
 * Dev entry point: shows one harness screen full-screen with a floating chip (top-end) to switch. Keeps both
 * the drag harness and the pager test reachable without editing code.
 */
@Composable
fun DevRootScreen(modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf(DevScreen.Drag) }
    Box(modifier.fillMaxSize()) {
        when (screen) {
            DevScreen.Drag -> DragPlaygroundScreen()
            DevScreen.Pager -> PagerPlaygroundScreen()
            DevScreen.PagerDrag -> PagerDragPlaygroundScreen()
            DevScreen.Surface -> SurfacePagerPlaygroundScreen()
        }
        // Theme-independent chip so it reads over either screen; label shows what tapping switches TO.
        Text(
            text = "→ ${screen.next().label}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .clickable { screen = screen.next() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
