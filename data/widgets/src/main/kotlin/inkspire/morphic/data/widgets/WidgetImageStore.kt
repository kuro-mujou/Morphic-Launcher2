package inkspire.morphic.data.widgets

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.common.image.decodeSampled
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pictures placed in widgets: each imported once as a widget-sized copy, and removed when nothing uses it.
 *
 * **Written the moment it is picked**, unlike an icon layer's, because the widget studio has no Save — a picture is
 * part of the widget from the tap that adds it. What that costs is a file for every picture later removed or undone,
 * which is why the other half is [retainOnly]: a sweep over what every widget still refers to, rather than a delete at
 * each of the places a picture can stop being used.
 *
 * **Aspect is kept.** A widget picture is laid out by its layer's extents with a crop or a fit, so squaring it on the
 * way in, as an icon layer is, would add margins the person never asked for.
 */
class WidgetImageStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {

    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Stores a widget-sized copy of the picture at [uri] and returns its path, or null when it cannot be read or
     * written.
     */
    suspend fun import(uri: Uri): String? = withContext(dispatchers.io) {
        runCatching {
            val source = decodeSampled(context, uri, atLeast = LongestEdge) ?: return@runCatching null
            val fitted = fitted(source).also { if (it !== source) source.recycle() }
            val file = File(directory, "${UUID.randomUUID()}.png")
            file.outputStream().use { fitted.compress(Bitmap.CompressFormat.PNG, Lossless, it) }
            fitted.recycle()
            file.absolutePath
        }.onFailure { Timber.w(it, "Could not import a widget picture") }.getOrNull()
    }

    /** Deletes every stored picture whose path is not in [referenced]. */
    suspend fun retainOnly(referenced: Set<String>) = withContext(dispatchers.io) {
        directory.listFiles().orEmpty()
            .filterNot { it.absolutePath in referenced }
            .forEach { file -> if (!file.delete()) Timber.w("Could not delete orphaned widget picture %s", file.name) }
    }

    /** [source] scaled down, aspect kept, so its longest edge is at most [LongestEdge]; as-is if it already is. */
    private fun fitted(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= LongestEdge) return source
        val scale = LongestEdge.toFloat() / longest
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private companion object {
        const val DIRECTORY = "widget_images"

        /** PNG ignores the quality argument; stated so the call does not read as a choice of 100%. */
        const val Lossless = 100

        /**
         * The longest edge a stored picture keeps: a phone screen's width in pixels, since a widget is at most that
         * wide, and so what the renderer — which decodes a picture whole — can afford to hold per layer.
         */
        const val LongestEdge = 1080
    }
}
