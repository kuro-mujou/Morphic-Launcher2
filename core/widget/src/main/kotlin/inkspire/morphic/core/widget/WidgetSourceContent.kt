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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.widget.WidgetGlobals
import inkspire.morphic.core.model.widget.WidgetSource
import inkspire.morphic.core.widgetscript.ScriptData
import inkspire.morphic.core.widgetscript.WidgetExpression
import inkspire.morphic.core.widgetscript.withGlobals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draws one [WidgetSource] into the box its layer was given. */
@Composable
internal fun WidgetSourceContent(source: WidgetSource, data: ScriptData, globals: WidgetGlobals) {
    when (source) {
        is WidgetSource.Text -> WidgetText(source, data, globals)
        is WidgetSource.Shape -> WidgetShape(source, globals)
        is WidgetSource.Image -> WidgetImage(source)
        is WidgetSource.Progress -> WidgetProgress(source, data, globals)
        is WidgetSource.Overlap -> WidgetGroup(source, data, globals)
        is WidgetSource.Stack -> WidgetStack(source, data, globals)
    }
}

/**
 * A group, drawn in its own scope when it declares globals: its bindings and its formulas' `gv` read those first.
 */
@Composable
private fun WidgetGroup(source: WidgetSource.Overlap, data: ScriptData, globals: WidgetGlobals) {
    val inner = remember(source.globals, globals) { globals.inside(source) }
    val scoped = remember(data, inner) { if (inner === globals) data else data.withGlobals(inner.asScriptValues()) }
    WidgetOverlap(source.layers, scoped, inner)
}

/**
 * The text's formulas evaluated against [data]. A formula that fails shows as typed — `WidgetExpression`'s own rule —
 * so a broken widget reads as its source rather than going blank.
 */
@Composable
private fun WidgetText(source: WidgetSource.Text, data: ScriptData, globals: WidgetGlobals) {
    val expression = remember(source.text) { WidgetExpression.parse(source.text) }
    val text = remember(expression, data) { expression.evaluate(data).text }
    // dp through toSp, so the system font scale does not enlarge one line of a composed design.
    val size = with(LocalDensity.current) { globals.number(source.sizeGlobal, source.size).dp.toSp() }
    BasicText(
        text = text,
        style = TextStyle(
            color = Color(globals.color(source.colorGlobal, source.color)),
            fontSize = size,
            fontFamily = when (globals.font(source.fontGlobal, source.font)) {
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

/**
 * Fills exactly the layer's box — a shape has no size of its own, so with a content extent it draws nothing.
 *
 * With a picture, the picture fills the shape cropped and the color is washed over it at the picture's tint. Until the
 * picture has decoded, and if its file is gone, the shape draws as it would without one.
 */
@Composable
private fun WidgetShape(source: WidgetSource.Shape, globals: WidgetGlobals) {
    val shape = when (source.kind) {
        WidgetSource.Shape.Kind.RECTANGLE ->
            RoundedCornerShape(globals.number(source.cornerRadiusGlobal, source.cornerRadius).coerceAtLeast(0f).dp)
        WidgetSource.Shape.Kind.OVAL -> CircleShape
    }
    val color = Color(globals.color(source.colorGlobal, source.color))
    val picture = globals.picture(source.pictureGlobal, source.picture)
    val bitmap = rememberPicture(picture?.path)
    val painter = remember(bitmap) { bitmap?.let(::BitmapPainter) }
    Spacer(
        if (picture == null || painter == null) {
            Modifier.background(color, shape)
        } else {
            // `paint` draws the picture before its content, so the wash after it in the chain lands on top.
            Modifier
                .clip(shape)
                .paint(painter, sizeToIntrinsics = false, contentScale = ContentScale.Crop)
                .background(color.copy(alpha = picture.tint.coerceIn(0f, 1f)))
        },
    )
}

/**
 * The picture at the source's path, decoded off the main thread; nothing is drawn until it arrives, or at all if the
 * file is missing.
 */
@Composable
private fun WidgetImage(source: WidgetSource.Image) {
    rememberPicture(source.path)?.let {
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

/**
 * The stored picture at [path], decoded off the main thread — null while it decodes, when the file is missing, and
 * for no path.
 *
 * Decoded at full resolution. That is only safe because `WidgetImageStore` keeps a copy sized for a widget — a picture
 * stored as the camera took it would cost its whole size in memory on every render.
 */
@Composable
private fun rememberPicture(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    return bitmap
}
