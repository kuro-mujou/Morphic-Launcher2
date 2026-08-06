package inkspire.morphic.core.designsystem.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import inkspire.morphic.core.model.HomeEdge
import kotlin.math.roundToInt

/**
 * The HOME surface plus the side surfaces off its edges, panned between by a swipe.
 *
 * HOME sits in the centre; each entry in [sideContent] is a [SurfaceBinding] parked just off the matching
 * [HomeEdge], off-screen until swiped in. A swipe from an edge slides HOME aside and the side surface in; a
 * swipe back returns to HOME (also handled by the system back button). Which edges are swipeable is exactly
 * [sideContent]'s keys — an edge with no entry can't be opened — and each binding's [SurfaceBinding.swipe]
 * decides whether one finger or two is needed to reach it.
 *
 * **First cut, deliberately minimal.** Only the SLIDE transform is wired (HOME and the side surface translate
 * together, no scale/fade/parallax), the surfaces are whatever the caller passes, and there is no frosted
 * backdrop, drag-out, or nested-scroll return-edge handling yet. Those were all tangled into L1's single
 * `CrossPager`; here they are follow-ups on top of this clean base. This is why the side surfaces are an open
 * `Map<HomeEdge, …>` slot rather than the real HOME/APPS layouts — the demo drops plain boxes in, the real
 * layouts come later.
 *
 * @param state the pan position + operations; also learns the swipeable edges + their finger policy here.
 * @param sideContent the binding for each swipeable edge. Absent edge = not swipeable.
 * @param overlay drawn **above [center] and below every side surface**, filling the viewport and *not* panned with
 *   either. The frost a side surface is read against lives here (`SurfaceBackdropLayer`): it has to cover HOME, be
 *   covered by what arrives, and — the reason it cannot be a modifier on either — move differently from both, since
 *   the content slides while the frost merely fades. Empty by default, which is what the harness passes.
 * @param center the HOME surface, always present in the centre.
 */
@Composable
fun SurfacePager(
    state: SurfacePagerState,
    modifier: Modifier = Modifier,
    sideContent: Map<HomeEdge, SurfaceBinding> = emptyMap(),
    overlay: @Composable () -> Unit = {},
    center: @Composable () -> Unit,
) {
    // Teach the state which edges have a surface (its drag/settle clamps can't open an empty edge) and each
    // edge's open/close finger policy (the gesture reads it to decide whether a one-finger swipe counts).
    // Mapping down to the value type keeps the published map structurally stable across recompositions.
    // SideEffect runs after a successful composition — the safe place to publish composition inputs into
    // snapshot state.
    SideEffect { state.edgeSwipes = sideContent.mapValues { EdgeSwipe(it.value.openSwipe, it.value.closeSwipe) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                state.viewportWidth = it.width
                state.viewportHeight = it.height
            }
            .surfacePagerGesture(state),
    ) {
        Box(Modifier.fillMaxSize().surfaceSlide(state) { centerSlide(state.panX, state.panY) }) {
            center()
        }
        // Between the two, and with no `surfaceSlide` of its own — see [overlay].
        overlay()
        for ((edge, binding) in sideContent) {
            Box(Modifier.fillMaxSize().surfaceSlide(state) { sideSlide(edge, state.panX, state.panY) }) {
                binding.content()
            }
        }
    }
}

/**
 * Offsets a full-screen surface slot by a [fraction] of the viewport (`1f` == one viewport). The [fraction]
 * lambda — and the viewport size it scales against — are read *inside* [offset]'s layout-phase block, so a pan
 * change re-places the slot without recomposing. Every slot fills the same parent, so the viewport dimensions
 * on [state] are also each slot's own size (L1 threaded these in the same way rather than reaching for `size`,
 * which `offset` doesn't expose).
 */
private fun Modifier.surfaceSlide(state: SurfacePagerState, fraction: () -> Offset): Modifier = offset {
    val f = fraction()
    IntOffset((f.x * state.viewportWidth).roundToInt(), (f.y * state.viewportHeight).roundToInt())
}

/**
 * Where HOME sits: it simply slides opposite the pan, so panning toward a side surface pushes HOME the other
 * way by the same amount.
 */
private fun centerSlide(panX: Float, panY: Float): Offset = Offset(-panX, -panY)

/**
 * Where a side surface sits: parked one full viewport off its edge, then slid by the pan so it tracks in as
 * HOME slides out. At rest (pan `0`) each side surface is exactly off-screen; at full pan it covers the
 * viewport. E.g. LEFT starts at `x = -1` and reaches `0` when [panX] hits `-1`.
 */
private fun sideSlide(edge: HomeEdge, panX: Float, panY: Float): Offset = when (edge) {
    HomeEdge.LEFT -> Offset(-(1f + panX), -panY)
    HomeEdge.RIGHT -> Offset(1f - panX, -panY)
    HomeEdge.TOP -> Offset(-panX, -(1f + panY))
    HomeEdge.BOTTOM -> Offset(-panX, 1f - panY)
}
