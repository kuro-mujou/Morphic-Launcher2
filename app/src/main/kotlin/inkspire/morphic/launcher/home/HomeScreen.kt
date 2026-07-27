package inkspire.morphic.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.grid.LauncherGrid
import inkspire.morphic.core.designsystem.grid.coordinateItems
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GridConfig
import org.koin.compose.koinInject

/**
 * The real HOME surface (first cut): renders the placed apps from [HomeStateHolder] on a [LauncherGrid] with
 * live baked icons via [AppCell], using the **coordinate** placement strategy (each app at its stored cell).
 *
 * This is the first place the whole stack meets on screen — `data:layout` placements + `data:apps` metadata +
 * `core:icon` bakes + `core:designsystem`'s grid. Drag-to-rearrange (persisting through
 * [inkspire.morphic.data.layout.LayoutRepository.apply]) and the real grid sizing / dock / pager are the next
 * parts; taps are a no-op until P7 wires app launching.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val holder = koinInject<HomeStateHolder>()
    val state by holder.state.collectAsStateWithLifecycle()

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        Box(modifier.fillMaxSize().background(colors.background)) {
            LauncherGrid(
                config = GridConfig(rows = HomeStateHolder.ROWS, cols = HomeStateHolder.COLS),
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                coordinateItems(
                    items = state.apps,
                    itemKey = { it.info.componentKey },
                    placement = { it.placement },
                ) { placed, cellModifier ->
                    AppCell(
                        app = placed.info,
                        onClick = {}, // TODO(P7): launch the app
                        modifier = cellModifier,
                    )
                }
            }
        }
    }
}
