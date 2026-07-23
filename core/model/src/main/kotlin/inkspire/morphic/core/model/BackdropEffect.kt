package inkspire.morphic.core.model

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
 */
sealed interface BackdropEffect {

    /** No backdrop effect — surfaces are clear over the content beneath. */
    data object None : BackdropEffect

    /**
     * A frosted blur.
     *
     * @property tone Whether the overlay leans [BackdropBlurTone.LIGHT] (white) or [BackdropBlurTone.DARK] (black).
     * @property strength Blur amount, `0..1`.
     * @property tint Overlay alpha, `0..1` — applied as white when [tone] is [BackdropBlurTone.LIGHT], black when [BackdropBlurTone.DARK].
     */
    data class Blur(
        val tone: BackdropBlurTone,
        val strength: Float = 0.5f,
        val tint: Float = 0.22f,
    ) : BackdropEffect

    /**
     * A blur tinted with the system dynamic ("Material You") colour.
     *
     * @property strength Blur amount, `0..1`.
     * @property tint Surface-tone overlay alpha, `0..1`.
     */
    data class MaterialYou(
        val strength: Float = 0.5f,
        val tint: Float = 0.5f,
    ) : BackdropEffect

    /**
     * A refractive "liquid glass" effect: the backdrop bends and brightens the content behind it, like a lens.
     *
     * @property blur Lens-source blur amount, `0..1`.
     * @property vibrancy Saturation boost on the refracted content, `0..1`.
     * @property refraction Edge displacement (bend) amount, `0..1`.
     * @property depth How far the refraction band reaches in from the edge, `0..1`.
     * @property dispersion Chromatic aberration — the rainbow rim, `0..1`.
     * @property sheen Rim highlight intensity, `0..1`.
     */
    data class LiquidGlass(
        val blur: Float = 0.4f,
        val vibrancy: Float = 0.5f,
        val refraction: Float = 0.4f,
        val depth: Float = 0.3f,
        val dispersion: Float = 0f,
        val sheen: Float = 0.4f,
    ) : BackdropEffect
}
