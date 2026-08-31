package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.abs

/**
 * Which of a palette's stops read as a different color from a given one — the shared answer to "will this be visible on
 * that ground".
 *
 * **Merely *different* is not enough, and that is what makes this worth sharing.** A palette here runs light to dark by
 * convention, so neighbouring stops are neighbouring tones: a shape one stop from its ground is a shape you have to
 * hunt for, and a line one stop from the ground is one that fades out along its length. Neither fails loudly — the
 * wallpaper just quietly has fewer shapes, or fewer lines, than it was asked for. [MinGap] is the smallest separation
 * that reads at a glance across the curated palettes here.
 *
 * Both designs that need it arrived at the same rule for the same reason, so it lives in one place: [BauhausGenerator]
 * asks per tile, since each tile picks its own ground, and [RibbonsGenerator] asks once for the frame's ground and
 * takes the answer as the ramp its bundle is colored along.
 */
internal object StopContrast {

    /** How far apart two stops must sit in the palette to read as two colors rather than two tones of one. */
    const val MinGap = 2

    /**
     * The stops out of `0 until stops` that read against [ground], ascending.
     *
     * **Degrades rather than empties**, which matters because the color modes hand this palettes of every length: a
     * bichromatic palette has two stops and no pair [MinGap] apart, so it falls back to merely different — those two
     * *are* the furthest apart it has — and a single-color palette falls back to the ground itself, having no second
     * color to offer and nothing visible to draw either way.
     */
    fun readableAgainst(ground: Int, stops: Int): IntArray {
        val far = (0 until stops).filter { abs(it - ground) >= MinGap }
        val usable = far.ifEmpty { (0 until stops).filter { it != ground } }
        return usable.ifEmpty { listOf(ground) }.toIntArray()
    }
}
