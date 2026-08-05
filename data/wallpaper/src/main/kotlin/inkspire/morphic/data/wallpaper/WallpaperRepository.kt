package inkspire.morphic.data.wallpaper

import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The wallpaper image this launcher owns: a file it wrote, and the size it wrote it at.
 *
 * **A path rather than a `Uri`**, because the point of owning a copy is that the source may go away — a picked image
 * comes from a document provider whose grant does not survive a reboot, so an app that kept the `Uri` would lose the
 * user's wallpaper. L1 owned a copy for the same reason and this keeps that.
 *
 * @property path an absolute path under `filesDir/wallpaper`, written by this module.
 * @property width the stored bitmap's width in px, and [height] its height — recorded so a consumer can size a preview
 *   without decoding the file.
 * @property source where the image came from, which is what decides whether [WallpaperRepository.apply] means
 *   anything for it.
 */
@Serializable
data class WallpaperImage(
    val path: String,
    val width: Int,
    val height: Int,
    val source: WallpaperSource = WallpaperSource.PICKED,
)

/**
 * How the launcher came by its wallpaper image — and, because of that, whether setting it on the system is meaningful.
 *
 * L1's `WallpaperSource` narrowed to what exists: its `LIVE_ROTATE` belongs to the rotating pair (S5e) and arrives with
 * it. The distinction is not bookkeeping — L1's `applySingle` branches on exactly this, and skips `WallpaperManager`
 * entirely for a capture.
 */
enum class WallpaperSource {

    /** Chosen from the photo picker and framed on the crop screen. The only kind that can *become* the wallpaper. */
    PICKED,

    /**
     * One half of the **rotating pair** — the portrait or landscape image the launcher's own live wallpaper draws.
     *
     * Not applied through `WallpaperManager` either, and for a different reason from a capture: a live wallpaper is set
     * by the *system's* chooser, which the user has to confirm, so [WallpaperRepository.apply] has nothing to do with
     * it. What sets it is [RotatingWallpaperService] becoming the active wallpaper; what this source marks is which
     * file the service will draw.
     */
    ROTATING,

    /**
     * A screenshot the user took with the launcher's UI hidden — **a picture of the wallpaper, not a wallpaper.**
     *
     * Applying one would set a photograph of the current wallpaper as the wallpaper: a no-op at best, and at worst a
     * slow drift as each capture re-encodes the last. It exists as an *effect input*: it is the only way to sample a
     * live wallpaper, which cannot be read as a bitmap. So it is stored and previewed like any image, and
     * [WallpaperRepository.apply] declines it.
     */
    CAPTURED,
}

/** Where a wallpaper is set on the system: the home screen, the lock screen, or both. L1's `WallpaperTarget`. */
enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * The region of a source image to keep, as fractions of it — L1's `NormalizedCropRect`, kept name and all.
 *
 * **Fractions rather than pixels, because the crop is decided against a bitmap this module chose the size of.** The
 * screen shows a *sampled* decode (a 50-megapixel photo is not going on screen at full size), so a rectangle in that
 * bitmap's pixels would be meaningless against the source — and doubly so if the sampling ever changes. Fractions
 * survive both: they describe the picture rather than the decode.
 *
 * The whole image is `0f..1f` on both axes, which is also what [Full] means and what a caller with nothing to say
 * should pass.
 */
@Serializable
data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    companion object {
        /** Keep everything. The identity crop, for a caller that has not framed one. */
        val Full = NormalizedCropRect()
    }
}

/**
 * The launcher's **rotating wallpaper**: one image per orientation, drawn by [RotatingWallpaperService].
 *
 * A pair rather than a list, because that is what it is — a phone has two orientations, and the service picks between
 * them by comparing the surface's width and height. Either half may be absent while the user is still setting it up,
 * and the service falls back to whichever exists, so a half-configured pair still draws something rather than black.
 *
 * **This is where "the wallpaper" stops being a single image**, which is the reason this slice lands before the effects:
 * an effect has to answer "which image do I sample?", and the answer is per-orientation as soon as this exists.
 */
@Serializable
data class RotatingImages(
    val portrait: WallpaperImage? = null,
    val landscape: WallpaperImage? = null,
) {
    /** The image for [orientation], or null if that half has not been set. */
    operator fun get(orientation: Orientation): WallpaperImage? = when (orientation) {
        Orientation.PORTRAIT -> portrait
        Orientation.LANDSCAPE -> landscape
    }

    /** [image] as the [orientation] half, leaving the other alone — the merge `setRotatingImage` commits. */
    fun with(orientation: Orientation, image: WallpaperImage): RotatingImages = when (orientation) {
        Orientation.PORTRAIT -> copy(portrait = image)
        Orientation.LANDSCAPE -> copy(landscape = image)
    }

    /** True when neither half is set — the state in which applying the live wallpaper would draw nothing. */
    val isEmpty: Boolean get() = portrait == null && landscape == null
}

/**
 * What this module knows about the wallpaper — **the chosen image, and whether we are the one that set it**.
 *
 * **Much smaller than L1's `WallpaperState`, and deliberately so.** That one carried six fields (`appliedMode`,
 * `single`, `rotate`, `appliedSingle`, `singleDirty`, `appliedSystemWallpaperId`) because it juggled *two* image sets
 * (a single image and a per-orientation rotating pair) and kept a **snapshot copy** of whichever was applied so a
 * frosted backdrop could keep sampling the real system wallpaper. Neither exists here yet: this slice owns one image,
 * and the effects that need the snapshot arrive with `BackdropEffect`. Adding a field then is additive; carrying five
 * unread ones now would be five things to reason about with nothing checking them.
 *
 * @property image the wallpaper the user chose, or null if they never have.
 * @property rotating the per-orientation pair the launcher's own live wallpaper draws, empty until the user sets one.
 *   Beside [image] rather than instead of it, because they are independent: a user may keep a static image *and* a
 *   rotating pair, and switch by setting one or the other on the system. L1 kept both for the same reason.
 * @property appliedSystemId the `WallpaperManager` wallpaper id at the moment we last applied [image], or 0 if we never
 *   did. Kept for two jobs a boolean could not do: it is what makes the section's button read "Apply" or "Re-apply",
 *   and comparing it against the live id is how a wallpaper set **outside** this launcher will be detected (L1 stored
 *   the same `appliedSystemWallpaperId` and used it exactly that way).
 *
 * **What is deliberately *not* here: which wallpaper is currently active.** L1 stored that (`appliedMode`, latched by
 * `markRotateApplied`) and then needed `reconcileLiveWallpaper` on every resume to repair the cache when the user
 * changed wallpaper outside the app — a copy of something the system already knows, plus a job to keep the copy honest.
 * `WallpaperManager.wallpaperInfo` answers it directly, so [WallpaperRepository.isRotatingActive] asks instead of
 * remembering, and there is no latch, no reconciler, and nothing to go stale. That is smell 7 on this port's own list
 * ("derived state persisted as settings") declined rather than ported.
 */
@Serializable
data class WallpaperState(
    val image: WallpaperImage? = null,
    val rotating: RotatingImages = RotatingImages(),
    val appliedSystemId: Int = 0,
) {
    companion object {
        val Default = WallpaperState()
    }
}

/** The files this module owns under `filesDir/`[DIR]. Named here because the file *is* the persisted wallpaper. */
object WallpaperFiles {
    const val DIR = "wallpaper"

    /** The chosen image, cropped and scaled to the screen. L1's `owned.jpg`. */
    const val IMAGE = "single.jpg"

    /** The rotating pair, one per orientation — L1's `owned_portrait.jpg` / `owned_landscape.jpg`. */
    const val ROTATING_PORTRAIT = "rotating_portrait.jpg"
    const val ROTATING_LANDSCAPE = "rotating_landscape.jpg"
}

/**
 * **Reading and setting the launcher's wallpaper** — the module `feature:settings`' wallpaper section is built on.
 *
 * A *service* rather than a preferences store, which is why it is its own module rather than another slice of
 * `data:settings`: it decodes bitmaps, writes files, and talks to `WallpaperManager`. What it persists is a **pointer**
 * to a file it wrote plus the id of the wallpaper it set — bookkeeping, not preferences, which is the distinction the
 * settings port's S0 drew when it refused to bring L1's `WallpaperState` across into the settings blob. The *effect*
 * params (`BackdropEffect`) genuinely are preferences and stay there, arriving with S5f.
 *
 * **All three of L1's sources are here — picked, captured and the rotating pair** — and what is left out is the half
 * that reads them:
 * - **the blur and the dominant colour** (`loadBackdropBlur`, `loadDominantColor`) — both are effect inputs, and both
 *   need L1's `Blur.kt` image processing, which the plan already says belongs beside the graphics code rather than in a
 *   repository. The **capture** exists for them, and lands first on purpose: an effect has to answer "which image do I
 *   sample?", and answering that once against every source beats re-answering it per source.
 */
interface WallpaperRepository {

    /** The chosen image and whether we set it — see [WallpaperState]. Emits [WallpaperState.Default] before a choice. */
    val wallpaper: Flow<WallpaperState>

    /**
     * A downsampled bitmap of [uri], for showing the user what they picked before anything is written.
     *
     * Sampled rather than decoded whole: a modern camera image is tens of megapixels, and a preview is a few hundred
     * dp. Null when the image cannot be read at all — a provider that revoked the grant, or a file that is not an image.
     */
    suspend fun decodePreview(uri: Uri): Bitmap?

    /**
     * Copies [uri] into this module's own storage as the wallpaper image: the [crop] region of it, scaled to
     * [outWidth] × [outHeight].
     *
     * **The rectangle is the caller's**, which is the change S5c made. This used to centre-crop, as a stand-in for a
     * chooser that did not exist; now the crop screen passes the region the user framed, and there is nothing left
     * here that invents one. A caller with genuinely nothing to say passes [NormalizedCropRect.Full] — that is not the
     * old behaviour under a new name, since keeping the whole image *stretches* it to the output rather than filling
     * it, which is why nothing does that today.
     *
     * Storing a cropped, screen-sized file at all (rather than the original) is what makes the stored file the thing
     * that is displayed — the same reason L1 scaled on the way in.
     *
     * **Does not touch the system wallpaper**; [apply] does. Choosing and applying are separate because the user may
     * pick an image, look at it, and change their mind — and because applying asks *where* (home, lock, both).
     *
     * @param outWidth the size to store at, which the crop screen passes as the **viewport it framed against**. That
     *   is what makes the result what the user saw: the rectangle and the output share one coordinate space.
     * @param source how the image was obtained. One write path for both, because they differ in exactly this: a
     *   capture is already the size and shape of the screen, so it passes [NormalizedCropRect.Full] — which is what
     *   that value was declared for.
     */
    suspend fun setImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        source: WallpaperSource,
    )

    /**
     * Sets the stored image as the system wallpaper on [target], and records the id the system gave it.
     *
     * A no-op when nothing has been chosen, **and when the stored image is a [WallpaperSource.CAPTURED] one** — that
     * is a picture *of* the wallpaper, so setting it would either change nothing or re-encode the last capture. L1
     * branched on the same field in `applySingle` for the same reason. The section correspondingly offers no Apply for
     * a capture, but the rule lives here, where it cannot be worked around.
     *
     * Failure to set is logged rather than thrown: `WallpaperManager` can refuse for reasons the caller cannot fix or
     * predict (a device policy, a provider that vanished), and a settings screen has nothing useful to do with an
     * exception. L1 swallowed it the same way, with the same `runCatching`.
     */
    suspend fun apply(target: WallpaperTarget)

    /** The stored image as a bitmap, for a preview at real size. Null when nothing is stored, or the file is gone. */
    suspend fun loadImage(): Bitmap?

    /**
     * Stores the [crop] region of [uri] as the [orientation] half of the rotating pair, at [outWidth] × [outHeight].
     *
     * **Merges rather than replaces**: setting the landscape half leaves the portrait one alone, because the pair is
     * built one orientation at a time and losing the other half to each edit would make it impossible to finish. L1's
     * `setRotateImage` says the same in its own KDoc.
     *
     * **Touches nothing on the system.** Unlike [apply], there is nothing this could do: a live wallpaper is set through
     * the system's own chooser, which the user has to confirm. Writing the file is the whole of this module's part —
     * [RotatingWallpaperService] reads it the next time it draws, so an image changed while the wallpaper is live
     * appears on the next redraw without anything being re-applied.
     *
     * @param outWidth the size to store at, which is the *target orientation's* screen rather than the current one — a
     *   landscape half is framed and stored landscape-shaped even while the phone is held upright.
     */
    suspend fun setRotatingImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        orientation: Orientation,
    )

    /** The [orientation] half of the rotating pair as a bitmap, for its slot in the section. Null when unset. */
    suspend fun loadRotatingImage(orientation: Orientation): Bitmap?

    /**
     * Whether the launcher's own [RotatingWallpaperService] is the wallpaper the system is currently showing.
     *
     * **Asked, never remembered** — see [WallpaperState]. `WallpaperManager.wallpaperInfo` is non-null exactly when a
     * live wallpaper is set and names which, so this is a read rather than a cache: a user who changes wallpaper in the
     * system's own settings makes the next call return the truth, with nothing to reconcile. L1 latched the answer into
     * `appliedMode` and needed a resume-time reconciler to repair it.
     *
     * Suspending because it is a binder call, not because it is slow.
     */
    suspend fun isRotatingActive(): Boolean

    /**
     * The launcher's live-wallpaper service, for a caller that needs to name it in an intent.
     *
     * The component is this module's to know — it declares the service — and starting an activity is the *screen's*, so
     * the split is: this hands over the name, and `feature:settings` builds `ACTION_CHANGE_LIVE_WALLPAPER` around it.
     * L1 kept both in its feature module, which meant its data layer could not say what its own service was called.
     */
    fun rotatingServiceComponent(): ComponentName

    /**
     * Emits each image that appears in the device's gallery **after collection starts** — how a capture is noticed.
     *
     * There is no API for "take a screenshot", so L1's capture flow is the only one available: hide the launcher's UI,
     * ask the user to take one, and watch `MediaStore` for what arrives. This is that watch, moved out of the screen it
     * lived in — a `ContentObserver` and a query are system reads, and a composable is the wrong place to hold either.
     *
     * **It cannot tell a screenshot from any other new image**, and neither could L1's: what it reports is the newest
     * image, whatever produced it. That is why the screen asks for a screenshot *now* and takes the first emission — a
     * photo arriving from a sync at that exact moment is the known failure, and it is recoverable by capturing again.
     *
     * Requires read access to media images; without the permission the query returns nothing and this stays silent
     * rather than throwing, since the screen has already asked and can do nothing more about it.
     */
    fun newGalleryImages(): Flow<Uri>
}
