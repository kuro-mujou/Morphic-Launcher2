package inkspire.morphic.data.layout

import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo

/**
 * The write-command vocabulary for the HOME layout — one value per intended change to *where items sit and
 * which container holds what*. A UI gesture resolves to a list of these, and [LayoutRepository.apply] is the
 * single sink that persists them; nothing else mutates the layout stores.
 *
 * **Why it lives in `data:layout`, not `core:model`.** It is the repository's *command set*, not a persisted
 * shape — no row stores a `LayoutChange`. Keeping it beside the repository (rather than with the plain data
 * models) is the honest home for a write-verb vocabulary. `core:model` is the wrong layer for it.
 *
 * **Orientation-free by design.** A change says *what* to do; the caller scopes *which* orientation via
 * [LayoutRepository.apply]`(orientation, …)`, so the same command replays into either orientation's tables.
 *
 * **Thirteen ops, not nineteen.** Repeating the same four verbs once per item type is what makes nineteen; because
 * the model already unifies those types, the duplication collapses:
 * - **Move ×5 → 1.** `MoveApp/MoveFolder/MoveWidget/MoveWidgetContainer/MoveIconContainer` were an identical
 *   `(id, to, surface)` differing only by id type → one [Move], keyed by the unified [GridItem], with the zone
 *   carried as [HomeZone].
 * - **Add-to-icon-container ×2 → 1.** `AddAppToIconContainer` + `AddFolderToIconContainer` → one
 *   [AddToIconContainer], since [IconItem] already *is* "app or folder".
 * - **Removal is intent-split, not one `Remove(GridItem)`.** A naive single remove conflates distinct actions.
 *   Detaching from the grid ([RemoveFromGrid]) is a different store and meaning from pulling an app out of a
 *   folder ([RemoveFromFolder]) or a container ([RemoveFromIconContainer] / [RemoveFromWidgetContainer]) — each
 *   mirrors its `Add`/`Create` counterpart. And **uninstall is not here at all**: destroying the package is a
 *   system action in `data:apps`; the layout merely reacts to the resulting removal event and prunes.
 *
 * What did *not* collapse (genuinely distinct payloads, kept separate): the three `Create*` ops and the three
 * `Add*`/`Remove*From*` membership ops — different holders hold different child types.
 */
sealed interface LayoutChange {

    // ── Placement on the grid ────────────────────────────────────────────────────────────────────────────

    /**
     * Place-or-move [item] to cell [to] in [zone] (an upsert — this is also how an item is first *added* to the
     * grid). One command rather than a `Move*` per item type, unified by [GridItem].
     */
    data class Move(
        val item: GridItem,
        val to: GridPlacement,
        val zone: HomeZone = HomeZone.MAIN,
    ) : LayoutChange

    /**
     * Detaches [item] from its grid cell. For a [GridItem.Folder] / [GridItem.IconContainer] /
     * [GridItem.WidgetContainer] the now-unplaced container is destroyed and its membership rows cascade with it.
     * The referenced **app stays installed** — this is a layout detach, never an uninstall.
     *
     * **This drops records only; it unbinds nothing.** A [GridItem.Widget]'s definition row goes, but releasing the
     * `appWidgetId` is an `AppWidgetHost` call and therefore `data:widgets`' job — a caller does both (see
     * `HomeViewModel.removeWidget`).
     *
     * **A [GridItem.WidgetContainer] is the case that bites**, because the cascade looks complete and is not:
     * `widget_container_item` has no foreign key to the `widget` table, so every contained widget's definition row
     * and allocated id outlives the container with nothing left pointing at it — a leak the user can neither see
     * nor clear. Removing a widget container means removing each widget it holds first.
     */
    data class RemoveFromGrid(val item: GridItem) : LayoutChange

    /**
     * Records a newly bound [widget] **and** places it at [at] in [zone], as one command.
     *
     * **Why a widget cannot just be `Move`d onto the grid the way an app is.** An app is identified by a component
     * the app cache already knows, so a placement row is the whole of "it is on home". A widget is identified by
     * an `appWidgetId` the platform has just handed us, and nothing else in the launcher has ever heard of it —
     * its provider and label live in a row only this command writes. `Move` on a [GridItem.Widget] would leave a
     * placement pointing at a widget with no definition, which renders as nothing.
     *
     * The pair is one op rather than two for the reason `CreateFolder` is: the two rows are meaningless apart, and
     * a batch that could be interrupted between them would leave exactly the dangling state above.
     *
     * It is the mirror of [RemoveFromGrid] on a widget, which drops the definition and lets the placement cascade.
     * What neither of them does is talk to the `AppWidgetHost` — allocating and releasing the id belongs to
     * `data:widgets`, and this store only keeps records.
     */
    data class PlaceWidget(
        val widget: WidgetInfo,
        val at: GridPlacement,
        val zone: HomeZone = HomeZone.MAIN,
    ) : LayoutChange

    // ── Folders (hold apps only) ─────────────────────────────────────────────────────────────────────────

    /** Creates a folder of [apps] labeled [label] and places it at [at] in [zone]. */
    data class CreateFolder(
        val label: String,
        val apps: List<ComponentKey>,
        val at: GridPlacement,
        val zone: HomeZone = HomeZone.MAIN,
    ) : LayoutChange

    /** Adds [app] to the folder [folderId] (appended). */
    data class AddToFolder(val folderId: Long, val app: ComponentKey) : LayoutChange

    /** Removes [app] from the folder [folderId]; the app is not uninstalled. */
    data class RemoveFromFolder(val folderId: Long, val app: ComponentKey) : LayoutChange

    /** Reorders the folder [folderId] to exactly [apps] (a full new ordering). */
    data class ReorderFolder(val folderId: Long, val apps: List<ComponentKey>) : LayoutChange

    // ── Icon containers (hold apps or folders — [IconItem]) ──────────────────────────────────────────────

    /** Creates an icon container laid out by [arrangement] holding [items], placed at [at] in [zone]. */
    data class CreateIconContainer(
        val arrangement: IconArrangement,
        val items: List<IconItem>,
        val at: GridPlacement,
        val zone: HomeZone = HomeZone.MAIN,
    ) : LayoutChange

    /**
     * Adds [item] (an app or folder) to icon container [containerId]. Collapses L1's two typed adds.
     *
     * @property index where it lands. **Null appends**, which is what filling one from a picker means — the user
     *   named a set, not an arrangement. A drag names a *place*, so a drop resolves the slot under the finger and
     *   passes it, and whatever was at that slot and after it shifts along. That is an insert rather than
     *   [ReorderIconContainer]'s exchange, and the difference is which question the gesture answered: an item
     *   already inside has a place to trade, one arriving from outside has none.
     */
    data class AddToIconContainer(
        val containerId: Long,
        val item: IconItem,
        val index: Int? = null,
    ) : LayoutChange

    /** Removes [item] from icon container [containerId]. */
    data class RemoveFromIconContainer(val containerId: Long, val item: IconItem) : LayoutChange

    /**
     * Reorders icon container [containerId] to exactly [items] (a full new ordering), [ReorderFolder]'s shape one
     * holder over.
     *
     * **A whole ordering rather than a move, because the surface reorders by *exchange*.** A container's icons sit
     * at slots a shape decided — a ring, a honeycomb spiral — where "the index before this one" is not a place the
     * user can point at. What a finger over a ring means is *that position*, so a drop swaps the two icons and
     * leaves the rest where they are, and the honest way to say that is the resulting list. The folder's
     * MovingGap insert is the other answer and stays the right one there: a folder's grid has a reading order.
     */
    data class ReorderIconContainer(val containerId: Long, val items: List<IconItem>) : LayoutChange

    /**
     * Sets icon container [containerId]'s own icon and gap scaling, as percentages of what the surface would
     * otherwise give them.
     *
     * One op for both, because the screen offers them as one group and lowering the spacing is how the icons are
     * given room to grow — a user moving one is usually about to move the other, and two ops would write two
     * states the container was never in.
     */
    data class SetIconContainerScales(
        val containerId: Long,
        val iconScalePercent: Int,
        val spacingScalePercent: Int,
    ) : LayoutChange

    /** Changes how icon container [containerId] arranges its icons. */
    data class SetIconContainerArrangement(
        val containerId: Long,
        val arrangement: IconArrangement,
    ) : LayoutChange

    // ── Widget containers (hold bound widgets) ───────────────────────────────────────────────────────────

    /** Creates a widget container paging [widgetIds] along [axis], placed at [at] in [zone]. */
    data class CreateWidgetContainer(
        val axis: WidgetContainerAxis,
        val widgetIds: List<Int>,
        val at: GridPlacement,
        val zone: HomeZone = HomeZone.MAIN,
    ) : LayoutChange

    /**
     * Adds bound [widget] to widget container [containerId] — recording its definition as well as its membership.
     *
     * **It carries a [WidgetInfo] rather than a bare id for [PlaceWidget]'s reason, one holder over.** A membership
     * row joins *through* the definition, so a container given an id nothing else has heard of resolves to a
     * container holding nothing — the same unrenderable half-state a bare `Move` would produce on the grid. Taking
     * the whole thing makes that unrepresentable instead of relying on the caller to have written the definition
     * first. The upsert is idempotent, so the other caller — a widget dragged in off the grid, whose definition is
     * already there — pays nothing for it and has the value to hand anyway.
     */
    data class AddToWidgetContainer(val containerId: Long, val widget: WidgetInfo) : LayoutChange

    /**
     * Removes widget [appWidgetId] from container [containerId] — **membership only**. The widget keeps its
     * definition row and stays bound; where it goes next is the caller's to say, so this pairs with a [Move] to put
     * it back on the grid or an [AddToWidgetContainer] to re-home it, exactly as [RemoveFromFolder] does.
     */
    data class RemoveFromWidgetContainer(val containerId: Long, val appWidgetId: Int) : LayoutChange

    /**
     * Reorders widget container [containerId] to exactly [appWidgetIds] — the page order, which is the only order a
     * container that shows one widget at a time has.
     *
     * A whole ordering rather than a move, which is [ReorderIconContainer]'s shape for a different reason. There the
     * gesture *is* an exchange; here the gesture is a step, and the list is still the honest op — the store keeps a
     * `sortOrder` per row, so "move this one up" is a statement about two rows and the resulting order is what says
     * which two without the repository having to re-derive them.
     */
    data class ReorderWidgetContainer(val containerId: Long, val appWidgetIds: List<Int>) : LayoutChange

    /**
     * Sets widget container [containerId]'s three settings — how it is paged, and the two behaviors that page it
     * without being asked.
     *
     * **All three together, where [SetIconContainerArrangement] sets one.** That is not inconsistency: an icon
     * container has exactly one setting and this holder has three, edited on one screen that holds all of them. A
     * whole-value write is what stops two controls toggled in quick succession racing into a lost update, and the
     * caller always has every value to hand.
     */
    data class SetWidgetContainerOptions(
        val containerId: Long,
        val axis: WidgetContainerAxis,
        val autoRotate: Boolean,
        val resetOnReturn: Boolean,
    ) : LayoutChange
}
