package inkspire.morphic.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.feature.apps.layout.AppsVerticalGrid
import inkspire.morphic.feature.apps.layout.AppsVerticalList
import inkspire.morphic.feature.apps.layout.categorycard.AppsCategoryCard
import inkspire.morphic.feature.apps.layout.categorypager.AppsCategoryPager
import inkspire.morphic.feature.apps.layout.pager.AppsPager
import org.koin.androidx.compose.koinViewModel

/**
 * The APPS surface: the full app collection, rendered in whichever [AppsLayout] is selected.
 *
 * **This is the one place a layout is chosen**, and the reason this module exists as a single `feature:apps`
 * rather than L1's split `feature:appdrawer` + `feature:applibrary`. Those two modules were the same collection
 * of the same apps with the same launch behaviour, differing only in arrangement — so they duplicated the data
 * wiring, and the "drawer or library?" question had to be answered *before* the layout question, twice. The model
 * already collapsed that (`Surface.APPS` + [AppsLayout], "the layout alone decides the look"); this is the code
 * catching up. A new layout is a new file under `layout/` and a new arm below, with nothing else to touch.
 *
 * Everything above the `when` is shared by construction — the ViewModel, the ordering, the background, and the
 * **device configuration** every layout's sizing resolves against — so no layout can quietly disagree with another
 * about what the app list *is* or how big its icons are. The **theme** is shared from further out still:
 * `feature:shell` themes the whole launcher zone, so this surface cannot disagree with home either.
 *
 * **Icon sizing arrives resolved, and is handed down explicitly** rather than published as one ambient
 * `LocalIconMetrics` for the whole surface. Two layouts host a folder overlay, and a folder is *its own grid with its
 * own configuration* — one ambient value would silently give it the page's sizing. So each layout takes the metrics for
 * the grid it draws, and the two that open folders take the folder's as well.
 *
 * @param layout which arrangement to render. A parameter with a default rather than a read of user preference,
 *   because nothing owns that preference yet: it belongs to `data:settings` (B7), per-binding, since the same
 *   surface can be reached from different home edges with different layouts. Wire it there, not here.
 */
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    layout: AppsLayout = AppsLayout.VERTICAL_LIST,
) {
    val viewModel = koinViewModel<AppsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The one thing the UI knows and the ViewModel cannot: which window configuration this is. Everything
    // device-dependent — the pager's page capacity, every grid's icon sizing — derives from it down there rather than
    // being resolved up here and pushed down piecemeal, which is what `setPagerGrid` used to do.
    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }

    // No `LauncherTheme` here: the launcher **zone** is themed once by `feature:shell`'s `LauncherShell`, as home's
    // comment here used to promise would happen. Settings keeps its own boundary, so the two can disagree about
    // dark/light — the launcher follows wallpaper brightness, settings follows the system.
    val colors = LocalMorphicColors.current
    Box(modifier.fillMaxSize().background(colors.background)) {
        when (layout) {
            AppsLayout.VERTICAL_LIST -> AppsVerticalList(
                apps = state.apps,
                onLaunch = viewModel::launch,
                metrics = state.metricsFor(GridSlot.APPS_LIST),
            )
            AppsLayout.VERTICAL_GRID -> AppsVerticalGrid(
                apps = state.apps,
                onLaunch = viewModel::launch,
                metrics = state.metricsFor(GridSlot.APPS_SCROLL),
            )
            AppsLayout.PAGER -> AppsPager(
                pages = state.pagerPages,
                onLaunch = viewModel::launch,
                onMove = viewModel::movePagerItem,
                onMerge = viewModel::mergePagerItem,
                onReorderFolder = viewModel::reorderFolder,
                onAddToFolder = viewModel::addToFolder,
                onDropExtracted = viewModel::dropExtractedApp,
                onMergeExtracted = viewModel::mergeExtractedApp,
                metrics = state.metricsFor(GridSlot.APPS_PAGER),
                // A folder opened over the pager is a different grid, so it takes its own sizing rather than
                // inheriting the page's.
                folderMetrics = state.metricsFor(GridSlot.FOLDER),
            )
            AppsLayout.PAGER_WITH_CATEGORY -> AppsCategoryPager(
                categories = state.categories,
                onLaunch = viewModel::launch,
                onMove = viewModel::moveCategoryItem,
                metrics = state.metricsFor(GridSlot.APPS_CATEGORY),
            )
            // The fifth and last layout, sharing the category store the one above uses. Named rather than folded
            // into an `else`, like every arm here: adding a value to [AppsLayout] must fail to compile until it
            // is rendered.
            AppsLayout.CATEGORY_CARD -> AppsCategoryCard(
                categories = state.categories,
                onLaunch = viewModel::launch,
                // The same `Move` the category pager commits: on both layouts a re-file and a reposition are one
                // op, because the destination id carries the difference.
                onMove = viewModel::moveCategoryItem,
                onReorder = viewModel::reorderCategory,
                // An expansion *is* a folder overlay on the folder grid, so it takes that slot's sizing. A card's
                // previews are derived from its own square instead, and pass their own.
                metrics = state.metricsFor(GridSlot.FOLDER),
            )
        }
    }
}

/**
 * The resolved [IconMetrics] for [slot], or the ambient default when the store has not answered yet.
 *
 * The fallback is the honest one rather than a guessed constant: `LocalIconMetrics`' own default is what every cell
 * would have used anyway, and it applies for at most the frame between this surface reporting its device and the first
 * emission arriving. A per-slot placeholder here would be a second source of truth for a value the blueprint owns.
 */
@Composable
private fun AppsState.metricsFor(slot: GridSlot): IconMetrics =
    iconSizing[slot]?.toIconMetrics() ?: LocalIconMetrics.current
