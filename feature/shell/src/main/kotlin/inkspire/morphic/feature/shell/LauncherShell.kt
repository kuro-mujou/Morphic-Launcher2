package inkspire.morphic.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.surface.OneFingerSwipe
import inkspire.morphic.core.designsystem.surface.SurfaceBinding
import inkspire.morphic.core.designsystem.surface.SurfacePager
import inkspire.morphic.core.designsystem.surface.rememberSurfacePagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.Surface
import inkspire.morphic.feature.apps.AppsScreen
import inkspire.morphic.feature.home.HomeScreen
import kotlinx.coroutines.launch

/**
 * The launcher itself: **HOME in the centre, side surfaces off its edges**, panned between by a swipe.
 *
 * This is the real version of what `app/dev/SurfacePagerPlaygroundScreen` prototyped. That harness proved the gesture
 * and the finger-policy table against *simulated* surfaces — coloured boxes standing in for layouts that didn't exist
 * yet. Everything it simulated now exists, so the boxes are gone and the surfaces are the actual screens; what
 * remains of it is the one thing it was built to validate, which is how an edge decides between one and two fingers.
 *
 * **It owns the theme boundary, and that is its first real job.** [LauncherTheme] wraps the whole launcher *zone*
 * here, once, rather than each screen theming itself — which is why `HomeScreen` and `AppsScreen` no longer do (both
 * of their wrappers said in a comment that they existed only until a shell arrived to take over). Settings is a
 * separate zone with its own boundary and its own "is-dark" input, so the two can disagree; L1 wrapped one theme
 * around its entire `NavDisplay` and could not.
 *
 * **What is deliberately not decided here: which surface sits on which edge.** [sideSurfaces] is empty by default, so
 * out of the box this is HOME and nothing else — no edge is swipeable. That is not an oversight and not a placeholder
 * constant either: per-edge binding is **user configuration**, it belongs to `data:settings` (B7), and the model
 * already says so ("each edge is bound independently; the binding config lives in the settings layer, not the
 * model"). Picking an edge here would be inventing a dimension nothing owns — and unlike a dp constant, it would be a
 * *product* decision hidden in a composable. When the settings layer lands, its per-edge binding feeds this parameter
 * and nothing else in the shell changes.
 *
 * **Still to come**, all of it deferred from `SurfacePager` rather than forgotten: the frosted backdrop behind a
 * panned surface, drag-out from a side surface onto HOME (`EjectToHome`), and the nested-scroll hand-off that makes
 * [OneFingerSwipe.AT_EDGE] genuinely different from [OneFingerSwipe.ALWAYS]. L1 had all three tangled into one
 * 549-line `CrossPager`; they arrive here as separate additions on a clean base.
 *
 * @param sideSurfaces which [Surface] is reachable from which [HomeEdge]. An edge with no entry cannot be swiped —
 *   the pager reads the swipeable set straight off these keys.
 */
@Composable
fun LauncherShell(
    modifier: Modifier = Modifier,
    sideSurfaces: Map<HomeEdge, Surface> = emptyMap(),
) {
    // TODO(data:settings B7): the launcher's dark/light input is **wallpaper brightness**, not the system setting —
    //  chrome has to contrast the wallpaper behind it. Hardcoded dark until the wallpaper-brightness analyzer exists;
    //  see the design-system note on "one theme, two is-dark inputs".
    LauncherTheme(darkTheme = true) {
        val scope = rememberCoroutineScope()
        val state = rememberSurfacePagerState()

        // Back closes an open side surface and returns to HOME. Disabled when already on HOME so back falls through
        // to the system — on a launcher there is nowhere further to go, and swallowing it would trap the user.
        BackHandler(enabled = state.openEdge != null) { scope.launch { state.close() } }

        SurfacePager(
            state = state,
            modifier = modifier.fillMaxSize(),
            sideContent = sideSurfaces.mapValues { (edge, surface) -> surface.bindingFor(edge) },
        ) {
            HomeScreen()
        }
    }
}

/**
 * The [SurfaceBinding] for this surface sitting off [edge] — its content, and the one-finger policy in each
 * direction.
 *
 * **Why the shell answers this and not the pager.** The policy is a property of the *content* on the swipe's axis,
 * and the shell is the only layer that knows what content is on both sides: HOME's layout decides whether a
 * one-finger swipe can leave HOME, and the side surface's own layout decides whether one can come back. `SurfaceBinding`
 * says as much in its KDoc — "set by the shell from the layout on each side".
 *
 * **Both policies are [OneFingerSwipe.ALWAYS] for now, and that is a stand-in, not an answer.** The honest values are
 * a function of two settings that do not exist: HOME's [inkspire.morphic.core.model.HomeLayout] and the surface's
 * [inkspire.morphic.core.model.AppsLayout] — a bounded scroller on the axis means `AT_EDGE`, an infinite one means
 * `NEVER`, nothing on the axis means `ALWAYS`. The playground has that whole table worked out and it can be lifted
 * across wholesale once the layouts are readable here. `ALWAYS` is the safe stand-in in the meantime: it means every
 * bound edge is reachable with one finger, so nothing is unreachable while the real policy is unowned — and
 * `AT_EDGE` currently behaves as `ALWAYS` anyway, since the nested-scroll hand-off it depends on is itself deferred.
 */
private fun Surface.bindingFor(edge: HomeEdge): SurfaceBinding = when (this) {
    Surface.APPS -> SurfaceBinding(
        openSwipe = OneFingerSwipe.ALWAYS,
        closeSwipe = OneFingerSwipe.ALWAYS,
    ) {
        // The layout is per-binding user preference (the same surface can be reached from different edges with
        // different layouts), so it too waits on `data:settings`; `AppsScreen`'s own default stands in until then.
        AppsScreen()
    }

    // HOME is the centre, so it can never be a side surface. Enumerated rather than left to an `else` for the reason
    // every `when` over a model enum in this codebase is: adding a `Surface` value must fail to compile here until
    // someone decides what swiping to it means.
    Surface.HOME -> error("HOME is the centre surface and cannot be bound to $edge")
}
