package inkspire.morphic.core.graphics.wallpaper

import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * Walking a particle through a flow field, and stroking the polyline it leaves — the shared half of the thin-line
 * family (Flow Field, Flow Lines, Ribbon Flow, and the ribbon designs to come).
 *
 * **Both halves live here because a streamline design is exactly these two steps.** The stepping is the part that is
 * silently wrong when a sign is flipped or a bounds check runs a step late; the drawing is a `moveTo` plus a `lineTo`
 * loop over interleaved `x, y` that three generators had each written out. [trace] used to sit on
 * [FlowFieldGenerator], which was true while that design was its main caller and stopped being true the moment it was
 * rebuilt on collision-stopped trails and no longer traces at all.
 */
object Streamlines {

    /**
     * A streamline through [angleAt], from ([startX], [startY]) — up to [steps] points, each a [stepLength] hop in
     * the field's direction, stopping the moment it leaves the unit square.
     *
     * Returns interleaved `x, y` in unit-square coordinates. **The first point is recorded before the bounds check**,
     * so a particle that starts at the very edge still contributes a mark rather than vanishing; the check then ends
     * the line as soon as it wanders out, which is what keeps a streak from smearing along the frame's edge.
     */
    fun trace(
        startX: Float,
        startY: Float,
        steps: Int,
        stepLength: Float,
        angleAt: (Float, Float) -> Float,
    ): FloatArray {
        val points = ArrayList<Float>(steps * 2)
        var x = startX
        var y = startY
        var step = 0
        while (step < steps) {
            points.add(x)
            points.add(y)
            if (x !in 0f..1f || y !in 0f..1f) break

            val angle = angleAt(x, y)
            x += cos(angle) * stepLength
            y += sin(angle) * stepLength
            step++
        }
        return points.toFloatArray()
    }

    /**
     * The interleaved unit-square points `[x0, y0, x1, y1, …]` as a [Path] scaled to a `[width] × [height]` frame.
     * Assumes at least one point (two values); a caller filters out streamlines too short to stroke before calling.
     */
    fun pathOf(points: FloatArray, width: Int, height: Int): Path =
        polyline(points, width.toFloat(), height.toFloat())

    /**
     * The same, for points that are **already in pixels** — the designs that work in pixels so their shapes stay
     * circular on a non-square frame ([PolygonCascadeGenerator]'s rosette, [FlowFieldGenerator]'s trails, whose
     * spacing rule is a distance and so cannot live in a stretched unit square).
     */
    fun pathOfPixels(points: FloatArray): Path = polyline(points, 1f, 1f)

    /** The one `moveTo` + `lineTo` loop both entry points run, over interleaved `x, y` scaled by [sx] and [sy]. */
    private fun polyline(points: FloatArray, sx: Float, sy: Float): Path = Path().apply {
        moveTo(points[0] * sx, points[1] * sy)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] * sx, points[i + 1] * sy)
            i += 2
        }
    }
}
