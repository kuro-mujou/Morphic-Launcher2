package inkspire.morphic.core.designsystem.component.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * Browse a palette bank: one named row per palette, over a ribbon of color chips that narrows the list.
 *
 * **A named list, not a ribbon of pills — the shape a bank of hundreds actually needs.** The swatch ribbon this sits
 * beside answers "show me the next one" in one tap and is the right control for that; it cannot answer "something
 * green", which is how a person arrives at a palette when they already have a picture in mind. So the list scrolls
 * vertically (one row is one palette, whole, with room for its name), and [PaletteColorFilter] gives it the one axis
 * worth spending chrome on.
 *
 * **The name is in the row and the colors are the rest of it.** A pill of swatches identifies a palette perfectly well
 * while you are scanning past it and not at all when you want to come back to one, which is the whole difference
 * between a ribbon and a browser. The name column is the fixed one and the swatches take the slack — the other way
 * round leaves every row's first swatch at a different place and nothing to read down the list.
 *
 * **Picking commits and does not confirm.** There is no preview state and no OK button: whatever is behind this panel
 * is already re-rendering in the tapped palette, so a confirmation step would only ask the user to agree with what
 * they can see. Closing is the caller's business — [onPick] is where it happens.
 *
 * **No ground of its own.** The browser draws rows, chips and text and nothing behind them, so the surface it floats
 * over is the caller's decision; the wallpaper studio hands it the same scrim its Style panel uses. A component that
 * painted its own black would be unusable on the settings surfaces this bank also serves.
 *
 * @param palettes the bank to browse, in the order it should appear unfiltered.
 * @param selected the colors currently applied, so the row carrying them is ringed. Compared by value, since a palette
 *   is identified downstream by its colors rather than by its name.
 * @param onPick called with the whole palette — a wallpaper generator wants a set, not a swatch.
 */
@Composable
fun PalettePresetBrowser(
    palettes: List<ColorPalette>,
    selected: List<Int>,
    onPick: (ColorPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    var chip by rememberSaveable { mutableStateOf<Int?>(null) }
    val shown = remember(palettes, chip) {
        chip?.let { PaletteColorFilter.matching(palettes, it) } ?: palettes
    }
    val listState = rememberLazyListState()
    // A new filter is a new list, and leaving it scrolled where the last one was shows the middle of an answer whose
    // best matches are at the top — the ranking is only useful if the top is what you land on.
    LaunchedEffect(chip) { listState.scrollToItem(0) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shown.isEmpty()) {
            Text(
                text = "Nothing in that color",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shown, key = { it.name }) { palette ->
                    PresetRow(
                        palette = palette,
                        selected = palette.colors == selected,
                        onClick = { onPick(palette) },
                    )
                }
            }
        }

        FilterRibbon(chip = chip, onChip = { chip = it })
    }
}

/** One palette in the list: its name, then its stops as circles, the whole row a tap that applies it. */
@Composable
private fun PresetRow(palette: ColorPalette, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.content.copy(alpha = 0.10f))
            .then(if (selected) Modifier.border(2.dp, colors.content, shape) else Modifier)
            .clickable(onClickLabel = palette.name, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The name column is what is fixed and the swatches take the rest, which is the way round that makes the
        // colors line up: give the *name* the slack and it grows to fill it, pushing every row's first swatch to a
        // different place and leaving nothing to read down the list.
        Text(
            text = palette.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(92.dp)
                .padding(horizontal = 4.dp),
        )
        // Left-packed at a fixed size rather than shared out by weight, so a three-stop palette is three circles and
        // a gap rather than three blobs the size of its neighbor's eight. The size is what makes the bank's longest
        // palette fit the narrowest phone: eight of these plus their gaps is 188dp, against the ~192dp this row has
        // to spend on a 360dp screen.
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            palette.colors.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        // Every swatch is outlined, which matters only for the dark ones and is why it cannot be
                        // conditional: this row is a dark pill, so a near-black stop with no edge reads as a *gap* in
                        // the palette rather than as one of its colors. A palette that appears to be missing a stop is
                        // the silent failure here — it is still applied in full, so nothing but the eye ever objects.
                        // Drawn from `content` rather than `outline`, which in the dark theme is darker than the pill
                        // it would have to show up against.
                        .border(1.dp, colors.content.copy(alpha = 0.3f), CircleShape),
                )
            }
        }
    }
}

/**
 * The filter chips, pinned under the list: a clear button while a filter is on, then one circle per
 * [PaletteColorFilter.chips] entry, the chosen one ringed.
 *
 * **The clear button appears rather than sitting there greyed**, and it leads the ribbon rather than hiding at its
 * end — tapping the lit chip again would also clear, but that is a gesture with nothing on screen to suggest it.
 */
@Composable
private fun FilterRibbon(chip: Int?, onChip: (Int?) -> Unit) {
    val colors = LocalMorphicColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Filter by",
            style = MaterialTheme.typography.labelMedium,
            color = colors.contentMuted,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chip != null) {
                item(key = "clear") {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Clear color filter") { onChip(null) }
                            .border(1.dp, colors.content.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "✕", style = MaterialTheme.typography.labelMedium, color = colors.content)
                    }
                }
            }
            items(PaletteColorFilter.chips, key = { it }) { swatch ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Filter by this color") { onChip(swatch) }
                        // The ring sits on the target's own edge and the swatch is inset inside it, so selecting one
                        // does not change how much room the ribbon takes.
                        .border(2.dp, if (swatch == chip) colors.content else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        // A hairline on every chip, because the panel's ground is near-black and the dark chips
                        // otherwise read as holes in the ribbon rather than as colors that can be tapped.
                        .border(1.dp, colors.content.copy(alpha = 0.3f), CircleShape),
                )
            }
        }
    }
}
