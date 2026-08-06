package inkspire.morphic.feature.shell

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.backdrop.BackdropState
import inkspire.morphic.core.designsystem.backdrop.LocalBackdrop
import inkspire.morphic.core.designsystem.backdrop.LocalBackdropEffect
import inkspire.morphic.core.designsystem.backdrop.SurfaceBackdropLayer
import inkspire.morphic.core.designsystem.backdrop.screenToBitmapMapping
import inkspire.morphic.core.designsystem.surface.OneFingerSwipe
import inkspire.morphic.core.designsystem.surface.ScrollAxes
import inkspire.morphic.core.designsystem.surface.SurfaceBinding
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock
import inkspire.morphic.core.designsystem.surface.SurfaceGestureLock
import inkspire.morphic.core.designsystem.surface.SurfacePager
import inkspire.morphic.core.designsystem.surface.rememberSurfacePagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.pagerSlot
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.wallpaper.WallpaperBrightness
import inkspire.morphic.feature.apps.AppsScreen
import inkspire.morphic.feature.apps.scrollAxes
import inkspire.morphic.feature.home.HomeScreen
import inkspire.morphic.feature.home.scrollAxes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The launcher itself: **HOME in the centre, side surfaces off its edges**, panned between by a swipe.
 *
 * This is the real version of what `app/dev/SurfacePagerPlaygroundScreen` prototyped. That harness proved the gesture
 * and the finger-policy table against *simulated* surfaces — coloured boxes standing in for layouts that didn't exist.
 * They all exist now, so the boxes are the real screens; the playground stays in the dev harness as the regression
 * test for the gesture itself.
 *
 * **It owns the launcher's theme boundary.** [LauncherTheme] wraps the whole launcher *zone* here, once, rather than
 * each screen theming itself — which is why `HomeScreen` and `AppsScreen` no longer do. Settings is a separate zone
 * with its own boundary and its own "is-dark" input, so the two can disagree; L1 wrapped one theme around its entire
 * `NavDisplay` and could not.
 *
 * **Which surface sits on which edge is now a user setting**, read from `SurfaceRegister` via [ShellViewModel]. Out of
 * the box the register binds nothing, so this is HOME and nothing swipeable until an edge is bound in settings — the
 * defaults deliberately don't choose an edge, because which edge opens the app list is a product decision and the data
 * layer is not where product decisions should be made quietly.
 *
 * **Still to come**, deferred from `SurfacePager` rather than forgotten: drag-out from a side surface onto HOME
 * (`EjectToHome`), and the five transitions beyond SLIDE. L1 had those tangled into one 549-line `CrossPager` along
 * with the frosted backdrop and the nested-scroll hand-off, both of which have since arrived here as separate
 * additions on a clean base.
 */
@Composable
fun LauncherShell(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<ShellViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The **whole** window, insets included, because that is what a wallpaper covers — and it is what the backdrop's
    // screen→bitmap mapping has to be built against, since a frosted surface may sit under the status bar. The same
    // measurement `WallpaperDetail` makes for its preview, and deliberately *not* `usableWindowArea`, which every
    // grid uses because a grid must stay out from under the bars.
    val windowSize = LocalWindowInfo.current.containerSize
    val orientation = if (windowSize.width > windowSize.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
    // Pushed down rather than read in the holder, the same way every surface reports its `DeviceConfiguration`: the
    // rotating wallpaper is two pictures, so "which one" is a question only something holding the window can answer.
    LaunchedEffect(orientation) { viewModel.setOrientation(orientation) }

    // The launcher's dark/light input is **wallpaper brightness**, not the system's dark-mode switch: chrome sits
    // directly on the picture with nothing between, so what it has to contrast is the picture. Settings is the other
    // half of that rule — its own surface, so its own `isSystemInDarkTheme()`. The two can therefore disagree, and
    // should. `WallpaperBrightness.DARK` before the first read, which is what this line hardcoded until now.
    LauncherTheme(darkTheme = state.brightness == WallpaperBrightness.DARK) {
        val scope = rememberCoroutineScope()
        val pagerState = rememberSurfacePagerState()

        // **Who owns the finger, when the answer is not "the pager".** Hosted here because the pager it guards is
        // here, and because the claimants are spread across three surfaces (an open folder, an item held down with its
        // menu up) that cannot see each other. See `SurfaceGestureLock` for why this is state rather than pointer
        // consumption.
        val gestureLock = remember { SurfaceGestureLock() }

        // Back closes an open side surface and returns to HOME. Disabled when already on HOME so back falls through to
        // the system — on a launcher there is nowhere further to go, and swallowing it would trap the user.
        BackHandler(enabled = pagerState.openEdge != null) { scope.launch { pagerState.close() } }

        // **Provided at the shell, which is the same boundary the theme is applied at, and for the same reason**: a
        // frosted surface samples *this launcher's* wallpaper, and the settings graph is a different zone with
        // different rules (its icon preview punches through to the real window instead). L1 provided these inside its
        // `HomeScreen`, which is exactly why its settings feature needed a second provider of its own.
        CompositionLocalProvider(
            LocalBackdrop provides rememberBackdropState(state.backdropImage, state.backdropAccent, windowSize),
            LocalBackdropEffect provides state.backdropEffect,
            LocalSurfaceGestureLock provides gestureLock,
        ) {
            // TODO(SurfacePager): `state.register.transition` is read but not applied — only SLIDE is implemented, so
            //  the other five values are stored and ignored until the transforms land. Deliberately not faked.
            SurfacePager(
                state = pagerState,
                modifier = modifier.fillMaxSize(),
                sideContent = state.register.sides.mapValues { (edge, binding) ->
                    binding.toSurfaceBinding(edge, state.register.homeLayout, state::wraps)
                },
                // A swipe switches surfaces only when nothing on screen has claimed the finger. Read as a lambda, so
                // the gesture asks at the two moments it can still hand the swipe back rather than at composition.
                enabled = { !gestureLock.isLocked },
                // **The frost, between HOME and whatever is sliding over it.** A side surface is transparent and is
                // read against this; the two move differently on purpose — the pane translates, the frost only fades
                // — which is why it is a slot on the pager rather than a modifier on either. `progress` is the pan
                // collapsed to "how far in is the other surface", so the screen frosts over as the content arrives
                // and clears as it leaves, from any edge.
                //
                // The scrim is this launcher's own background: with no wallpaper to sample there is nothing to blur,
                // and the surface above still has to be legible. That is the state a fresh install is in, and it
                // looks exactly like the opaque APPS background this replaced.
                overlay = {
                    SurfaceBackdropLayer(
                        alpha = pagerState::progress,
                        scrimColor = LocalMorphicColors.current.background,
                    )
                },
            ) {
                HomeScreen()
            }
        }
    }
}

/**
 * [image], [accent] and [windowSize] as the [BackdropState] frosted surfaces sample, or null while the image or the
 * window is missing.
 *
 * Split out because it is two conversions that both want caching and neither belongs in the state holder: the
 * `Bitmap` → `ImageBitmap` wrap, and the screen→bitmap mapping, which is a closure that would otherwise be rebuilt on
 * every recomposition and hand every frosted surface a new lambda to invalidate against.
 *
 * A null [accent] is not a reason to return null — it only makes the washes plain white and black — which is why it is
 * `Color.Unspecified` here rather than a second early return.
 */
@Composable
private fun rememberBackdropState(image: Bitmap?, accent: Int?, windowSize: IntSize): BackdropState? =
    remember(image, accent, windowSize) {
        if (image == null || windowSize.width == 0 || windowSize.height == 0) {
            null
        } else {
            BackdropState(
                image = image.asImageBitmap(),
                screenToBitmap = screenToBitmapMapping(
                    bitmapWidth = image.width,
                    bitmapHeight = image.height,
                    screenWidth = windowSize.width,
                    screenHeight = windowSize.height,
                ),
                tintColor = accent?.let { Color(it) } ?: Color.Unspecified,
            )
        }
    }

/**
 * The drag-toolkit [SurfaceBinding] for a stored [SideBinding] — its content, and the one-finger policy each way.
 *
 * **The `when` is exhaustive over a sealed type, so there is no invalid case to handle.** An earlier cut mapped the
 * `Surface` enum here and needed `error("HOME is the centre surface and cannot be bound to $edge")` for a state the
 * type system should have refused; making the stored binding a sealed hierarchy with one variant per *bindable*
 * surface deleted that branch. Adding a side surface now fails to compile here until someone says what swiping to it
 * shows — which is the intent the runtime throw was only approximating.
 *
 * **Why the shell answers the policy question and not the pager.** The policy is a property of the *content* on the
 * swipe's axis, and the shell is the only layer that sees both sides: HOME's layout decides whether one finger can
 * leave HOME, the side surface's own layout decides whether one can come back. `SurfaceBinding` says as much — "set by
 * the shell from the layout on each side".
 *
 * **Each side names its own scroll behaviour, and one rule turns that into the policy.** `HomeLayout.scrollAxes` and
 * `AppsLayout.scrollAxes` are declared in the modules that draw those layouts; [ScrollAxes.oneFingerSwipe] is the
 * whole derivation — a bounded scroller on the edge's axis means `AT_EDGE`, an infinite one `NEVER`, nothing on the
 * axis `ALWAYS`. That expression is the surface-pager playground's table, promoted from private demo code once the
 * real layouts existed to answer it. Until this landed both were a hardcoded [OneFingerSwipe.ALWAYS] stand-in, which
 * cost nothing only because `AT_EDGE` had no hand-off behind it and behaved as `ALWAYS` anyway.
 *
 * **A wrapping pager changes the policy, not just the animation**, which is why [wraps] is consulted here at all: a
 * pager with no ends has no edge to hand a one-finger swipe off at, so its axis is `AxisScroll.INFINITE` and the
 * policy comes out `OneFingerSwipe.NEVER`. Turn wrapping on for HOME's pager and a LEFT- or RIGHT-bound surface
 * becomes two-finger-only; turn it on for the APPS pager and getting *back* does. Both settings sections say so.
 *
 * @param homeLayout HOME's pairing, which decides the *open* half. Passed in because it is the other side of the
 *   edge: a binding knows what swiping to it shows, not what swiping away from HOME has to cross first.
 * @param wraps whether the pager behind a given slot loops. A lookup rather than two booleans, because the two sides
 *   of an edge ask about different grids and only [HomeLayout.pagerSlot] / [AppsLayout.pagerSlot] know which.
 */
private fun SideBinding.toSurfaceBinding(
    edge: HomeEdge,
    homeLayout: HomeLayout,
    wraps: (GridSlot?) -> Boolean,
): SurfaceBinding = when (this) {
    is SideBinding.Apps -> SurfaceBinding(
        openSwipe = homeLayout.scrollAxes(wraps(homeLayout.pagerSlot)).oneFingerSwipe(edge),
        closeSwipe = layout.scrollAxes(wraps(layout.pagerSlot)).oneFingerSwipe(edge),
    ) {
        AppsScreen(layout = layout)
    }
}

/**
 * Whether the pager in [slot] wraps — false for a layout that has no pager, which is what a null [slot] means.
 *
 * The null-absorbing read exists so the two call sites above stay one expression each: `pagerSlot` is nullable
 * precisely because most layouts do not page, and a wrap value for one of those would be meaningless rather than
 * false. Collapsing "no pager" and "does not wrap" is safe *here* only because both produce
 * `AxisScroll.BOUNDED`-or-better — a layout with no pager is not gated on that axis at all.
 */
private fun ShellState.wraps(slot: GridSlot?): Boolean = slot != null && pagerWraps[slot] == true
