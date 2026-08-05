package inkspire.morphic.feature.settings.wallpaper

import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.Orientation
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
 * @property rotatingPortrait the portrait half of the rotating pair, decoded for its slot, or null if unset.
 * @property rotatingLandscape likewise for landscape. Two fields rather than a map, because there are exactly two and
 *   the section draws them side by side — a map would be a lookup where a name will do.
 * @property rotatingActive whether the launcher's own live wallpaper is what the system is currently showing. **Read
 *   from the system, never stored** (see `WallpaperState`), and refreshed on resume because the only way it changes is
 *   the user confirming in the system's chooser — which happens while this screen is stopped.
 * @property busy a write is in flight — read by the **crop** screen, whose Save button it disables and relabels while
 *   a large photo is decoded and scaled, and by the section, whose buttons it disables while an apply runs. L1 had no
 *   such flag and did not need one for the crop (its screen had a local `saving`), but it also could not tell you that
 *   an *apply* was still going.
 */
data class WallpaperSectionState(
    val image: WallpaperImage? = null,
    val preview: Bitmap? = null,
    val applied: Boolean = false,
    val rotatingPortrait: Bitmap? = null,
    val rotatingLandscape: Bitmap? = null,
    val rotatingActive: Boolean = false,
    val busy: Boolean = false,
) {
    val applicable: Boolean get() = image?.source == WallpaperSource.PICKED

    /** True once either half exists — the point at which applying the live wallpaper would draw something. */
    val hasRotating: Boolean get() = rotatingPortrait != null || rotatingLandscape != null
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

    /**
     * Bumped to re-ask the system which wallpaper is live.
     *
     * A trigger rather than a stored flag, which is the whole point: the answer lives in `WallpaperManager` and changes
     * while this app is stopped (the user confirms in the system's chooser), so what this holder needs is a reason to
     * look again — not a copy to repair. L1 kept the copy and needed `reconcileLiveWallpaper` to repair it.
     */
    private val rotatingProbe = MutableStateFlow(0)

    val state: StateFlow<WallpaperSectionState> =
        combine(
            // The previews are loaded *inside* the map rather than in separate flows: each is a function of the stored
            // state and nothing else, so re-deriving them per emission is both correct and cheap — the state changes
            // only when the user picks or applies, not per frame.
            wallpaperRepository.wallpaper.map { stored ->
                WallpaperSectionState(
                    image = stored.image,
                    preview = stored.image?.let { wallpaperRepository.loadImage() },
                    applied = stored.appliedSystemId != 0,
                    rotatingPortrait = stored.rotating.portrait
                        ?.let { wallpaperRepository.loadRotatingImage(Orientation.PORTRAIT) },
                    rotatingLandscape = stored.rotating.landscape
                        ?.let { wallpaperRepository.loadRotatingImage(Orientation.LANDSCAPE) },
                )
            },
            busy,
            rotatingProbe.map { wallpaperRepository.isRotatingActive() },
        ) { base, inFlight, active -> base.copy(busy = inFlight, rotatingActive = active) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WallpaperSectionState())

    /**
     * Re-reads whether the launcher's live wallpaper is active — the section calls this on resume.
     *
     * The one thing this holder cannot learn by listening: setting a live wallpaper happens in the *system's* chooser,
     * so the app is stopped for the moment it changes and there is no callback on the way back. Asking on resume is
     * exactly what L1 did (`reconcileLiveWallpaper`), minus the stored mode it existed to repair.
     */
    fun refreshRotatingActive() {
        rotatingProbe.value += 1
    }

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
     * Takes [crop] of [uri] as the [orientation] half of the rotating pair, stored at [outWidth] × [outHeight].
     *
     * The sibling of [chooseImage] and deliberately not a flag on it: they write different files, and the one they
     * write is not a variation on a shared behaviour but the whole of what distinguishes them. The size is the *target*
     * orientation's screen rather than the current one — a landscape half is stored landscape-shaped while the phone is
     * held upright, which is what [CropTarget] works out for the crop screen.
     *
     * Sets nothing on the system, and here there is nothing it could set: a live wallpaper is applied through the
     * system's chooser, and the service reads whatever file is on disk the next time it draws.
     */
    fun chooseRotatingImage(
        uri: Uri,
        crop: NormalizedCropRect,
        outWidth: Int,
        outHeight: Int,
        orientation: Orientation,
        onSaved: () -> Unit = {},
    ) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                wallpaperRepository.setRotatingImage(uri, crop, outWidth, outHeight, orientation)
                onSaved()
            } finally {
                busy.value = false
            }
        }
    }

    /**
     * The launcher's live-wallpaper service, for the section to name in the system intent that applies it.
     *
     * Handed through rather than resolved in the UI: the component belongs to the module that declares the service, and
     * starting an activity belongs to the screen that has a `Context`. L1 built the `ComponentName` in its UI, which is
     * how its data layer ended up unable to say what its own service was called.
     */
    fun rotatingServiceComponent(): ComponentName = wallpaperRepository.rotatingServiceComponent()

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
