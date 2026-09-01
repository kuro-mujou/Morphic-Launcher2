package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.Palette
import kotlin.math.max

/**
 * A palette read as a set of tones on the ramp **above its first stop** — for the designs that spend stop 0 on the
 * ground and paint with what is left.
 *
 * **The floor is the whole point, and what it guards against fails at a design's own default.** One tone per stop
 * above the ground lands on the palette's own colors exactly, which is what you want — but the default color mode
 * reduces the palette to *two* stops, leaving a single tone above the ground and nothing to vary. That is the design
 * dead where most users will first see it: Dot Grid drew a flat block with nothing for its dither to trade
 * ([DotGridGenerator], W11e) and Flowing Blobs drew a ground with two lumps on it (W11i). Reading the palette as a
 * continuous ramp and taking at least [Floor] tones of it costs nothing where there are stops to land on — four tones
 * of a five-stop palette *are* its four stops — and gives a two-stop palette three real tones of its own.
 *
 * **Tone `i` of `n` sits at `(i + 1) / n` along the ramp**, so the last tone is the palette's final stop and none is
 * the ground. Shared rather than restated because that off-by-one is invisible when wrong: a tone count or offset one
 * out draws a set of colors slightly beside the ones the user picked, which is a wallpaper nobody can point at.
 */
internal object RampTones {

    /** The fewest tones the ramp is read at, so a palette reduced to two stops still steps rather than going flat. */
    const val Floor = 3

    /**
     * How many tones the ramp above the ground is read at, for a palette of [stops].
     *
     * A single-stop palette answers `0`: it is all ground, and there is nothing above it to paint with. A caller must
     * handle that rather than dividing by it — the honest picture is the bare ground.
     */
    fun countFor(stops: Int): Int = if (stops <= 1) 0 else max(stops - 1, Floor)

    /**
     * The [countFor] tones of [palette] above its ground, from the one nearest the ground to its final stop.
     *
     * Empty for a single-stop palette, for [countFor]'s reason.
     */
    fun aboveGround(palette: Palette): IntArray {
        val count = countFor(palette.size)
        return IntArray(count) { LinearGradientGenerator.colorAt((it + 1f) / count, palette) }
    }
}
