package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.fitRowHeight
import inkspire.morphic.core.designsystem.surface.ReportScrollEdges
import inkspire.morphic.core.designsystem.surface.ScrollEdges
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
 * unwired half of that contract is waiting on. The target is the row's **icon and label**, not the strip they sit in:
 * `AppRowCell` hangs the gestures on a wrap-content group, so the width after a short label falls through to this
 * surface. That is space this layout does not use yet and deliberately keeps free — the alphabet filter strip and a
 * surface-level press both want it, and both are below.
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
 *   the guardrails said. Clamped below to the heights the current guardrails can honor, exactly as the vertical grid
 *   clamps its column count: a height chosen before the guardrails moved would otherwise draw an icon outside them.
 *
 * Deliberately **not** here yet, all of it L1 behavior worth rebuilding rather than porting: the alphabet filter
 * strip (L1 bundled the strip, its hover-dim animation, and four letter-indexing helpers into the same file as
 * the list — three concerns in one composable), search, and drag-out-to-home.
 */
@Composable
fun AppsVerticalList(
    apps: List<AppInfo>,
    onLaunch: (ComponentKey) -> Unit,
    metrics: IconMetrics,
    rowHeight: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val gestureConfig = rememberAppsGestureConfig()
    // The bar inset plus the grid's own margin, as **content** padding so the rows still scroll under the bars
    // instead of stopping short of them. A row's touch target is its icon and label (`AppRowCell` hangs the gestures
    // on a wrap-content group), so it narrows with the margin for free — the visible extent *is* the target, and
    // there is nothing to keep in step by hand.
    val contentPadding = appsContentPadding(horizontalPadding)
    // **The stored height, clamped to what this row can honor** — the list's counterpart of the vertical grid's column
    // fit, and the read half of the coupling the settings section's slider is bounded by. With icons on that means the
    // guardrails, which can move after a height was chosen: a row shorter than the smallest allowed icon would draw it
    // smaller than the user permitted. With icons off it means the label, since a row cannot be shorter than its own
    // text. Nothing is written either way, so widening the bound brings the stored height back.
    val drawnRowHeight = fitRowHeight(rowHeight, metrics)

    // **Where this list is scrolled, for the surface swipe.** Reaching HOME from a TOP or BOTTOM binding means
    // swiping across this list, so the pan may only claim once it has nothing further to scroll that way. Read
    // inside the lambda, never in composition — see `ReportScrollEdges`.
    val listState = rememberLazyListState()
    ReportScrollEdges {
        ScrollEdges(atTop = !listState.canScrollBackward, atBottom = !listState.canScrollForward)
    }

    CompositionLocalProvider(LocalIconMetrics provides metrics) {
        LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(items = apps, key = { it.componentKey.flatten() }) { app ->
                AppRowCell(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(drawnRowHeight),
                    itemGestures = Modifier.appsItemGestures(gestureConfig, app) {
                        onLaunch(app.componentKey)
                    },
                )
            }
        }
    }
}
