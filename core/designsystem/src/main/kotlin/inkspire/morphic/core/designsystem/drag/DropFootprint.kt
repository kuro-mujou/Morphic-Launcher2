package inkspire.morphic.core.designsystem.drag

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.DropIntent

/**
 * The drop shadow: the indicator drawn on a grid at the cell a drag would land on, sized by the caller to the
 * dragged item's footprint. Its look is a pure function of [intent] (docs/DRAG_AND_DROP_DESIGN.md §7), so the
 * shadow and the actual outcome can never disagree — the caller reads one [inkspire.morphic.core.model.PlacementPlan]
 * for both.
 *
 * Positioning is the surface's job: it places this over the target cell using its own cell geometry. This
 * component only renders the box, filling whatever bounds [modifier] gives it.
 *
 * The four painted states, in the monochrome palette (only [DropIntent.INVALID] uses real colour — red — since
 * that is the reserved error hue):
 * - [DropIntent.PLACE] — a quiet accent wash: "drops here".
 * - [DropIntent.MERGE] — a stronger accent that **expands** slightly, signalling a combine.
 * - [DropIntent.INVALID] — a red wash: can't drop here.
 * - [DropIntent.PUSH] — a debug-only tint (drops the same as PLACE); distinct just so pushes are visible while
 *   developing.
 *
 * **[DropIntent.REORDER] paints nothing and returns.** An ordered surface previews its drop by reflowing its own
 * cells around a migrating gap, so there is no target cell to shadow — the preview *is* the reflow. A
 * paint-nothing branch in a component whose contract is to paint looks wrong, and was avoided while the folder
 * was the only such surface; with the APPS pager it is the honest place for the rule, because the alternative is
 * every caller remembering not to call this.
 *
 * State changes animate (colour + the merge expansion) so the shadow morphs rather than jumps as the finger
 * crosses zones.
 */
@Composable
fun DropFootprint(
    intent: DropIntent,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    if (intent == DropIntent.REORDER) return

    val colors = LocalMorphicColors.current

    val fill = when (intent) {
        DropIntent.PLACE -> colors.accent.copy(alpha = 0.12f)
        DropIntent.MERGE -> colors.accent.copy(alpha = 0.22f)
        DropIntent.INVALID -> colors.error.copy(alpha = 0.16f)
        DropIntent.PUSH -> DebugPushTint.copy(alpha = 0.18f)
        DropIntent.REORDER -> Color.Transparent // unreachable: returned above
    }
    val stroke = when (intent) {
        DropIntent.PLACE -> colors.accent.copy(alpha = 0.45f)
        DropIntent.MERGE -> colors.accent
        DropIntent.INVALID -> colors.error.copy(alpha = 0.7f)
        DropIntent.PUSH -> DebugPushTint.copy(alpha = 0.7f)
        DropIntent.REORDER -> Color.Transparent // unreachable: returned above
    }
    // Merge grows the footprint a touch to read as "swallowing" the target; the rest sit at cell size.
    val targetScale = if (intent == DropIntent.MERGE) 1.08f else 1f

    val animatedFill by animateColorAsState(fill, label = "footprintFill")
    val animatedStroke by animateColorAsState(stroke, label = "footprintStroke")
    val animatedScale by animateFloatAsState(targetScale, label = "footprintScale")

    Box(
        modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(shape)
            .background(animatedFill)
            .border(BorderStroke(2.dp, animatedStroke), shape),
    )
}

/**
 * A deliberately off-palette tint for the debug-only [DropIntent.PUSH] shadow. Not a design token: pushes drop
 * exactly like [DropIntent.PLACE], and this only exists so a push is visible during development. Remove or gate
 * behind a debug flag before ship.
 */
private val DebugPushTint = Color(0xFF3B82F6)
