package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrain
import java.text.BreakIterator

/**
 * A single-line label on a picture, shortened with an ellipsis, **whose glow is never clipped**.
 *
 * Compose's `Text` cannot do this. Text that is ellipsized, or squeezed below its own height, is painted under a clip
 * to its box — `AndroidParagraph.paint` for an ellipsis, `TextStringSimpleNode.draw` for a squeeze — and the glow is
 * part of that paint, so it is cut into a hard rectangle. So the ellipsis is applied to the *string*: the platform's
 * layout still decides where it falls, and the shortened string plus `…` is then laid out as one line that fits and
 * painted with no clip at all. The box wraps the text and is reported within the constraints, while the paint may
 * overhang it — by the glow always, and by a pixel of line height where a tight cell rounds short.
 *
 * Color and glow are applied at draw time, so a [SpotTheme] cross-fade redraws the label without laying it out again.
 *
 * @param glow whether to draw [inkGlow]. Off for text on a fill of its own, for [inkGlow]'s reason.
 */
@Composable
fun InkLabel(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    glow: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val shadow = if (glow) inkGlow() else null
    // State rather than a plain field, so a new layout invalidates the draw even when the box keeps its size.
    var line by remember { mutableStateOf<TextLayoutResult?>(null) }
    val policy = remember(measurer, text, style, textAlign) {
        MeasurePolicy { _, constraints ->
            val result = measurer.fitLine(text, style.copy(textAlign = textAlign), constraints)
            line = result
            val size = constraints.constrain(result.size)
            layout(size.width, size.height) {}
        }
    }
    Layout(
        modifier = modifier
            .semantics { this.text = AnnotatedString(text) }
            .drawBehind { line?.let { drawText(it, color = color, shadow = shadow) } },
        measurePolicy = policy,
    )
}

/**
 * [text] as one line no wider than [constraints] allow: whole if it fits, otherwise cut where the platform would put
 * its ellipsis, with `…` appended. Laid out without overflow handling, so painting it applies no clip.
 */
private fun TextMeasurer.fitLine(text: String, style: TextStyle, constraints: Constraints): TextLayoutResult {
    val maxWidth = constraints.maxWidth
    val minWidth = constraints.minWidth
    fun single(value: String) = measure(
        text = value,
        style = style,
        overflow = TextOverflow.Visible,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(minWidth = minWidth, maxWidth = Constraints.Infinity),
    )

    val whole = single(text)
    if (!constraints.hasBoundedWidth || whole.size.width <= maxWidth) return whole

    val ellipsized = measure(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth),
    )
    var end = ellipsized.getLineEnd(0, visibleEnd = true)
    // The platform reserved room for its own ellipsis, but trimming and re-shaping can still come out a few px wide,
    // so graphemes are dropped until it fits — never a code unit, which could split a surrogate pair.
    val graphemes = BreakIterator.getCharacterInstance().apply { setText(text) }
    while (true) {
        val candidate = single(text.substring(0, end).trimEnd() + "…")
        if (candidate.size.width <= maxWidth || end == 0) return candidate
        end = graphemes.preceding(end).coerceAtLeast(0)
    }
}
