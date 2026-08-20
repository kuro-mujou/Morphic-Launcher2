package inkspire.morphic.feature.settings.effects

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.designsystem.backdrop.liquidGlassSupported
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
 * The chooser's vocabulary — **two entries, where there were five.**
 *
 * "Plain", "Light blur", "Dark blur" and "Material You" were four ways to say *blurred wallpaper with a wash on it*,
 * differing only in the color of the wash; that is a [BackdropTint] now, so what is left to choose between is the two
 * things that are genuinely unlike each other. A blur softens what is behind it. A lens bends it.
 *
 * Still the section's own type rather than the model's, for the reason the five-entry version was: what is persisted is
 * the sealed `BackdropEffect`, and a chooser is a screen's way of naming things, not storage's.
 */
internal enum class EffectKind { BLUR, GLASS }

/** Which entry [this] shows as selected. */
internal val BackdropEffect.kind: EffectKind
    get() = when (this) {
        is BackdropEffect.Blur -> EffectKind.BLUR
        is BackdropEffect.LiquidGlass -> EffectKind.GLASS
    }

/**
 * What the effects section shows.
 *
 * @property effect the stored choice and its parameters — which also decides *which* sliders the section draws, since
 *   a variant carries only its own.
 * @property liquidGlassAvailable whether this device can render the shader (API 33+). False hides the chip and shows
 *   the reason, rather than offering an effect that would silently come out as a plain blur.
 * @property backdropImage the wallpaper blurred at the **stored** strength, at the size a surface samples — what the
 *   preview settles on. Null when there is nothing the launcher may claim is on screen, which the preview renders as its
 *   scrim exactly as a real surface does.
 * @property backdropAccent the wallpaper's representative color, which the washes are blended toward. Without it the
 *   wallpaper wash previews as gray, since this launcher's `ColorScheme` is monochrome by design.
 * @property draggingImage the same wallpaper at the strength a **finger** is on, kept at a quarter of the screen so it
 *   can be re-blurred inside a frame — see `WallpaperRepository.backdropPreview`. The pane draws it only while the blur
 *   slider is actually moving, and [backdropImage] the rest of the time: this one is a frame of a gesture, not a picture
 *   to decide against, and at the sharp end of the slider it holds less of the wallpaper than the settled one does.
 */
internal data class EffectsState(
    val effect: BackdropEffect = BackdropEffect.Default,
    val liquidGlassAvailable: Boolean = true,
    val backdropImage: Bitmap? = null,
    val backdropAccent: Int? = null,
    val draggingImage: Bitmap? = null,
)

/**
 * Screen-level state holder for the **effects** section: the one global backdrop choice, and its parameters.
 *
 * The section that finally writes `backdropEffect`, which S5f-2 deliberately left read-only. Every edit is a
 * **transform** applied inside the write rather than a value computed here — see [editBlur] for the lost update that
 * forced it — so there is one command per variant rather than one per parameter, and a control says what to change
 * without saying what everything else was.
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

    /**
     * The blur strength a finger is on, or null when none is.
     *
     * A `StateFlow` rather than a channel of events, and that is what makes the live preview self-limiting: it
     * **conflates**, so the blur downstream runs on the newest value and silently drops the ones it was too slow for.
     * A drag therefore produces as many frames as the machine can manage and never a backlog — the correction
     * `IconPreview` needed the hard way, had for free here by picking the right holder.
     */
    private val draggedStrength = MutableStateFlow<Float?>(null)

    val state: StateFlow<EffectsState> = combine(
        settingsRepository.backdropEffect,
        settledBackdrop(),
        wallpaperRepository.accentColor,
        draggingBackdrop(settingsRepository),
    ) { effect, settled, accent, dragging ->
        EffectsState(
            effect = effect,
            liquidGlassAvailable = liquidGlassSupported,
            backdropImage = settled,
            backdropAccent = accent,
            draggingImage = dragging,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        EffectsState(liquidGlassAvailable = liquidGlassSupported),
    )

    /**
     * Reports the blur strength under the finger, or null when the gesture is over.
     *
     * Called from the slider's `onPreview`, beside the pane's own dragged-effect state — the pane needs the whole effect
     * to draw the wash live, and this needs the one number that costs a re-blur.
     */
    fun previewStrength(value: Float?) {
        draggedStrength.value = value
    }

    /** Reports the orientation the pane is drawn in, so a rotating pair's right half is previewed. */
    fun setOrientation(value: Orientation) {
        orientation.value = value
    }

    /**
     * The wallpaper blurred at the **stored** strength — the picture the preview settles on.
     *
     * The same shape `ShellViewModel` uses for a panel, and deliberately the *same request*, so what the preview settles
     * to is the picture the launcher's own panels are showing rather than one made for it. Keyed on the strength alone,
     * so a tint or a lens parameter moving re-blurs nothing — those are draw-time reads.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun settledBackdrop(): Flow<Bitmap?> = settingsRepository.backdropEffect
        .map { it.blurStrength }
        .distinctUntilChanged()
        .flatMapLatest { wallpaperRepository.backdrop(it, orientation) }

    /**
     * The wallpaper blurred at whatever strength the finger is on — one decode, a blur per frame.
     *
     * **The strength it follows falls back to the stored one**, so the picture is valid before any drag begins and the
     * decode happens once when the pane opens rather than at the start of every gesture. Which also means this flow is
     * always live while the pane is: the *pane* decides when to draw from it, because only the pane knows whether the
     * finger is on the blur slider or on a tint.
     */
    private fun draggingBackdrop(settingsRepository: SettingsRepository): Flow<Bitmap?> {
        val strengths = combine(
            draggedStrength,
            settingsRepository.backdropEffect.map { it.blurStrength },
        ) { dragged, stored -> dragged ?: stored }
        return wallpaperRepository.backdropPreview(strengths.distinctUntilChanged(), orientation)
    }

    /**
     * Switches to [kind], **carrying the blur across**, which is the one parameter both effects have.
     *
     * `Blur.strength` and `LiquidGlass.blur` are the same quantity under two names — how far the sampled wallpaper is
     * softened — so a user who has settled on a softness keeps it when they go to see what the lens does with it.
     * Everything else is genuinely per-effect and cannot be carried: the sealed type's trade, stated in
     * `SettingsRepository.updateBackdropEffect` and much smaller now that there are two variants rather than five.
     *
     * Selecting the kind that is already current is a no-op rather than a reset, so a stray tap on the segmented
     * control cannot discard a tuned lens — and the test is made against the **stored** value inside the write, not
     * against the last one this holder saw. See [editBlur] for why that distinction is the whole of this file's
     * correctness.
     */
    fun select(kind: EffectKind) = edit { current ->
        if (current.kind == kind) {
            current
        } else {
            when (kind) {
                EffectKind.BLUR -> BackdropEffect.Blur(strength = current.blurStrength)
                EffectKind.GLASS -> BackdropEffect.LiquidGlass(blur = current.blurStrength)
            }
        }
    }

    /**
     * Applies [transform] to the stored blur — **the only way this screen changes one of its parameters.**
     *
     * **A transform rather than a value, because a value is a lost update.** A control builds its next state with `copy`
     * off the effect it last *saw*, and seeing it means a flow emission: an edit issued before the previous one has come
     * back round carries the previous one's fields with it. That is not theoretical — tapping a tint swatch and then
     * pressing a stepper wrote the old tint back over the new one, because the stepper's `copy` still held the pre-tap
     * effect. The steppers made it easy to reach because they are fast: a drag lasts long enough for the store to catch
     * up, a press does not.
     *
     * So a control says *what to change* and nothing else, and the old value is read where it is current — inside the
     * write. See `SettingsRepository.updateBackdropEffect`.
     *
     * **A non-blur current value is left alone rather than converted.** If the effect changed kind while an edit was in
     * flight, that edit was about an effect that is no longer selected, and applying it would resurrect the one the user
     * just left.
     */
    fun editBlur(transform: (BackdropEffect.Blur) -> BackdropEffect.Blur) =
        edit { current -> if (current is BackdropEffect.Blur) transform(current) else current }

    /** [editBlur] for the lens, with the same reasoning and the same guard. */
    fun editGlass(transform: (BackdropEffect.LiquidGlass) -> BackdropEffect.LiquidGlass) =
        edit { current -> if (current is BackdropEffect.LiquidGlass) transform(current) else current }

    private fun edit(transform: (BackdropEffect) -> BackdropEffect) {
        viewModelScope.launch { settingsRepository.updateBackdropEffect(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
