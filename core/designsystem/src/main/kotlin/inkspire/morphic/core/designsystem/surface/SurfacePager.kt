package inkspire.morphic.core.designsystem.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import inkspire.morphic.core.model.HomeEdge
import kotlin.math.roundToInt

/**
 * The HOME surface plus the side surfaces off its edges, panned between by a swipe.
 *
 * HOME sits in the center; each entry in [sideContent] is a [SurfaceBinding] parked just off the matching
 * [HomeEdge], off-screen until swiped in. A swipe from an edge slides HOME aside and the side surface in; a
 * swipe back returns to HOME (also handled by the system back button). Which edges are swipeable is exactly
 * [sideContent]'s keys — an edge with no entry can't be opened — and each binding's [SurfaceBinding.swipe]
 * decides whether one finger or two is needed to reach it.
 *
 * **Deliberately minimal.** Only the SLIDE transform is wired (HOME and the side surface translate together, no
 * scale/fade/parallax). That is why the side surfaces are an open `Map<HomeEdge, …>` slot rather than the real
 * HOME/APPS layouts — the harness drops plain boxes in, the shell drops the real screens in.
 *
 * **A side slot is composed only while its surface is on screen at all, or while [retainedEdges] holds it.** This
 * used to compose every bound slot at all times, which was invisible with one binding and an ANR with four: each is
 * a whole APPS surface, and four of them meant four sets of cells all baking icons at once on a device that could
 * not afford one. Composition now begins the instant a swipe moves off HOME ([SurfacePagerState.engagedEdges]) —
 * before any of the surface is visible, so it has a frame to exist in — and ends when the pan settles back.
 *
 * Each slot's UI state survives that (`rememberSaveable` inside it, keyed per edge by a `SaveableStateHolder`), so
 * a drawer closed and re-opened is on the page it was left on. Without it the gate would be paid for in exactly the
 * thing a launcher is judged on.
 *
 * **Each slot reports where its content is scrolled**, through a [ScrollEdgeSlot] provided here and read by the
 * gesture — which is what makes [OneFingerSwipe.AT_EDGE] a real hand-off rather than a synonym for
 * [OneFingerSwipe.ALWAYS]. Content opts in with `ReportScrollEdges`; content that says nothing reads as at every
 * edge, which is how every surface behaved before this existed.
 *
 * @param state the pan position + operations; also learns the swipeable edges + their finger policy here.
 * @param sideContent the binding for each swipeable edge. Absent edge = not swipeable.
 * @param retainedEdges edges to keep composed regardless of the pan. **The drag toolkit's "keep a source surface
 *   composed while a drag from it is in flight" rule, which the caller owns because only it knows about drags:** an
 *   app lifted in the drawer and ejected onto HOME is still tracked by the lifted cell's own pointer stream, so
 *   disposing that cell as the surface slides away would kill the gesture mid-flight.
 * @param enabled whether a swipe may switch surfaces at all. False while something on screen has a better use for
 *   the finger — an open folder, an item held down with its menu up — which the shell resolves through
 *   [SurfaceGestureLock] rather than by trying to out-consume the item gestures. See [surfacePagerGesture].
 * @param overlay drawn **above [center] and below every side surface**, filling the viewport and *not* panned with
 *   either. The frost a side surface is read against lives here (`SurfaceBackdropLayer`): it has to cover HOME, be
 *   covered by what arrives, and — the reason it cannot be a modifier on either — move differently from both, since
 *   the content slides while the frost merely fades. Empty by default, which is what the harness passes.
 * @param center the HOME surface, always present in the center.
 */
@Composable
fun SurfacePager(
    state: SurfacePagerState,
    modifier: Modifier = Modifier,
    sideContent: Map<HomeEdge, SurfaceBinding> = emptyMap(),
    enabled: () -> Boolean = { true },
    overlay: @Composable () -> Unit = {},
    retainedEdges: Set<HomeEdge> = emptySet(),
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
            .surfacePagerGesture(state, enabled),
    ) {
        // **Which slot is on screen**, read once here rather than by each slot. The `0.5` cut is [openEdge]'s own, so
        // a half-dragged pan hands presence over at the same point it decides which surface it is closer to.
        //
        // Through `derivedStateOf` because [SurfacePagerState.openEdge] reads the pan animatables: a raw read here
        // would subscribe this composable to *every frame* of a pan, and re-running it re-invokes both content
        // lambdas. The derived value changes twice in a whole pan, which is the real rate of the question.
        val openEdge by remember(state) { derivedStateOf { state.openEdge } }
        // The other, coarser question — *composed* rather than *presented* — read the same way and for the same
        // reason. It flips at zero where the one above flips at a half.
        val engagedEdges by remember(state) { derivedStateOf { state.engagedEdges } }
        // Keeps each slot's `rememberSaveable` state alive across being un-composed, keyed by edge. The standard
        // mechanism (it is what a nav host uses), and what makes the composition gate above free rather than a
        // trade: a drawer comes back on the page it was left on.
        val slotState = rememberSaveableStateHolder()
        Box(Modifier.fillMaxSize().surfaceSlide(state) { centerSlide(state.panX, state.panY) }) {
            // Each slot gets its own channel for "where is my content resting", which is what makes the nested-scroll
            // hand-off work without the shell wiring anything: content calls `ReportScrollEdges` and the gesture reads
            // whichever slot the swipe is crossing. L1 hoisted four `mutableStateOf`s and four reporter lambdas into
            // its home screen for this; the pager composes the slots, so the pager owns them.
            CompositionLocalProvider(
                LocalScrollEdgeSlot provides state.centerScroll,
                LocalSurfacePresented provides (openEdge == null),
            ) { center() }
        }
        // Between the two, and with no `surfaceSlide` of its own — see [overlay].
        overlay()
        for ((edge, binding) in sideContent) {
            // Not composed at all until the surface is at least partly on screen, or the caller has asked for it to
            // be kept — see the class note. Everything below, including the slot's own state, is absent until then.
            if (edge !in engagedEdges && edge !in retainedEdges) continue
            Box(Modifier.fillMaxSize().surfaceSlide(state) { sideSlide(edge, state.panX, state.panY) }) {
                // **A composed surface is not necessarily an on-screen one**, which is what
                // [LocalSurfacePresented] exists to say: a slot stays composed through the whole of a pan and, when
                // [retainedEdges] holds it, for the whole of a drag lifted on it. Anything that must belong to
                // exactly one surface at a time — a drop zone, the floating proxy — reads this to tell the two
                // apart.
                CompositionLocalProvider(
                    LocalScrollEdgeSlot provides state.sideScroll.getValue(edge),
                    LocalSurfacePresented provides (openEdge == edge),
                ) {
                    slotState.SaveableStateProvider(edge) { binding.content() }
                }
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
