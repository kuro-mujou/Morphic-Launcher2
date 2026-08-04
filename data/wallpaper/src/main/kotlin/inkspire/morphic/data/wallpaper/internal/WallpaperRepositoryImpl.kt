package inkspire.morphic.data.wallpaper.internal

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.data.wallpaper.WallpaperFiles
import inkspire.morphic.data.wallpaper.WallpaperImage
import inkspire.morphic.data.wallpaper.WallpaperRepository
import inkspire.morphic.data.wallpaper.WallpaperState
import inkspire.morphic.data.wallpaper.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * This module's own store — **one file, one key, one blob**, the same shape a settings slice has.
 *
 * Its own rather than a slice of `launcher_settings` because what it holds is not a preference: a path to a file we
 * wrote and the id the system gave the wallpaper we set is bookkeeping, and the settings port's S0 refused exactly that
 * on the way in. Keeping it here also means the module is self-contained — nothing else has to be running for a
 * wallpaper to be read.
 */
private val Context.wallpaperDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_wallpaper")

private val StateKey = stringPreferencesKey("wallpaper_state")

/**
 * Default [WallpaperRepository]: a JSON blob for what was chosen, a JPEG under `filesDir/wallpaper` for the image
 * itself, and `WallpaperManager` for putting it on the system.
 *
 * **Every read and write goes through one `edit`**, which is the one thing L1's version got structurally wrong: its
 * `WallpaperRepositoryImpl` read the whole settings object, modified it, and wrote it back *outside* any transaction —
 * a lost update whenever two of its own operations overlapped (picking an image while an apply was still finishing).
 * `updateState` below does the read-modify-write *inside* `edit`, where DataStore serialises it.
 *
 * **All of it on the IO dispatcher**, because all of it is files: decoding a picked image, writing a JPEG, and handing a
 * bitmap to `WallpaperManager` are each capable of taking hundreds of milliseconds.
 */
internal class WallpaperRepositoryImpl(
    context: Context,
    private val dispatchers: AppDispatchers,
) : WallpaperRepository {

    private val appContext = context.applicationContext
    private val store = appContext.wallpaperDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override val wallpaper: Flow<WallpaperState> = store.data
        .map { prefs -> decodeState(prefs[StateKey]) }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    override suspend fun decodePreview(uri: Uri): Bitmap? = withContext(dispatchers.io) {
        decodeSampled(uri, PREVIEW_CAP)
    }

    override suspend fun setImage(uri: Uri, screenWidth: Int, screenHeight: Int): Unit =
        withContext(dispatchers.io) {
            val source = decodeSampled(uri, SOURCE_CAP) ?: run {
                Timber.w("Unable to decode wallpaper image from %s", uri)
                return@withContext
            }
            val scaled = centreCropTo(source, screenWidth, screenHeight)
            val file = writeImage(scaled)
            updateState {
                // The id is reset, not kept: this is a different image from the one we applied, so the section must
                // offer "Apply" again rather than claiming the new pick is already on the system.
                WallpaperState(
                    image = WallpaperImage(file.absolutePath, scaled.width, scaled.height),
                    appliedSystemId = 0,
                )
            }
        }

    override suspend fun apply(target: WallpaperTarget): Unit = withContext(dispatchers.io) {
        val image = wallpaper.first().image ?: return@withContext
        val bitmap = decodeFile(image.path) ?: run {
            Timber.w("Stored wallpaper file is missing: %s", image.path)
            return@withContext
        }
        val manager = WallpaperManager.getInstance(appContext)
        val which = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        // `allowBackup = true` and no crop hint: the bitmap is already the screen's size and aspect, so letting the
        // system crop again would only undo the scaling done on the way in.
        val set = runCatching { manager.setBitmap(bitmap, null, true, which) }
            .onFailure { Timber.w(it, "Failed to set the system wallpaper") }
            .isSuccess
        if (!set) return@withContext
        val systemId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        updateState { it.copy(appliedSystemId = systemId) }
    }

    override suspend fun loadImage(): Bitmap? = withContext(dispatchers.io) {
        wallpaper.first().image?.let { decodeFile(it.path) }
    }

    /** Read-modify-write **inside** the transaction, which is the fix for L1's lost update. */
    private suspend fun updateState(transform: (WallpaperState) -> WallpaperState) {
        store.edit { prefs ->
            val current = decodeState(prefs[StateKey])
            prefs[StateKey] = json.encodeToString(WallpaperState.serializer(), transform(current))
        }
    }

    /**
     * An unreadable blob is **reported and treated as absent**, not silently swallowed.
     *
     * The same rule `data:settings` follows: a decode failure means either a bug in this module or a hand-edited file,
     * and a user whose wallpaper choice quietly disappeared deserves the log line that explains it.
     */
    private fun decodeState(raw: String?): WallpaperState {
        if (raw == null) return WallpaperState.Default
        return runCatching { json.decodeFromString(WallpaperState.serializer(), raw) }
            .onFailure { Timber.w(it, "Unreadable wallpaper state; treating as unset") }
            .getOrDefault(WallpaperState.Default)
    }

    /**
     * The centre of [source], at [outWidth] × [outHeight]'s aspect ratio, scaled to that size.
     *
     * The aspect is taken first and the scale second, so nothing is stretched — the same two steps as L1's
     * `cropAndScale`, with the rectangle chosen here instead of supplied. When the crop screen lands it supplies one and
     * this becomes the fallback for "the user did not adjust it".
     */
    private fun centreCropTo(source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val targetW = outWidth.coerceAtLeast(1)
        val targetH = outHeight.coerceAtLeast(1)
        val scale = min(source.width.toFloat() / targetW, source.height.toFloat() / targetH)
        val cropW = (targetW * scale).roundToInt().coerceIn(1, source.width)
        val cropH = (targetH * scale).roundToInt().coerceIn(1, source.height)
        val left = ((source.width - cropW) / 2).coerceAtLeast(0)
        val top = ((source.height - cropH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
        return if (cropped.width == targetW && cropped.height == targetH) {
            cropped
        } else {
            cropped.scale(targetW, targetH)
        }
    }

    private fun writeImage(bitmap: Bitmap): File {
        val dir = File(appContext.filesDir, WallpaperFiles.DIR).apply { mkdirs() }
        return File(dir, WallpaperFiles.IMAGE).also { file ->
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        }
    }

    private fun decodeFile(path: String): Bitmap? {
        val file = File(path)
        return if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * [uri] decoded with its largest edge at most [maxDimension], via `inSampleSize`.
     *
     * Two passes over the stream, which is what `inJustDecodeBounds` is for: the first reads the header to learn the
     * size, the second decodes at a power-of-two reduction. Decoding a phone camera image whole is tens of megabytes
     * and the reason L1 sampled too.
     */
    private fun decodeSampled(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            .onFailure { Timber.w(it, "Cannot read %s", uri) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private companion object {
        /** Caps on the decode, in px on the largest edge — L1's, which are sized for "preview" and "about to store". */
        const val PREVIEW_CAP = 2048
        const val SOURCE_CAP = 3000

        /** L1's quality, kept: high enough that a gradient does not band, low enough that the file is a few hundred KB. */
        const val JPEG_QUALITY = 92
    }
}
