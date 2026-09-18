package inkspire.morphic.core.widget

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.WidgetExpression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draws one [WidgetSource] into the box its layer was given. */
@Composable
internal fun WidgetSourceContent(source: WidgetSource, data: ScriptData) {
    when (source) {
        is WidgetSource.Text -> WidgetText(source, data)
        is WidgetSource.Shape -> WidgetShape(source)
        is WidgetSource.Image -> WidgetImage(source)
        is WidgetSource.Overlap -> WidgetOverlap(source.layers, data)
    }
}

/**
 * The text's formulas evaluated against [data]. A formula that fails shows as typed — `WidgetExpression`'s own rule —
 * so a broken widget reads as its source rather than going blank.
 */
@Composable
private fun WidgetText(source: WidgetSource.Text, data: ScriptData) {
    val expression = remember(source.text) { WidgetExpression.parse(source.text) }
    val text = remember(expression, data) { expression.evaluate(data).text }
    // dp through toSp, so the system font scale does not enlarge one line of a composed design.
    val size = with(LocalDensity.current) { source.size.dp.toSp() }
    BasicText(
        text = text,
        style = TextStyle(
            color = Color(source.color),
            fontSize = size,
            fontFamily = when (source.font) {
                WidgetSource.Text.Font.SANS -> FontFamily.SansSerif
                WidgetSource.Text.Font.SERIF -> FontFamily.Serif
                WidgetSource.Text.Font.MONO -> FontFamily.Monospace
            },
            fontWeight = FontWeight(source.weight.coerceIn(1, 1000)),
            textAlign = when (source.align) {
                WidgetSource.Text.Align.LEFT -> TextAlign.Left
                WidgetSource.Text.Align.CENTER -> TextAlign.Center
                WidgetSource.Text.Align.RIGHT -> TextAlign.Right
            },
        ),
        maxLines = source.maxLines.coerceAtLeast(1),
        overflow = TextOverflow.Ellipsis,
    )
}

/** Fills exactly the layer's box — a shape has no size of its own, so with a content extent it draws nothing. */
@Composable
private fun WidgetShape(source: WidgetSource.Shape) {
    val shape = when (source.kind) {
        WidgetSource.Shape.Kind.RECTANGLE -> RoundedCornerShape(source.cornerRadius.coerceAtLeast(0f).dp)
        WidgetSource.Shape.Kind.OVAL -> CircleShape
    }
    Spacer(Modifier.background(Color(source.color), shape))
}

/**
 * The picture at the source's path, decoded off the main thread; nothing is drawn until it arrives, or at all if the
 * file is missing.
 *
 * Decoded at full resolution. That is only safe because whatever imports a picture is expected to store a copy sized
 * for a widget — an import that keeps the camera's original would cost its whole size in memory on every render.
 */
@Composable
private fun WidgetImage(source: WidgetSource.Image) {
    val bitmap by produceState<ImageBitmap?>(null, source.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(source.path)?.asImageBitmap() }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = when (source.fit) {
                WidgetSource.Image.Fit.CROP -> ContentScale.Crop
                WidgetSource.Image.Fit.FIT -> ContentScale.Fit
            },
        )
    }
}
