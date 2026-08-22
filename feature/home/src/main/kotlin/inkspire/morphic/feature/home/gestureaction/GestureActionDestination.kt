package inkspire.morphic.feature.home.gestureaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The picker's destination: turns a [GestureActionRoute] back into the item it names and hands it to the screen.
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
    val item: GridItem? = when (route) {
        is GestureActionRoute.App -> ComponentKey.parse(route.component)?.let(GridItem::App)
        is GestureActionRoute.Folder -> GridItem.Folder(route.folderId)
    }
    if (item == null) {
        onBack()
        return
    }
    GestureActionScreen(
        gesture = route.gesture,
        viewModel = koinViewModel { parametersOf(item, route.gesture) },
        onBack = onBack,
        onChosen = onChosen,
        modifier = modifier,
    )
}
