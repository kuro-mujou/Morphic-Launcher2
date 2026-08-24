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
 * **It is one of the three surfaces that render the user's *own* backdrop effect**, the others being the widget
 * container beside it and an icon's plate. Everything else frosted in this launcher — the sheet behind an arriving
 * surface, the bottom sheets, the context menu — takes the fixed film instead (`Modifier.filmBackdrop`), for
 * consistency and for legibility at a size where a slider could make a screenful of rows unreadable. What puts these
 * three on the other side of that line is that each genuinely *floats* on the wallpaper with edges of its own, which
 * is both what a tunable blur is worth having on and what liquid glass needs a rim for.
 *
 * The scrim behind it is `surface`, which is what it falls back to when the launcher has no wallpaper it may read.
 */
@Composable
fun Modifier.containerPanel(): Modifier {
    val colors = LocalMorphicColors.current
    return fillMaxSize()
        .clip(RoundedCornerShape(16.dp))
        .wallpaperBackdrop(shape = RoundedCornerShape(16.dp), scrimColor = colors.surface)
}
