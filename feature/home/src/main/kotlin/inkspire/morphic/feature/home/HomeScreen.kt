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
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HomeLayout
import org.koin.androidx.compose.koinViewModel

/**
 * **The HOME surface — and the one place its layout is chosen.**
 *
 * A `when` over [HomeLayout] above shared wiring, which is deliberately `AppsScreen`'s shape and for the same
 * reason: the two layouts render the same apps with the same launch behaviour and differ only in arrangement, so
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
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The device is the one thing this surface knows and the ViewModel cannot (it is a `@Composable` window read).
    // Reported here rather than in each arm, because it is an input to *both* and to the state they share: reporting
    // it twice would mean two writes racing on one `MutableStateFlow` every time the layout changed.
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    when (state.layout) {
        HomeLayout.PAGER_WITH_DOCK -> HomePagerSurface(viewModel, state, device, modifier)
        HomeLayout.LIST_WITH_WIDGET_AREA -> HomeListSurface(viewModel, state, device, modifier)
    }
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
