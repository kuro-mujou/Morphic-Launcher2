package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


/**
 * Choosing a color: quick swatches, and a full picker one tap away.
 *
 * **One component for all four colors in this editor** — a solid fill, a tint, and a gradient's two stops. They
 * were three near-identical swatch rows before the picker existed, which is exactly the shape that drifts: L1 has
 * a whole file of near-copies for the same reason.
 *
 * The swatches stay rather than being replaced by the picker. They are how a color is chosen *quickly* and the
 * picker is how one is chosen *exactly*, and an editor that made every black require a drag across a saturation
 * panel would be slower for the common case in exchange for precision nobody wanted there.
 *
 * @param clearable whether "no color" is a choice. False for a fill or a gradient stop, which must be *some*
 *   color; true for a tint, which is an effect a user has to be able to get back off.
 */
@Composable
internal fun ColorField(argb: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) =
    ColorFieldBody(argb, modifier, clearable = false) { picked -> picked?.let(onChange) }

/**
 * [ColorField] where *no color* is one of the choices.
 *
 * A separate function rather than a `clearable` flag on one, because the flag would not change the **type**: a
 * caller that must have a color would still be handed a nullable one and have to decide what to do with a null
 * that cannot happen. Two signatures make each call site say which it is, and the shared body is the same either
 * way.
 */
@Composable
internal fun ClearableColorField(argb: Int?, modifier: Modifier = Modifier, onChange: (Int?) -> Unit) =
    ColorFieldBody(argb, modifier, clearable = true, onChange = onChange)

@Composable
private fun ColorFieldBody(
    argb: Int?,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    onChange: (Int?) -> Unit,
) {
    val picker = LocalStudioColorPicker.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (clearable) Swatch(argb = null, selected = argb == null) { onChange(null) }
            FillSwatches.take(if (clearable) 6 else 7).forEach { swatch ->
                Swatch(argb = swatch, selected = argb == swatch) { onChange(swatch) }
            }
            // The way to a color that is not on the row. Shows the current one, so it doubles as the readout.
            //
            // **It opens the picker elsewhere rather than unfolding it here**, which is the whole of
            // `StudioColorPickerHost`: a saturation panel inside this scrolling section filled it and swallowed
            // every drag over it, so the section could not be scrolled past the control the user had just opened.
            Box(
                modifier = Modifier
                    // The swatches' own side, so the row lines up. It takes no selection ring: it is a way *to* a
                    // color rather than one of the choices, and nothing is ever "on" it.
                    .size(ColorSwatchSide)
                    .clip(CircleShape)
                    .background(argb?.let { Color(it) } ?: Color.Transparent)
                    .border(width = 1.dp, color = Color.White.copy(0.3f), shape = CircleShape)
                    .clickable {
                        // Black when there is nothing yet: the picker has to start somewhere, and it is the one
                        // value a user reading the panel will not mistake for a color that was already chosen.
                        picker.open(argb ?: 0xFF000000.toInt(), onChange)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = StudioContentColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * One color dot, or — for a null [argb] — the **no color** one.
 *
 * **That one is marked rather than merely hollow**, which is the fix for a real misreading. It was transparent
 * inside a faint ring, and transparent over the panel's own dark glass is a *grey dot*: the row read as seven
 * colors of which the first happened to be dim, and picking it looked like picking a color rather than declining
 * one. Nothing about the shape said "none".
 *
 * The mark is the shape chooser's own `Block`, which already answers this exact question one tool over — its first
 * tile is "no shape" and carries the same glyph. Reusing it means "none" looks the same wherever the studio offers
 * it, rather than each chooser inventing a way to say it.
 *
 * The glyph is also the only thing here a screen reader can name: a color dot has no honest description beyond its
 * value, where this one does.
 *
 * ### Selecting shrinks the color and spawns a ring in the space it gives up
 *
 * `StudioLayerRail`'s treatment exactly, and it is here for the reason it is there. The ring used to be drawn *over*
 * the swatch at its own bounds, which fails on precisely the swatches this row is made of: a white ring on the white
 * swatch is no ring at all, and on black it doubled as the edge every swatch already had, so "which one is chosen"
 * was answered by a one-dp difference in a border. Inset, the ring has ground of its own that no color can paint on.
 *
 * **The dot stays [ColorSwatchSide] whatever its state**, so the row never reflows and no neighbour moves when the
 * selection does — the ring is drawn in space the color gives up, not in space the swatch takes. That is the whole
 * of what "same size selected or not" costs: one animated padding.
 *
 * The faint ring stays on the *color* rather than moving to the bounds, because it is doing a different job — it is
 * the swatch's own edge, which a near-black or near-white fill needs against the glass whether or not it is chosen.
 *
 * Clamped for [LayerTile]'s reason, and it is a crash rather than a wobble: the spatial spec is a **spring**, so
 * deselecting settles on zero from below and an unclamped inset goes negative for a few frames — `Modifier.padding`
 * throws on that, on a plain tap, and only on the swatch being *left*.
 */
@Composable
private fun Swatch(argb: Int?, selected: Boolean, onClick: () -> Unit) {
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchSelection",
    )
    val progress = selection.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(ColorSwatchSide)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(ColorSwatchBorder, StudioContentColor.copy(alpha = progress), CircleShape)
            .padding(ColorSwatchInset * progress)
            .clip(CircleShape)
            .background(if (argb == null) Color.Transparent else Color(argb))
            .border(1.dp, Color.White.copy(0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (argb == null) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "No color",
                // Dimmed until chosen, like the shape grid's own: it is a mark saying what the slot is, not a
                // picture of something, so at rest it should not read as loudly as a color beside it.
                tint = StudioContentColor.copy(alpha = if (selected) 1f else 0.6f),
                modifier = Modifier.size(NoColorGlyph),
            )
        }
    }
}

/**
 * The "none" mark, filling nearly the whole dot — short of it only by enough that the faint ring still reads as the
 * swatch's edge rather than as part of the glyph.
 *
 * **It is bounded by the dot rather than sized against it**, which matters once the swatch is selected: the color
 * shrinks by [ColorSwatchInset] to make room for the ring, and this asks for more than the space left. `Modifier.size`
 * coerces into the constraints it is given, so the glyph shrinks with the circle instead of spilling out of it —
 * which is the behaviour wanted anyway, the mark being part of the content that gives up room to the ring.
 */
private val NoColorGlyph = 24.dp

/**
 * The swatch, and the gap its selection ring needs.
 *
 * [ColorSwatchInset] is deliberately wider than [ColorSwatchBorder], for the layer tile's reason: a border draws
 * *inward* from the bounds, so an inset no larger than it would leave the ring cropping the color it marks.
 *
 * [ColorSwatchSide] is what the picker button beside the row is sized to as well, so the two line up — it is one row.
 *
 * `Color`-prefixed because `BackgroundCycleButton` already owns a `SwatchSide` for the backdrop tile, and top-level
 * names share a package here whether or not they are private.
 */
private val ColorSwatchSide = 28.dp
private val ColorSwatchBorder = 2.dp
private val ColorSwatchInset = 4.dp

/**
 * The quick-pick palette behind every [ColorField] — neutrals, then hues, with the picker for everything else.
 *
 * **Material 3 tonal values rather than the saturated primaries this used to hold.** Those were Material *2*'s 600
 * level (`E53935`, `1E88E5`, `43A047`, `FDD835`) — the sRGB primaries barely darkened, which is exactly the look M3
 * replaced: a plate that loud competes with the artwork sitting on it instead of backing it. These are **tone 40** of
 * an M3 tonal palette, the level the `primary` role takes in a light scheme — deep enough to carry a white or
 * monochrome glyph, low enough in chroma to read as a surface. `6750A4` is M3's own baseline primary.
 *
 * **They are literals because the launcher's own scheme cannot supply them, and that is a trap worth stating.**
 * `MaterialTheme.colorScheme` here is the **monochrome** bridge (see `MorphicColors.toM3ColorScheme`), so reaching for
 * `colorScheme.primary` to get "the M3 purple" returns gray — correctly, since our chrome is grayscale so the
 * wallpaper and the icons carry the color.
 *
 * **A red among them does not breach that palette rule**, which reserves red for `error`: the rule is about *chrome*,
 * and these are content a user paints an icon with — the same exception the backdrop effects take in carrying the
 * wallpaper's hue.
 *
 * Ordered neutrals-first because the row is trimmed from the end: seven fit beside the picker, six when a "no color"
 * swatch takes the first slot, so the last entry is the one a tint's row drops.
 */
private val FillSwatches = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF79747E.toInt(),
    0xFF6750A4.toInt(), 0xFF415F91.toInt(), 0xFF386A20.toInt(),
    0xFF8F4C38.toInt(),
)
