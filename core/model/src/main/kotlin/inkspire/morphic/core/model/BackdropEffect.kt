package inkspire.morphic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The tone of a frosted [BackdropEffect.Blur] — which colour its translucent overlay leans toward.
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
 * another effect's parameters. All strengths are normalised `0..1`; each effect maps them to its own units
 * (blur radius, overlay alpha, refraction px, …). Defaults reproduce the baseline look.
 *
 * **Serialized polymorphically, with short stable names.** It is stored as a `data:settings` slice, so the
 * discriminator ends up in a user's blob; the default would be the fully-qualified class name, which a rename would
 * silently invalidate. `@SerialName` pins it to the concept instead. An unreadable blob falls back to [Default] with a
 * log, which is `SettingsSlice`'s existing behaviour and the reason a retired variant needs no migration.
 *
 * **Three of the four render; [LiquidGlass] is the gap** (S5f-3 — an AGSL shader), and until then it degrades to a
 * plain blurred crop, which is L1's own fallback on every device below API 33.
 */
@Serializable
sealed interface BackdropEffect {

    /** No backdrop effect — surfaces are clear over the content beneath. */
    @Serializable
    @SerialName("none")
    data object None : BackdropEffect

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
     * A blur washed in the **wallpaper's own primary colour** — the one effect whose subject is that colour.
     *
     * **The deliberate exception to the monochrome palette rule**, and worth stating as one. That rule makes chrome
     * greyscale *so that* the wallpaper and the app icons carry the colour, which reads as an argument against a
     * wallpaper-hued wash — but it is a rule about chrome the user did not ask for, and this is an effect they pick.
     * The colour it takes is the wallpaper's, so it is the wallpaper carrying the colour rather than the theme
     * inventing one.
     *
     * **Not the OS dynamic palette, which is how L1 got this above API 31.** L1's launcher ran a normal M3 dynamic
     * scheme, so `colorScheme.primary` *was* a wallpaper-derived hue; L2 feeds MaterialTheme a **monochrome** scheme
     * bridged from `MorphicColors`, so the same expression returns grey. `WallpaperRepository.accentColor` reads the
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
     * **Unbuilt (S5f-3), and it draws as a plain [Blur] at [blur] strength meanwhile** — which is not a stand-in but
     * L1's own fallback: its shader needs AGSL, so every device below API 33 gets exactly this. Unlike [MaterialYou]
     * there is nothing in principle against it; it is a shader nobody has written here yet.
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

    /** How much the sampled wallpaper is blurred for this effect, `0..1`. Zero for [None], which samples nothing. */
    val blurStrength: Float
        get() = when (this) {
            None -> 0f
            is Blur -> strength
            is MaterialYou -> strength
            is LiquidGlass -> blur
        }

    companion object {

        /**
         * The default: a dark blur at the baseline strength.
         *
         * **L1 defaults to `NONE` and this does not**, which is a choice rather than a slip. The surfaces this reaches
         * today are ones whose *current* look is a flat opaque black — a placeholder, not a design — so a blur is
         * strictly the better of the two; and there is no settings section until S5f-3, so a `None` default would make
         * the feature unreachable rather than off-by-default.
         *
         * It costs nothing where there is nothing to sample: the backdrop is null until the user has given the
         * launcher an image (see `WallpaperRepository.loadBackdrop`), and every frosted surface falls back to its own
         * flat colour until then. A fresh install therefore looks exactly as it did before this landed.
         */
        val Default: BackdropEffect = Blur(tone = BackdropBlurTone.DARK, strength = 0.5f, tint = 0.28f)
    }
}
