package inkspire.morphic.feature.home

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.Folder as FolderModel

/**
 * A single item placed on the home grid. Carries where it sits ([placement]) and its stable drag/layout
 * identity ([gridItem]) so the surface can render, drag, and persist any item type uniformly.
 */
sealed interface HomeItem {
    val placement: GridPlacement
    val gridItem: GridItem

    /** A placed app: its [info] (label + identity for the icon) at [placement]. */
    data class App(val info: AppInfo, override val placement: GridPlacement) : HomeItem {
        override val gridItem: GridItem get() = GridItem.App(info.componentKey)
    }

    /**
     * A placed folder: the [folder] definition plus the resolved [apps] it contains (for the preview), in
     * folder order, at [placement].
     */
    data class Folder(
        val folder: FolderModel,
        val apps: List<AppInfo>,
        override val placement: GridPlacement,
    ) : HomeItem {
        override val gridItem: GridItem get() = GridItem.Folder(folder.id)
    }
}

/**
 * The home surface's render state for the current orientation — the placed [items] (apps and folders). Widgets
 * and containers join this once their cells exist; the repository already streams them.
 */
data class HomeState(val items: List<HomeItem>)
