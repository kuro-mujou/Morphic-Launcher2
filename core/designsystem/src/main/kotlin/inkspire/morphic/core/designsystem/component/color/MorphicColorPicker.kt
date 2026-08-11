package inkspire.morphic.core.designsystem.component.color

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** The rainbow the hue bar is drawn from, and the same stops the saturation panel's tint is taken from. */
private val HueStops = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/**
 * Pick a colour: a saturation/value panel over a hue bar.
 *
 * **Deliberately no alpha channel.** Every colour this launcher lets a user pick sits somewhere that already has
 * its own opacity — an icon layer has one, a gradient has a strength — and `LayerEffect.Color` says outright that
 * a tint's alpha is ignored because "two ways to set one thing is one too many". An alpha slider here would be
 * that second way, and a colour that silently loses its transparency is worse than one that never offered it.
 *
 * **Hue is kept as state rather than re-derived from [argb] each time**, which is not an optimisation but a
 * correctness point: hue is undefined at black, white and every pure grey, so a picker that recomputed it would
 * lose track of where the user was the moment they dragged the panel into a corner — and the hue bar would jump
 * to red under their finger. The conversion runs the other way instead, and only re-seeds when [argb] is changed
 * from outside.
 *
 * @param argb the current colour. Its alpha is ignored on the way in and always opaque on the way out.
 */
@Composable
fun MorphicColorPicker(
    argb: Int,
    onArgbChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val currentOnChange by rememberUpdatedState(onArgbChange)

    var hsv by remember { mutableStateOf(argb.toHsv()) }
    // Re-seed only on a change this picker did not make, so dragging never fights the value coming back in — the
    // same in-and-out shape the sliders use, and the reason the comparison is on the *colour* rather than the HSV.
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

/** `[hue, saturation, value]` for this packed colour; alpha is dropped. */
private fun Int.toHsv(): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(this, it) }

/** The opaque packed colour for `[hue, saturation, value]`. */
private fun FloatArray.toArgb(): Int = AndroidColor.HSVToColor(this)
