package inkspire.morphic.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.surface.AxisScroll
import inkspire.morphic.core.designsystem.surface.ScrollAxes
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.ItemGesture
import org.koin.androidx.compose.koinViewModel

/**
 * **The HOME surface — and the one place its layout is chosen.**
 *
 * A `when` over [HomeLayout] above shared wiring, which is deliberately `AppsScreen`'s shape and for the same
 * reason: the two layouts render the same apps with the same launch behavior and differ only in arrangement, so
 * "which layout?" must be answered once, above everything both of them need. Everything above the `when` — the
 * ViewModel, the device report, the state — is shared, so switching layout reloads nothing and cannot leave the two
 * disagreeing about what HOME's contents are.
 *
 * The arms are listed individually rather than behind an `else`, so a third [HomeLayout] value fails to compile
 * until it is rendered.
 *
 * **L1 answered this with a registry** — a `HomeSurfaceRegistry` of `HomeSurface` implementations, each declaring a
 * `HomeGestureSet` its shell then interpreted (`hasPager`, `hasDock`, `hasWidgetArea`, `dropPolicy`, `longPress`).
 * That indirection buys nothing with two layouts that are both in this module and both known at compile time: the
 * registry made the set of layouts *open* while every consumer still had to branch on which one it had, so its home
 * screen ended up doing both — `isVerticalListHome` appears nine times in `HomeScreen.kt` alone, each occurrence
 * re-deciding which store to read. One `when`, here, is the whole of that decision.
 *
 * @param modifier passed to whichever surface is chosen; each fills it.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenIconContainerSettings: (Long) -> Unit = {},
    onOpenWidgetContainerSettings: (Long) -> Unit = {},
    /**
     * Opens the action picker for one gesture on one item — a full-screen destination, so `app` performs
     * the navigation and this surface only says which gesture was chosen.
     */
    onAssignGesture: (GridItem, ItemGesture) -> Unit = { _, _ -> },
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The device is the one thing this surface knows and the ViewModel cannot (it is a `@Composable` window read).
    // Reported here rather than in each arm, because it is an input to *both* and to the state they share: reporting
    // it twice would mean two writes racing on one `MutableStateFlow` every time the layout changed.
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    when (state.layout) {
        HomeLayout.PAGER_WITH_DOCK -> HomePagerSurface(
            viewModel = viewModel,
            state = state,
            device = device,
            modifier = modifier,
            onOpenIconContainerSettings = onOpenIconContainerSettings,
            onOpenWidgetContainerSettings = onOpenWidgetContainerSettings,
            onAssignGesture = onAssignGesture,
        )

        HomeLayout.LIST_WITH_WIDGET_AREA -> HomeListSurface(
            viewModel = viewModel,
            state = state,
            device = device,
            modifier = modifier,
            onAssignGesture = onAssignGesture,
        )
    }
}

/**
 * **What HOME scrolls, per axis, in each pairing** — the fact the launcher shell turns into each edge's *open*
 * policy, since a swipe leaving HOME crosses this content first.
 *
 * Published from here because it is a property of how these two arms are *drawn*, not of the register that chose
 * them: `HomePagerSurface` is a horizontal pager beside a fixed strip, `HomeListSurface` is a vertical scroller
 * beside one. The shell owns the *question* (it is the only layer that sees both sides of an edge) and each feature
 * owns its own answer — which is what `SurfaceBinding` means by "set by the shell from the layout on each side".
 *
 * The **side zone is not modeled here**, and that is a deliberate simplification rather than an oversight: a
 * vertical swipe that starts inside the dock or the widget area is not over the list at all, but the report is one
 * answer for the whole surface. L1 made the same simplification, and refining it would mean the report knowing which
 * region the finger is in — a per-zone answer for a gesture that is decided once, at slop.
 *
 * @param wraps whether the pager's pages loop — `SettingsRepository.pagerWraps` for [HomeLayout.pagerSlot], and
 *   ignored by the pairing that has no pager. **This is what makes wrapping more than an animation preference**: a
 *   wrapping pager never reaches an end, so there is no edge to hand a one-finger swipe off at, and the axis goes
 *   from [AxisScroll.BOUNDED] to [AxisScroll.INFINITE] — which becomes `OneFingerSwipe.NEVER`, making that edge
 *   two-finger-only. Turning wrapping on therefore takes a gesture away, and the settings control says so.
 */
fun HomeLayout.scrollAxes(wraps: Boolean): ScrollAxes = when (this) {
    HomeLayout.PAGER_WITH_DOCK -> ScrollAxes(horizontal = AxisScroll.ofPager(wraps))
    HomeLayout.LIST_WITH_WIDGET_AREA -> ScrollAxes(vertical = AxisScroll.BOUNDED)
}

/**
 * The resolved [IconMetrics] for [slot], or the ambient default before the store has answered.
 *
 * Zones resolve independently — `HOME_MAIN`, `HOME_DOCK` and `HOME_LIST` are separate grids with separate
 * blueprints — which is what makes each zone's icon size configurable apart from the others'. Until this landed they
 * all inherited `LocalIconMetrics`' default, which is also why that default is the honest fallback here rather than a
 * constant.
 *
 * Shared by both surfaces rather than living in either, since "what size are this grid's icons" is a question about
 * the state and not about the arrangement drawing it.
 */
@Composable
internal fun HomeState.metricsFor(slot: GridSlot): IconMetrics =
    iconSizing[slot]?.toIconMetrics() ?: LocalIconMetrics.current
