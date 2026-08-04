package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.grid.cellHeight
import inkspire.morphic.core.designsystem.grid.fitCols
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.AppsScrollGrid
import inkspire.morphic.core.model.ComponentKey

/**
 * The **vertical grid** layout of the APPS surface: every app A–Z, icon over label, in a scrolling grid.
 *
 * The second *derived* layout, and the reason it follows the list immediately: like the list it stores nothing
 * and re-renders straight from [apps], so it needs neither the APPS order repository nor any new schema. Between
 * them the two derived layouts cover the whole surface end to end, and everything left is blocked on that store.
 *
 * **Columns are given, the layout engine is not.** The count is resolved from settings by the surface (a
 * `SCROLL_GRID` has no rows to resolve, so it is a column count rather than a `GridConfig`), but the cells are laid
 * out by a `LazyVerticalGrid` rather than by `LauncherGrid`'s SCROLL_GRID mode. That mode
 * composes every child at once, which is right for the bounded per-category page it was built for and wrong here:
 * a full app collection is hundreds of items, each baking an icon bitmap. This is the grid plan's "right tool per
 * surface" rule — a custom `Layout` for coordinate grids, a lazy one for pure scrolling surfaces — and it costs
 * nothing, because a derived layout is never dragged *within* itself, so it needs no shared cell lattice or
 * published geometry. (A drag *out* to home is `EjectToHome`, which reads the finger, not the grid.)
 *
 * Not built here, the same list as the vertical list's: the alphabet filter strip, search, and drag-out-to-home.
 *
 * **The row height is derived from the column width, not stored.** A scrolling grid fixes its cell *width* (the
 * usable width over the column count) and has no fixed height to divide, so what remains is what the icon and its
 * label need — which is `cellHeight`, the same arithmetic `IconLabelCell` lays a cell out by. That makes it a
 * consequence of the icon sizing the user already chose in S3 rather than a second setting able to disagree with it:
 * enlarge the icons and the rows grow to hold them. L1 derived it the same way (`gridCellHeightDp`), which is why its
 * grids track their icon sliders.
 *
 * @param metrics this grid's icon sizing, resolved from `GridSlot.APPS_SCROLL`'s blueprint and the user's overrides.
 *   Denser than home's by default, for the reason the column count differs: a home cell is a 2×2 slot around one icon,
 *   an app grid packs four to eight columns of them.
 * @param cols how many columns across — resolved from the same slot's blueprint and overrides, and passed rather than
 *   read here for the reason [metrics] is: this surface resolves every grid's configuration in one place, so a layout
 *   cannot end up drawing a size nobody configured. It is the count the user *chose*, so it is clamped below to what
 *   the measured width can hold at this icon size — the one part of resolving a grid that cannot happen before the
 *   measurement, which is why it happens here and not in `AppsScreen`.
 */
@Composable
fun AppsVerticalGrid(
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    metrics: IconMetrics,
    cols: Int,
    modifier: Modifier = Modifier,
) {
    val gestureConfig = rememberAppsGestureConfig()
    // Content padding, not layout padding, so rows scroll under the bars rather than stopping short of them —
    // the same system-constraint-only inset the list applies.
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()

    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        // Measured here rather than inside the item, because a cell's height comes from its *width* and only the
        // grid knows that: `GridCells.Fixed` divides whatever is left after the content padding, so the same
        // subtraction has to happen here to name one column's width.
        BoxWithConstraints(modifier.fillMaxSize()) {
            val direction = LocalLayoutDirection.current
            val usableWidth = maxWidth -
                barInsets.calculateStartPadding(direction) - barInsets.calculateEndPadding(direction)
            // **The stored count, clamped to what this width can actually draw** — the scrolling twin of the
            // `fitGridConfig` read home's pager does, and the only piece of grid resolution that belongs here rather
            // than in `AppsScreen`: it needs the measured width, which only this layout has. Without it, raising the
            // minimum icon size leaves the columns as they were and the icons clamp up and overflow their cells, while
            // the settings editor offers a ceiling the grid was ignoring. Clamped, never written back, so shrinking the
            // icons again brings the column straight back.
            val drawnCols = AppsScrollGrid.fitCols(usableWidth.value, cols, metrics)
            val cellHeight = cellHeight(cellWidth = usableWidth / drawnCols, metrics = metrics)

            LazyVerticalGrid(
                columns = GridCells.Fixed(drawnCols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = barInsets,
            ) {
                items(items = apps, key = { it.componentKey.flatten() }) { app ->
                    // Only the height is set: the width is the column's, and `AppCell` sizes the icon from the cell
                    // it is given (via `IconLabelCell`), so the two metrics meet without either being computed here.
                    AppCell(
                        app = app,
                        modifier = Modifier.height(cellHeight),
                        itemGestures = Modifier.appsItemGestures(gestureConfig) { onLaunch(app.componentKey) },
                    )
                }
            }
        }
    }
}
