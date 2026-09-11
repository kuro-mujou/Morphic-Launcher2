package inkspire.morphic.core.graphics.wallpaper

import kotlin.math.PI

/**
 * [delta] radians folded into `-π..π` — the same turn, taken the short way round.
 *
 * **Shared by every scrub that interpolates a direction or a phase** — `GlassTree`'s cuts and Vitrall's pane gradients,
 * Waves' ripple phases — because the failure is silent and looks like motion: an angle interpolated the long way
 * spins most of a turn mid-scrub, which reads as the design doing something rather than as arithmetic being wrong.
 */
internal fun shortestTurn(delta: Float): Float {
    var turned = delta % FullTurn
    if (turned > HalfTurn) turned -= FullTurn
    if (turned < -HalfTurn) turned += FullTurn
    return turned
}

/** [from] to [to] radians at [t], the short way round — a turn of ten degrees rather than three hundred and fifty. */
internal fun lerpAngle(from: Float, to: Float, t: Float): Float = from + shortestTurn(to - from) * t

private const val HalfTurn = PI.toFloat()
private const val FullTurn = (2.0 * PI).toFloat()
