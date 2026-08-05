package inkspire.morphic.feature.shell

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.SurfaceRegister
import inkspire.morphic.data.wallpaper.WallpaperBrightness
import inkspire.morphic.data.wallpaper.WallpaperRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the launcher shell renders.
 *
 * @property register HOME's layout, its per-edge bindings, and the crossing transition. [SurfaceRegister.Default]
 *   until the store's first emission, which is why the shell never renders "no settings" — it renders the defaults,
 *   and the defaults bind no edge.
 * @property brightness how bright the wallpaper behind the chrome is, which is the launcher's dark/light input.
 *   [WallpaperBrightness.DARK] until the first read, which is what the shell hardcoded before this existed — so a
 *   first frame looks exactly as it used to and then corrects if the wallpaper is bright.
 * @property backdropEffect how frosted surfaces render over the wallpaper. Handed down as-is rather than resolved,
 *   because *what* it means is a drawing decision and belongs to the modifier that draws it.
 * @property backdropImage the wallpaper pre-blurred for [backdropEffect], or null when the launcher has nothing it can
 *   honestly claim is on screen — see `WallpaperRepository.backdrop`. An `android.graphics.Bitmap` and not an
 *   `ImageBitmap` on purpose: the conversion is a Compose concern, and a state holder that returns Compose graphics
 *   types is one step from doing composition work.
 * @property backdropAccent the wallpaper's representative colour as ARGB, which every frosted wash is blended toward.
 *   Null when unreadable, which makes the washes plain white and black. Separate from [backdropImage] because it has a
 *   separate source — the system usually answers it without any image being read at all.
 */
data class ShellState(
    val register: SurfaceRegister = SurfaceRegister.Default,
    val brightness: WallpaperBrightness = WallpaperBrightness.DARK,
    val backdropEffect: BackdropEffect = BackdropEffect.None,
    val backdropImage: Bitmap? = null,
    val backdropAccent: Int? = null,
)

/**
 * Screen-level state holder for the launcher shell: the surface register, and how bright the wallpaper is.
 *
 * **Why a ViewModel for what looked like one flow read.** It would have been one line to `koinInject` a repository in
 * the composable and collect it there — and that is precisely what L1's settings feature did, 25 times, which is why
 * it has no presentation layer to port. The rule is a ViewModel per screen, and the second input has now arrived: this
 * is the `combine` that was predicted, rather than a second `collectAsStateWithLifecycle` plus assembly logic in the
 * composable.
 *
 * **Two repositories, and they are unrelated on purpose.** The register is a preference and brightness is a reading of
 * the world; joining them is this holder's whole job, and neither store has to know the other exists.
 *
 * No write path: the shell *obeys* both. Editing the register is the settings surface's job, and brightness is not
 * anybody's to set — it is what the wallpaper happens to be.
 */
class ShellViewModel(
    settingsRepository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    /**
     * Which way the device is held, reported by the shell.
     *
     * The backdrop needs it and cannot derive it: a **rotating** wallpaper is two different pictures, so "the
     * wallpaper" is not one image until you say which orientation. Reported rather than read from a `Configuration`
     * here for the reason every other surface reports its `DeviceConfiguration` — the composable is where the window
     * is, and a state holder that reaches for one has a `Context` in it.
     */
    private val orientation = MutableStateFlow(Orientation.PORTRAIT)

    val state: StateFlow<ShellState> =
        combine(
            settingsRepository.surfaceRegister,
            wallpaperRepository.brightness,
            settingsRepository.backdropEffect,
            backdropImages(settingsRepository),
            wallpaperRepository.accentColor,
            ::ShellState,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShellState())

    /** Reports the orientation the shell is being drawn in, so the rotating pair's right half is sampled. */
    fun setOrientation(value: Orientation) {
        orientation.value = value
    }

    /**
     * The blurred wallpaper, re-collected only when something about *the picture* changes.
     *
     * **The key is a nullable strength, and it carries two facts in one value**: null means the effect samples nothing
     * at all, and a number is how hard to blur. That is what separates the two ways the effect can move — a tint
     * slider must not re-blur a bitmap, because how dark the wash is does not change the picture under it. Zero is a
     * real strength meaning "sample it sharp", which is why this cannot be `0f`-means-off.
     *
     * `blurStrength` is a property on the sealed `BackdropEffect` rather than a `when` here, so the next reader of it
     * does not write the same `when` again.
     *
     * `flatMapLatest`, so a strength drag (S5f-3) cancels the in-flight blur rather than queueing one per frame; the
     * flow it switches to is the repository's, which re-emits on its own when the displayed wallpaper changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun backdropImages(settingsRepository: SettingsRepository): Flow<Bitmap?> =
        combine(
            settingsRepository.backdropEffect
                .map { if (it == BackdropEffect.None) null else it.blurStrength }
                .distinctUntilChanged(),
            orientation,
            ::Pair,
        ).flatMapLatest { (strength, current) ->
            if (strength == null) flowOf(null) else wallpaperRepository.backdrop(strength, current)
        }

    private companion object {
        /** Keeps the store subscription alive across a configuration change instead of tearing it down and back up. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
