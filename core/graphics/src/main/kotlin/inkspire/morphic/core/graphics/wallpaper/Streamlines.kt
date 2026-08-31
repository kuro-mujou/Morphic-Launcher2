package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Path

/**
 * Turns a traced streamline into a canvas path — the shared stroke half of the thin-line family (Flow Field, Flow
 * Lines, Neon Ribbons, and the ribbon designs to come).
 *
 * **The field-stepping is [FlowFieldGenerator.trace]; this is the drawing.** A streamline design is two halves: walk a
 * particle through the flow field (that is `trace`, already shared), then stroke the polyline it left behind (this). The
 * second half was written out identically in two generators — a `moveTo` plus a `lineTo` loop over interleaved `x, y`,
 * scaled to the frame — which is the duplication a third thin-line generator would copy again. Pulled out here so every
 * streamline design scales its points the same way; a transposed axis or an off-by-one over the interleaving would be
 * silently wrong in the picture, and now it can be wrong in only one place.
 */
object Streamlines {

    /**
     * The interleaved unit-square points `[x0, y0, x1, y1, …]` as a [Path] scaled to a `[width] × [height]` frame.
     * Assumes at least one point (two values); a caller filters out streamlines too short to stroke before calling.
     */
    fun pathOf(points: FloatArray, width: Int, height: Int): Path = Path().apply {
        moveTo(points[0] * width, points[1] * height)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] * width, points[i + 1] * height)
            i += 2
        }
    }
}
