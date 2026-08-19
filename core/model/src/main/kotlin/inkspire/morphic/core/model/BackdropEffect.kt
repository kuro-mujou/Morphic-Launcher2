package inkspire.morphic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * **What color a frosted blur is washed in** — the choice the effects section offers as five swatches.
 *
 * This replaces `BackdropBlurTone`, which had two values because light and dark were the only washes a *variant* could
 * express: "no wash" was a separate variant (a `Plain`) and so was the wallpaper's own hue (`MaterialYou`). Four
 * variants that blurred identically and differed only in the color painted over the blur is four names for one effect,
 * and it made the chooser five entries long for two genuinely different things. A wash is a *parameter* of a blur, so
 * it is one here, and the sealed type is down to the two effects that really are unlike: a blur, and a lens.
 *
 * **An enum plus a stored color rather than a sealed type with a payload**, which is the one design call worth stating.
 * A `Custom(argb)` variant would carry the color, and selecting Light would then *discard* it — so trying the other
 * swatches and coming back would hand the user a fresh default instead of the color they mixed. The color lives in
 * [BackdropEffect.Blur.customTintArgb] beside the choice, exactly as the icon studio keeps an effect's parameters while
 * its switch is off, and picking [CUSTOM] again returns to it.
 */
enum class BackdropTint {

    /**
     * No wash at all — the wallpaper, blurred, and nothing over it.
     *
     * The old `Plain` variant, and the naming difference is worth keeping: that one was renamed *away* from `None`
     * because it still blurred, so "none" read as a bug. Here the blur is the effect and the tint is genuinely absent,
     * which makes [NONE] the honest name.
     */
    NONE,

    /** A white-leaning wash: frosts toward light. */
    LIGHT,

    /** A black-leaning wash: frosts toward dark. */
    DARK,

    /**
     * The **wallpaper's own** representative color — the one wash whose subject is the picture under it.
     *
     * `MaterialYou`'s, and the deliberate exception to the design system's monochrome rule: that rule keeps *chrome*
     * grayscale so the wallpaper and the icons carry the color, and this is a decoration the user picked whose whole
     * point is the wallpaper's hue. Labeled **"Wallpaper"** in the section, not "Material You": that is Google's name
     * for the *OS* palette, and this deliberately is not one — the launcher bridges a monochrome `ColorScheme`, so the
     * color is read off the wallpaper rather than taken from a dynamic scheme.
     */
    WALLPAPER,

    /** A color the user mixed, held in [BackdropEffect.Blur.customTintArgb]. */
    CUSTOM,
}

/**
 * How the frosted backdrop behind launcher surfaces renders over whatever sits beneath it (wallpaper,
 * content). A single, global choice.
 *
 * Each variant carries only the tunables that effect actually uses — folding the former separate
 * `WallpaperEffect` enum and flat `WallpaperEffectParams` bag into one type, so an effect can never hold
 * another effect's parameters. All strengths are normalized `0..1`; each effect maps them to its own units
 * (blur radius, overlay alpha, refraction px, …). Defaults reproduce the baseline look.
 *
 * **Serialized polymorphically, with short stable names.** It is stored as a `data:settings` slice, so the
 * discriminator ends up in a user's blob; the default would be the fully-qualified class name, which a rename would
 * silently invalidate. `@SerialName` pins it to the concept instead. An unreadable blob falls back to [Default] with a
 * log, which is `SettingsSlice`'s existing behavior and the reason a retired variant needs no migration.
 *
 * **Every variant blurs; what they differ in is the *wash* over that blur.** That is the model the full-screen
 * backdrop layer forced, and it is the honest one: a surface floating over the wallpaper has to occlude what is
 * behind it whatever decoration the user picked, so "blur or not" was never really the choice being offered. Which
 * wash — including none at all — is [BackdropTint], a parameter of [Blur] rather than a variant of its own.
 */
@Serializable
sealed interface BackdropEffect {

    /**
     * **A blurred wallpaper with a wash over it** — the effect that four variants used to be.
     *
     * `Plain`, `Blur(LIGHT)`, `Blur(DARK)` and `MaterialYou` blurred identically and differed only in the color painted
     * on top, so they are one variant with a [BackdropTint] now. What that buys is not only a shorter chooser: the
     * parameters *survive* a change of wash, where switching variants discarded them (see
     * `SettingsRepository.setBackdropEffect`) — so a user who has tuned a strength and an amount can try all five
     * washes without losing either.
     *
     * @property strength Blur amount, `0..1`.
     * @property tint Which color the wash is. [BackdropTint.NONE] means there is none, which is the old `Plain`.
     * @property tintAmount The wash's alpha, `0..1`. Meaningless when [tint] is [BackdropTint.NONE], which is why the
     *   section shows no control for it there rather than one that does nothing.
     * @property customTintArgb The color [BackdropTint.CUSTOM] uses, kept whichever tint is selected so that choosing
     *   [BackdropTint.CUSTOM] again returns to the color the user mixed rather than to a default. Its alpha is ignored —
     *   [tintAmount] is the alpha, and two ways to set one thing is how they come to disagree.
     */
    @Serializable
    @SerialName("blur")
    data class Blur(
        val strength: Float = 0.5f,
        val tint: BackdropTint = BackdropTint.DARK,
        val tintAmount: Float = 0.3f,
        val customTintArgb: Int = DEFAULT_CUSTOM_TINT,
    ) : BackdropEffect

    /**
     * A refractive "liquid glass" effect: the backdrop bends and brightens the content behind it, like a lens.
     *
     * **A lens needs an edge to bend light at, so at full screen it is not one.** The refraction band is a rim on a
     * bounded rounded rect; across a whole screen that rim falls under the system bars, where it costs a shader and
     * shows almost nothing. A full-screen surface therefore renders this as its blur plus its [vibrancy] — a
     * saturation boost, which is what makes a frosted sheet read as glass rather than as fog, and what separates it
     * from an untinted [Blur]. That is also iOS's own recipe for its materials, and it works on every API where the rim does
     * not. See `Modifier.wallpaperBackdrop`'s `refracts`.
     *
     * The rim remains what a *panel* gets — a popup menu, the widget picker — on API 33+.
     *
     * @property blur Lens-source blur amount, `0..1`.
     * @property vibrancy Saturation boost on the refracted content, `0..1`.
     * @property refraction Edge displacement (bend) amount, `0..1`.
     * @property depth How far the refraction band reaches in from the edge, `0..1`.
     * @property dispersion Chromatic aberration — the rainbow rim, `0..1`.
     * @property sheen Rim highlight intensity, `0..1`.
     */
    @Serializable
    @SerialName("liquid_glass")
    data class LiquidGlass(
        val blur: Float = 0.4f,
        val vibrancy: Float = 0.5f,
        val refraction: Float = 0.4f,
        val depth: Float = 0.3f,
        val dispersion: Float = 0f,
        val sheen: Float = 0.2f,
    ) : BackdropEffect

    /**
     * How much the sampled wallpaper is blurred for this effect, `0..1`.
     *
 * **Both variants have one**, which is the whole of an earlier model change: there is no longer a
 * value of this type that means "sample nothing". A surface with nothing to sample is a surface with no
     * *backdrop* — `LocalBackdrop` being null — which is a different question and one this type never answered.
     */
    val blurStrength: Float
        get() = when (this) {
            is Blur -> strength
            is LiquidGlass -> blur
        }

    /**
     * How much the sampled wallpaper's color is boosted, as a saturation multiplier — `1f` for no change.
     *
     * Only [LiquidGlass] raises it, and that is what gives a full-screen sheet of it a look of its own once the rim
     * is gone: a box blur alone leaves colors muddy, and pushing saturation back up is what reads as glass. The
     * ceiling is a drawing decision rather than a preference, so it lives here with the parameter it scales and not
     * in the shader that was its first consumer.
     */
    val saturation: Float
        get() = when (this) {
            is LiquidGlass -> 1f + vibrancy * MAX_VIBRANCY_BOOST
            else -> 1f
        }

    /**
     * **The full-screen frost this effect casts** — the same variant with its parameters *fixed*.
     *
     * The frost behind an arriving surface is deliberately **not tunable**. It is what a screenful of content is read
     * against, and a strength or tint slider that can make that content unreadable is not a preference worth
     * offering — so choosing the variant chooses the whole look, and the per-variant sliders govern the smaller
 * frosted panels instead. Choosing between a blur and a lens, and which wash the blur carries, is the entire
 * control a user has over it, which is the design this exists to express.
     *
     * **Every variant blurs by the same amount**, and that is load-bearing rather than tidy: the blurred bitmap is
     * produced upstream from this strength, so one shared value means switching variants never re-blurs anything. It
     * is a redraw with a different wash over an identical picture, not a re-decode.
     *
 * What each variant keeps is exactly what distinguishes it: the **wash** for a [Blur] — its color and, where that
 * is [BackdropTint.CUSTOM], which color — and the saturation boost for [LiquidGlass], whose refraction parameters
 * are dropped because a lens needs a rim and there is none at this size (see `wallpaperBackdrop`'s `refracts`). A
 * blur tinted [BackdropTint.NONE] therefore casts a film with no wash on it, which is what `Plain`'s used to be.
     */
    val fullScreenFilm: BackdropEffect
        get() = when (this) {
            is Blur -> copy(strength = FULL_SCREEN_BLUR, tintAmount = FULL_SCREEN_TINT)
            is LiquidGlass -> LiquidGlass(blur = FULL_SCREEN_BLUR, vibrancy = FULL_SCREEN_VIBRANCY)
        }

    companion object {

        /** The most a [LiquidGlass] sheet may push saturation — `vibrancy = 1f` lands here. */
        private const val MAX_VIBRANCY_BOOST = 0.8f

        /**
         * How hard the **full-screen** frost blurs, for every variant — see [fullScreenFilm].
         *
         * Above the per-surface default (0.5), because this one has to hold a whole screen of text back rather than
         * sit behind a card of icons, and because with no slider on it there is no second chance to fix a wallpaper
         * that shows through.
         */
        private const val FULL_SCREEN_BLUR = 0.6f

        /** The wash a full-screen [Blur] carries — a little above the per-surface default of 0.30. */
        private const val FULL_SCREEN_TINT = 0.35f

        /** [LiquidGlass]'s saturation boost at full screen — the half of the effect that survives without a rim. */
        private const val FULL_SCREEN_VIBRANCY = 0.6f

        /**
         * The default: a dark blur at the baseline strength.
         *
         * **L1 defaults to `NONE` and this does not**, which is a choice rather than a slip. The surfaces this reaches
         * today are ones whose *current* look is a flat opaque black — a placeholder, not a design — so a washed blur
         * is strictly the better of the two.
         *
         * It costs nothing where there is nothing to sample: the backdrop is null until the user has given the
         * launcher an image (see `WallpaperRepository.backdrop`), and every frosted surface falls back to its own
         * flat color until then. A fresh install therefore looks exactly as it did before this landed.
         */
        val Default: BackdropEffect = Blur()

        /**
         * Where [BackdropTint.CUSTOM] starts before anyone has mixed anything: a mid gray.
         *
         * Neutral on purpose. A custom wash arriving pre-tinted would look like a choice the user had made, and gray is
         * the one color that reads as "not yet decided" against any wallpaper. Opaque, since [Blur.tintAmount] is the
         * alpha and this carries only the hue.
         */
        const val DEFAULT_CUSTOM_TINT: Int = 0xFF808080.toInt()
    }
}
