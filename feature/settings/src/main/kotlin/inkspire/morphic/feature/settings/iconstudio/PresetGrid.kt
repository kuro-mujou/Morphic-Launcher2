package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import inkspire.morphic.data.settings.IconPreset

/**
 * The rows-of-[PresetColumns] layout both preset libraries are drawn in — the Icons pane's and the studio panel's.
 *
 * A preset *is* a look, so a library is a grid of pictures rather than a list of names; what the two libraries share
 * is only that arrangement. The tiles themselves are deliberately different — one applies, edits and deletes, the
 * other loads and renames — so the tile is a slot rather than a parameter list, and each caller keeps its own.
 *
 * **The two things worth sharing are the two that would drift silently.** The cell width is a formula over the
 * spacing and the column count, and a tile bounded by a slightly different number in each library is a difference
 * nobody would think to compare. And a short last row is *spread* like a full one rather than stretched across the
 * width, which is what the trailing [Spacer]s buy — omit them in one library and its final row would silently lay
 * out differently from the other's.
 *
 * [spacing] and [tileMax] stay the caller's, because they genuinely differ: the panel is floating glass and packs
 * its tiles tighter than a full settings pane does.
 *
 * @param tile draws one preset, given the width its cell may not exceed. The cap goes on the tile rather than on the
 *   cell, so the cell keeps its equal share of the row and the tile is centered in what it does not use.
 */
@Composable
internal fun PresetGrid(
    presets: List<IconPreset>,
    spacing: Dp,
    tileMax: Dp,
    modifier: Modifier = Modifier,
    tile: @Composable (preset: IconPreset, cellMax: Dp) -> Unit,
) {
    BoxWithConstraints {
        // Capped, so the extra width on a tablet goes to the gaps between tiles rather than making a four-preset
        // library four huge squares — the effect grid's own arrangement and its reason.
        val cell = ((maxWidth - spacing * (PresetColumns - 1)) / PresetColumns).coerceAtMost(tileMax)

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            presets.chunked(PresetColumns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { preset ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            tile(preset, cell)
                        }
                    }
                    repeat(PresetColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Tiles across a preset library. Three in both, and the grid's arithmetic reads it rather than taking it. */
internal const val PresetColumns = 3
