package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.AppsScrollGrid
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.colsFor

/**
 * Provisional cell height — **a placeholder, not a design choice.**
 *
 * A grid cell's height is a **user-configurable** surface metric (it is the vertical half of icon density, the
 * column count being the horizontal half), and the icon follows it through [GridIconMetrics]. Deliberately *not*
 * derived from the measured cell width to look principled: a square cell would be arithmetic standing in for a
 * decision nobody has made, and it would hide that the value is still unowned. A flat constant says so, and is
 * the single line that changes when the setting lands.
 */
private val CellHeight = 96.dp

/**
 * The grid's own icon proportion, denser than the home default.
 *
 * Home's 0.88 is tuned for a 2×2 home cell with generous slack around one icon; an app grid packs four to eight
 * columns of them, where the same fraction leaves the icons nearly touching. Like [CellHeight], a placeholder for
 * the icon-size setting — the value is a starting point, the *mechanism* (a per-surface [IconMetrics] through
 * [LocalIconMetrics]) is the real answer.
 */
private val GridIconMetrics = IconMetrics(iconPercent = 0.75f)

/**
 * The **vertical grid** layout of the APPS surface: every app A–Z, icon over label, in a scrolling grid.
 *
 * The second *derived* layout, and the reason it follows the list immediately: like the list it stores nothing
 * and re-renders straight from [apps], so it needs neither the APPS order repository nor any new schema. Between
 * them the two derived layouts cover the whole surface end to end, and everything left is blocked on that store.
 *
 * **Columns come from the blueprint, the layout engine does not.** [AppsScrollGrid] gives the per-device column
 * count (`colsFor` — a `SCROLL_GRID` blueprint has no rows to resolve, so it cannot go through `toGridConfig`),
 * but the cells are laid out by a `LazyVerticalGrid` rather than by `LauncherGrid`'s SCROLL_GRID mode. That mode
 * composes every child at once, which is right for the bounded per-category page it was built for and wrong here:
 * a full app collection is hundreds of items, each baking an icon bitmap. This is the grid plan's "right tool per
 * surface" rule — a custom `Layout` for coordinate grids, a lazy one for pure scrolling surfaces — and it costs
 * nothing, because a derived layout is never dragged *within* itself, so it needs no shared cell lattice or
 * published geometry. (A drag *out* to home is `EjectToHome`, which reads the finger, not the grid.)
 *
 * Not built here, the same list as the vertical list's: the alphabet filter strip, search, and drag-out-to-home.
 */
@Composable
fun AppsVerticalGrid(
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = currentDeviceConfiguration()
    val cols = remember(device) { AppsScrollGrid.colsFor(device) }
    val gestureConfig = rememberAppsGestureConfig()
    // Content padding, not layout padding, so rows scroll under the bars rather than stopping short of them —
    // the same system-constraint-only inset the list applies.
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()

    CompositionLocalProvider(LocalIconMetrics provides GridIconMetrics) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = modifier.fillMaxSize(),
            contentPadding = barInsets,
        ) {
            items(items = apps, key = { it.componentKey.flatten() }) { app ->
                // Only the height is set: the width is the column's, and `AppCell` sizes the icon from the cell
                // it is given (via `IconLabelCell`), so the two metrics meet without either being computed here.
                AppCell(
                    app = app,
                    modifier = Modifier.height(CellHeight),
                    itemGestures = Modifier.appsItemGestures(gestureConfig) { onLaunch(app.componentKey) },
                )
            }
        }
    }
}
