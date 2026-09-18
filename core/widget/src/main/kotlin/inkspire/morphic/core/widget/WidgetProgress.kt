package inkspire.morphic.core.widget

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.WidgetExpression

/**
 * A [WidgetSource.Progress] filling the layer's box. Like a shape it has no size of its own, so with a content extent
 * it draws nothing.
 */
@Composable
internal fun WidgetProgress(source: WidgetSource.Progress, data: ScriptData, globals: WidgetGlobals) {
    val expression = remember(source.value) { WidgetExpression.parse(source.value) }
    val fraction = remember(expression, data, source.min, source.max) {
        progressFraction(expression.evaluate(data).text, source.min, source.max)
    }
    val color = Color(globals.color(source.colorGlobal, source.color))
    val track = Color(globals.color(source.trackColorGlobal, source.trackColor))
    Spacer(
        Modifier.drawBehind {
            when (source.kind) {
                WidgetSource.Progress.Kind.BAR -> drawBar(fraction, color, track, source.rounded)
                WidgetSource.Progress.Kind.ARC -> drawArc(source, fraction, color, track)
            }
        },
    )
}

/**
 * Where [text] sits between [min] and [max], from 0 to 1. Text that is not a number, and a range with no width, are 0 —
 * an empty track rather than a full one, so a broken formula never reads as a full battery.
 */
internal fun progressFraction(text: String, min: Float, max: Float): Float {
    val value = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return 0f
    if (max <= min) return 0f
    return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
}

/** The track fills the box; the fill is its left share, clipped to the track so a short fill keeps the pill's end. */
private fun DrawScope.drawBar(fraction: Float, color: Color, track: Color, rounded: Boolean) {
    val radius = if (rounded) CornerRadius(size.height / 2) else CornerRadius.Zero
    drawRoundRect(track, cornerRadius = radius)
    if (fraction <= 0f) return
    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
    clipPath(shape) { drawRect(color, size = Size(size.width * fraction, size.height)) }
}

/**
 * The largest circle that fits, inset by half the stroke so the stroke stays inside the box. An empty fill is skipped
 * rather than drawn at zero sweep, which a round cap would still show as a dot.
 */
private fun DrawScope.drawArc(source: WidgetSource.Progress, fraction: Float, color: Color, track: Color) {
    val stroke = source.thickness.coerceAtLeast(0f).dp.toPx()
    val diameter = (size.minDimension - stroke).coerceAtLeast(0f)
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    val arcSize = Size(diameter, diameter)
    val style = Stroke(stroke, cap = if (source.rounded) StrokeCap.Round else StrokeCap.Butt)
    // Compose measures from three o'clock; the model from twelve.
    val start = source.startAngle - 90f
    drawArc(track, start, source.sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    if (fraction > 0f) {
        drawArc(color, start, source.sweep * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    }
}
