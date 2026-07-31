package inkspire.morphic.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import inkspire.morphic.core.navigation.HomeRoute
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.core.navigation.SettingsRoute
import inkspire.morphic.core.navigation.rememberLauncherNavigator
import inkspire.morphic.feature.settings.SettingsScreen
import inkspire.morphic.feature.settings.SurfaceRegisterRoute
import inkspire.morphic.feature.settings.register.SurfaceRegisterScreen
import inkspire.morphic.feature.shell.LauncherShell
import inkspire.morphic.launcher.dev.DevRootScreen
import kotlinx.serialization.Serializable

/**
 * The dev harness, as a destination.
 *
 * **Declared here rather than in `core:navigation` on purpose.** It is not a product screen — it is scaffolding only
 * `app` knows about — and a `core` module has no business exporting it to every consumer. That this key works
 * perfectly well from outside the navigation module is the point: `entryProvider` is a mapping, not a registry, so
 * any module can contribute a destination. L1 put *everything* in its navigation module, which is how an 11-value
 * settings enum ended up on `feature:home`'s compile classpath.
 */
@Serializable
private data object DevHarnessRoute : NavKey

/**
 * The launcher's navigation host: the back stack, the [inkspire.morphic.core.navigation.Navigator] over it, and the
 * key → screen mapping.
 *
 * **This is the whole of navigation, in one small file.** L1's equivalent lived inside a 204-line
 * `MainActivity.setContent` mixed with wallpaper-colour loading, icon-cache invalidation and six
 * `CompositionLocalProvider`s, with the navigator as an anonymous object in the middle of it. Here the Activity only
 * provides what it owns (the icon render manager) and calls this; the navigator itself is
 * [rememberLauncherNavigator], in the module that owns navigation.
 *
 * **No theme wrapper here, deliberately.** Each destination themes its own *zone* — the launcher via `LauncherShell`
 * (which follows wallpaper brightness), settings via its own boundary (which follows the system). One theme around
 * the whole `NavDisplay`, as L1 had, makes it impossible for them to differ.
 *
 * Start destination is [HomeRoute] — with the `HOME` intent category still unset (P9) this is a normal app that opens
 * on the launcher surface, which is exactly what is wanted for developing it.
 */
@Composable
fun LauncherNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeRoute)
    val navigator = rememberLauncherNavigator(backStack)

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            // Delegated to the navigator so the start destination is guarded in one place: back on HOME must fall
            // through to the system rather than pop the launcher off its own stack.
            onBack = { navigator.goBack() },
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    // The launcher. Which edges are swipeable now comes from the surface register in
                    // `data:settings`; nothing is bound until the user binds one — see `LauncherShell`.
                    Box(Modifier.fillMaxSize()) {
                        LauncherShell()
                        // TODO(P7 gestures): a home long-press → options menu is the real way into settings, and the
                        //  free cell space already falls through to the surface for exactly that. Until it exists
                        //  this chip is the only entry point, so it is scaffolding kept in `app` rather than in
                        //  `feature:shell` — a feature module should not ship a temporary affordance.
                        DevEntryChip(onClick = { navigator.goTo(SettingsRoute) })
                    }
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navigator.goBack() },
                        // Section rows. `feature:settings` declares its own section keys but does not map them,
                        // so the label→action pairing lives here with the rest of the graph. The dev harness rides the
                        // same seam, which is what keeps `feature:settings` from ever learning it exists.
                        sections = listOf(
                            "Surface register →" to { navigator.goTo(SurfaceRegisterRoute) },
                            "Dev harness →" to { navigator.goTo(DevHarnessRoute) },
                        ),
                    )
                }
                entry<SurfaceRegisterRoute> { SurfaceRegisterScreen(onBack = { navigator.goBack() }) }
                entry<DevHarnessRoute> { DevRootScreen() }
            },
        )
    }
}

/**
 * The temporary way into settings: a small chip over the launcher.
 *
 * Deliberately styled outside the theme (fixed translucent black, white text) for the reason the dev harness chip is
 * — it has to stay legible over any surface underneath, and it is not part of the design system because it is not
 * meant to survive. The P7 long-press menu replaces it.
 */
@Composable
private fun DevEntryChip(onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Text(
            text = "⚙",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
