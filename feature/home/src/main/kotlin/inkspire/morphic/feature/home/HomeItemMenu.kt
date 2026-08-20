package inkspire.morphic.feature.home

import androidx.compose.ui.geometry.Rect
import inkspire.morphic.core.designsystem.menu.LauncherMenuHost
import inkspire.morphic.core.designsystem.menu.MenuAction
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.widgets.AppWidgetHostController

/**
 * What a long-press on a HOME item offers, per kind of item.
 *
 * **Only an app gets [LauncherMenuHost.showApp], and that is the whole shape of the dispatch.** That call adds the
 * shortcuts stage — what the *app* publishes — which nothing else here has: a widget, a folder and both containers
 * are the launcher's own objects, so their menus are one stage of verbs this surface owns. What each contributes is
 * only what HOME can do to it; the app commands (App info, Uninstall, Edit icon) are bound once at the shell,
 * because the same app is reachable from the drawer and from inside a folder too.
 *
 * A plain function rather than a composable: it reads nothing from composition and runs on a press, so it takes its
 * collaborators as parameters. That is also what keeps it out of `HomePagerSurface`, which is the point — this is 85
 * lines of vocabulary, not of surface.
 *
 * @param onResize hands back the resize the widget arm started, since the overlay it drives is the surface's state.
 */
@Suppress("LongParameterList")
internal fun showHomeItemMenu(
    item: HomeItem,
    anchor: Rect,
    menuHost: LauncherMenuHost?,
    viewModel: HomeViewModel,
    widgetHost: AppWidgetHostController,
    onResize: (HomeResize) -> Unit,
    onOpenIconContainerSettings: (Long) -> Unit,
    onOpenWidgetContainerSettings: (Long) -> Unit,
) {
    when (item) {
        is HomeItem.App -> menuHost?.showApp(
            component = item.info.componentKey,
            label = item.info.label,
            anchor = anchor,
            surfaceActions = listOf(
                MenuAction("Remove") {
                    viewModel.applyChanges(listOf(LayoutChange.RemoveFromGrid(item.gridItem)))
                },
            ),
        )
        // A widget offers one verb and no shortcuts stage: it is not an app, so App info and Uninstall would
        // name its *provider* rather than the thing being long-pressed. Removing it also releases the
        // `appWidgetId` — see [HomeViewModel.removeWidget], which is why this is not a plain `RemoveFromGrid`.
        is HomeItem.Widget -> menuHost?.show(
            title = item.info.label.ifBlank { UnnamedWidget },
            anchor = anchor,
            actions = buildList {
                // **Every widget is offered a resize**, whatever its `resizeMode` says — see
                // `WidgetResizeRules` for why that declaration is not honored. What the provider *is*
                // believed about is how small it can be drawn, which is a live read of it rather than
                // anything the layout stores. A widget the platform can no longer describe gets no row,
                // because there is nothing to bound the drag with.
                val rules = widgetHost.boundWidget(item.info.appWidgetId)?.resize
                if (rules != null) {
                    add(
                        MenuAction("Resize") {
                            onResize(
                                HomeResize(
                                    item = item.gridItem,
                                    zone = item.zone,
                                    rules = rules,
                                    placement = item.placement,
                                ),
                            )
                        },
                    )
                }
                add(MenuAction("Remove widget") { viewModel.removeWidget(item.info.appWidgetId) })
            },
        )
        // No shortcuts stage and no App info — a folder is the launcher's own object, not an installed app.
        // "Remove folder" takes the folder off the grid and its membership with it (`RemoveFromGrid` cascades),
        // leaving the apps themselves installed and still in the drawer.
        is HomeItem.Folder -> menuHost?.show(
            title = item.folder.label.ifBlank { UnnamedFolder },
            anchor = anchor,
            actions = listOf(
                MenuAction("Remove folder") {
                    viewModel.applyChanges(listOf(LayoutChange.RemoveFromGrid(item.gridItem)))
                },
            ),
        )
        // **Add app** is the same picker the empty container's "+" opens, offered here because once a container
        // holds anything there is no "+" left to press. **No arrangement chooser yet** — that is the next
        // container slice, and a row that opened nothing would be worse than a missing one (the settings
        // sections' own rule).
        //
        // Removing takes the membership rows with it by cascade and leaves every app installed and every nested
        // folder's contents alone. What it does *not* do is put those icons back on the grid: they lose their
        // home placement and remain in the drawer, exactly as removing a folder does. Consistent, and worth
        // knowing before pressing it.
        is HomeItem.IconContainer -> menuHost?.show(
            title = IconContainerTitle,
            anchor = anchor,
            actions = listOf(
                MenuAction("Container settings") { onOpenIconContainerSettings(item.container.id) },
                MenuAction("Remove container") {
                    viewModel.applyChanges(listOf(LayoutChange.RemoveFromGrid(item.gridItem)))
                },
            ),
        )
        // **Add widget** is what makes the paging reachable before any drag work exists, and it costs nothing —
        // it reuses the add flow this surface already holds, aimed at this container. **Remove container** goes
        // through the ViewModel rather than being a plain `RemoveFromGrid` for `removeWidget`'s reason, once per
        // contained widget: the cascade drops membership but not the widgets' definitions or their allocated
        // ids, so the plain op would leak every widget in it.
        is HomeItem.WidgetContainer -> menuHost?.show(
            title = WidgetContainerTitle,
            anchor = anchor,
            actions = listOf(
                MenuAction("Container settings") { onOpenWidgetContainerSettings(item.container.id) },
                MenuAction("Remove container") { viewModel.removeWidgetContainer(item.container.id) },
            ),
        )
    }
}
