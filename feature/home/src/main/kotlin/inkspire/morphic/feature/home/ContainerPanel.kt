package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.wallpaperBackdrop
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * The frosted panel both containers are drawn on — the fill, the corner and the backdrop, as one modifier.
 *
 * Shared because there are three consumers and they must not drift: the icon container's cell, the widget
 * container's cell, and the **floating drag proxy**, which is the one that would have made a divergence visible —
 * a container that changed shape or tint the moment it was picked up reads as picking up a different object.
 *
 * Frosted rather than filled for the reason every panel in this launcher is: it floats over the wallpaper, so it
 * samples the wallpaper. The scrim behind it is `surface`, which is what it falls back to when the launcher has no
 * wallpaper it may read.
 */
@Composable
fun Modifier.containerPanel(): Modifier {
    val colors = LocalMorphicColors.current
    return fillMaxSize()
        .clip(RoundedCornerShape(16.dp))
        .wallpaperBackdrop(shape = RoundedCornerShape(16.dp), scrimColor = colors.surface)
}
