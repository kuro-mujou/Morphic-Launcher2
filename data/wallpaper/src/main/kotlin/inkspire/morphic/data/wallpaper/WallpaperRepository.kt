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
     * Copies [uri] into this module's own storage as the wallpaper image, **centre-cropped** to
     * [screenWidth] × [screenHeight] and scaled to it.
     *
     * Centre-cropped because nothing chooses a region yet: the crop *screen* is the next slice, and it will pass the
     * rectangle the user dragged rather than have this invent one. Cropping to the screen at all (rather than storing
     * the original) is what makes the stored file the thing that is displayed — the same reason L1 scaled on the way in.
     *
     * **Does not touch the system wallpaper**; [apply] does. Choosing and applying are separate because the user may
     * pick an image, look at it, and change their mind — and because applying asks *where* (home, lock, both).
     */
    suspend fun setImage(uri: Uri, screenWidth: Int, screenHeight: Int)

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
