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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CardAlpha
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.FanAnchor
import inkspire.morphic.core.model.GridFill
import inkspire.morphic.core.model.HexOrientation
import inkspire.morphic.core.model.IconArrangement

/**
 * How an icon container's arrangement is chosen — **the shape, and under it the parameters that shape has**.
 *
 * Two rows rather than one list of every combination, because they are two questions and only the first can be
 * answered cold: which shape is picked from a picture, and which *variant* of it is adjusted afterwards on
 * something that exists. Flattened, the second multiplies the first, which is what [IconArrangement] stopped being.
 *
 * **What sits under the shape row belongs to the shape**, and is not always a row of pictures — the grid's is not,
 * and the reason is worth stating because it looks like an inconsistency. A fan's four corners are four different
 * pictures at any count. A grid's pinned axes are not: at six icons `columns = 2` and `rows = 3` draw the *same*
 * three-by-two block, and differ only in which way it grows as icons are added. A swatch can only show the count
 * you are looking at, so for the grid it would show two identical tiles meaning opposite things. That is the one
 * place here where a number says what a picture cannot.
 *
 * **A shape with nothing to say gets nothing** rather than an empty row — the launcher's standing rule for a
 * control with no op behind it, and the reason the row above must not move when one appears (it does not: it is
 * the row above).
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
        // Exhaustive, so a shape that grows a parameter has to say here how it is set before the control can offer
        // it — the same guard `slots` and `swatchCount` apply to the geometry and the picture.
        when (arrangement) {
            is IconArrangement.Fan -> SwatchRow(
                options = FanAnchor.entries.map { IconArrangement.Fan(it) },
                selected = arrangement,
                onPick = onArrangement,
            )

            is IconArrangement.Grid -> GridFillRows(
                fill = arrangement.fill,
                onFill = { onArrangement(IconArrangement.Grid(it)) },
            )

            is IconArrangement.Beehive -> SwatchRow(
                options = HexOrientation.entries.map { IconArrangement.Beehive(it) },
                selected = arrangement,
                onPick = onArrangement,
            )

            IconArrangement.Circle -> Unit
        }
    }
}

/**
 * The four shapes, with the fan standing at **the corner it is already set to** and the grid at its own fill.
 *
 * That is what keeps the two rows from disagreeing: tapping the shape you are on is then a no-op rather than a
 * reset to a setting you did not ask for, and plain equality decides which tile is selected without anyone
 * comparing types. A shape being switched *to* opens at its default, and each shape's default is what it was
 * before it could be told otherwise: [GridFill.Auto], [HexOrientation.FLAT_TOP], and [FanAnchor.TOP_LEFT], which
 * fills in reading order.
 */
private fun shapeOptions(current: IconArrangement): List<IconArrangement> = listOf(
    current as? IconArrangement.Grid ?: IconArrangement.Grid(),
    IconArrangement.Circle,
    current as? IconArrangement.Fan ?: IconArrangement.Fan(),
    current as? IconArrangement.Beehive ?: IconArrangement.Beehive(),
)

/**
 * The grid's two counts — **the pair a user thinks in**, over a model that holds exactly one of them.
 *
 * Setting either returns the other to *Auto*, and nothing here has to arrange that: [GridFill] is a one-of, so the
 * row that is not pinned has nothing to be selected. What the user sees is two ordinary settings; what is stored
 * cannot say "three rows and four columns", which is a frame with a capacity and not what this container is.
 */
@Composable
private fun GridFillRows(fill: GridFill, onFill: (GridFill) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CountRow(
            label = "Rows",
            selected = (fill as? GridFill.Rows)?.count,
            onPick = { count -> onFill(count?.let(GridFill::Rows) ?: GridFill.Auto) },
        )
        CountRow(
            label = "Columns",
            selected = (fill as? GridFill.Columns)?.count,
            onPick = { count -> onFill(count?.let(GridFill::Columns) ?: GridFill.Auto) },
        )
    }
}

/** One axis: its name, then *Auto* and the counts it can be pinned to. Null is Auto, both in and out. */
@Composable
private fun CountRow(label: String, selected: Int?, onPick: (Int?) -> Unit) {
    val colors = LocalMorphicColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.contentMuted,
            modifier = Modifier.width(76.dp),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CountChip(label = "Auto", chosen = selected == null, onPick = { onPick(null) })
            // Eight is the reference's ceiling and about where a phone-sized container stops being aimable — a
            // container is a few home cells wide, so its ninth column is smaller than the fingertip picking it.
            // Nothing enforces it in the geometry: a stored count beyond this still lays out, it just cannot be
            // chosen here.
            for (count in 1..8) {
                CountChip(label = "$count", chosen = selected == count, onPick = { onPick(count) })
            }
        }
    }
}

/** One count, or *Auto* — selection reading by contrast, as [SwatchTile]'s does. */
@Composable
private fun CountChip(label: String, chosen: Boolean, onPick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (chosen) colors.accent else colors.contentMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (chosen) colors.accent.copy(alpha = 0.22f) else colors.surface.copy(alpha = CardAlpha))
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
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
