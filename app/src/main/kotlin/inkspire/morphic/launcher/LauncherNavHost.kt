package inkspire.morphic.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import inkspire.morphic.core.navigation.HomeRoute
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.core.navigation.rememberLauncherNavigator
import inkspire.morphic.feature.settings.SettingsRoute
import inkspire.morphic.feature.settings.SettingsScreen
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.wallpaper.WallpaperCaptureRoute
import inkspire.morphic.feature.settings.wallpaper.WallpaperCaptureScreen
import inkspire.morphic.feature.settings.wallpaper.WallpaperCropRoute
import inkspire.morphic.feature.settings.wallpaper.WallpaperCropScreen
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
 * Start destination is [HomeRoute], which since the P9 flip is also what the home button resolves to.
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
                    //
                    // **The way in is a long-press on empty space**, which is why the shell takes this action rather
                    // than reading `LocalNavigator` itself: `app` owns the back stack, so `app` says where "Settings"
                    // goes. It replaces the gear chip that stood in for the menu until it existed — that chip was
                    // scaffolding kept here rather than in `feature:shell` precisely so deleting it would touch no
                    // feature module, and it hasn't.
                    LauncherShell(
                        onOpenSettings = { navigator.goTo(SettingsRoute()) },
                        // **`app` is the only layer that may name a section**, which is what keeps the settings
                        // taxonomy inside `feature:settings`: the APPS surface says "open my settings" and passes
                        // the arrangement it is showing (`AppsLayout`, which is `core:model` and shared by
                        // everyone), and the mapping from that to a pane happens here, where both are already
                        // visible. L1 exported its 11-value section enum to every consumer to achieve the same
                        // thing.
                        onOpenAppsSettings = { layout ->
                            navigator.goTo(SettingsRoute(SettingsSection.APPS, layout))
                        },
                    )
                }
                entry<SettingsRoute> { route ->
                    SettingsScreen(
                        onBack = { navigator.goBack() },
                        // Null for the ordinary way in, which is what opens settings where it always opens.
                        initialSection = route.section,
                        initialLayout = route.layout,
                        // Settings is **one** destination: its sections are panes, two of which share the screen on a
                        // tablet, so which one is showing is that screen's own state. The dev harness is the only
                        // thing it needs from out here, and it is passed as an action — which keeps
                        // `feature:settings` from ever learning that destination exists.
                        onOpenDevHarness = { navigator.goTo(DevHarnessRoute) },
                    )
                }
                // Declared by `feature:settings`, mapped here — which is the whole point of `entryProvider` being a
                // mapping rather than a registry: a destination that belongs to one feature stays in it, and `app`
                // only says where it goes.
                entry<WallpaperCropRoute> { route ->
                    // Both fields, not just the uri: the target decides the shape the screen frames against and the
                    // size it stores at, so dropping it here would quietly frame a landscape half as a portrait one.
                    WallpaperCropScreen(
                        uri = route.uri,
                        target = route.target,
                        onDone = { navigator.goBack() },
                    )
                }
                entry<WallpaperCaptureRoute> { WallpaperCaptureScreen(onDone = { navigator.goBack() }) }
                entry<DevHarnessRoute> { DevRootScreen() }
            },
        )
    }
}

