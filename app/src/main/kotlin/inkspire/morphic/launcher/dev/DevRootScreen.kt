package inkspire.morphic.launcher.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.drag.DragCoordinator
import inkspire.morphic.core.designsystem.drag.LocalDragCoordinator
import inkspire.morphic.core.designsystem.drag.rememberDragCoordinator
import inkspire.morphic.core.designsystem.surface.LocalEjectToHome
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.feature.apps.AppsScreen
import inkspire.morphic.feature.home.HomeScreen

/** The dev harness screens; the floating chip switches between them. */
private enum class DevScreen(val label: String) {
    Home("Home"),
    Apps("Apps"),
    AppsGrid("AppsGrid"),
    AppsPager("AppsPager"),
    AppsCategory("AppsCategory"),
    AppsCards("AppsCards"),
    Drag("Drag"),
    Pager("Pager"),
    PagerDrag("Pager+Drag"),
    Surface("Surface"),
    Grid("Grid"),
    Reflow("Reflow"),
    ScrollGrid("ScrollGrid"),
    CategoryPager("CategoryPager"),
    IconLayers("IconLayers"),
    ;

    fun next(): DevScreen = entries[(ordinal + 1) % entries.size]
}

/**
 * Dev entry point: shows one harness screen full-screen with a floating chip (top-end) to switch. Keeps every
 * interaction harness reachable without editing code.
 *
 * Reached from a row in settings rather than from the launcher, so no dev chrome ships on the real surfaces. It is
 * still a peer destination in the nav graph — the playgrounds are the regression harness for the drag toolkit, the
 * grid and the surface pager, and `DevGalleryScreen` is the component reference the design-system rules require be
 * kept current, so none of it is scaffolding to be thrown away with the shell landing.
 *
 * **It supplies its own [LauncherTheme] and its own [DragCoordinator].** `HomeScreen` and `AppsScreen` used to own
 * both; now that `feature:shell` owns the launcher's theme boundary *and* hosts the one coordinator every surface
 * shares, anything hosting those screens outside the shell has to provide them — this harness is the only such
 * caller. The theme is fixed dark rather than following the system, matching what the shell does, so a harness screen
 * looks like the launcher it is standing in for.
 *
 * The coordinator makes each screen work *in isolation*: a harness shows one surface at a time, so there is nothing
 * to eject onto and [LocalEjectToHome] is deliberately left null — an APPS drag here simply has nowhere to land. What
 * the harness still exercises is everything within a surface, which is what these entries are for; cross-surface drag
 * is the shell's, and the surface-pager playground is where that is prodded.
 */
@Composable
fun DevRootScreen(modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf(DevScreen.Home) }
    LauncherTheme(darkTheme = true) {
        CompositionLocalProvider(LocalDragCoordinator provides rememberDragCoordinator()) {
            Box(modifier.fillMaxSize()) {
                when (screen) {
                    DevScreen.Home -> HomeScreen()
                    DevScreen.Apps -> AppsScreen()
                    // The layout is normally a user setting (data:settings); until then the harness picks it
                    // per entry, which is also the cheapest way to eyeball two layouts side by side.
                    DevScreen.AppsGrid -> AppsScreen(layout = AppsLayout.VERTICAL_GRID)
                    DevScreen.AppsPager -> AppsScreen(layout = AppsLayout.PAGER)
                    DevScreen.AppsCategory -> AppsScreen(layout = AppsLayout.PAGER_WITH_CATEGORY)
                    DevScreen.AppsCards -> AppsScreen(layout = AppsLayout.CATEGORY_CARD)
                    DevScreen.Drag -> DragPlaygroundScreen()
                    DevScreen.Pager -> PagerPlaygroundScreen()
                    DevScreen.PagerDrag -> PagerDragPlaygroundScreen()
                    DevScreen.Surface -> SurfacePagerPlaygroundScreen()
                    DevScreen.Grid -> GridPlaygroundScreen()
                    DevScreen.Reflow -> ReflowPlaygroundScreen()
                    DevScreen.ScrollGrid -> ScrollGridPlaygroundScreen()
                    DevScreen.CategoryPager -> CategoryPagerPlaygroundScreen()
                    DevScreen.IconLayers -> IconLayerPlaygroundScreen()
                }
                // Theme-independent chip so it reads over either screen; label shows what tapping switches TO.
                Text(
                    text = "→ ${screen.next().label}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .clickable { screen = screen.next() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
