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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.fitRowHeightDp
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

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
 * **The row height is the setting here, and the icon follows it** — the reverse of every grid on the surface, where
 * the icon size is chosen and the cell's height falls out of it. A list is one lane: there is no cell width to derive
 * a height from and no extent to divide, so nothing determines the row but the user. `AppRowCell` then sizes the icon
 * as `iconPercent` of what it is given, which at the blueprint's `1f` fills the row.
 *
 * @param metrics this list's icon sizing, resolved from `GridSlot.APPS_LIST`'s blueprint and the user's overrides.
 *   Passed rather than read ambiently because the surface resolves every grid's sizing in one place; the default the
 *   blueprint carries is `iconPercent = 1f`, since a row's label sits *beside* the icon rather than under it and so
 *   leaves no height to reserve.
 * @param rowHeight how tall each row is — resolved from the same blueprint and the same overrides, and the reason the
 *   icon controls above it do anything at all: until it was stored, a 56dp row clamped every icon to 40dp whatever
 *   the guardrails said. Clamped below to the heights the current guardrails can honour, exactly as the vertical grid
 *   clamps its column count: a height chosen before the guardrails moved would otherwise draw an icon outside them.
 *
 * Deliberately **not** here yet, all of it L1 behaviour worth rebuilding rather than porting: the alphabet filter
 * strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing helpers into the same file as
 * the list — three concerns in one composable), search, and drag-out-to-home.
 */
@Composable
fun AppsVerticalList(
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    metrics: IconMetrics,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val gestureConfig = rememberAppsGestureConfig()
    // Inset so the first and last rows clear the status and navigation bars. Applied as *content* padding, not as
    // padding on the list, so the scrolling content still passes under the bars instead of being clipped short of
    // them. A system constraint, not styling — the surface adds no decorative padding until a setting owns it.
    val barInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    // **The stored height, clamped to what the current icon guardrails can honour** — the list's counterpart of the
    // vertical grid's column fit, and the read half of the coupling the settings section's slider is bounded by. The
    // guardrails can move after a height was chosen, and a row shorter than the smallest allowed icon would draw that
    // icon smaller than the user permitted. Nothing is written, so widening the guardrails brings the height back.
    val drawnRowHeight = fitRowHeightDp(rowHeight.value, metrics).dp

    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = barInsets) {
            items(items = apps, key = { it.componentKey.flatten() }) { app ->
                AppRowCell(
                    app = app,
                    modifier = Modifier.fillMaxWidth().height(drawnRowHeight),
                    itemGestures = Modifier.appsItemGestures(gestureConfig) { onLaunch(app.componentKey) },
                )
            }
        }
    }
}
