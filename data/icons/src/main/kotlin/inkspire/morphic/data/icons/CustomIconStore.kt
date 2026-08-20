package inkspire.morphic.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.core.graphics.createBitmap
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The images a user imports as icon layers: decoding them, writing them, and — the part that is easy to skip —
 * removing the ones nothing refers to any more.
 *
 * ## Decode and write are separate, and that is the whole file-lifecycle design
 *
 * L1 wrote the file at the moment of picking, and recorded the consequence in its own plan: abandoning the edit
 * without saving left an orphan PNG in app storage that nothing would ever clean up or show. It accepted that.
 *
 * Here [decode] and [write] are two steps. The studio decodes on pick and previews the result **from memory**, so
 * an image the user is still deciding about exists nowhere on disk; only Save writes. Backing out leaves nothing
 * behind because nothing was ever created. The path is reserved up front ([reservePath]) so the recipe being
 * edited can refer to the image before it exists, which is what lets the preview and the eventual file agree
 * without rewriting the recipe at save time.
 *
 * ## Squared on the way in
 *
 * A layer is drawn into a square box, so a non-square image would stretch. [decode] fits it inside a transparent
 * square instead — which also means **no crop screen**, unlike L1. A layer already has offset, zoom and rotation;
 * a crop step would be a second way to do the same thing, and a destructive one, where the transform can be
 * changed later or undone.
 */
class CustomIconStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {

    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /** A path this store will accept a [write] at. Nothing is created — the file appears only when written. */
    fun reservePath(): String = File(directory, "${UUID.randomUUID()}.png").absolutePath

    /**
     * Reads [uri] and returns it fitted, aspect preserved, inside a transparent [ImageSize] square — or `null` if
     * it cannot be read.
     *
     * Sampled during decode rather than after, so a 12-megapixel photo never becomes a 48 MB bitmap on its way to
     * being a 512-pixel icon layer.
     */
    suspend fun decode(uri: Uri): Bitmap? = withContext(dispatchers.io) {
        runCatching {
            val source = decodeSampled(uri) ?: return@runCatching null
            squared(source).also { if (it !== source) source.recycle() }
        }.onFailure { Timber.w(it, "Could not read the picked image") }.getOrNull()
    }

    /** The stored image at [path], or `null` if it is not there — which a recipe outliving its file can produce. */
    suspend fun read(path: String): Bitmap? = withContext(dispatchers.io) {
        runCatching { BitmapFactory.decodeFile(path) }
            .onFailure { Timber.w(it, "Could not read custom icon layer %s", path) }
            .getOrNull()
    }

    /** Writes [bitmap] to [path] as a PNG. Returns false if it could not be written. */
    suspend fun write(path: String, bitmap: Bitmap): Boolean = withContext(dispatchers.io) {
        runCatching {
            File(path).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.onFailure { Timber.w(it, "Could not write custom icon layer %s", path) }.getOrDefault(false)
    }

    /**
     * Deletes every stored image not in [referenced].
     *
     * **A sweep rather than a delete per action**, which is the other half of not leaking. Per-action deletes have
     * to be right at every site that can drop a layer — remove, reset, undo past a pick, replacing an image, a
     * whole recipe going away with an uninstalled app — and missing one is invisible. Asking "what does anything
     * still refer to?" is one question with one answer, and it cleans up after the sites that were missed as well
     * as the ones that were not. L1 deleted per action and had at least one hole it knew about.
     */
    suspend fun retainOnly(referenced: Set<String>) = withContext(dispatchers.io) {
        directory.listFiles().orEmpty()
            .filterNot { it.absolutePath in referenced }
            .forEach { file ->
                if (!file.delete()) Timber.w("Could not delete orphaned icon layer %s", file.name)
            }
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight))
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /** The largest power-of-two reduction that still leaves at least [ImageSize] on the longest edge. */
    private fun sampleSizeFor(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= ImageSize) sample *= 2
        return sample
    }

    /** [source] centered in a transparent [ImageSize] square, scaled to fit; returned as-is if it already fits. */
    private fun squared(source: Bitmap): Bitmap {
        val scale = ImageSize.toFloat() / max(source.width, source.height)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)

        val output = createBitmap(ImageSize, ImageSize)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        Canvas(output).drawBitmap(scaled, (ImageSize - width) / 2f, (ImageSize - height) / 2f, null)
        if (scaled !== source) scaled.recycle()
        return output
    }

    private companion object {
        const val DIRECTORY = "icon_layers"

        /**
         * The square every imported image is stored at.
         *
         * Comfortably above the largest size an icon bakes at, so a layer never looks soft, and small enough that
         * a handful of them are not worth thinking about on disk.
         */
        const val ImageSize = 512
    }
}
