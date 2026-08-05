package inkspire.morphic.data.wallpaper.internal

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.graphics.scale
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.wallpaper.NormalizedCropRect
import inkspire.morphic.data.wallpaper.RotatingWallpaperService
import inkspire.morphic.data.wallpaper.WallpaperFiles
import inkspire.morphic.data.wallpaper.WallpaperImage
import inkspire.morphic.data.wallpaper.WallpaperRepository
import inkspire.morphic.data.wallpaper.WallpaperSource
import inkspire.morphic.data.wallpaper.WallpaperState
import inkspire.morphic.data.wallpaper.WallpaperTarget
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

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

    override suspend fun setImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        source: WallpaperSource,
    ): Unit = withContext(dispatchers.io) {
        val decoded = decodeSampled(uri, SOURCE_CAP) ?: run {
            Timber.w("Unable to decode wallpaper image from %s", uri)
            return@withContext
        }
        val scaled = cropAndScale(decoded, crop, outWidth, outHeight)
        val file = writeImage(scaled)
        updateState {
            // The id is reset, not kept: this is a different image from the one we applied, so the section must
            // offer "Apply" again rather than claiming the new pick is already on the system. A capture resets it
            // for a second reason - it can never be applied at all, so any id it inherited would be a claim about
            // an image that is no longer stored.
            WallpaperState(
                image = WallpaperImage(file.absolutePath, scaled.width, scaled.height, source),
                appliedSystemId = 0,
            )
        }
    }

    override suspend fun apply(target: WallpaperTarget): Unit = withContext(dispatchers.io) {
        val image = wallpaper.first().image ?: return@withContext
        // A capture is a picture *of* the wallpaper. Setting it would at best change nothing and at worst re-encode
        // the last capture into the next one; L1 skipped `WallpaperManager` on the same field for the same reason.
        if (image.source == WallpaperSource.CAPTURED) {
            Timber.w("Refusing to apply a captured image: it is a picture of the wallpaper, not a wallpaper")
            return@withContext
        }
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

    override suspend fun setRotatingImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        orientation: Orientation,
    ): Unit = withContext(dispatchers.io) {
        val decoded = decodeSampled(uri, SOURCE_CAP) ?: run {
            Timber.w("Unable to decode rotating wallpaper image from %s", uri)
            return@withContext
        }
        val scaled = cropAndScale(decoded, crop, outWidth, outHeight)
        val file = writeImage(scaled, fileNameFor(orientation))
        val stored = WallpaperImage(file.absolutePath, scaled.width, scaled.height, WallpaperSource.ROTATING)
        // `with` rather than a copy of the whole pair: the other orientation is a separate file that this write has
        // nothing to say about, and losing it to each edit would make a pair impossible to finish assembling.
        updateState { it.copy(rotating = it.rotating.with(orientation, stored)) }
    }

    override suspend fun loadRotatingImage(orientation: Orientation): Bitmap? = withContext(dispatchers.io) {
        wallpaper.first().rotating[orientation]?.let { decodeFile(it.path) }
    }

    override suspend fun isRotatingActive(): Boolean = withContext(dispatchers.io) {
        // Asked rather than remembered — see `WallpaperState`. `wallpaperInfo` is non-null exactly when a live
        // wallpaper is set, and names which, so there is nothing here to fall out of step with the system.
        WallpaperManager.getInstance(appContext).wallpaperInfo?.component == rotatingServiceComponent()
    }

    override fun rotatingServiceComponent(): ComponentName =
        ComponentName(appContext, RotatingWallpaperService::class.java)

    /**
     * A `ContentObserver` on the image collection, turned into a flow - registered on collection and unregistered when
     * the collector goes away, which is what `callbackFlow`'s `awaitClose` is for.
     *
     * **Each change is answered with a query for the newest image**, because an observer says only *that* something
     * changed. The cutoff is taken a couple of seconds back rather than at exactly now: `DATE_ADDED` has second
     * resolution, so a screenshot taken in the same second this is collected would otherwise be missed - L1 took the
     * same two-second slack for the same reason.
     *
     * Duplicate emissions are possible (an observer can fire more than once for one insert, and a pending image
     * becomes non-pending), which is left alone rather than smoothed over: the only collector takes the first
     * emission, so de-duplication here would be a rule for a caller that does not exist.
     */
    override fun newGalleryImages(): Flow<Uri> = callbackFlow {
        val resolver = appContext.contentResolver
        val since = System.currentTimeMillis() / MILLIS_PER_SECOND - GALLERY_SLACK_SECONDS
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                newestImageSince(resolver, since)?.let { trySend(it) }
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(dispatchers.io)

    /**
     * The newest non-pending image added at or after [sinceSeconds], or null when there is none.
     *
     * `IS_PENDING = 0` matters: a screenshot appears in `MediaStore` before its bytes are written, and importing a
     * pending row reads a truncated file. L1 guarded it the same way, gated on the API level where the column arrives;
     * this codebase's `minSdk` is past that, so the branch is gone rather than carried.
     *
     * Failure is null rather than a throw: the query can run without the media permission (a user may revoke it
     * between the ask and the shot), and "no image" is the honest answer to that.
     */
    private fun newestImageSince(resolver: ContentResolver, sinceSeconds: Long): Uri? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = MediaStore.Images.Media.DATE_ADDED + " >= ? AND " + MediaStore.Images.Media.IS_PENDING + " = 0"
        val order = MediaStore.Images.Media.DATE_ADDED + " DESC, " + MediaStore.Images.Media._ID + " DESC"
        return runCatching {
            resolver.query(collection, projection, selection, arrayOf(sinceSeconds.toString()), order)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                ContentUris.withAppendedId(collection, id)
            }
        }.onFailure { Timber.w(it, "Cannot read the image collection") }.getOrNull()
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
     * [crop] of [source], scaled to [outWidth] × [outHeight] — L1's `cropAndScale`, arithmetic and all.
     *
     * **Every bound is clamped into the source, and each edge against the opposite one**, so a rectangle that arrived
     * inverted or out of range yields a small crop rather than an `IllegalArgumentException` out of
     * `Bitmap.createBitmap`. The fractions come from a gesture over a viewport, so "impossible" values are a rounding
     * error away rather than a caller bug, and this is a settings screen with nothing useful to do with a throw.
     *
     * The scale is applied second, and only when it is needed: a crop that already matches the output — the common
     * case, since the crop screen frames against the very viewport it passes as the output size — is stored as it is.
     */
    private fun cropAndScale(source: Bitmap, crop: NormalizedCropRect, outWidth: Int, outHeight: Int): Bitmap {
        val targetW = outWidth.coerceAtLeast(1)
        val targetH = outHeight.coerceAtLeast(1)
        val sourceW = source.width
        val sourceH = source.height
        val left = (crop.left * sourceW).roundToInt().coerceIn(0, sourceW - 1)
        val top = (crop.top * sourceH).roundToInt().coerceIn(0, sourceH - 1)
        val right = (crop.right * sourceW).roundToInt().coerceIn(left + 1, sourceW)
        val bottom = (crop.bottom * sourceH).roundToInt().coerceIn(top + 1, sourceH)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        return if (cropped.width == targetW && cropped.height == targetH) {
            cropped
        } else {
            cropped.scale(targetW, targetH)
        }
    }

    /** The file each orientation of the rotating pair is written to — the two names the service reads. */
    private fun fileNameFor(orientation: Orientation): String = when (orientation) {
        Orientation.PORTRAIT -> WallpaperFiles.ROTATING_PORTRAIT
        Orientation.LANDSCAPE -> WallpaperFiles.ROTATING_LANDSCAPE
    }

    private fun writeImage(bitmap: Bitmap, fileName: String = WallpaperFiles.IMAGE): File {
        val dir = File(appContext.filesDir, WallpaperFiles.DIR).apply { mkdirs() }
        return File(dir, fileName).also { file ->
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

        /** `DATE_ADDED` is in seconds. */
        const val MILLIS_PER_SECOND = 1_000

        /** How far back the gallery watch looks, in seconds - see `newGalleryImages`. */
        const val GALLERY_SLACK_SECONDS = 2
    }
}
