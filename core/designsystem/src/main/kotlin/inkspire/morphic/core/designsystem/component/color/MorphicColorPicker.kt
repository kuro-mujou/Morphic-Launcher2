package inkspire.morphic.core.designsystem.component.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import android.graphics.Color as AndroidColor

/** The rainbow the hue bar is drawn from, and the same stops the saturation panel's tint is taken from. */
private val HueStops = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/**
 * Pick a color: a saturation/value panel over a hue bar.
 *
 * **Deliberately no alpha channel.** Every color this launcher lets a user pick sits somewhere that already has
 * its own opacity — an icon layer has one, a gradient has a strength — and `LayerEffect.Color` says outright that
 * a tint's alpha is ignored because "two ways to set one thing is one too many". An alpha slider here would be
 * that second way, and a color that silently loses its transparency is worse than one that never offered it.
 *
 * **Hue is kept as state rather than re-derived from [argb] each time**, which is not an optimization but a
 * correctness point: hue is undefined at black, white and every pure gray, so a picker that recomputed it would
 * lose track of where the user was the moment they dragged the panel into a corner — and the hue bar would jump
 * to red under their finger. The conversion runs the other way instead, and only re-seeds when [argb] is changed
 * from outside.
 *
 * @param argb the current color. Its alpha is ignored on the way in and always opaque on the way out.
 * @param palettes the curated swatch sets offered below the hue bar. Defaulted to [ColorPalettes.all] so the picker
 *   works out of the box; a parameter rather than a hard reference so this generic component stays one — a caller
 *   with its own sets can pass them, and one that wants none passes an empty list.
 */
@Composable
fun MorphicColorPicker(
    argb: Int,
    onArgbChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    palettes: List<ColorPalette> = ColorPalettes.all,
) {
    val colors = LocalMorphicColors.current
    val currentOnChange by rememberUpdatedState(onArgbChange)

    var hsv by remember { mutableStateOf(argb.toHsv()) }
    // Re-seed only on a change this picker did not make, so dragging never fights the value coming back in — the
    // same in-and-out shape the sliders use, and the reason the comparison is on the *color* rather than the HSV.
    LaunchedEffect(argb) { if (hsv.toArgb() != argb) hsv = argb.toHsv() }

    fun update(next: FloatArray) {
        hsv = next
        currentOnChange(next.toArgb())
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SaturationValuePanel(
            hue = hsv[0],
            saturation = hsv[1],
            value = hsv[2],
            onChange = { s, v -> update(floatArrayOf(hsv[0], s, v)) },
            outline = colors.outline,
        )
        HueBar(
            hue = hsv[0],
            onChange = { hue -> update(floatArrayOf(hue, hsv[1], hsv[2])) },
            outline = colors.outline,
        )
        // A picked swatch goes in the same door a drag does — `update` re-seeds the hue and panel from it, so the
        // knobs jump to the color the user tapped rather than the picker showing one thing and reporting another.
        if (palettes.isNotEmpty()) {
            PaletteRibbon(palettes = palettes, onPick = { update(it.toHsv()) }, outline = colors.outline)
        }
    }
}

/**
 * The curated palettes as one horizontally-scrolling row of pills, each pill a palette's colors packed side by side
 * and each segment a tap that picks that color.
 *
 * **Scrolls sideways rather than stacking**, which is the placement decision: this sits inside a bottom-anchored
 * panel whose height is already near the tool panel's, so a wrapping grid of a dozen palettes would push the hue bar
 * and the icon it is judged against off the top. One short row that scrolls keeps the whole picker the height it was.
 *
 * **No labels in this first cut.** A pill reads as "a set that goes together" on its own, and a name under each
 * would double the row's height for identification the colors mostly carry themselves. A named, filterable palette
 * list is the natural next step, and where the community sets would live.
 */
@Composable
private fun PaletteRibbon(palettes: List<ColorPalette>, onPick: (Int) -> Unit, outline: Color) {
    val pill = RoundedCornerShape(7.dp)
    // Lazy, because the bank runs to a couple of hundred palettes now — a plain scrolling Row would compose every
    // pill and every swatch up front the moment the picker opened.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(palettes, key = { it.name }) { palette ->
            Row(
                modifier = Modifier
                    .clip(pill)
                    .border(1.dp, outline.copy(alpha = 0.3f), pill),
            ) {
                palette.colors.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 28.dp)
                            .background(Color(swatch))
                            .clickable { onPick(swatch) },
                    )
                }
            }
        }
    }
}

/** Saturation left→right, value bottom→top, over the current hue. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    outline: Color,
) {
    val hueColor = Color(floatArrayOf(hue, 1f, 1f).toArgb())
    val current by rememberUpdatedState(onChange)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                fun report(position: Offset) = current(
                    (position.x / size.width).coerceIn(0f, 1f),
                    1f - (position.y / size.height).coerceIn(0f, 1f),
                )
                detectTapGestures { report(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    current(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        val knob = Offset(saturation * size.width, (1f - value) * size.height)
        // Two rings, dark under light, so the knob stays visible over every part of the panel — including the
        // white corner, where a single white ring would vanish.
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 9.dp.toPx(), center = knob, style = KnobStroke)
        drawCircle(outline, radius = 8.dp.toPx(), center = knob, style = KnobStroke)
    }
}

/** The hue track, with the same two-ring knob. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit, outline: Color) {
    val current by rememberUpdatedState(onChange)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(HueStops))
            .pointerInput(Unit) {
                detectTapGestures { current((it.x / size.width).coerceIn(0f, 1f) * 360f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    current((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        val knob = Offset((hue / 360f) * size.width, size.height / 2f)
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 11.dp.toPx(), center = knob, style = KnobStroke)
        drawCircle(outline, radius = 10.dp.toPx(), center = knob, style = KnobStroke)
    }
}

private val KnobStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)

/** `[hue, saturation, value]` for this packed color; alpha is dropped. */
private fun Int.toHsv(): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(this, it) }

/** The opaque packed color for `[hue, saturation, value]`. */
private fun FloatArray.toArgb(): Int = AndroidColor.HSVToColor(this)
