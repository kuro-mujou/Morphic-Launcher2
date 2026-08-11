package inkspire.morphic.feature.home

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridConfig
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.WidgetInfo
import java.text.Collator
import inkspire.morphic.core.model.Folder as FolderModel
import inkspire.morphic.core.model.IconContainer as IconContainerModel
import inkspire.morphic.core.model.WidgetContainer as WidgetContainerModel

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
     * A placed widget: the [info] the layout store keeps about it (its `appWidgetId`, provider and label) at
     * [placement] in [zone].
     *
     * **Its [placement] carries a real span**, unlike an app's or a folder's — those are always one visual cell,
     * where a widget is whatever size its provider asked for when it was added. Everything downstream already
     * handles that (the planner, the occupancy map and the cell layout all read spans), which is why adding
     * widgets needed no change to any of them.
     */
    data class Widget(
        val info: WidgetInfo,
        override val placement: GridPlacement,
        override val zone: HomeZone,
    ) : HomeItem {
        override val gridItem: GridItem get() = GridItem.Widget(info.appWidgetId)
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

    /**
     * A placed icon container: the [container] definition plus its resolved [icons], in container order.
     *
     * Unresolvable members are dropped from [icons] on the same terms as a folder's — an app the cache cannot
     * describe has no icon to draw — while the *stored* membership keeps them, so an app that is briefly
     * unavailable comes back to the slot it had.
     */
    data class IconContainer(
        val container: IconContainerModel,
        val icons: List<ContainerIcon>,
        override val placement: GridPlacement,
        override val zone: HomeZone,
    ) : HomeItem {
        override val gridItem: GridItem get() = GridItem.IconContainer(container.id)
    }

    /**
     * A placed widget container: the [container] definition plus the resolved [widgets] it pages between, in
     * container order.
     *
     * [widgets] is the same [WidgetInfo] a loose [Widget] carries, because a contained widget is hosted exactly as
     * a placed one is — what the container changes is *where* it draws and *when* it is visible, not what it is.
     */
    data class WidgetContainer(
        val container: WidgetContainerModel,
        val widgets: List<WidgetInfo>,
        override val placement: GridPlacement,
        override val zone: HomeZone,
    ) : HomeItem {
        override val gridItem: GridItem get() = GridItem.WidgetContainer(container.id)
    }
}

/**
 * One resolved entry inside an icon container — an app or a nested folder, ready to draw.
 *
 * The rendered twin of `core:model`'s `IconItem`, which names the same two cases by *id*: this carries what a cell
 * needs instead (an [AppInfo], or a folder plus the apps its preview tile shows), exactly as [HomeItem.Folder]
 * carries resolved apps beside the definition. Keeping the two separate is what lets the store stay id-keyed while
 * the surface never re-resolves anything.
 */
sealed interface ContainerIcon {

    /** An app in the container, as its own cell would draw it. */
    data class App(val info: AppInfo) : ContainerIcon

    /** A folder in the container: the [folder] definition plus the resolved [apps] behind its preview tile. */
    data class Folder(val folder: FolderModel, val apps: List<AppInfo>) : ContainerIcon
}

/**
 * HOME's **side zone**'s stored size — everything about it a user has an opinion about.
 *
 * One type for both zones (the dock and the widget area) because they are the same *kind* of thing: a fixed-extent
 * strip whose counts divide that extent. Which of the two is on screen is `HomeLayout`'s answer, and nothing here
 * changes with it — the store, the arithmetic and the settings control are shared, which is why this is not
 * `DockSizing` and `WidgetAreaSizing`.
 *
 * The one number that is *not* here is the dimension it does not set: the screen's, on the axis it runs along.
 * Turning these three into a grid is `CellFit.fitGridConfig`, in the surface, because that needs the measured area
 * and (for an icon grid) the current type scale.
 *
 * @property extentDp how thick the zone is, in dp — its **height** as a strip and its **width** as a rail
 *   (`SideZoneEdge`). It also **bounds the count divided out of it**: a cell is `extent ÷ count`, so past a point
 *   another line would leave cells too small to draw in — [rows] on a strip, [cols] on a rail.
 * @property cols how many columns across. Clamped to what fits when the grid is built and never written back, so a
 *   count too large for today's icon size comes back when the icons shrink.
 * @property rows how many rows down, on the same terms. Whichever of the two the extent bounds is *also reduced in
 *   storage* when the extent no longer supports it, which the zone's settings screen does on commit.
 */
data class SideZoneSizing(val extentDp: Int, val cols: Int, val rows: Int)

/**
 * What HOME's **main area** is, resolved — and the reason it is a sum type is that the two layouts do not merely
 * size their main area differently, they configure a *different quantity*.
 *
 * A pager divides the space it is given into a grid, so its setting is a pair of counts. A list is one lane and so
 * has nothing to divide: its setting is how tall one row is. There is no value either could give the other, which is
 * exactly what a sealed type says and what a nullable-pair-of-fields would not.
 *
 * It also carries the layout implicitly: a state holding [Pager] is a state whose main area is the pager. The
 * surface still reads `HomeState.layout` to *choose* what to draw, because it must choose on the frame before the
 * store has answered and this is null until then.
 */
sealed interface HomeMainSizing {

    /**
     * [HomeLayout.PAGER_WITH_DOCK]'s main area: a grid of [config], divided out of whatever space is left.
     *
     * @property wraps whether the pages loop round at the ends. Not a size, which stretches this type's name — but it
     *   is a setting only a *pager* can have, so it belongs to this arm for the same reason the counts do: a `List`
     *   given one would be a state with no meaning, and keeping it beside the state as a nullable field would make
     *   that expressible. The sum type is here to stop exactly that.
     */
    data class Pager(val config: GridConfig, val wraps: Boolean = false) : HomeMainSizing

    /**
     * [HomeLayout.LIST_WITH_WIDGET_AREA]'s main area: one lane of rows [rowHeightDp] tall.
     *
     * Clamped to what the icon guardrails allow where it is *drawn* (`fitRowHeightDp`), not here — the same
     * clamp-on-read the grid counts get, and for the same reason: a height too short for today's icons must come
     * back when the icons shrink.
     */
    data class List(val rowHeightDp: Int) : HomeMainSizing
}

/**
 * The home surface's render state for the current orientation — every placed item across *all* zones, exactly as the
 * repository streams them: apps, folders, widgets and both kinds of container.
 *
 * One flat list rather than a field per zone: the zones share every rule that matters (one item is in one zone,
 * one drag crosses between them), so splitting them here would only mean re-joining them at each use. The surface
 * filters with [inZone] to fill each grid.
 *
 * @property layout which pairing HOME is drawing — the one field the surface reads *before* anything is resolved,
 *   because it decides which surface to compose at all. It has a real default rather than being nullable for that
 *   reason: `PAGER_WITH_DOCK` is also `SurfaceRegister`'s default, so the frame before the store answers shows what
 *   an unconfigured launcher shows rather than nothing.
 * @property listApps the vertical list's apps in their stored order, resolved through the app cache. Empty on the
 *   pager layout, which has no list — and empty until the store answers on the one that does. Unlike [items] this is
 *   an *order*, not a set of placements: see `HomeListRepository` for why the two stores are separate.
 * @property catalog every installed app by component — the resolver [items] and [listApps] are already built from,
 *   published because home now has to render an app it has **never placed**: one dragged in from the APPS surface,
 *   whose icon has to follow the finger from the moment that surface closes. It has no placement, is in no folder,
 *   and until it is dropped it is in nothing home owns. [appInfo] falls back to it for exactly that window.
 * @property iconSizing each zone's **resolved** icon sizing, by slot — the blueprint's default with any user
 *   override merged in. One entry per zone that draws icons, because they are separate grids with their own
 *   blueprints and so their own independent icon config; the widget area contributes none, since a widget is not an
 *   icon in a cell. Empty until the surface reports its device, since resolution is per configuration.
 * @property main what the main area is and how it is sized, or null until the surface reports its device — resolved
 *   per device configuration, so there is nothing honest to say before then and the blueprint stands in for the
 *   frame or two until the store answers.
 * @property side the side zone's stored size, on the same terms as [main].
 */
data class HomeState(
    val items: List<HomeItem>,
    val layout: HomeLayout = HomeLayout.PAGER_WITH_DOCK,
    val listApps: List<AppInfo> = emptyList(),
    val catalog: Map<ComponentKey, AppInfo> = emptyMap(),
    val iconSizing: Map<GridSlot, IconSizing> = emptyMap(),
    val main: HomeMainSizing? = null,
    val side: SideZoneSizing? = null,
    val horizontalPaddingDp: Map<GridSlot, Int> = emptyMap(),
)

/**
 * The blank margin at [slot]'s left and right edges, in dp — zero until the store answers.
 *
 * Zero is the right "not yet": it is also every grid's blueprint default, so the frame before the first emission
 * looks like an unconfigured launcher rather than one whose grids jump inward. That is the opposite call from the
 * grid *counts*, whose fallback is the blueprint's own and whose settle is guarded — because there a wrong first
 * value would be written back, and here nothing is written at all.
 */
fun HomeState.paddingFor(slot: GridSlot): Int = horizontalPaddingDp[slot] ?: 0

/** The items placed in [zone] — one zone's grid contents, in the order the state reports them. */
fun HomeState.inZone(zone: HomeZone): List<HomeItem> = items.filter { it.zone == zone }

/** The placed icon container [containerId], or null when it is gone or its definition is not resolved yet. */
fun HomeState.iconContainer(containerId: Long): HomeItem.IconContainer? =
    items.filterIsInstance<HomeItem.IconContainer>().firstOrNull { it.container.id == containerId }

/**
 * Every installed app that icon container [containerId] does **not** already hold, in label order — what its "+"
 * offers.
 *
 * Filtered because `AppPicker`'s own KDoc says the caller does it ("a folder picker offers only apps not already in
 * it"), and here it is more than tidiness: adding an app a container already holds is a no-op the user cannot
 * distinguish from a broken button, since `AddToIconContainer` detaches before it inserts.
 *
 * **Sorted with a locale-aware `Collator`, never `lowercase()`** — the lesson the APPS ordering already learned and
 * the picker's own matching applies: raw UTF-16 puts every accented label after `Z`, so a Vietnamese or French list
 * breaks into two alphabets. Equal labels keep catalogue order, since `sortedWith` is stable.
 *
 * A derived read on the state rather than a ViewModel method, beside [appInfo] and [inZone], because that is what
 * it is: a projection of [catalog] with no store behind it and nothing to persist. Being a function of the
 * collected state is also what keeps it live — an app installed while the sheet is open appears in it.
 */
fun HomeState.appsNotIn(containerId: Long): List<AppInfo> {
    val held = iconContainer(containerId)
        ?.container?.items
        ?.filterIsInstance<IconItem.App>()
        ?.mapTo(mutableSetOf()) { it.component }
        .orEmpty()
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return catalog.values
        .filterNot { it.componentKey in held }
        .sortedWith(compareBy(collator) { it.label })
}

/**
 * Resolves [component] to the app info home can draw — **placed on a grid, inside a folder, or merely installed**.
 *
 * The first two are needed because a drag detaches an app from neither until it lands: an app dragged out of a folder
 * is still a member of it and has no placement at all, so anything that looks up "the app under the finger" by
 * placement alone — the floating proxy, the app a folder is being handed to render — finds nothing and draws nothing.
 * Searching both is what lets one drag cross grids and folders without the icon blinking out at each boundary.
 *
 * The third extends that same rule across the *surface* boundary, and is why [HomeState.catalog] exists: an app
 * dragged in from the APPS drawer is in neither of the first two until the drop commits, so home would have nothing
 * to put under the finger for the whole of the gesture it is meant to be hosting.
 *
 * An **icon container** is searched for the first reason, one level deeper: an app dragged out of one is still a
 * member and has no placement of its own, so nothing but the container could name it. Its nested folders are
 * searched too, since a folder inside a container holds apps exactly as one on the grid does.
 */
fun HomeState.appInfo(component: ComponentKey): AppInfo? =
    items.firstNotNullOfOrNull { item ->
        when (item) {
            is HomeItem.App -> item.info.takeIf { it.componentKey == component }
            is HomeItem.Folder -> item.apps.firstOrNull { it.componentKey == component }
            is HomeItem.IconContainer -> item.icons.firstNotNullOfOrNull { icon ->
                when (icon) {
                    is ContainerIcon.App -> icon.info.takeIf { it.componentKey == component }
                    is ContainerIcon.Folder -> icon.apps.firstOrNull { it.componentKey == component }
                }
            }
            // Neither kind of widget is an app or holds one, so they can never answer this.
            is HomeItem.Widget, is HomeItem.WidgetContainer -> null
        }
    } ?: catalog[component]
