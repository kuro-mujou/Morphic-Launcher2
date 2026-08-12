package inkspire.morphic.feature.shell

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.backdrop.BackdropState
import inkspire.morphic.core.designsystem.backdrop.LocalBackdrop
import inkspire.morphic.core.designsystem.backdrop.LocalBackdropEffect
import inkspire.morphic.core.designsystem.backdrop.SurfaceBackdropLayer
import inkspire.morphic.core.designsystem.backdrop.screenToBitmapMapping
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.DropZone
import inkspire.morphic.core.designsystem.drag.LocalDragCoordinator
import inkspire.morphic.core.designsystem.drag.RegisterDropZone
import inkspire.morphic.core.designsystem.drag.ZoneId
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.menu.LauncherMenuHost
import inkspire.morphic.core.designsystem.menu.LocalMenuHost
import inkspire.morphic.core.designsystem.menu.MenuAction
import inkspire.morphic.core.designsystem.menu.MenuOverlay
import inkspire.morphic.core.designsystem.surface.EjectToHome
import inkspire.morphic.core.designsystem.surface.LocalEjectToHome
import inkspire.morphic.core.designsystem.surface.OneFingerSwipe
import inkspire.morphic.core.designsystem.surface.ScrollAxes
import inkspire.morphic.core.designsystem.surface.SurfaceBinding
import inkspire.morphic.core.designsystem.surface.LocalSurfaceGestureLock
import inkspire.morphic.core.designsystem.surface.SurfaceGestureLock
import inkspire.morphic.core.designsystem.surface.SurfacePager
import inkspire.morphic.core.designsystem.surface.rememberSurfacePagerState
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.topaction.TopActionExpandedHeight
import inkspire.morphic.core.designsystem.topaction.TopActionMode
import inkspire.morphic.core.designsystem.topaction.TopActionTarget
import inkspire.morphic.core.designsystem.topaction.TopActionZone
import inkspire.morphic.core.designsystem.topaction.rememberTopActionState
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.PlacementPlan
import inkspire.morphic.core.model.HomeEdge
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.pagerSlot
import inkspire.morphic.data.settings.SideBinding
import inkspire.morphic.data.wallpaper.WallpaperBrightness
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.feature.apps.AppsScreen
import inkspire.morphic.feature.apps.scrollAxes
import inkspire.morphic.feature.home.HomeScreen
import inkspire.morphic.feature.home.scrollAxes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The launcher itself: **HOME in the centre, side surfaces off its edges**, panned between by a swipe.
 *
 * This is the real version of what `app/dev/SurfacePagerPlaygroundScreen` prototyped. That harness proved the gesture
 * and the finger-policy table against *simulated* surfaces — colored boxes standing in for layouts that didn't exist.
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
fun LauncherShell(
    onOpenSettings: () -> Unit,
    onOpenAppsSettings: (AppsLayout) -> Unit,
    onEditIcon: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
    onOpenIconContainerSettings: (Long) -> Unit = {},
    onOpenWidgetContainerSettings: (Long) -> Unit = {},
) {
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

    // **The widget host listens while the launcher is on screen, and only then.** A provider only pushes updates to
    // a listening host, so a clock stops ticking without this; listening while the launcher is not visible is work
    // for nobody. Here rather than in `MainActivity` because "the launcher is on screen" is precisely what this
    // composable means — the Activity also hosts settings, where no widget is drawn.
    val widgetHost = koinInject<AppWidgetHostController>()
    LifecycleStartEffect(widgetHost) {
        widgetHost.startListening()
        onStopOrDispose { widgetHost.stopListening() }
    }

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

        // **The launcher's one drag coordinator, and this is the layer it belongs to** — the common ancestor of HOME
        // and every side surface, which is what docs/DRAG_AND_DROP_DESIGN.md §2 has specified from the start. Each
        // surface used to remember its own, which was indistinguishable from this while no drag could leave the
        // surface it started on. Now one can: an app lifted in the APPS drawer is dropped onto home's grid, and the
        // only reason that needs no hand-off is that both surfaces' zones are in *this* registry, hit-tested against
        // one finger in one coordinate space.
        //
        // It is also why there is no L1 `HomeDragBridge` here. That existed because `CrossPager` stopped delivering
        // pointer events to either subtree as it collapsed, so the finger had to be re-tracked from scratch at the
        // common ancestor. `SurfacePager` keeps every slot composed, so the lifted cell simply keeps its own pointer
        // stream the whole way.
        val coordinator = rememberDragCoordinator()

        // **The launcher's one item-menu host, and it belongs at this layer for the coordinator's reason.** The verbs
        // on an item's menu are the *item's* — App info, Uninstall, its own shortcuts — and the same app is reachable
        // from home, from the drawer, and from inside a folder. Binding the commands here once is what stops those
        // three offering different things for the same icon, which is exactly what happened in L1: home had an
        // `ItemContextMenu` and the side surfaces a near-copy `SideContextMenu`, each with its own two-stage logic.
        // A surface adds only what it owns — home's "Remove", because home is where an item is *placed*.
        val menuHost = remember(viewModel, onOpenSettings, onEditIcon) {
            LauncherMenuHost(
                onAppInfo = viewModel::openAppInfo,
                // Another destination the shell must not name — see `onOpenSettings` below. The icon studio is
                // `feature:settings`', and `app` is the only layer that knows where a verb goes.
                onEditIcon = onEditIcon,
                onUninstall = viewModel::uninstall,
                // Rasterised icons become `ImageBitmap` here rather than in the ViewModel, which deliberately deals
                // in platform bitmaps — the same line `ShellState.backdropImage` draws.
                loadShortcuts = { component ->
                    viewModel.shortcuts(component).map { shortcut ->
                        MenuAction(
                            label = shortcut.label,
                            icon = shortcut.icon?.asImageBitmap(),
                        ) { viewModel.startShortcut(shortcut) }
                    }
                },
                // **Navigation arrives as an action, not as a `Navigator`.** `feature:shell` composes the launcher's
                // surfaces; which *destination* settings is belongs to `app`, which owns the back stack — the same
                // reason `SettingsScreen` takes `onOpenDevHarness` rather than learning that route exists.
                onOpenSettings = onOpenSettings,
            )
        }

        // **The surface an in-flight drag was ejected from, kept composed until that drag ends.** `SurfacePager`
        // composes a slot only while it is on screen, so without this the lifted cell would be disposed the moment
        // its surface finished sliding away — and with it the pointer stream tracking the finger. This is the drag
        // toolkit's "keep a source surface composed while a drag from it is in flight" rule, stated here because the
        // pager knows about pans and not about gestures.
        var dragSourceEdge by remember { mutableStateOf<HomeEdge?>(null) }

        // Closing the open side surface *without ending the drag* — the whole of `EjectToHome`. The gesture carries
        // on over home the moment it is uncovered, because nothing about the drag changes here.
        //
        // **The edge is pinned here, synchronously, rather than from an effect watching the drag.** This is the exact
        // instant the surface starts to leave, so it is the only moment at which the answer is both needed and still
        // true. Watching `isDragging` instead would work today purely because a spring takes ~150ms to reach the
        // half-way point and a recomposition takes one frame — a race that is fine until the day it isn't.
        val eject = remember(pagerState, scope) {
            EjectToHome {
                dragSourceEdge = pagerState.openEdge
                scope.launch { pagerState.close() }
            }
        }
        // Released when the drag does, whatever became of it. A drag that never ejected pinned nothing, and one that
        // stayed on its own surface needed no pin — that surface was open the whole time.
        LaunchedEffect(coordinator.isDragging) {
            if (!coordinator.isDragging) dragSourceEdge = null
        }

        // **Provided at the shell, which is the same boundary the theme is applied at, and for the same reason**: a
        // frosted surface samples *this launcher's* wallpaper, and the settings graph is a different zone with
        // different rules (its icon preview punches through to the real window instead). L1 provided these inside its
        // `HomeScreen`, which is exactly why its settings feature needed a second provider of its own.
        CompositionLocalProvider(
            LocalBackdrop provides rememberBackdropState(state.backdropImage, state.backdropAccent, windowSize),
            LocalBackdropEffect provides state.backdropEffect,
            LocalSurfaceGestureLock provides gestureLock,
            LocalDragCoordinator provides coordinator,
            LocalMenuHost provides menuHost,
            LocalEjectToHome provides eject,
        ) {
            // A `Box` because the eject band below is a **sibling** of the pager rather than content inside it — see
            // that call. The caller's [modifier] moves out here with the stacking.
            Box(modifier.fillMaxSize()) {
                // TODO(SurfacePager): `state.register.transition` is read but not applied — only SLIDE is implemented, so
                //  the other five values are stored and ignored until the transforms land. Deliberately not faked.
                SurfacePager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    sideContent = state.register.sides.mapValues { (edge, binding) ->
                        binding.toSurfaceBinding(
                            edge = edge,
                            homeLayout = state.register.homeLayout,
                            wraps = state::wraps,
                            onOpenAppsSettings = onOpenAppsSettings,
                        )
                    },
                    // A swipe switches surfaces only when nothing on screen has claimed the finger. Read as a lambda, so
                    // the gesture asks at the two moments it can still hand the swipe back rather than at composition.
                    enabled = { !gestureLock.isLocked },
                retainedEdges = setOfNotNull(dragSourceEdge),
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
                    // Two more destinations the shell passes on without naming, exactly as `onEditIcon` is —
                    // a container's settings key belongs to `feature:home`, and `app` is the only layer that
                    // maps a key to a screen. Ids rather than the key itself, so this module never imports it.
                    HomeScreen(
                        onOpenIconContainerSettings = onOpenIconContainerSettings,
                        onOpenWidgetContainerSettings = onOpenWidgetContainerSettings,
                    )
                }

                // **The top-action band, above every surface** — hence a sibling of the pager rather than its
                // `overlay` slot, which is deliberately drawn *under* the side surfaces so the frost can sit between
                // them. The band has to be over the drawer it takes apps out of.
                TopActionOverlay(
                    coordinator = coordinator,
                    openEdge = pagerState.openEdge,
                    onEject = eject,
                    onRemove = viewModel::removeFromHome,
                    onUninstall = viewModel::uninstall,
                )

                // **The item menu, above everything including the band.** A sibling for the band's reason and one
                // more of its own: the menu is anchored to an item that may be on any surface, and it is *modal*
                // while it is up (it locks the surface swipe), so nothing may pan out from under it. It draws
                // nothing at all when no menu is open.
                MenuOverlay(menuHost)
            }
        }
    }
}

/** Where the band's drop zone lives in the registry. Above every surface's own, so it wins the finger. */
private val TopActionZoneId = ZoneId("top-action")

/**
 * The plan the band reports: droppable, naming no destination, painting nothing.
 *
 * The footprint is a required field with nothing meaningful to put in it — no cell is involved in taking an item off
 * the launcher — which is exactly what [DropIntent.REMOVE] exists to say. The band itself is the affordance.
 */
private val TopActionRemovePlan = PlacementPlan(GridPlacement(0, 0, 0), DropIntent.REMOVE)

/**
 * The **top-action band** and the drop zone behind it: two halves of one thing, so they are wired together here
 * rather than in either.
 *
 * **The band's mode follows which surface the drag is over**, which is the whole reason it belongs to the shell. A
 * side surface is open → the band hands the drag to HOME. HOME is showing → it takes the item *off* the launcher.
 * One band with a mode, where L1 had two entirely separate implementations with their own thresholds and their own
 * copies of the view (the drawer's `SurfaceExtractEngagement` and home's `rememberTopActionState`).
 *
 * **The two modes commit at different moments, and [rememberTopActionState] owns why:**
 * - `ADD_TO_HOME` fires on a **dwell**. The point is to get the drawer out of the way *while the finger is still
 *   down*, so the same drag carries on over home; a release would end the very gesture it exists to continue. There
 *   is therefore nothing to drop on, and releasing here is a plain cancel.
 * - `DELETE` fires on **release**, so it registers a real drop zone. A destructive action that armed itself under a
 *   held finger would be a trap, and a finger crosses the top of the screen on its way elsewhere often enough.
 *
 * **The DELETE zone is registered only while the band is expanded**, which is what makes the collapsed strip
 * harmless: until the user has held the finger up there long enough to open the band, the top of the screen belongs
 * to whatever is beneath it and a release goes there. Once open, `z = 2` puts it above every surface's own zone —
 * necessary rather than decorative, since the drawer's viewport zone covers the same pixels.
 */
@Composable
private fun TopActionOverlay(
    coordinator: DragCoordinator,
    openEdge: HomeEdge?,
    onEject: EjectToHome,
    onRemove: (GridItem) -> Unit,
    onUninstall: (ComponentKey) -> Unit,
) {
    val session = coordinator.session
    val item = session?.item
    val mode = when {
        item == null -> null
        // A folder living in the drawer has nowhere to go on home yet, so the band offers it nothing there; over
        // HOME everything placed can at least be removed.
        openEdge != null -> TopActionMode.ADD_TO_HOME.takeIf { item is GridItem.App }
        else -> TopActionMode.DELETE
    }

    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width.toFloat()
    val state = rememberTopActionState(
        mode = mode,
        fingerInRoot = session?.fingerInRoot,
        viewportWidth = width,
        // Only an app has a package to uninstall; a folder gets Remove alone.
        showUninstall = item is GridItem.App,
        onEngage = onEject::invoke,
    )

    // Read here rather than inside `onDrop`: by the time that runs the drag has ended, and the band's own state has
    // started collapsing behind it. Same "read it before the drop clears it" discipline every surface's landing
    // handler follows.
    val target = state.hoveredTarget
    val bounds = remember(width, density) {
        Rect(0f, 0f, width, with(density) { TopActionExpandedHeight.toPx() })
    }
    RegisterDropZone(
        coordinator = coordinator,
        zone = DropZone(
            id = TopActionZoneId,
            bounds = bounds,
            z = 2,
            // A plan only in DELETE mode, because only there does a release mean anything: a non-null plan is what
            // makes the coordinator dispatch a *landing* rather than treat the release as a cancel. ADD_TO_HOME
            // returns null on purpose — its commit is the dwell, so lifting the finger over it means "never mind".
            planner = { _, _ -> if (mode == TopActionMode.DELETE) TopActionRemovePlan else null },
            onDrop = { outcome ->
                when (target) {
                    TopActionTarget.REMOVE -> onRemove(outcome.item)
                    TopActionTarget.UNINSTALL ->
                        (outcome.item as? GridItem.App)?.let { onUninstall(it.component) }
                    null -> Unit // no half armed — nothing to commit
                }
            },
        ),
        // **Registered whenever the band is open, in either mode** — and in ADD_TO_HOME that is for the masking
        // rather than for the drop. While the finger is up here it must stop being the drawer's: otherwise the
        // pager's own planner keeps migrating its reorder gap toward the top-left, and a release before the dwell
        // completes lands the app there instead of cancelling. Being the topmost zone is what stops that, and it is
        // L1's rule too — its drawer explicitly blanked `hoverTarget` and `pendingGap` while over the top bar.
        enabled = state.expanded,
        // The shell sits in no slot, so `LocalSurfacePresented` would be its default `true`; the real condition is
        // above, and is passed explicitly for that reason.
    )

    TopActionZone(
        mode = state.mode,
        expanded = state.expanded,
        showUninstall = state.showUninstall,
        hoveredTarget = state.hoveredTarget,
    )
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
    onOpenAppsSettings: (AppsLayout) -> Unit,
): SurfaceBinding = when (this) {
    is SideBinding.Apps -> SurfaceBinding(
        openSwipe = homeLayout.scrollAxes(wraps(homeLayout.pagerSlot)).oneFingerSwipe(edge),
        closeSwipe = layout.scrollAxes(wraps(layout.pagerSlot)).oneFingerSwipe(edge),
    ) {
        // The binding is what knows which arrangement this edge shows, so it is what closes over it — the surface
        // itself only has to say "open my settings".
        AppsScreen(layout = layout, onOpenLayoutSettings = { onOpenAppsSettings(layout) })
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
