package inkspire.morphic.core.model

/**
 * The animation played when navigating between the home surface and a side surface (see [Surface] /
 * [HomeEdge]). A single, global choice. Values are added incrementally as each animation is implemented.
 *
 * - [SLIDE]: the surfaces slide horizontally, the incoming one following the finger.
 * - [PARALLAX]: incoming and outgoing surfaces move at different speeds for a sense of depth.
 * - [ZOOM]: the outgoing surface scales down as the incoming one scales up into place.
 * - [DEPTH]: the outgoing surface recedes (shrinks + fades) while the incoming one comes forward.
 * - [FADE]: a straight cross-fade, no motion.
 * - [RISE]: the incoming surface rises up over the stationary one.
 */
enum class SurfaceTransition { SLIDE, PARALLAX, ZOOM, DEPTH, FADE, RISE }
