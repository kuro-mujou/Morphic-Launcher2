package inkspire.morphic.feature.settings.wallpaper

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.data.wallpaper.WallpaperImage
import inkspire.morphic.data.wallpaper.WallpaperRepository
import inkspire.morphic.data.wallpaper.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * @property busy a write is in flight. **L2's own, not a port**: L1 never needed it because picking an image took the
 *   user to its crop screen, and the work happened behind that. Here the picker returns straight to this section, so
 *   without it a decode-and-scale of a 50-megapixel photo is a second of nothing happening.
 */
data class WallpaperSectionState(
    val image: WallpaperImage? = null,
    val preview: Bitmap? = null,
    val applied: Boolean = false,
    val busy: Boolean = false,
)

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
     * Takes [uri] as the wallpaper image, cropped and scaled to a [screenWidthPx] × [screenHeightPx] screen.
     *
     * **The screen size comes from the UI**, as the device configuration does on every other surface: it is a window
     * read, and it is the *whole* window rather than the usable area — a wallpaper sits under the system bars, so
     * subtracting insets would store an image too small for what it has to cover.
     *
     * Sets nothing on the system: [apply] does that, and the user may want to look first.
     */
    fun chooseImage(uri: Uri, screenWidthPx: Int, screenHeightPx: Int) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                wallpaperRepository.setImage(uri, screenWidthPx, screenHeightPx)
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
