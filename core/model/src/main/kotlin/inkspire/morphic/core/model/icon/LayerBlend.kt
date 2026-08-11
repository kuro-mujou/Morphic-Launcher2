package inkspire.morphic.core.model.icon

import kotlinx.serialization.Serializable

/**
 * How a layer's pixels combine with everything beneath it in the stack.
 *
 * **A compositing property, not a [LayerEffect]** — and the distinction is the reason this sits on
 * [IconLayerSpec] as a field. An effect changes what a layer *is* (tinted, blurred, shadowed); a blend mode and an
 * opacity describe how it *joins* the stack. Every layer has both, always, with a meaningful default, which is
 * what a field is for and what membership of a list is not.
 *
 * The set is L1's, which is the set worth having: enough to build with, short of a full Porter-Duff menu whose
 * exotic entries produce results nobody chose. Persisted by name inside the layer set, so these are a stable
 * on-disk contract.
 */
@Serializable
enum class LayerBlend {
    /** Ordinary source-over painting: the layer covers what is beneath it. */
    NORMAL,

    /** Darkens: white leaves the layer beneath untouched, black flattens it. */
    MULTIPLY,

    /** Lightens: the inverse of [MULTIPLY]. Black is a no-op, white saturates. */
    SCREEN,

    /** Multiplies dark areas and screens light ones — contrast, keeping the underlying tone. */
    OVERLAY,

    /** Keeps whichever is darker, channel by channel. */
    DARKEN,

    /** Keeps whichever is lighter, channel by channel. */
    LIGHTEN,
}
