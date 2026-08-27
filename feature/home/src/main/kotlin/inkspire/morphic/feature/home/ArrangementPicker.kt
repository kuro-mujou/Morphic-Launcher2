package inkspire.morphic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CardAlpha
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.FanAnchor
import inkspire.morphic.core.model.IconArrangement

/**
 * How an icon container's arrangement is chosen — **the shape, and under it the parameters that shape has**.
 *
 * Two rows rather than one list of every combination, because they are two questions and only the first can be
 * answered cold: which shape is picked from a picture, and which *variant* of it is adjusted afterwards on
 * something that exists. Flattened, the second multiplies the first, which is what [IconArrangement] stopped being.
 *
 * **The second row is simply absent for a shape with nothing to say**, rather than present and empty — the
 * launcher's standing rule for a control with no op behind it, and the reason the first row must not move when it
 * appears (it does not: it is the row above).
 *
 * **Both places that set an arrangement use this one control**: the widget picker, where the shape is chosen before
 * the container exists, and the container's own settings. Two consumers is what made extracting it right rather
 * than speculative — and it is what replaced the settings dialog, which covered the live preview at the one moment
 * the preview had something to say.
 */
@Composable
internal fun ArrangementPicker(
    arrangement: IconArrangement,
    onArrangement: (IconArrangement) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwatchRow(options = shapeOptions(arrangement), selected = arrangement, onPick = onArrangement)
        val variants = variantOptions(arrangement)
        if (variants.isNotEmpty()) {
            SwatchRow(options = variants, selected = arrangement, onPick = onArrangement)
        }
    }
}

/**
 * The four shapes, with the fan standing at **the corner it is already set to**.
 *
 * That is what keeps the two rows from disagreeing: tapping the shape you are on is then a no-op rather than a
 * reset to a corner you did not ask for, and plain equality decides which tile is selected without anyone
 * comparing types. A fan on a container that is not one opens at [FanAnchor.TOP_LEFT], which fills in reading
 * order — the innermost arc sits nearest the corner a left-to-right reader starts from.
 */
private fun shapeOptions(current: IconArrangement): List<IconArrangement> = listOf(
    IconArrangement.Grid,
    IconArrangement.Circle,
    current as? IconArrangement.Fan ?: IconArrangement.Fan(),
    IconArrangement.Beehive,
)

/**
 * What the chosen shape offers beyond itself — empty for a shape that offers nothing.
 *
 * Exhaustive, so a shape that grows a parameter has to say here that it has one before the row can show it.
 */
private fun variantOptions(current: IconArrangement): List<IconArrangement> = when (current) {
    is IconArrangement.Fan -> FanAnchor.entries.map { IconArrangement.Fan(it) }
    IconArrangement.Grid, IconArrangement.Circle, IconArrangement.Beehive -> emptyList()
}

/** One row of arrangements, each drawn as the shape it makes. */
@Composable
private fun SwatchRow(
    options: List<IconArrangement>,
    selected: IconArrangement,
    onPick: (IconArrangement) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            SwatchTile(arrangement = option, chosen = option == selected, onPick = { onPick(option) })
        }
    }
}

/**
 * One tile: an arrangement drawn by itself, over a fill that says whether it is the chosen one.
 *
 * Selection reads by **contrast**, not by hue — the accent is a grayscale emphasis, per the design system.
 */
@Composable
private fun SwatchTile(arrangement: IconArrangement, chosen: Boolean, onPick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (chosen) colors.accent.copy(alpha = 0.22f) else colors.surface.copy(alpha = CardAlpha))
            .clickable(onClick = onPick)
            .padding(9.dp),
    ) {
        IconArrangementSwatch(
            arrangement = arrangement,
            color = if (chosen) colors.accent else colors.contentMuted,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
