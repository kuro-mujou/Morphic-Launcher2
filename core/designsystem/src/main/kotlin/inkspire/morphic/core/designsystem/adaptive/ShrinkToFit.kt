package inkspire.morphic.core.designsystem.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * [content] **laid out** at exactly [size] and then drawn smaller if the space given is smaller — never larger —
 * centered in that space.
 *
 * For previewing something whose layout depends on its size: a widget re-lays rather than scales, so showing one in a
 * smaller box by *measuring* it smaller would preview a different layout from the one it has on HOME. Laying it out at
 * its real size and scaling the pixels is what keeps the preview true. Nothing is drawn for an empty [size].
 */
@Composable
fun ShrinkToFit(size: DpSize, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (size.width <= 0.dp || size.height <= 0.dp) return@BoxWithConstraints
        val scale = minOf(1f, maxWidth / size.width, maxHeight / size.height)
        Box(Modifier.size(size.width * scale, size.height * scale), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) { content() }
        }
    }
}
