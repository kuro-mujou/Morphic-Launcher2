package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * Provisional row height — **a placeholder, not a design choice.**
 *
 * Row height is a **user-configurable** surface metric (it is how a list trades density for reach), and the icon
 * follows it through [ListIconMetrics] rather than being sized independently. Both need the settings layer, which
 * isn't built, so a flat constant stands in and is the single line that changes when the setting lands.
 */
private val RowHeight = 56.dp

/**
 * The list's own icon proportion — the icon fills the row's inner height.
 *
 * Each surface supplies its own [IconMetrics] (that is what [LocalIconMetrics] is for), and a list's needs differ
 * from a grid's: there is no label *underneath* to leave room for, so the icon can take the whole inner height
 * instead of the grid default's fraction of the cell. The guardrails are inherited unchanged.
 */
private val ListIconMetrics = IconMetrics(iconPercent = 1f)

/**
 * The **vertical list** layout of the APPS surface: every app A–Z in one scrolling column, one row each.
 *
 * A *derived* layout — it stores nothing and re-renders straight from [apps], which is why it takes a plain list
 * and a launch callback and owns no state beyond scroll position. That is the whole reason it is the first layout
 * built: it exercises the surface end to end (repository → ordering → cells → launch) without needing the APPS
 * order repository, which doesn't exist yet.
 *
 * **Rows use the shared gesture contract**, not a `clickable` — see [appsItemGestures] for why, and for what the
 * unwired half of that contract is waiting on.
 *
 * Deliberately **not** here yet, all of it L1 behaviour worth rebuilding rather than porting: the alphabet filter
 * strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing helpers into the same file as
 * the list — three concerns in one composable), search, and drag-out-to-home.
 */
@Composable
fun AppsVerticalList(
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gestureConfig = rememberAppsGestureConfig()
    // Inset so the first and last rows clear the status and navigation bars. Applied as *content* padding, not as
    // padding on the list, so the scrolling content still passes under the bars instead of being clipped short of
    // them. A system constraint, not styling — the surface adds no decorative padding until a setting owns it.
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()

    CompositionLocalProvider(LocalIconMetrics provides ListIconMetrics) {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = barInsets) {
            items(items = apps, key = { it.componentKey.flatten() }) { app ->
                AppRowCell(
                    app = app,
                    modifier = Modifier.fillMaxWidth().height(RowHeight),
                    itemGestures = Modifier.appsItemGestures(gestureConfig) { onLaunch(app.componentKey) },
                )
            }
        }
    }
}
