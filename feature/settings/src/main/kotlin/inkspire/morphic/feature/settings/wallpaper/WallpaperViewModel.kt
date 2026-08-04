package inkspire.morphic.feature.settings.wallpaper

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.wallpaper.NormalizedCropRect
import inkspire.morphic.data.wallpaper.WallpaperImage
import inkspire.morphic.data.wallpaper.WallpaperRepository
import inkspire.morphic.data.wallpaper.WallpaperSource
import inkspire.morphic.data.wallpaper.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the wallpaper section shows.
 *
 * @property image the chosen image's pointer, or null if the user never picked one — what tells "nothing yet" from
 *   "something, still loading".
 * @property preview that image decoded, for the section to draw. Null while it loads, and null for good if the file
 *   went missing under us (a wipe of app storage), which is why it is separate from [image] rather than implied by it.
 * @property applied whether *this launcher* set the current system wallpaper — the difference between "Apply" and
 *   "Re-apply". Read from `WallpaperState.appliedSystemId`, which is an id rather than a boolean because the same
 *   field will later detect a wallpaper set outside the launcher.
 * @property applicable whether the stored image can be set on the system at all. False for a capture, which is a
 *   picture *of* the wallpaper — the repository declines it, and the section shows why instead of a dead button.
 * @property busy a write is in flight — read by the **crop** screen, whose Save button it disables and relabels while
 *   a large photo is decoded and scaled, and by the section, whose buttons it disables while an apply runs. L1 had no
 *   such flag and did not need one for the crop (its screen had a local `saving`), but it also could not tell you that
 *   an *apply* was still going.
 */
data class WallpaperSectionState(
    val image: WallpaperImage? = null,
    val preview: Bitmap? = null,
    val applied: Boolean = false,
    val busy: Boolean = false,
) {
    val applicable: Boolean get() = image?.source == WallpaperSource.PICKED
}

/**
 * Screen-level state holder for the **wallpaper section**: the chosen image, and the two things a user can do to it.
 *
 * **Thin on purpose.** Everything interesting — decoding, cropping, writing the file, talking to `WallpaperManager` —
 * is `data:wallpaper`'s, because it is a *service* rather than a store; this holder only says when. That split is why
 * the wallpaper never went into `data:settings`, and it is what keeps this class the smallest section here despite
 * being the one that touches the filesystem.
 *
 * **Choosing and applying are two commands, as they are two things.** A user may pick an image, look at it, and change
 * their mind; and applying asks *where* (home, lock, both), which choosing does not. L1 separated them the same way —
 * its picker wrote the image and its Apply button set it — and the repository's own KDoc states the split.
 */
class WallpaperViewModel(
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)

    val state: StateFlow<WallpaperSectionState> =
        combine(
            // The preview is loaded *inside* the map rather than in a separate flow: it is a function of the stored
            // image and nothing else, so re-deriving it per emission is both correct and cheap — the state changes
            // only when the user picks or applies, not per frame.
            wallpaperRepository.wallpaper.map { stored ->
                WallpaperSectionState(
                    image = stored.image,
                    preview = stored.image?.let { wallpaperRepository.loadImage() },
                    applied = stored.appliedSystemId != 0,
                )
            },
            busy,
        ) { base, inFlight -> base.copy(busy = inFlight) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WallpaperSectionState())

    /**
     * [uri] decoded small enough to show, for the crop screen to frame — the read half of choosing an image.
     *
     * Suspending and returning rather than pushing into [state], because it belongs to the *crop* screen and not to
     * the section: two screens share this holder, and a picked-but-unsaved image is one screen's business. Null when
     * the image cannot be read at all, which that screen shows as an empty frame with nothing to save.
     */
    suspend fun preview(uri: Uri): Bitmap? = wallpaperRepository.decodePreview(uri)

    /**
     * Takes [crop] of [uri] as the wallpaper image, stored at [outWidth] × [outHeight].
     *
     * **Every argument comes from the crop screen's viewport**, which is the whole window: a wallpaper sits under the
     * system bars, so framing (and storing) against the usable area would produce an image too small for what it has
     * to cover. The rectangle and the output size share that one coordinate space, which is what makes the stored
     * image the thing the user framed.
     *
     * Sets nothing on the system: [apply] does that, and the user may want to look first.
     *
     * @param onSaved runs when the image is stored — the crop screen's cue to leave. A continuation rather than a
     *   navigator: this holder still cannot navigate, which is the property worth keeping (see `Navigator`'s KDoc).
     */
    fun chooseImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        onSaved: () -> Unit = {},
    ) = store(uri, crop, outWidth, outHeight, WallpaperSource.PICKED, onSaved)

    /**
     * Waits for the next image to appear in the gallery — the capture screen's cue that a screenshot was taken.
     *
     * Suspends until one arrives and returns it, so the caller reads as the sequence it is: hide the launcher, wait,
     * import. Null only if the flow completes without emitting, which it does not do on its own — a cancelled wait
     * (the screen left) unwinds as a cancellation instead.
     */
    suspend fun awaitCapture(): Uri? = wallpaperRepository.newGalleryImages().firstOrNull()

    /**
     * Stores [uri] — a screenshot — as the wallpaper image, at [outWidth] × [outHeight].
     *
     * **No crop**, because a screenshot is already exactly the screen: it passes `NormalizedCropRect.Full`, which is
     * the caller that value's KDoc was written for. It is marked [WallpaperSource.CAPTURED], which is what stops it
     * being applied to the system — see that constant.
     */
    fun capture(uri: Uri, outWidth: Int, outHeight: Int, onSaved: () -> Unit = {}) =
        store(uri, NormalizedCropRect.Full, outWidth, outHeight, WallpaperSource.CAPTURED, onSaved)

    /** The one write path both sources take; they differ in the rectangle and in what they are called. */
    private fun store(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        source: WallpaperSource,
        onSaved: () -> Unit,
    ) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                wallpaperRepository.setImage(uri, crop, outWidth, outHeight, source)
                onSaved()
            } finally {
                busy.value = false
            }
        }
    }

    /** Sets the stored image as the system wallpaper on [target]. A no-op in the repository when nothing is stored. */
    fun apply(target: WallpaperTarget) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                wallpaperRepository.apply(target)
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
