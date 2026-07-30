package inkspire.morphic.feature.apps

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.Folder as FolderModel

/**
 * One entry on an APPS surface that arranges its own items — an app or a folder, resolved for rendering.
 *
 * The APPS counterpart of `HomeItem`, and deliberately a *different* type rather than a shared one: a home item
 * carries where it sits (a placement and a zone), because on a coordinate surface that is part of identifying it.
 * An APPS entry carries nothing of the sort — its position is its index in the page that holds it, so the list
 * structure says where it is and the item says only what it is.
 *
 * This is [inkspire.morphic.core.model.IconItem] with its ids resolved: the store speaks in components and folder
 * ids, and the UI needs labels, icons and contents.
 */
sealed interface AppsItem {

    /** A placed app: [info] is everything the cell needs to draw and launch it. */
    data class App(val info: AppInfo) : AppsItem

    /**
     * A folder: the [folder] definition plus its resolved [apps], in folder order, for the preview tile.
     *
     * [apps] can be shorter than the folder's membership — a member the app cache can't resolve (uninstalled and
     * not yet pruned, a paused work profile) has no [AppInfo] to draw. That is why a reorder reported by the UI
     * must be reconciled against real membership before it is written, exactly as home does.
     */
    data class Folder(val folder: FolderModel, val apps: List<AppInfo>) : AppsItem
}

/**
 * The APPS surface's render state.
 *
 * **One state for every layout, not one per layout** — switching layout must not reload anything, so both shapes
 * are always present and each layout reads the one it needs. They are not redundant: the derived layouts *are*
 * [apps] (their order is a function of the app cache and nothing else), while the pager is an arrangement the user
 * owns, which is why it is stored and comes back as pages rather than being re-derived.
 *
 * @property apps every installed app in A–Z display order — what the vertical list and grid render, and the order
 *   new apps are appended to the pager in.
 * @property pagerPages the APPS pager's arrangement: pages in order, each dense from its first slot. Empty until
 *   the surface has told the ViewModel its page capacity (see [AppsViewModel.setPagerGrid]), since how many items
 *   fit a page is a property of the device and the grid, not of the store.
 */
data class AppsState(
    val apps: List<AppInfo> = emptyList(),
    val pagerPages: List<List<AppsItem>> = emptyList(),
)

/**
 * This entry's **drag identity** — what the drag coordinator carries and a drop reports back.
 *
 * `GridItem` is the toolkit's currency (it spans widgets and containers too), while the APPS store speaks
 * [inkspire.morphic.core.model.IconItem], which is exactly {app, folder}. The two conversions below are the seam
 * between them, kept as one-liners here rather than spread across the drag wiring.
 */
val AppsItem.gridItem: GridItem
    get() = when (this) {
        is AppsItem.App -> GridItem.App(info.componentKey)
        is AppsItem.Folder -> GridItem.Folder(folder.id)
    }

/** This entry as the store's item type. */
val AppsItem.iconItem: IconItem
    get() = when (this) {
        is AppsItem.App -> IconItem.App(info.componentKey)
        is AppsItem.Folder -> IconItem.Folder(folder.id)
    }

/**
 * The dragged [GridItem] as an APPS store item, or null for a kind this surface cannot hold.
 *
 * Null is reachable in principle (a widget dragged from somewhere else) and should stay a null rather than a
 * throw: the APPS pager simply declines items it has nowhere to put, which is what its zone's `accepts` says too.
 */
fun GridItem.asIconItem(): IconItem? = when (this) {
    is GridItem.App -> IconItem.App(component)
    is GridItem.Folder -> IconItem.Folder(folderId)
    else -> null
}

/**
 * Resolves [component] to the app info this surface knows about, whether it is loose on a page or inside a folder.
 *
 * Both are needed for the same reason home needs its equivalent: a drag detaches an app from neither until it
 * lands, so an app being carried out of a folder is still a member and appears nowhere else. Anything that looks
 * up "the app under the finger" by position alone would find nothing and draw nothing.
 */
fun AppsState.appInfo(component: ComponentKey): AppInfo? =
    pagerPages.firstNotNullOfOrNull { page ->
        page.firstNotNullOfOrNull { item ->
            when (item) {
                is AppsItem.App -> item.info.takeIf { it.componentKey == component }
                is AppsItem.Folder -> item.apps.firstOrNull { it.componentKey == component }
            }
        }
    } ?: apps.firstOrNull { it.componentKey == component }
