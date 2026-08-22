package inkspire.morphic.core.designsystem.component.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Emphasis ladder for [MorphicButton], strongest to weakest. */
enum class MorphicButtonStyle { Filled, Tonal, Outlined, Text, Elevated }

/**
 * The in-house labeled button. Built **on** the M3 button family (which renders monochrome via the bridged
 * ColorScheme), so it gets the full Expressive press motion — including the pressed-shape morph — for free
 * via `ButtonDefaults.shapes()`. [style] selects the M3 emphasis variant. Put any leading icon in [content].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MorphicButtonStyle = MorphicButtonStyle.Filled,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shapes = ButtonDefaults.shapes(
        shape = RoundedCornerShape(12.dp),
        pressedShape = RoundedCornerShape(6.dp),
    )
    when (style) {
        MorphicButtonStyle.Filled ->
            Button(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, content = content)

        MorphicButtonStyle.Tonal ->
            FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, content = content)

        MorphicButtonStyle.Outlined ->
            OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, content = content)

        MorphicButtonStyle.Text ->
            TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, content = content)

        MorphicButtonStyle.Elevated ->
            ElevatedButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, content = content)
    }
}

/**
 * Single-select segmented control (e.g. an "All / Individual" scope switch). Kept fully custom — M3's
 * segmented button doesn't match the compact look — reading the monochrome roles from [LocalMorphicColors].
 * The group sits in a rounded surface-elevated container with the selected segment filled in the accent.
 *
 * **The fill is one indicator that travels, not a background switched off one segment and on at another.** Those
 * two look identical in a screenshot and nothing alike in the hand: a control whose selection teleports reads as a
 * redraw, where one that slides reads as the same object being moved — which is what picking between segments is.
 * It is drawn behind the labels rather than laid out among them so that no frame of the spring costs a
 * recomposition or a re-layout; the animated value is read in [drawBehind], the cheapest of the three phases.
 * `MorphicSwitch` offsets its knob at layout for the same reason and settles for that only because it has a
 * `Modifier.offset` to hand.
 *
 * **The pill springs and the labels cross-fade**, which is `MorphicSwitch`'s division applied here: spatial motion
 * for the thing that moves, the effects spec for the thing that only changes color. A label snapping to its
 * selected color under a pill still travelling is the tell that the two were treated as one.
 *
 * The ripple stays on each segment. Unlike the switch — where the knob crossing *is* the acknowledgement — pressing
 * the segment that is already selected moves nothing, and without a ripple that press would go unanswered.
 *
 * A [selectedIndex] outside [options] draws no pill at all, which is the honest picture of a control whose stored
 * value is not among the choices it is offering.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphicSegmentedButtons(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current

    // Coerced, so a [selectedIndex] out of range parks the pill at an end rather than springing to a position no
    // segment occupies. Whether it is *drawn* is decided separately, below.
    val travel by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)).toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "MorphicSegmentedIndicator",
    )
    val showIndicator = selectedIndex in options.indices

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .padding(4.dp)
            // After the padding, so this draws in the content box the segments themselves occupy — the same
            // rectangle the arithmetic below divides up.
            .drawBehind {
                if (!showIndicator) return@drawBehind
                val gap = 4.dp.toPx()
                val segment = (size.width - gap * (options.size - 1)) / options.size
                drawRoundRect(
                    color = colors.accent,
                    topLeft = Offset(x = (segment + gap) * travel, y = 0f),
                    size = Size(segment, size.height),
                    cornerRadius = CornerRadius(9.dp.toPx()),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val labelColor by animateColorAsState(
                targetValue = if (index == selectedIndex) colors.onAccent else colors.content,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "MorphicSegmentedLabel",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = labelColor)
            }
        }
    }
}
