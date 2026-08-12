package inkspire.morphic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The tone of a frosted [BackdropEffect.Blur] — which color its translucent overlay leans toward.
 *
 * - [LIGHT]: a white overlay; frosts toward light.
 * - [DARK]: a black overlay; frosts toward dark.
 */
enum class BackdropBlurTone { LIGHT, DARK }

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
 * behind it whatever decoration the user picked, so "blur or not" was never really the choice being offered. [Plain]
 * is the variant with no wash at all — which is why it is not called `None`.
 */
@Serializable
sealed interface BackdropEffect {

    /**
     * A blur with **no wash over it** — the wallpaper, softened, and nothing else.
     *
     * **Named for what it does, not for what it lacks.** It was `None`, from a model in which the effect decided
     * whether a surface sampled the wallpaper *at all*; under the current one every variant blurs and the effect
     * chooses the wash, so "none" would have meant "it still blurs, but no color" — the kind of name that reads as
     * a bug six months on. The `@SerialName` deliberately stays `"none"`: it is a discriminator in a user's stored
     * blob, and this is a rename rather than a change of meaning to anything already saved.
     *
     * @property strength Blur amount, `0..1` — the one thing left to tune once there is no wash.
     */
    @Serializable
    @SerialName("none")
    data class Plain(val strength: Float = 0.5f) : BackdropEffect

    /**
     * A frosted blur.
     *
     * @property tone Whether the overlay leans [BackdropBlurTone.LIGHT] (white) or [BackdropBlurTone.DARK] (black).
     * @property strength Blur amount, `0..1`.
     * @property tint Overlay alpha, `0..1` — applied as white when [tone] is [BackdropBlurTone.LIGHT], black when [BackdropBlurTone.DARK].
     */
    @Serializable
    @SerialName("blur")
    data class Blur(
        val tone: BackdropBlurTone,
        val strength: Float = 0.5f,
        val tint: Float = 0.22f,
    ) : BackdropEffect

    /**
     * A blur washed in the **wallpaper's own primary color** — the one effect whose subject is that color.
     *
     * **The deliberate exception to the monochrome palette rule**, and worth stating as one. That rule makes chrome
     * grayscale *so that* the wallpaper and the app icons carry the color, which reads as an argument against a
     * wallpaper-hued wash — but it is a rule about chrome the user did not ask for, and this is an effect they pick.
     * The color it takes is the wallpaper's, so it is the wallpaper carrying the color rather than the theme
     * inventing one.
     *
     * **Not the OS dynamic palette, which is how L1 got this above API 31.** L1's launcher ran a normal M3 dynamic
     * scheme, so `colorScheme.primary` *was* a wallpaper-derived hue; L2 feeds MaterialTheme a **monochrome** scheme
     * bridged from `MorphicColors`, so the same expression returns gray. `WallpaperRepository.accentColor` reads the
     * wallpaper directly instead, on every API. [Blur] carries the same hue at lower strength — see `tintOf`.
     *
     * @property strength Blur amount, `0..1`.
     * @property tint Overlay alpha, `0..1`.
     */
    @Serializable
    @SerialName("material_you")
    data class MaterialYou(
        val strength: Float = 0.5f,
        val tint: Float = 0.5f,
    ) : BackdropEffect

    /**
     * A refractive "liquid glass" effect: the backdrop bends and brightens the content behind it, like a lens.
     *
     * **A lens needs an edge to bend light at, so at full screen it is not one.** The refraction band is a rim on a
     * bounded rounded rect; across a whole screen that rim falls under the system bars, where it costs a shader and
     * shows almost nothing. A full-screen surface therefore renders this as its blur plus its [vibrancy] — a
     * saturation boost, which is what makes a frosted sheet read as glass rather than as fog, and what separates it
     * from [Plain]. That is also iOS's own recipe for its materials, and it works on every API where the rim does
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
        val sheen: Float = 0.4f,
    ) : BackdropEffect

    /**
     * How much the sampled wallpaper is blurred for this effect, `0..1`.
     *
     * **Every variant has one now, [Plain] included**, which is the whole of the model change: there is no longer a
     * value of this type that means "sample nothing". A surface with nothing to sample is a surface with no
     * *backdrop* — `LocalBackdrop` being null — which is a different question and one this type never answered.
     */
    val blurStrength: Float
        get() = when (this) {
            is Plain -> strength
            is Blur -> strength
            is MaterialYou -> strength
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
     * frosted panels instead. Switching between `Plain`, the two blurs, Material You and glass is the entire control
     * a user has over it, which is the design this exists to express.
     *
     * **Every variant blurs by the same amount**, and that is load-bearing rather than tidy: the blurred bitmap is
     * produced upstream from this strength, so one shared value means switching variants never re-blurs anything. It
     * is a redraw with a different wash over an identical picture, not a re-decode.
     *
     * What each variant keeps is exactly what distinguishes it: nothing for [Plain], the tone for a [Blur], the
     * wallpaper's hue for [MaterialYou], and the saturation boost for [LiquidGlass] — whose refraction parameters are
     * dropped because a lens needs a rim and there is none at this size (see `wallpaperBackdrop`'s `refracts`).
     */
    val fullScreenFilm: BackdropEffect
        get() = when (this) {
            is Plain -> Plain(strength = FULL_SCREEN_BLUR)
            is Blur -> Blur(tone = tone, strength = FULL_SCREEN_BLUR, tint = FULL_SCREEN_TINT)
            is MaterialYou -> MaterialYou(strength = FULL_SCREEN_BLUR, tint = FULL_SCREEN_HUE_TINT)
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

        /** The white or black wash a full-screen [Blur] carries — a little above the per-surface default's 0.28. */
        private const val FULL_SCREEN_TINT = 0.35f

        /**
         * [MaterialYou]'s, which is higher because its wash *is* the effect: a hue at the blurs' alpha reads as a
         * tinted blur rather than as a colored sheet, which is the whole thing the variant is for.
         */
        private const val FULL_SCREEN_HUE_TINT = 0.45f

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
        val Default: BackdropEffect = Blur(tone = BackdropBlurTone.DARK, strength = 0.5f, tint = 0.28f)
    }
}
