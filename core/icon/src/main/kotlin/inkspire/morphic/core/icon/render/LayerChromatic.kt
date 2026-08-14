package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect

/**
 * Which channel goes where in a chromatic split.
 *
 * Shared for the reason the rest of this package is, and here the thing that would drift is not the arithmetic but
 * the *convention*: which channel leads and which trails. Both renderers drawing a red fringe on opposite sides is a
 * difference nobody would call a bug — the icon looks fine either way — right up until the editor and the home screen
 * are seen together.
 *
 * **Nothing new is computed here.** The channel isolations are [ColorMatrices.mix] with a single one in each row,
 * which is exactly what that builder exists for and what [ColorMatrices.scale] structurally cannot express. What this
 * file contributes is the list, the order and the offsets.
 */
object LayerChromatic {

    /**
     * One channel's copy: the [matrix] that isolates it and how far it is displaced.
     *
     * The copies are recombined **additively**, so their order does not change the picture — they are returned in a
     * fixed one anyway, since a list that shuffled would make two renderers hard to compare while debugging.
     */
    data class Fringe(val matrix: FloatArray, val dxPx: Float, val dyPx: Float)

    /**
     * The three copies [split] is drawn as, over a box of [sizePx].
     *
     * **Red leads, blue trails, green stays put.** That is the convention real lens dispersion produces — long
     * wavelengths refract least — and it is the one worth fixing precisely because either direction looks plausible.
     * Green holding still is what keeps the icon recognisably where it was: the eye reads luminance mostly from
     * green, so moving it would shift the whole icon rather than fringe it.
     */
    fun fringes(split: LayerEffect.ChromaticSplit, sizePx: Int): List<Fringe> {
        val dx = split.offsetX * sizePx
        val dy = split.offsetY * sizePx
        return listOf(
            Fringe(RedOnly, dx, dy),
            Fringe(GreenOnly, 0f, 0f),
            Fringe(BlueOnly, -dx, -dy),
        )
    }

    // A single one per row: each output channel takes its own input and nothing else, and alpha passes through — so
    // every copy keeps the layer's silhouette and contributes only its own colour to the sum.
    private val RedOnly = ColorMatrices.mix(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val GreenOnly = ColorMatrices.mix(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f)
    private val BlueOnly = ColorMatrices.mix(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)
}
