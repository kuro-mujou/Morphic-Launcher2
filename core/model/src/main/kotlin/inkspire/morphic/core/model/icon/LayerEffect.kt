package inkspire.morphic.core.model.icon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A visual effect applied to a single layer while compositing — something that changes what the layer *is*.
 *
 * Deliberately an open sealed hierarchy held in a **list**, rather than one fixed nullable field per effect. That
 * is a direct lesson from L1, which hard-coded a column per effect and paid for it in repeated schema churn: here
 * a new effect is a new variant with `@SerialName`, and a stored recipe written by a build that has it still loads
 * on one that does not, because `ignoreUnknownKeys` drops what it cannot read.
 *
 * **Opacity and blend mode are deliberately *not* here.** They live on [IconLayerSpec] as fields, because they
 * describe how a layer joins the stack rather than what it looks like — see [LayerBlend].
 *
 * Shadows and gradient overlays are the next variants; the render path takes them without further change.
 */
/**
 * How a [LayerEffect.Color.tintArgb] is laid onto the layer it tints.
 *
 * **Two modes because a multiply cannot lift a color, and one real case needs lifting.** App-shipped themed-icon
 * layers are not consistent in the wild — some ship a black glyph, some white, some a colored one, some artwork that
 * is not a silhouette at all — and the platform's own contract is that *only their alpha is meaningful*, the consumer
 * tinting them to whatever it wants. A multiply cannot do that: black times any tint is still black, so a black glyph
 * can never be made white. [SOLID] is what makes the app's shipped color stop mattering.
 *
 * Persisted inside the layer set as part of [LayerEffect.Color], so the names are an on-disk contract.
 */
@Serializable
enum class TintMode {

    /**
     * Multiplies each channel by the tint's, pushing the layer toward that color **while keeping its own shading**.
     * The only mode there used to be, and still the right one for tinting artwork that has internal detail.
     */
    @SerialName("multiply")
    MULTIPLY,

    /**
     * Replaces the color outright and **keeps only the alpha**, so the layer becomes a flat silhouette of itself.
     * The platform's own treatment of a themed-icon layer, and the way to make apps that ship different colors agree.
     *
     * Everything that recolors is spent by this: [LayerEffect.Color.hueDegrees], `saturation` and `brightness` all
     * act on channels this then overwrites. That falls out of the matrix arithmetic rather than being special-cased,
     * and it is correct — a flat color has no shading left for them to act on.
     */
    @SerialName("solid")
    SOLID,
}

@Serializable
sealed interface LayerEffect {

    /**
     * Recoloring, as one color matrix: hue rotation, then saturation, then brightness, then an optional [tintArgb].
     *
     * One variant rather than four because they compose into a single matrix and are applied in one pass — splitting
     * them would mean four list entries whose *order* silently changed the result, which is a way to be wrong that
     * this shape simply does not have.
     *
     * **Monochrome is this, not a variant of its own**: `saturation = 0` with a [tintArgb] is a tinted grayscale,
     * which is what L1's monochrome fallback computed. Note that is a different thing from
     * [LayerSource.AppDefaultMonochrome], which swaps in artwork the *app* ships; this recolors whatever is there.
     *
     * @property tintArgb the color [tintMode] applies; null leaves the channels alone. The tint's own alpha is
     *   ignored — [IconLayerSpec] already has opacity, and two ways to set one thing is one too many.
     * @property tintMode how [tintArgb] is applied. Defaults to [TintMode.MULTIPLY], which is what a tint has
     *   always meant here, so every stored recipe reads back unchanged.
     * @property saturation 0 is grayscale, 1 unchanged, above 1 oversaturated.
     * @property brightness a plain multiplier on the color channels; 1 is unchanged.
     * @property hueDegrees rotation around the color wheel, 0 unchanged.
     */
    @Serializable
    @SerialName("color")
    data class Color(
        val tintArgb: Int? = null,
        val saturation: Float = 1f,
        val brightness: Float = 1f,
        val hueDegrees: Float = 0f,
        val tintMode: TintMode = TintMode.MULTIPLY,
    ) : LayerEffect {

        /** True when this would not change a single pixel, so the renderers can skip building a filter. */
        val isIdentity: Boolean
            get() = tintArgb == null && saturation == 1f && brightness == 1f && hueDegrees == 0f
    }

    /**
     * A two-stop linear gradient painted **over the layer and clipped to it** — source-atop, so it colors the
     * artwork rather than covering the icon with a rectangle.
     *
     * @property angleDegrees the direction the gradient runs, clockwise from "straight down". 0 is top-to-bottom.
     * @property strength how strongly it is laid over the layer; 0 is invisible, 1 fully replaces the color. A
     *   separate knob from the stops' own alpha because it is the one a user reaches for, and having to dilute two
     *   colors by hand to soften a gradient is the sort of thing that makes a control feel broken.
     */
    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val startArgb: Int = 0xFFFFFFFF.toInt(),
        val endArgb: Int = 0xFF000000.toInt(),
        val angleDegrees: Float = 0f,
        val strength: Float = 1f,
    ) : LayerEffect {

        /** True when it would paint nothing, which is the only way a gradient is a no-op. */
        val isIdentity: Boolean get() = strength <= 0f
    }
}
