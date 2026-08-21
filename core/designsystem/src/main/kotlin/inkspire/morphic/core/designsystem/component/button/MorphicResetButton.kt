package inkspire.morphic.core.designsystem.component.button

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **Put this control back where it started** — one turning arrow, beside the value it acts on.
 *
 * Every control whose value has a resting position carries one, which is what replaced the "Reset …" text buttons at
 * the foot of each settings section: a button naming a *group* cannot say which of its numbers has moved, so it was
 * both the only way back and no answer at all to "have I changed this?". Per control, it is both.
 *
 * **Disabled is the resting state, and that is the whole of the second job.** Lit means "this differs from its
 * default"; dimmed means it does not — so a section can be read for what has been touched without remembering what
 * any default was. A caller that cannot answer the question must not draw one of these rather than draw a lit one
 * that lies.
 *
 * A tap, never a hold: there is one place to go, and pressing again would not move it further.
 *
 * @param tint the arrow's color when enabled; dimmed from it when not. Passed rather than read from the theme because
 *   the icon studio's chrome is fixed white over a canvas the user sets to black or white — the same reason
 *   `SliderRowStyle` exists.
 */
@Composable
fun MorphicResetButton(
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(ResetSlot)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        val color = if (enabled) tint else tint.copy(alpha = tint.alpha * DisabledAlpha)
        Canvas(Modifier.size(GlyphSide)) {
            // A ring open at one point with a head on its leading end: the least that still reads as "turn it back".
            // Drawn rather than imported — `core:designsystem` carries no material-icons dependency, and `TopActionZone`
            // draws its three marks by hand for the same reason.
            val side = size.minDimension
            val mid = side / 2f
            val stroke = side * StrokeFraction
            // Short of the button's own half, because the head sticks out past the arc it grows from.
            val radius = mid * RadiusFraction
            drawArc(
                color = color,
                startAngle = ArcStart,
                sweepAngle = ArcSweep,
                useCenter = false,
                topLeft = Offset(mid - radius, mid - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val end = ((ArcStart + ArcSweep) * PI / 180f).toFloat()
            val tip = Offset(mid + radius * cos(end), mid + radius * sin(end))
            // Backwards along the tangent, splayed either side of it — a chevron rather than a filled triangle, which
            // at this size would read as a blob.
            val back = Offset(sin(end), -cos(end))
            val arm = radius * HeadFraction
            listOf(HeadSplay, -HeadSplay).forEach { splay ->
                val a = (splay * PI / 180f).toFloat()
                val dir = Offset(back.x * cos(a) - back.y * sin(a), back.x * sin(a) + back.y * cos(a))
                drawLine(color, tip, Offset(tip.x + dir.x * arm, tip.y + dir.y * arm), stroke, StrokeCap.Round)
            }
        }
    }
}

/** Where the ring's gap sits and how far the arrow travels — a gap at the top right, read as "not quite closed". */
private const val ArcStart = -60f
private const val ArcSweep = 285f

/** How far each arm of the head splays off the tangent. Wide enough to read, narrow enough not to look like a V. */
private const val HeadSplay = 38f

private const val StrokeFraction = 0.11f
private const val RadiusFraction = 0.62f
private const val HeadFraction = 0.7f

/** M3's own disabled content alpha: plainly spent, without disappearing. */
private const val DisabledAlpha = 0.38f

/** Smaller than a stepper, because it sits in a caption row rather than beside a track. */
private val ResetSlot = 32.dp

/** Short of the slot, so the press target is larger than the mark it shows. */
private val GlyphSide = 20.dp
