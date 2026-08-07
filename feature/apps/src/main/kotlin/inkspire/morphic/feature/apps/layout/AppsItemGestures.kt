package inkspire.morphic.feature.apps.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.surface.LocalEjectToHome
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The gesture wiring for the APPS layouts that own **no arrangement of their own** — the A–Z list and the A–Z grid.
 * A tap launches; a long-press-and-move **ejects the app onto HOME**.
 *
 * **Why go through the contract for what used to be just a tap.** A `clickable` would be one line — and would give
 * these surfaces their own recogniser with their own long-press timing and slop, drifting from the rest of the
 * launcher. That is precisely L1's defect (four parallel recognisers, 350ms vs 500ms long-press), and a list or grid
 * of apps is where a user most notices the difference, since they alternate between it and home constantly.
 *
 * ## Eject on lift, not on reaching a target
 *
 * These two layouts close the drawer the instant the drag begins, where the pager, the category pager and the
 * category card wait for the finger to reach the eject band at the top. The difference is not a preference: those
 * three store an arrangement, so a drag inside them is a *rearrangement* until the user says otherwise, and the band
 * is how they say it. These are **derived** — recomputed A–Z from the app cache, with nothing to rearrange — so a
 * drag on one of them can only mean one thing, and making the user carry the app to a target to say the only thing
 * it could say would be ceremony.
 *
 * Nothing else about the drag changes at that moment: the coordinator is the launcher's, the lifted cell keeps its
 * own pointer stream (its surface stays composed, merely panned away), and home's zones are already registered. The
 * eject is only the surface getting out of the way.
 *
 * A release is `coordinator.drop()` and nothing more — whichever zone the finger came to rest in commits, and if that
 * is none (a release over the gap between home and its dock, say) the drag is simply abandoned. There is no
 * source-side bookkeeping to do here, because these layouts own nothing a drag could have left.
 *
 * @param onOpen a tap: launch [component].
 */
@Composable
internal fun Modifier.appsItemGestures(
    config: ItemGestureConfig,
    component: ComponentKey,
    onOpen: () -> Unit,
): Modifier {
    val coordinator = requireDragCoordinator()
    val eject = LocalEjectToHome.current
    return launcherItemGestures(
        config = config,
        onOpen = onOpen,
        // TODO(P7 gestures): the app's options menu (app info, hide, uninstall). The gesture contract already has the
        //  long-press → menu → drag path; what is missing is the menu itself, and nothing here changes when it lands.
        onShowMenu = {},
        onDismissMenu = {},
        onEdgeAction = {},
        onBeginDrag = { root ->
            coordinator.start(GridItem.App(component), root)
            eject?.invoke()
        },
        onDragTo = coordinator::moveTo,
        onDrop = { coordinator.drop() },
        onCancelDrag = coordinator::cancel,
    )
}

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
