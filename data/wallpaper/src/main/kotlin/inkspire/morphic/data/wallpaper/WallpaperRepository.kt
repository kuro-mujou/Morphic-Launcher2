package inkspire.morphic.data.wallpaper

import android.graphics.Bitmap
import android.net.Uri
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
 */
@Serializable
data class WallpaperImage(
    val path: String,
    val width: Int,
    val height: Int,
)

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
 * @property appliedSystemId the `WallpaperManager` wallpaper id at the moment we last applied [image], or 0 if we never
 *   did. Kept for two jobs a boolean could not do: it is what makes the section's button read "Apply" or "Re-apply",
 *   and comparing it against the live id is how a wallpaper set **outside** this launcher will be detected (L1 stored
 *   the same `appliedSystemWallpaperId` and used it exactly that way).
 */
@Serializable
data class WallpaperState(
    val image: WallpaperImage? = null,
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
}

/**
 * **Reading and setting the launcher's wallpaper** — the module `feature:settings`' wallpaper section is built on.
 *
 * A *service* rather than a preferences store, which is why it is its own module rather than another slice of
 * `data:settings`: it decodes bitmaps, writes files, and talks to `WallpaperManager`. What it persists is a **pointer**
 * to a file it wrote plus the id of the wallpaper it set — bookkeeping, not preferences, which is the distinction the
 * settings port's S0 drew when it refused to bring L1's `WallpaperState` across into the settings blob. The *effect*
 * params (`BackdropEffect`) genuinely are preferences and stay there, arriving with S5b.
 *
 * **This first cut is the static single image only.** Three of L1's capabilities are deliberately absent, each with a
 * reason rather than an omission:
 * - **`CAPTURE`** — an effect-only source (a screenshot taken with the launcher's own UI hidden) that never becomes the
 *   system wallpaper. It exists *for* the frosted backdrop, so it waits on the effects that read it.
 * - **`LIVE_ROTATE`** — a per-orientation pair rendered by L1's own `RotateWallpaperService`. That is a live wallpaper
 *   with a service, a manifest entry and its own XML metadata; it is a feature beside this one rather than a step in it.
 * - **the blur and the dominant colour** (`loadBackdropBlur`, `loadDominantColor`) — both are effect inputs, and both
 *   need L1's `Blur.kt` image processing, which the plan already says belongs beside the graphics code rather than in a
 *   repository.
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
     */
    suspend fun setImage(uri: Uri, crop: NormalizedCropRect, outWidth: Int, outHeight: Int)

    /**
     * Sets the stored image as the system wallpaper on [target], and records the id the system gave it.
     *
     * A no-op when nothing has been chosen. Failure to set is logged rather than thrown: `WallpaperManager` can refuse
     * for reasons the caller cannot fix or predict (a device policy, a provider that vanished), and a settings screen
     * has nothing useful to do with an exception. L1 swallowed it the same way, with the same `runCatching`.
     */
    suspend fun apply(target: WallpaperTarget)

    /** The stored image as a bitmap, for a preview at real size. Null when nothing is stored, or the file is gone. */
    suspend fun loadImage(): Bitmap?
}
