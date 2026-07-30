package inkspire.morphic.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.feature.apps.layout.AppsCategoryCard
import inkspire.morphic.feature.apps.layout.AppsCategoryPager
import inkspire.morphic.feature.apps.layout.AppsPager
import inkspire.morphic.feature.apps.layout.AppsVerticalGrid
import inkspire.morphic.feature.apps.layout.AppsVerticalList
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
 * Everything above the `when` is shared by construction — the ViewModel, the ordering, the theme, the background
 * — so no layout can quietly disagree with another about what the app list *is*.
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

    // Themed here for the same reason home is: there is no launcher shell yet to own the boundary. When the shell
    // lands it themes the whole launcher zone once and this wrapper goes, along with home's.
    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        Box(modifier.fillMaxSize().background(colors.background)) {
            when (layout) {
                AppsLayout.VERTICAL_LIST -> AppsVerticalList(
                    apps = state.apps,
                    onLaunch = viewModel::launch,
                )
                AppsLayout.VERTICAL_GRID -> AppsVerticalGrid(
                    apps = state.apps,
                    onLaunch = viewModel::launch,
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
                    onGridResolved = viewModel::setPagerGrid,
                )
                AppsLayout.PAGER_WITH_CATEGORY -> AppsCategoryPager(
                    categories = state.categories,
                    onLaunch = viewModel::launch,
                    onMove = viewModel::moveCategoryItem,
                )
                // The fifth and last layout, sharing the category store the one above uses. Named rather than folded
                // into an `else`, like every arm here: adding a value to [AppsLayout] must fail to compile until it
                // is rendered.
                AppsLayout.CATEGORY_CARD -> AppsCategoryCard(
                    categories = state.categories,
                    onLaunch = viewModel::launch,
                    onReorder = viewModel::reorderCategory,
                )
            }
        }
    }
}
