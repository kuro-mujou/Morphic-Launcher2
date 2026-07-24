package inkspire.morphic.core.designsystem.component.button

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Emphasis ladder for [MorphicButton], strongest to weakest. */
enum class MorphicButtonStyle { Filled, Tonal, Outlined, Text, Elevated }

/**
 * The in-house labelled button. Built **on** the M3 button family (which renders monochrome via the bridged
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
    val shapes = ButtonDefaults.shapes()
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
 * The selected segment fills with the accent; the group sits in a rounded surface-elevated container.
 */
@Composable
fun MorphicSegmentedButtons(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onAccent else colors.content,
                )
            }
        }
    }
}
