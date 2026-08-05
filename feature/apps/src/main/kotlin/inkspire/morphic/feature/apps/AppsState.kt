package inkspire.morphic.feature.apps

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.Category
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.IconSizing
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
 * One category and the apps filed under it, resolved for rendering — a page of the category pager, or a card of the
 * category card layout.
 *
 * The UI-side twin of `data:layout`'s `CategoryContents`, which speaks in components; this carries the [AppInfo]s a
 * cell needs. Named for the contents rather than for either look, for the same reason that type is.
 *
 * @property apps in the user's order. Can be **empty**: a category the user emptied keeps its definition, so its
 *   page stays on screen and can be dragged back into.
 */
data class AppsCategory(val category: Category, val apps: List<AppInfo>)

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
 * @property pagerPages the APPS pager's arrangement: pages in order, each dense from its first slot. Empty until the
 *   surface has reported both its device (see [AppsViewModel.setDevice]) *and* the grid it can draw
 *   ([AppsViewModel.setPagerFit]), since how many items fit a page is a property of the device, the chosen grid and the
 *   space it has — none of them the store's to know.
 * @property categories the category arrangement, in category order. Needs no capacity to arrive — a category is one
 *   scrolling list, so unlike [pagerPages] it is populated from the first emission.
 * @property iconSizing each grid's **resolved** icon sizing, by slot: the blueprint's default with any user override
 *   already merged in. Empty until the surface reports its device configuration, since resolution is per
 *   configuration — a layout reading a missing slot falls back to `LocalIconMetrics`' own default for that frame.
 * @property gridCols each **scrolling** grid's resolved visual column count, by slot — the whole of such a grid's
 *   size, since its rows are however many its content reaches. Empty until the device is reported, with the
 *   blueprint standing in meanwhile, exactly as [iconSizing] does.
 * @property pagerConfig the APPS pager's resolved **stored** grid — the one APPS grid with a row count, and so the only
 *   one with a `GridConfig` to give. Null until the device is reported. It is the size the user chose, *not* the page
 *   capacity: the surface fits it to the measured window and reports the result back
 *   ([AppsViewModel.setPagerFit]), and that fit is what the store paginates against. Both are needed, and this is the
 *   input half — a screen cannot fit a size it was never told.
 * @property listRowHeightDp how tall one row of the vertical list is, resolved. Null until the device is reported.
 *   The one *cell* dimension on this surface that is stored rather than derived, because a one-lane grid has no cell
 *   width to derive from — see `AppsListGrid`.
 */
data class AppsState(
    val apps: List<AppInfo> = emptyList(),
    val pagerPages: List<List<AppsItem>> = emptyList(),
    val categories: List<AppsCategory> = emptyList(),
    val iconSizing: Map<GridSlot, IconSizing> = emptyMap(),
    val gridCols: Map<GridSlot, Int> = emptyMap(),
    val pagerConfig: GridConfig? = null,
    val listRowHeightDp: Int? = null,
    val horizontalPaddingDp: Map<GridSlot, Int> = emptyMap(),
)

/**
 * The blank margin at [slot]'s left and right edges, in dp — zero until the store answers.
 *
 * Zero is also every grid's blueprint default, so the frame before the first emission looks like an unconfigured
 * launcher rather than one whose grids jump inward. Nothing is ever written from a read, so unlike the pager's
 * capacity this needs no "has the store answered?" guard.
 */
fun AppsState.paddingFor(slot: GridSlot): Int = horizontalPaddingDp[slot] ?: 0

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

// There is deliberately no `AppsState.appInfo(component)` here, though home has the equivalent on `HomeState`. It was
// written and was dead on arrival: a layout is handed a *slice* of this state (`pagerPages`, `categories`), never the
// whole of it, so neither place that resolves "the app under the finger" could reach a state-level helper. Each
// arranging layout owns that lookup over the shape it actually receives instead — `appInPages` in `layout/pager`,
// `appInCategories` in `layout/categorycard`.
