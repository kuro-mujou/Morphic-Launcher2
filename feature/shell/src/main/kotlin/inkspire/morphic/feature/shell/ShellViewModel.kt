package inkspire.morphic.feature.shell

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.GridSlot
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the launcher shell renders.
 *
 * @property register HOME's layout, its per-edge bindings, and the crossing transition. [SurfaceRegister.Default]
 *   until the store's first emission, which is why the shell never renders "no settings" — it renders the defaults,
 *   and the defaults bind no edge.
 * @property pagerWraps whether each pager's pages loop, resolved. The shell needs it because wrapping decides the
 *   *gesture*, not just the animation: a pager with no end has no edge to hand a one-finger swipe off at, so it makes
 *   that edge two-finger-only. This is the one state field read for both halves of a binding at once — HOME's pager
 *   for the open policy, the bound layout's for the close — which is why it arrives as a map rather than as the two
 *   booleans a single surface would want.
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
    val pagerWraps: Map<GridSlot, Boolean> = emptyMap(),
    val brightness: WallpaperBrightness = WallpaperBrightness.DARK,
    val backdropEffect: BackdropEffect = BackdropEffect.Plain(),
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
        // Six sources against `combine`'s five, so the two that come from the same store and answer the same
        // question — what is bound to each edge, and how the pagers behind those bindings page — are grouped first.
        combine(
            combine(settingsRepository.surfaceRegister, settingsRepository.pagerWraps, ::Pair),
            wallpaperRepository.brightness,
            settingsRepository.backdropEffect,
            backdropImages(settingsRepository),
            wallpaperRepository.accentColor,
        ) { (register, wraps), brightness, effect, image, accent ->
            ShellState(register, wraps, brightness, effect, image, accent)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShellState())

    /** Reports the orientation the shell is being drawn in, so the rotating pair's right half is sampled. */
    fun setOrientation(value: Orientation) {
        orientation.value = value
    }

    /**
     * The blurred wallpaper, re-collected only when something about *the picture* changes.
     *
     * **Keyed on the *film's* blur strength, which is a constant** — so today this re-collects only when the
     * orientation or the displayed wallpaper changes, and never because a preference moved. The full-screen frost is
     * not tunable (see `BackdropEffect.fullScreenFilm`), and every variant blurs by the same amount, so switching
     * between them is a redraw with a different wash rather than a re-decode.
     *
     * It stays keyed on a strength rather than dropping the key, because the moment a *panel* wants the user's own
     * strength there will be two, and this is the seam that takes it: `distinctUntilChanged` already means a value
     * that does not move costs nothing.
     *
     * **Every effect gets an image now, `Plain` included.** This used to map `None` to null and load nothing, on the
     * model that an effect could decline to sample; the full-screen frost overturned that — a surface arriving over
     * HOME has to occlude it whatever decoration is chosen, so the choice is only ever *which wash*, never *whether
     * there is a picture*. Null now means one thing, which is what `WallpaperRepository.backdrop` itself means by it:
     * there is no wallpaper this launcher may read.
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
            settingsRepository.backdropEffect.map { it.fullScreenFilm.blurStrength }.distinctUntilChanged(),
            orientation,
            ::Pair,
        ).flatMapLatest { (strength, current) -> wallpaperRepository.backdrop(strength, current) }

    private companion object {
        /** Keeps the store subscription alive across a configuration change instead of tearing it down and back up. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
