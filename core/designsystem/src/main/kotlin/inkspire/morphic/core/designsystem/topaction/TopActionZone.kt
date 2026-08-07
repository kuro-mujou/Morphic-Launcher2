package inkspire.morphic.core.designsystem.topaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** How deep the band reaches, measured from the very top of the window — insets included, since it sits over them. */
val TopActionZoneHeight = 96.dp

/** The drawn plus, sized to sit level with `titleMedium` beside it. */
private val GlyphSize = 24.dp

/**
 * **The band across the top of an open side surface that hands a dragged app to HOME.** Carry an app into it and the
 * surface closes with the drag still in flight, so the same gesture goes on over home's grids and the app is placed
 * where the finger releases it.
 *
 * Ported from L1's `TopActionZone`, and **reduced to one of its two modes**. L1's also served as a delete /
 * uninstall target, with a second row of cells and a `showUninstall` flag; nothing in L2 can delete an app yet (that
 * is the P7 item menu's business, and `LayoutChange` deliberately has no uninstall op at all), so the mode enum, the
 * two-cell row and the hovered-target parameter all go. What is left is what a launcher needs the moment a side
 * surface can be dragged out of, which is now.
 *
 * **Why a band at the top and not "drag anywhere near the edge".** The surfaces this appears over fill the screen and
 * use every edge for something — the drawer pages sideways, a category page scrolls, and holding near a left or right
 * edge already flips a page. A dedicated target says where to go and cannot be triggered by aiming at anything else.
 * It also gives the gesture a name: the user is not "dragging off the drawer", they are dropping onto home.
 *
 * **It is drawn, and separately it is a drop zone.** This composable is only the affordance; the shell registers the
 * matching zone on the shared coordinator and closes the surface when the finger arrives. Keeping the two apart is
 * why this file has no drag types in it — the same split every other cell in this module follows.
 *
 * **Monochrome, unlike L1's**, which was a red delete band and a green add band. The palette reserves colour for the
 * wallpaper and the icons, and reserves red for `error` specifically; a target that is *the* target while it is on
 * screen does not need a hue to say so, and gets its emphasis from `accent` and from lighting up on hover instead.
 *
 * @param visible whether a drag that could be ejected is in flight. The band fades rather than appearing whole, so it
 *   does not read as a jump cut in the middle of a gesture.
 * @param highlighted whether the finger is inside it right now — the moment before the surface closes.
 */
@Composable
fun TopActionZone(visible: Boolean, highlighted: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalMorphicColors.current
    val presence by animateFloatAsState(if (visible) 1f else 0f, label = "topActionPresence")
    // Nothing to draw and — more to the point — nothing to lay out, so a hidden band cannot swallow anything.
    if (presence <= 0f) return

    // The wash reads as "above the surface" rather than as a panel: opaque where the icons are, fading out into the
    // content below so the band has no hard bottom edge to be mistaken for a real one. L1's gradient exactly, with
    // its fixed colour replaced by the theme's.
    val base = if (highlighted) colors.accent else lerp(colors.surfaceElevated, colors.accent, 0.4f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopActionZoneHeight)
            .alpha(presence)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.85f), base.copy(alpha = 0f))))
            // Padded *inside* the band rather than measured below the bars: the wash covers the status bar, which is
            // what makes the target reachable by a thumb that has run out of screen, while the label stays clear of
            // it. The launcher's own inset expression, so a notch moves the label and not the band.
            .uiInsetsPadding(WindowInsetsSides.Top)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (highlighted) colors.onAccent else colors.content
        // A plus drawn rather than `Icons.Filled.Add`: `core:designsystem` carries no material-icons dependency, and
        // pulling one in for a single two-stroke glyph is not a trade worth making. L1 used the icon set here.
        Canvas(Modifier.size(GlyphSize)) {
            val mid = size.minDimension / 2f
            val arm = mid * 0.6f
            val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
            drawLine(tint, Offset(mid - arm, mid), Offset(mid + arm, mid), stroke.width, stroke.cap)
            drawLine(tint, Offset(mid, mid - arm), Offset(mid, mid + arm), stroke.width, stroke.cap)
        }
        Text(
            text = "Drop to home",
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
