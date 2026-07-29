package inkspire.morphic.feature.home

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Folder as FolderModel

/**
 * A single item placed on the home surface. Carries where it sits — which [zone]'s grid, and the [placement]
 * within it — plus its stable drag/layout identity ([gridItem]), so the surface can render, drag, and persist any
 * item type uniformly.
 *
 * **Why the zone is on the item.** HOME is not one grid: the pager (`MAIN`) and the dock are separate coordinate
 * grids with *independent* coordinate spaces, so a [GridPlacement] alone does not say where something is — dock
 * cell (0,0) and main cell (0,0) are the same placement value in different places. The zone is what disambiguates
 * them, and an item is in exactly one zone, so it belongs on the item rather than in a per-zone list. This mirrors
 * [inkspire.morphic.data.layout.PlacedItem], the repository's own value shape.
 */
sealed interface HomeItem {
    val placement: GridPlacement
    val zone: HomeZone
    val gridItem: GridItem

    /** A placed app: its [info] (label + identity for the icon) at [placement] in [zone]. */
    data class App(
        val info: AppInfo,
        override val placement: GridPlacement,
        override val zone: HomeZone,
    ) : HomeItem {
        override val gridItem: GridItem get() = GridItem.App(info.componentKey)
    }

    /**
     * A placed folder: the [folder] definition plus the resolved [apps] it contains (for the preview), in
     * folder order, at [placement] in [zone].
     */
    data class Folder(
        val folder: FolderModel,
        val apps: List<AppInfo>,
        override val placement: GridPlacement,
        override val zone: HomeZone,
    ) : HomeItem {
        override val gridItem: GridItem get() = GridItem.Folder(folder.id)
    }
}

/**
 * The home surface's render state for the current orientation — the placed [items] (apps and folders) across
 * *all* zones, exactly as the repository streams them. Widgets and containers join this once their cells exist.
 *
 * One flat list rather than a field per zone: the zones share every rule that matters (one item is in one zone,
 * one drag crosses between them), so splitting them here would only mean re-joining them at each use. The surface
 * filters with [inZone] to fill each grid.
 */
data class HomeState(val items: List<HomeItem>)

/** The items placed in [zone] — one zone's grid contents, in the order the state reports them. */
fun HomeState.inZone(zone: HomeZone): List<HomeItem> = items.filter { it.zone == zone }

/**
 * Resolves [component] to the app info home knows about, whether it is **placed on a grid or inside a folder**.
 *
 * Both are needed because a drag detaches an app from neither until it lands: an app dragged out of a folder is still
 * a member of it and has no placement at all, so anything that looks up "the app under the finger" by placement alone
 * — the floating proxy, the app a folder is being handed to render — finds nothing and draws nothing. Searching both
 * is what lets one drag cross grids and folders without the icon blinking out at each boundary.
 */
fun HomeState.appInfo(component: ComponentKey): AppInfo? =
    items.firstNotNullOfOrNull { item ->
        when (item) {
            is HomeItem.App -> item.info.takeIf { it.componentKey == component }
            is HomeItem.Folder -> item.apps.firstOrNull { it.componentKey == component }
        }
    }
