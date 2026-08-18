package inkspire.morphic.feature.settings.effects

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.backdrop.liquidGlassSupported
import inkspire.morphic.core.model.BackdropBlurTone
import inkspire.morphic.core.model.BackdropEffect
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.data.settings.SettingsRepository
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
import kotlinx.coroutines.launch

/**
 * The chooser's vocabulary — one row per thing a user can pick, which is **not** one per model variant.
 *
 * `BackdropEffect.Blur` is one variant with a `tone`, because light and dark share every parameter and differ in a
 * color; but "Light blur" and "Dark blur" are two things to pick between, exactly as they were in L1's flat enum. So
 * the split lives here, in the section that draws the chips, rather than being pushed back into the model.
 *
 * **This is not the stored enum coming back.** What is persisted is still the sealed `BackdropEffect`; this exists for
 * the length of one screen and never reaches storage. The distinction matters because collapsing L1's
 * `WallpaperEffect` + `WallpaperEffectParams` pair into one sealed type is a decision `core:model` already made, and a
 * chip list is not a reason to undo it.
 */
internal enum class BackdropOption { PLAIN, LIGHT_BLUR, DARK_BLUR, MATERIAL_YOU, LIQUID_GLASS }

/** Which chip [this] shows as selected. */
internal val BackdropEffect.option: BackdropOption
    get() = when (this) {
        is BackdropEffect.Plain -> BackdropOption.PLAIN
        is BackdropEffect.Blur -> when (tone) {
            BackdropBlurTone.LIGHT -> BackdropOption.LIGHT_BLUR
            BackdropBlurTone.DARK -> BackdropOption.DARK_BLUR
        }
        is BackdropEffect.MaterialYou -> BackdropOption.MATERIAL_YOU
        is BackdropEffect.LiquidGlass -> BackdropOption.LIQUID_GLASS
    }

/**
 * What the effects section shows.
 *
 * @property effect the stored choice and its parameters — which also decides *which* sliders the section draws, since
 *   a variant carries only its own.
 * @property liquidGlassAvailable whether this device can render the shader (API 33+). False hides the chip and shows
 *   the reason, rather than offering an effect that would silently come out as a plain blur.
 * @property backdropImage the wallpaper blurred at the **stored** strength — what the preview samples. Null when there
 *   is nothing the launcher may claim is on screen, which the preview renders as its scrim exactly as a real surface
 *   does. Blurred at the stored strength rather than a dragged one, which is what makes the blur slider's effect on the
 *   preview land on release: the blur lives in the bitmap, and re-baking one per frame is a different piece of work.
 * @property backdropAccent the wallpaper's representative color, which the washes are blended toward. Without it
 *   `MaterialYou` previews as gray, since this launcher's `ColorScheme` is monochrome by design.
 */
internal data class EffectsState(
    val effect: BackdropEffect = BackdropEffect.Default,
    val liquidGlassAvailable: Boolean = true,
    val backdropImage: Bitmap? = null,
    val backdropAccent: Int? = null,
)

/**
 * Screen-level state holder for the **effects** section: the one global backdrop choice, and its parameters.
 *
 * The section that finally writes `backdropEffect`, which S5f-2 deliberately left read-only. Every edit is a
 * whole-value write (see `SettingsRepository.setBackdropEffect`), so there is one command here rather than one per
 * parameter — the sliders build the new value with `copy` and hand it over.
 *
 * **Selecting a chip preserves what it can.** Switching a blur's tone keeps its strength and tint, because both are
 * the same variant; switching *between* variants cannot, since nothing stores the parameters of an effect that is not
 * selected. That is the sealed type's trade and it is stated in the repository, not worked around here — an
 * in-memory stash would survive a chip tap and quietly not survive leaving the screen, which is worse than a rule.
 */
internal class EffectsViewModel(
    private val settingsRepository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    /**
     * Which way the device is held, reported by the pane.
     *
     * The backdrop cannot derive it — a **rotating** wallpaper is two pictures, so "the wallpaper" is not one image
     * until you say which orientation. Reported rather than read here for the reason every other surface reports its
     * own: the composable is where the window is, and a state holder that reaches for one has a `Context` in it.
     */
    private val orientation = MutableStateFlow(Orientation.PORTRAIT)

    val state: StateFlow<EffectsState> = combine(
        settingsRepository.backdropEffect,
        previewBackdrop(),
        wallpaperRepository.accentColor,
    ) { effect, image, accent ->
        EffectsState(
            effect = effect,
            liquidGlassAvailable = liquidGlassSupported,
            backdropImage = image,
            backdropAccent = accent,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        EffectsState(liquidGlassAvailable = liquidGlassSupported),
    )

    /** Reports the orientation the pane is drawn in, so a rotating pair's right half is previewed. */
    fun setOrientation(value: Orientation) {
        orientation.value = value
    }

    /**
     * The wallpaper blurred at the stored strength — the picture the preview samples.
     *
     * The same shape `ShellViewModel` uses for a panel, and deliberately the *same request*, so the preview is showing
     * the picture the launcher's own panels are showing rather than one made for it. Keyed on the strength alone, so a
     * tint or a lens parameter moving re-blurs nothing — those are draw-time reads, which is exactly why they preview
     * per frame while this one lands on commit.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun previewBackdrop(): Flow<Bitmap?> = settingsRepository.backdropEffect
        .map { it.blurStrength }
        .distinctUntilChanged()
        .flatMapLatest { wallpaperRepository.backdrop(it, orientation) }

    /** Switches to [option], carrying the current parameters across wherever the variant is the same. */
    fun select(option: BackdropOption) {
        val current = state.value.effect
        val next = when (option) {
            BackdropOption.PLAIN -> current as? BackdropEffect.Plain ?: BackdropEffect.Plain()
            BackdropOption.LIGHT_BLUR -> current.asBlur(BackdropBlurTone.LIGHT)
            BackdropOption.DARK_BLUR -> current.asBlur(BackdropBlurTone.DARK)
            BackdropOption.MATERIAL_YOU -> current as? BackdropEffect.MaterialYou ?: BackdropEffect.MaterialYou()
            BackdropOption.LIQUID_GLASS -> current as? BackdropEffect.LiquidGlass ?: BackdropEffect.LiquidGlass()
        }
        set(next)
    }

    /** Writes [effect] — what every slider's commit calls, having built the new value with `copy`. */
    fun set(effect: BackdropEffect) {
        viewModelScope.launch { settingsRepository.setBackdropEffect(effect) }
    }

    /** [tone]'s blur, keeping this effect's strength and tint when it is already a blur. */
    private fun BackdropEffect.asBlur(tone: BackdropBlurTone): BackdropEffect.Blur =
        (this as? BackdropEffect.Blur)?.copy(tone = tone) ?: BackdropEffect.Blur(tone = tone)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
