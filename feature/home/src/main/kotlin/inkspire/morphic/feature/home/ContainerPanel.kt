package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/**
 * The **"+" an empty container shows**, sized to the container it is in.
 *
 * Both containers show one, and the widget picker previews both, so it is one composable rather than four — which is
 * also what makes the two cells agree. They did not: the icon container scaled its glyph to a fraction of the cell
 * while the widget container took `IconButton`'s default 24dp, so the same "+" was two sizes on one home screen. That
 * `IconButton` is gone: it drew a *second* control over a cell that already has the surface's one gesture contract on
 * it, so a long-press raised the container's menu and the button's own `onClick` then fired the add flow on release —
 * the overlap this launcher removed everywhere else (CLAUDE.md: cells carry no `onClick`). The "+" is now a plain
 * glyph, and the empty cell's tap reaches the add flow through `onOpen` like every other tap.
 *
 * A **fraction of the smaller side**, not a dp: a container is sized by the grid it sits on, and a fixed glyph would
 * be a speck in a 4×4 container and fill a 1×1 one.
 */
@Composable
internal fun ContainerAddGlyph(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = contentDescription,
            tint = colors.contentMuted,
            modifier = Modifier.size(maxWidth.coerceAtMost(maxHeight) * EmptyGlyphFraction),
        )
    }
}

/**
 * How much of a container's smaller side its empty "+" takes.
 *
 * Named rather than written at the call site because it is a **fraction**, not a dimension — the rule about writing
 * a dp where it is used does not reach it, and `0.3f` sitting bare in a `size()` reads as neither.
 */
private const val EmptyGlyphFraction = 0.3f
