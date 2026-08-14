package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                    .size(28.dp)
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

/** One color dot. A null [argb] is the "no tint" dot, drawn hollow. */
@Composable
private fun Swatch(argb: Int?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (argb == null) Color.Transparent else Color(argb))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) StudioContentColor else Color.White.copy(0.3f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

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
