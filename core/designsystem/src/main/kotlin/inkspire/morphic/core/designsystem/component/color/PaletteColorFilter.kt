package inkspire.morphic.core.designsystem.component.color

import kotlin.math.sqrt

/**
 * Narrows a palette bank to the palettes that carry a given color — the one axis [PalettePresetBrowser] filters on.
 *
 * **A bank of 357 is scrolled, not scanned, so it needs one way in.** Nobody reads three hundred pills looking for
 * "something green"; they ask for green and read the twenty that come back. That is the whole job here, and it is
 * deliberately *one* axis — a second (warm/cool, light/dark, stop count) would need a second row of chrome over a
 * full-screen preview, and the color is what a person actually arrives with.
 *
 * **Ranked, never bucketed.** The obvious implementation classifies every stop into a hue bucket and matches bucket to
 * bucket; the reason this does not is that a bucket edge is *invisible*. A palette whose only green leans a few
 * degrees to teal simply vanishes from "green" with nothing on screen to say why, and the user's only recourse is to
 * try the neighboring chip and hope. Distance degrades instead: a near-miss still appears, further down. So the chip is
 * a **target color**, a palette qualifies when any stop is within [Nearness] of it, and the order is by how *much* of
 * the palette is that near — which is what puts an all-red ramp above a palette carrying one red accent, and is what
 * the reference studio's list visibly does.
 */
object PaletteColorFilter {

    /**
     * The colors offered as filter chips: three neutrals, then the hue wheel from red round to pink, each family as a
     * dark, a mid and a light target.
     *
     * **Three tones per family rather than one, because lightness is most of the distance.** [distance] is a weighted
     * RGB metric, so a near-black green sits far from a mid green — far enough that one target per hue would leave
     * every dark or pastel palette unreachable. The tones are what make the whole bank addressable; the ranking is
     * what keeps a dark green still visible under the mid-green chip, lower down.
     */
    val chips: List<Int> = listOf(
        0xFF111111, 0xFF808080, 0xFFF5F5F5,
        0xFF7F1D1D, 0xFFE53935, 0xFFFFAFAF,
        0xFF7C3A12, 0xFFF57C00, 0xFFFFCC9E,
        0xFF7A6A10, 0xFFFDD835, 0xFFFBEFA8,
        0xFF1B5E20, 0xFF43A047, 0xFFA5D6A7,
        0xFF0F5F63, 0xFF00ACC1, 0xFFA0E7EA,
        0xFF123B7A, 0xFF1E88E5, 0xFFA8CBF5,
        0xFF4A227A, 0xFF8E44D0, 0xFFD6BBEF,
        0xFF7B1B4B, 0xFFE5399B, 0xFFF8B4D2,
    ).map { it.toInt() }

    /**
     * How near a stop must sit to a chip to count as carrying it, as a fraction of [distance]'s full range.
     *
     * Tuned against the shipped bank rather than derived — measured per chip over all 357: most return 20-70, none
     * returns nothing, and the widest are the *light* chips (near-white ~135), because a great many palettes carry one
     * near-white stop and near-whites all sit close together under [distance]. That width is the ranking's job rather
     * than the threshold's: a palette with one white among eight has a share of an eighth and sinks, where a genuinely
     * pale palette leads. Loosening this to `0.12` roughly doubles every count and starts returning half the bank.
     */
    internal const val Nearness = 0.10f

    /**
     * What [distance] divides by to land in `0f..1f` — black to white under this weighting, which comes to `764.83`,
     * rounded up so that pair lands a hair *under* `1` rather than over. The cube's far corners reach past it (pure
     * blue to pure red is the widest), so the result is clamped rather than normalized by a maximum nothing real ever
     * approaches and every real pair crowding into the low end.
     */
    private const val CubeSpan = 765f

    /**
     * The palettes in [palettes] carrying [chip], most saturated in it first.
     *
     * The order is by the **share** of a palette's stops within [Nearness], then by its nearest stop — share first
     * because a palette that is mostly this color is what "filter by green" means, and the nearest stop only to settle
     * ties between palettes carrying the same proportion. Ties beyond that keep the bank's own order, so the featured
     * palettes stay ahead of the harvested banks.
     */
    fun matching(palettes: List<ColorPalette>, chip: Int): List<ColorPalette> =
        palettes
            .map { it to score(it, chip) }
            .filter { (_, score) -> score.carried > 0 }
            .sortedWith(
                compareByDescending<Pair<ColorPalette, Score>> { it.second.carried.toFloat() / it.first.colors.size }
                    .thenBy { it.second.nearest },
            )
            .map { it.first }

    /** How many of a palette's stops land within [Nearness] of the chip, and how near its nearest one gets. */
    private data class Score(val carried: Int, val nearest: Float)

    private fun score(palette: ColorPalette, chip: Int): Score {
        var carried = 0
        var nearest = 1f
        palette.colors.forEach { stop ->
            val d = distance(stop, chip)
            if (d <= Nearness) carried++
            if (d < nearest) nearest = d
        }
        return Score(carried, nearest)
    }

    /**
     * How far apart two opaque colors look, `0f` (identical) to `1f` (the width of the cube).
     *
     * The "redmean" weighting — the red channel's weight rising and the blue's falling with the pair's mean red — which
     * is a well-known cheap stand-in for a perceptual metric. Chosen over plain RGB because plain RGB puts pure blue
     * and pure green closer together than a person reads them, and over a real Lab conversion because this runs over
     * every stop of 357 palettes on each chip tap and needs no color-space machinery to be right enough to *rank* by.
     *
     * Alpha is ignored: a filter asks what a stop looks like, and a palette's translucent stop is still that hue.
     */
    internal fun distance(a: Int, b: Int): Float {
        val ar = a shr 16 and 0xFF
        val ag = a shr 8 and 0xFF
        val ab = a and 0xFF
        val br = b shr 16 and 0xFF
        val bg = b shr 8 and 0xFF
        val bb = b and 0xFF
        val meanRed = (ar + br) / 2f
        val dr = (ar - br).toFloat()
        val dg = (ag - bg).toFloat()
        val db = (ab - bb).toFloat()
        val weighted = (2f + meanRed / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - meanRed) / 256f) * db * db
        return (sqrt(weighted) / CubeSpan).coerceAtMost(1f)
    }
}
