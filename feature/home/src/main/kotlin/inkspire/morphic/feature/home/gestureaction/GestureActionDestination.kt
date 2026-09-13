package inkspire.morphic.feature.home.gestureaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The picker's destination: turns a [GestureActionRoute] back into the [GestureTarget] it names and hands it to the
 * screen.
 *
 * **The unflattening lives here rather than in `app`**, which only maps keys to composables: reconstructing a
 * `GridItem` is `feature:home`'s vocabulary, and doing it in the entry provider would put home's model in the
 * navigation layer — the thing `LauncherRoute`'s KDoc exists to prevent.
 *
 * A component that no longer parses closes the destination rather than showing an empty picker. That is reachable:
 * the back stack is restored across process death, and an app can be uninstalled in between.
 */
@Composable
fun GestureActionDestination(
    route: GestureActionRoute,
    onBack: () -> Unit,
    onChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target: GestureTarget? = when (route) {
        is GestureActionRoute.App ->
            ComponentKey.parse(route.component)?.let { GestureTarget.Item(GridItem.App(it), route.gesture) }

        is GestureActionRoute.Folder -> GestureTarget.Item(GridItem.Folder(route.folderId), route.gesture)
        is GestureActionRoute.HomeSwipe -> GestureTarget.HomeSwipe(route.direction)
        GestureActionRoute.HomeDoubleTap -> GestureTarget.HomeDoubleTap
    }
    if (target == null) {
        onBack()
        return
    }
    GestureActionScreen(
        target = target,
        viewModel = koinViewModel { parametersOf(target) },
        onBack = onBack,
        onChosen = onChosen,
        modifier = modifier,
    )
}
