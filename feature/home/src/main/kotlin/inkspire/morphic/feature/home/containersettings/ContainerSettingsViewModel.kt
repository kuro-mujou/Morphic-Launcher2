package inkspire.morphic.feature.home.containersettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.layout.LayoutChange
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.feature.home.ContainerIcon
import inkspire.morphic.feature.home.HomeViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * State holder for one container's settings screen: resolves what the container holds, and owns every write the
 * screen can make.
 *
 * **Scoped to the container, which is what makes it a per-instance ViewModel.** [route] is a constructor parameter
 * supplied through Koin (`parametersOf`), the second in the launcher to take one after the icon studio — and the
 * reason `NavDisplay` needs its `rememberViewModelStoreNavEntryDecorator` is exactly this: without it every entry
 * shares the Activity's store, so opening one container's settings and then another's would hand back the first
 * one's instance.
 *
 * **It writes straight through, with no optimistic layer** — deliberately, and the opposite call from
 * [HomeViewModel]. Optimism exists there because a *drag* has to land under the finger that dropped it; here every
 * write is a switch, a checkbox or a list row, where a store round-trip is imperceptible and a second source of
 * truth would be a way for the screen and the surface behind it to disagree.
 */
class ContainerSettingsViewModel(
    private val route: ContainerSettingsRoute,
    private val layoutRepository: LayoutRepository,
    private val appRepository: AppRepository,
    private val widgetHost: AppWidgetHostController,
) : ViewModel() {

    val state: StateFlow<ContainerSettingsState> =
        when (route) {
            is ContainerSettingsRoute.Icon -> iconState()
            is ContainerSettingsRoute.Widget -> widgetState()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContainerSettingsState())

    /**
     * The icon container's, joined through the same three stores the home surface uses — the container definitions,
     * the folder definitions a nested folder resolves through, and the app cache everything resolves through.
     */
    private fun iconState() = combine(
        layoutRepository.iconContainers(),
        layoutRepository.folders(),
        appRepository.observeApps(),
    ) { containers, folders, apps ->
        val container = containers.firstOrNull { it.id == route.containerId }
            ?: return@combine ContainerSettingsState()
        val infoByComponent = apps.associateBy { it.componentKey }
        val folderById = folders.associateBy { it.id }
        // Unresolvable members are dropped exactly as the cell drops them: an app the cache cannot describe has no
        // icon and no label, so there is no row to draw for it. The membership row survives, so it comes back.
        val icons = container.items.mapNotNull { member ->
            when (member) {
                is IconItem.App -> infoByComponent[member.component]?.let(ContainerIcon::App)
                is IconItem.Folder -> folderById[member.folderId]?.let { folder ->
                    ContainerIcon.Folder(folder, folder.apps.mapNotNull(infoByComponent::get))
                }
            }
        }
        ContainerSettingsState(
            settings = ContainerSettings.Icon(icons, container.arrangement),
            availableApps = apps.notIn(container.items),
        )
    }

    /** The widget container's. No app cache in it — a widget resolves through the layout store's own definitions. */
    private fun widgetState() = combine(
        layoutRepository.widgetContainers(),
        layoutRepository.widgets(),
    ) { containers, widgets ->
        val container = containers.firstOrNull { it.id == route.containerId }
            ?: return@combine ContainerSettingsState()
        val widgetById = widgets.associateBy { it.appWidgetId }
        ContainerSettingsState(
            settings = ContainerSettings.Widget(
                widgets = container.widgetIds.mapNotNull(widgetById::get),
                axis = container.axis,
                autoRotate = container.autoRotate,
                resetOnReturn = container.resetOnReturn,
            ),
        )
    }

    /** Lays the icon container out by [arrangement]. */
    fun setArrangement(arrangement: IconArrangement) {
        write(LayoutChange.SetIconContainerArrangement(route.containerId, arrangement))
    }

    /**
     * Writes all three of the widget container's settings.
     *
     * Whole-value, because the op is — see `LayoutChange.SetWidgetContainerOptions`. The screen passes the two it is
     * not changing back unchanged, which is what it has them for.
     */
    fun setWidgetOptions(axis: WidgetContainerAxis, autoRotate: Boolean, resetOnReturn: Boolean) {
        write(LayoutChange.SetWidgetContainerOptions(route.containerId, axis, autoRotate, resetOnReturn))
    }

    /**
     * Adds [components] to the icon container, in the order the picker reported them.
     *
     * One batch, so a multi-select of four apps is one write rather than four echoes the screen re-renders through.
     * Each `AddToIconContainer` detaches its app from wherever it was, so an app already on the grid leaves it.
     */
    fun addApps(components: List<ComponentKey>) {
        if (components.isEmpty()) return
        write(*components.map { LayoutChange.AddToIconContainer(route.containerId, IconItem.App(it)) }.toTypedArray())
    }

    /**
     * Takes [item] out of the icon container — **membership only, so it goes nowhere**.
     *
     * That is the honest behavior and worth stating: the app is not put back on the home grid, because the grid
     * has no cell reserved for it and inventing one is a placement decision this screen has no business making. It
     * stays installed and in the drawer, exactly as removing a folder leaves its apps.
     */
    fun removeIcon(item: IconItem) {
        write(LayoutChange.RemoveFromIconContainer(route.containerId, item))
    }

    /**
     * Takes widget [appWidgetId] out of the container **and gives its id back to the platform**.
     *
     * Both halves, for `HomeViewModel.removeWidget`'s reason: an allocated id outlives this process, so dropping the
     * membership row alone would leave the platform believing a widget nobody can see still exists. Unlike an icon,
     * a widget genuinely is destroyed here — there is no drawer for it to fall back to, so leaving it bound would
     * leak rather than preserve.
     */
    fun removeWidget(appWidgetId: Int) {
        write(
            LayoutChange.RemoveFromWidgetContainer(route.containerId, appWidgetId),
            LayoutChange.RemoveFromGrid(GridItem.Widget(appWidgetId)),
        )
        widgetHost.deleteId(appWidgetId)
    }

    /** Files a widget the add flow has just bound into this container. */
    fun addWidget(widget: WidgetInfo) {
        write(LayoutChange.AddToWidgetContainer(route.containerId, widget))
    }

    private fun write(vararg changes: LayoutChange) {
        viewModelScope.launch { layoutRepository.apply(HomeViewModel.ORIENTATION, changes.toList()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The apps in this list that [held] does not already contain, in label order — what the "Add apps" picker offers.
 *
 * Filtered because `AppPicker`'s own KDoc says the caller does it, and here it is more than tidiness: adding an app
 * the container already holds is a no-op the user cannot tell from a broken button, since `AddToIconContainer`
 * detaches before it inserts.
 *
 * **Sorted with a locale-aware `Collator`, never `lowercase()`** — the lesson the APPS ordering already learned:
 * raw UTF-16 puts every accented label after `Z`, so a Vietnamese or French list breaks into two alphabets. Equal
 * labels keep cache order, since `sortedWith` is stable.
 */
private fun List<AppInfo>.notIn(held: List<IconItem>): List<AppInfo> {
    val taken = held.filterIsInstance<IconItem.App>().mapTo(mutableSetOf()) { it.component }
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return filterNot { it.componentKey in taken }.sortedWith(compareBy(collator) { it.label })
}
