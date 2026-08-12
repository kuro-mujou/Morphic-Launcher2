package inkspire.morphic.data.wallpaper.internal

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.graphics.ColorUtils
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
import inkspire.morphic.data.wallpaper.WallpaperBrightness
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
 * `updateState` below does the read-modify-write *inside* `edit`, where DataStore serializes it.
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

    /**
     * Our stored state, re-emitted on every reason **what is displayed** could have changed.
     *
     * **Two triggers because there are two ways it moves, and neither covers the other.**
     * `addOnColorsChangedListener` catches a wallpaper set by anyone (including us, through [apply], and including the
     * system's live-wallpaper chooser), but it does not exist below API 27; the state flow catches our own writes on
     * every API, which is what keeps this alive on 26 for the case a launcher can actually control.
     *
     * Shared by both readings below rather than each building its own, because they are answering one question — "what
     * is behind the chrome right now?" — and two change signals for one question is how they end up disagreeing.
     */
    private val displayedWallpaperChanges: Flow<WallpaperState> =
        combine(wallpaper, systemColorChanges()) { state, _ -> state }

    override val brightness: Flow<WallpaperBrightness> = displayedWallpaperChanges
        .map { resolveBrightness(it) }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    override val accentColor: Flow<Int?> = displayedWallpaperChanges
        .map { resolveAccent(it) }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    override fun backdrop(strength: Float, orientation: Orientation): Flow<Bitmap?> = displayedWallpaperChanges
        .map { backdropSourcePath(it, orientation) }
        // On the *path*, not on the bitmap: re-blurring an unchanged file would hand every frosted surface a new,
        // equal image and invalidate all of them for nothing. It also means an unrelated state write (a rotating half
        // being set while a static image is displayed) costs a comparison rather than a decode.
        .distinctUntilChanged()
        .map { path -> path?.let { blurBackdrop(it, strength) } }
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

    /** The file at [path] decoded small and blurred, or null if it has gone missing under us. */
    private fun blurBackdrop(path: String, strength: Float): Bitmap? {
        val sharp = decodeFile(path, BACKDROP_SAMPLE_STEP) ?: return null
        // Radius and passes come from one 0..1 preference so a slider never has to speak in pixels; the ceiling is
        // L1's, tuned against a bitmap already downscaled by the same factor.
        val radius = (strength.coerceIn(0f, 1f) * MAX_BLUR_RADIUS).roundToInt()
        return downscaleAndBlur(sharp, downscale = BACKDROP_DOWNSCALE, radius = radius, passes = BLUR_PASSES)
    }

    /**
     * Which stored file a frosted surface may sample, or null if none can honestly be claimed to be on screen.
     *
     * The four-way answer `loadBackdrop`'s KDoc sets out, kept in one function because it is *the* rule rather than an
     * implementation detail of one caller — the icon studio's preview and the panned-surface backdrop will both want
     * exactly this, and a second copy of it is how L1's `resolveDockDrop` happened.
     */
    private suspend fun backdropSourcePath(state: WallpaperState, orientation: Orientation): String? {
        if (isRotatingActive()) {
            // The same `?: portrait ?: landscape` fallback the service draws with, so a half-configured pair is
            // sampled as what is actually on screen rather than reported as nothing.
            val pair = state.rotating
            return (pair[orientation] ?: pair.portrait ?: pair.landscape)?.path
        }
        val image = state.image ?: return null
        // A capture is a picture *of* the displayed wallpaper — it can never be applied, so the "did we apply it?"
        // gate below would reject it always, and sampling it is the one job it has.
        if (image.source == WallpaperSource.CAPTURED) return image.path
        return if (ownsSystemWallpaper(state)) image.path else null
    }

    /**
     * Whether the image this launcher stored is still the one the system is showing.
     *
     * One gate, two readers ([brightness] and [backdropSourcePath]), because "is our file what is behind the chrome?"
     * is one question and two answers to it could disagree. The id is the evidence: it was taken at the moment we set
     * the wallpaper, so it still matching means nothing has replaced ours since — including a wallpaper set outside
     * the launcher, which is the case a stored boolean could never notice.
     */
    private fun ownsSystemWallpaper(state: WallpaperState): Boolean {
        if (state.appliedSystemId == 0) return false
        val manager = WallpaperManager.getInstance(appContext)
        val liveId = runCatching { manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(0)
        return state.appliedSystemId == liveId
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
     * A tick each time the system's wallpaper colors change, plus one on subscription so the first resolve happens.
     *
     * `Unit` rather than the colors themselves: the resolve below re-reads them anyway (it also needs the wallpaper
     * *id*, which this callback does not carry), and a flow of colors would tempt a caller into using them without
     * the fallback chain. Below API 27 there is no listener to register, so this is the subscription tick alone and
     * the state flow beside it carries the updates.
     */
    private fun systemColorChanges(): Flow<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return flowOf(Unit)
        return callbackFlow {
            val manager = WallpaperManager.getInstance(appContext)
            val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                // FLAG_LOCK changes are none of our business: the launcher's chrome sits over the home wallpaper.
                if (which and WallpaperManager.FLAG_SYSTEM != 0) trySend(Unit)
            }
            manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            trySend(Unit)
            awaitClose { manager.removeOnColorsChangedListener(listener) }
        }
    }

    /**
     * Ask the system; if it will not say, read our own file — but only when our file is provably what is on screen.
     *
     * **That guard is the whole of the correctness here.** The wallpaper the chrome sits on may have been set by
     * another app entirely, so "we have an image stored" is not evidence of anything. `appliedSystemId` is: it is the
     * id the system gave the wallpaper *at the moment we set it*, so it still matching the live id means nothing has
     * replaced ours since. That is the second job `WallpaperState`'s KDoc reserved the field for, now doing it.
     */
    private fun resolveBrightness(state: WallpaperState): WallpaperBrightness {
        systemBrightness(WallpaperManager.getInstance(appContext))?.let { return it }
        if (!ownsSystemWallpaper(state)) return WallpaperBrightness.DARK
        val path = state.image?.path ?: return WallpaperBrightness.DARK
        val bitmap = decodeFile(path, BRIGHTNESS_SAMPLE_STEP) ?: return WallpaperBrightness.DARK
        return brightnessOf(meanLuminance(bitmap))
    }

    /**
     * The wallpaper's representative color: the system's primary if it has one, else our own file's.
     *
     * The same two-step [resolveBrightness] takes, and deliberately so — three readings of "what is displayed" that
     * disagreed about *which image* they were reading would be worse than any one of them being slightly off. The
     * orientation passed to [backdropSourcePath] is portrait because a color is not per-orientation in any meaningful
     * sense; a rotating pair whose two halves have different accents is a wallpaper whose color is genuinely
     * ambiguous, and picking one beats flickering between them on every rotation.
     */
    private suspend fun resolveAccent(state: WallpaperState): Int? {
        systemAccent()?.let { return it }
        val path = backdropSourcePath(state, Orientation.PORTRAIT) ?: return null
        val bitmap = decodeFile(path, BRIGHTNESS_SAMPLE_STEP) ?: return null
        return dominantColor(bitmap)
    }

    /** `WallpaperColors.primaryColor` — the most-represented color of whatever is on screen. Null below API 27. */
    private fun systemAccent(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val manager = WallpaperManager.getInstance(appContext)
        val colors = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return null
        return colors.primaryColor.toArgb()
    }

    /**
     * The system's own reading of the live wallpaper, or null if it has none to give.
     *
     * **Preferred over anything we could compute**, because it is computed over what is *actually displayed* — another
     * app's wallpaper, or a live one, neither of which we can read as a bitmap at all. It needs no permission and no
     * decode. Null below API 27, and null for a live wallpaper whose service publishes no colors (ours does — see
     * [RotatingWallpaperService]).
     *
     * On API 31+ the OS also states its verdict directly: `HINT_SUPPORTS_DARK_TEXT` *is* the question this method
     * asks, decided with area-weighted analysis rather than a single color, so it wins where it exists. The getter
     * arrived in 31 even though the constant dates from 27, which is the only reason for the second branch.
     */
    private fun systemBrightness(manager: WallpaperManager): WallpaperBrightness? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val colors = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        ) {
            return WallpaperBrightness.LIGHT
        }
        return brightnessOf(ColorUtils.calculateLuminance(colors.primaryColor.toArgb()))
    }

    /**
     * Mean relative luminance over a tiny downscale of [source].
     *
     * **Per-pixel luminance averaged, not the luminance of an averaged color** — the two differ because luminance is
     * gamma-expanded, and a picture that is half black and half white is a mid-gray by the second reading while the
     * first correctly reports it as the borderline case it is.
     *
     * Deliberately *not* `Blur.kt`'s `dominantColor`, which the port plan expected to be reused here: that one weights
     * each pixel by saturation so a vivid accent beats washed-out gray, which is exactly right for picking an accent
     * and exactly wrong for asking how bright something is.
     */
    private fun meanLuminance(source: Bitmap): Double {
        val small = source.scale(BRIGHTNESS_GRID, BRIGHTNESS_GRID)
        val pixels = IntArray(BRIGHTNESS_GRID * BRIGHTNESS_GRID)
        small.getPixels(pixels, 0, BRIGHTNESS_GRID, 0, 0, BRIGHTNESS_GRID, BRIGHTNESS_GRID)
        return pixels.sumOf { ColorUtils.calculateLuminance(it) } / pixels.size
    }

    private fun brightnessOf(luminance: Double): WallpaperBrightness =
        if (luminance >= DARK_TEXT_LUMINANCE) WallpaperBrightness.LIGHT else WallpaperBrightness.DARK

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

    /**
     * The stored JPEG at [path], optionally decoded [sampleSize]× smaller.
     *
     * The sample is for readings rather than for drawing: a brightness average over a screen-sized bitmap is the same
     * answer as one over a thumbnail, and the thumbnail costs a fraction of the allocation. Callers that need the
     * pixels (a preview, the bitmap handed to `WallpaperManager`) take the default and get the file as written.
     */
    private fun decodeFile(path: String, sampleSize: Int = 1): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
        return BitmapFactory.decodeFile(file.absolutePath, options)
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

        /**
         * The relative luminance at which black text beats white text on a background.
         *
         * Not a taste value — it is where the two WCAG contrast ratios cross. Contrast against white is
         * `1.05 / (L + 0.05)` and against black is `(L + 0.05) / 0.05`; setting them equal gives
         * `(L + 0.05)² = 0.0525`, so `L ≈ 0.179`. Above it the wallpaper wants dark chrome, below it light.
         */
        const val DARK_TEXT_LUMINANCE = 0.179

        /** The stored image is decoded this much smaller for a brightness read — see `decodeFile`. */
        const val BRIGHTNESS_SAMPLE_STEP = 8

        /** Side of the square the sampled image is reduced to before averaging. L1 uses the same 32 in `Blur.kt`. */
        const val BRIGHTNESS_GRID = 32

        /**
         * How much smaller the backdrop is than the wallpaper — the decode step and the blur's own downscale.
         *
         * Two reductions rather than one because they buy different things: the decode step keeps a screen-sized JPEG
         * from being fully materialized, and the blur's downscale is what makes the passes cheap. A backdrop is
         * upscaled at draw time regardless, so the resolution lost here is resolution the blur was about to destroy.
         */
        const val BACKDROP_SAMPLE_STEP = 2
        const val BACKDROP_DOWNSCALE = 4

        /** L1's ceiling: the radius a strength of 1.0 maps to, in pixels of the already-downscaled bitmap. */
        const val MAX_BLUR_RADIUS = 12

        /** Three box passes approximate a gaussian closely enough that no one can tell. L1's number. */
        const val BLUR_PASSES = 3
    }
}
