package inkspire.morphic.feature.apps.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures

/**
 * The gesture wiring for the APPS layouts that **don't** drag: the launcher's one item-gesture contract, with only
 * the tap connected. The pager wires the same contract through `LauncherDragCell` instead, since it needs the drag
 * callbacks this one deliberately leaves empty.
 *
 * **Why go through the contract for what is, today, a tap.** A `clickable` would be one line — and would give
 * this surface its own recogniser with its own long-press timing and slop, drifting from the rest of the
 * launcher. That is precisely L1's defect (four parallel recognisers, 350ms vs 500ms long-press), and a list or
 * grid of apps is where a user most notices the difference, since they alternate between it and home constantly.
 *
 * **Why the no-ops are here and not at each call site.** They are identical for every layout and will stop being
 * no-ops together: [onShowMenu] becomes the app options menu (P7) and the drag callbacks become drag-out-to-home
 * (`EjectToHome`, part 7 of the drag plan) — one surface-wide behaviour each, not a per-layout choice. Keeping
 * them in one place means a layout cannot half-wire them, and there is exactly one file to change when they land.
 */
internal fun Modifier.appsItemGestures(
    config: ItemGestureConfig,
    onOpen: () -> Unit,
): Modifier = launcherItemGestures(
    config = config,
    onOpen = onOpen,
    // TODO(P7 gestures): the app's options menu (app info, hide, uninstall).
    onShowMenu = {},
    onDismissMenu = {},
    // TODO(EjectToHome): a drag off an app lifts it onto home. Until then an APPS item is draggable by contract
    //  but no coordinator is listening, so a long-press-and-move simply does nothing.
    onEdgeAction = {},
    onBeginDrag = {},
    onDragTo = {},
    onDrop = {},
    onCancelDrag = {},
)

/**
 * The APPS surface's gesture timings.
 *
 * A copy of home's values rather than a shared default, because nothing owns them yet: they are the launcher's
 * *one* set of item timings by intent, so a genuine fix is a default on
 * [inkspire.morphic.core.designsystem.drag.ItemGestureConfig] — worth doing once a third surface asks, and worth
 * not faking with a constant in whichever feature happened to need it second.
 */
@Composable
internal fun rememberAppsGestureConfig(): ItemGestureConfig {
    val density = LocalDensity.current
    return remember(density) {
        ItemGestureConfig(touchSlopPx = with(density) { 20.dp.toPx() }, longPressTimeoutMillis = 400L)
    }
}
