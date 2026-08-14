package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * How many copies an extrusion is made of, and how far apart they sit.
 *
 * Shared for the reason every other derivation in this package is: an extrusion that reaches a different distance in
 * the editor than on the home screen is the bug the editor structurally cannot show you. Here the *count* matters as
 * much as the direction — the same depth built from twelve copies and from forty looks like a ribbed edge and a
 * smooth one — so both are decided once, here.
 *
 * **An extrusion is the union of a silhouette with a line segment, and nothing draws that directly.** So it is N
 * copies, and N is the whole of the cost. That is a real cost in the live path, where the copies are re-draws of the
 * layer's content rather than blits of a finished bitmap — see [MaxSteps].
 *
 * Pure float arithmetic, so all of it is unit-testable without an emulator.
 */
object LayerExtrude {

    /**
     * The copies an extrusion is drawn as: [count] of them, each a further ([dxPx], [dyPx]) from the last.
     *
     * So copy `i` sits at `i × (dxPx, dyPx)` for `i` in `1..count`, and the furthest lands exactly at the requested
     * depth however the count was capped — which is what keeps the *reach* honest when the smoothness is not.
     */
    data class Steps(val count: Int, val dxPx: Float, val dyPx: Float)

    /**
     * How [extrude] is built over a box of [sizePx].
     *
     * **One copy per pixel of depth, capped.** A pixel apart is the point at which the stepped edge stops being
     * visible; past the cap the copies spread out and the edge starts to stair, which is a look rather than a
     * failure — and far better than an editor that stops responding while a slider moves.
     */
    fun steps(extrude: LayerEffect.Extrude, sizePx: Int): Steps {
        val depthPx = extrude.depth * sizePx
        if (depthPx <= 0f) return Steps(count = 0, dxPx = 0f, dyPx = 0f)

        val count = ceil(depthPx).toInt().coerceIn(1, MaxSteps)
        val radians = extrude.angleDegrees * Math.PI.toFloat() / 180f
        // Straight down at 0°, which is `LayerGradient.endpoints`' own direction vector — so every angle in the
        // studio means the same thing whatever effect it belongs to.
        return Steps(
            count = count,
            dxPx = sin(radians) * depthPx / count,
            dyPx = cos(radians) * depthPx / count,
        )
    }

    /**
     * How many copies an extrusion may be made of.
     *
     * **Chosen against the live path, which is where this is expensive.** The bake blits a finished bitmap and runs
     * once off the main thread behind a cache; the editor re-runs the layer's *content* for every copy, on every
     * frame of a drag, at preview size. Forty-eight of those is a few milliseconds of real work — deep enough that
     * the stepping is invisible at any sane depth, bounded enough that a slider stays smooth.
     *
     * If it ever proves too slow this is the first effect that should preview from its bake instead, which is what
     * `LayerEffect.drawsLive` exists to say — it is left true only because that fallback is not built yet.
     */
    private const val MaxSteps = 48
}
