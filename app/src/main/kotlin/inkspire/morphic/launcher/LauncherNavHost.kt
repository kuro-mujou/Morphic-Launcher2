package inkspire.morphic.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.navigation.HomeRoute
import inkspire.morphic.core.navigation.LocalNavigator
import inkspire.morphic.core.navigation.rememberLauncherNavigator
import inkspire.morphic.feature.home.containersettings.ContainerSettingsRoute
import inkspire.morphic.feature.home.containersettings.ContainerSettingsScreen
import inkspire.morphic.feature.home.gestureaction.GestureActionDestination
import inkspire.morphic.feature.home.gestureaction.GestureActionRoute
import inkspire.morphic.feature.settings.SettingsRoute
import inkspire.morphic.feature.settings.SettingsScreen
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.iconstudio.IconStudioRoute
import inkspire.morphic.feature.settings.iconstudio.IconStudioScreen
import inkspire.morphic.feature.settings.wallpaper.WallpaperCaptureRoute
import inkspire.morphic.feature.settings.wallpaper.WallpaperCaptureScreen
import inkspire.morphic.feature.settings.wallpaper.WallpaperCropRoute
import inkspire.morphic.feature.settings.wallpaper.WallpaperCropScreen
import inkspire.morphic.feature.settings.wallpaperstudio.WallpaperStudioRoute
import inkspire.morphic.feature.settings.wallpaperstudio.WallpaperStudioScreen
import inkspire.morphic.feature.shell.LauncherShell

/**
 * The launcher's navigation host: the back stack, the [inkspire.morphic.core.navigation.Navigator] over it, and the
 * key → screen mapping.
 *
 * **This is the whole of navigation, in one small file** — rather than living inside a `MainActivity.setContent`
 * mixed with wallpaper-color loading, icon-cache invalidation and six
 * `CompositionLocalProvider`s, with the navigator as an anonymous object in the middle of it. Here the Activity only
 * provides what it owns (the icon render manager) and calls this; the navigator itself is
 * [rememberLauncherNavigator], in the module that owns navigation.
 *
 * **No theme wrapper here, deliberately.** Each destination themes its own *zone* — the launcher via `LauncherShell`
 * (which follows wallpaper brightness), settings via its own boundary (which follows the system). One theme around
 * the whole `NavDisplay` would make it impossible for them to differ.
 *
 * Start destination is [HomeRoute], which since the P9 flip is also what the home button resolves to.
 */
@Composable
fun LauncherNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeRoute)
    val navigator = rememberLauncherNavigator(backStack)

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            modifier = modifier,
            backStack = backStack,
            onBack = { navigator.goBack() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    LauncherShell(
                        onOpenSettings = { navigator.goTo(SettingsRoute()) },
                        onOpenAppsSettings = { layout ->
                            navigator.goTo(SettingsRoute(SettingsSection.APPS, layout))
                        },
                        onEditIcon = { component ->
                            navigator.goTo(IconStudioRoute.App(component.flatten()))
                        },
                        onOpenIconContainerSettings = { id ->
                            navigator.goTo(ContainerSettingsRoute.Icon(id))
                        },
                        onOpenWidgetContainerSettings = { id ->
                            navigator.goTo(ContainerSettingsRoute.Widget(id))
                        },
                        onAssignGesture = { item, gesture ->
                            when (item) {
                                is GridItem.App -> {
                                    navigator.goTo(GestureActionRoute.App(item.component.flatten(), gesture))
                                }

                                is GridItem.Folder -> {
                                    navigator.goTo(GestureActionRoute.Folder(item.folderId, gesture))
                                }

                                else -> Unit
                            }
                        },
                    )
                }
                entry<GestureActionRoute.App> { route ->
                    GestureActionDestination(
                        route = route,
                        onBack = { navigator.goBack() },
                        onChosen = { navigator.goBack() },
                    )
                }
                entry<GestureActionRoute.Folder> { route ->
                    GestureActionDestination(
                        route = route,
                        onBack = { navigator.goBack() },
                        onChosen = { navigator.goBack() },
                    )
                }
                entry<SettingsRoute> { route ->
                    SettingsScreen(
                        onBack = { navigator.goBack() },
                        initialSection = route.section,
                        initialLayout = route.layout,
                    )
                }
                entry<WallpaperCropRoute> { route ->
                    WallpaperCropScreen(
                        uri = route.uri,
                        target = route.target,
                        onDone = { navigator.goBack() },
                    )
                }
                entry<WallpaperCaptureRoute> {
                    WallpaperCaptureScreen(
                        onDone = { navigator.goBack() },
                    )
                }
                entry<IconStudioRoute.Global> { route ->
                    IconStudioScreen(
                        route = route,
                        onBack = { navigator.goBack() },
                    )
                }
                entry<IconStudioRoute.App> { route ->
                    IconStudioScreen(
                        route = route,
                        onBack = { navigator.goBack() },
                    )
                }
                entry<WallpaperStudioRoute> {
                    WallpaperStudioScreen(
                        onBack = { navigator.goBack() },
                    )
                }
                entry<ContainerSettingsRoute.Icon> { route ->
                    ContainerSettingsScreen(
                        route = route,
                        onBack = { navigator.goBack() },
                    )
                }
                entry<ContainerSettingsRoute.Widget> { route ->
                    ContainerSettingsScreen(
                        route = route,
                        onBack = { navigator.goBack() },
                    )
                }
            },
        )
    }
}
