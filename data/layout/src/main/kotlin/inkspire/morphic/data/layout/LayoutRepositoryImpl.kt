package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.FolderItemDao
import inkspire.morphic.core.database.entity.FolderEntity
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.database.entity.IconContainerEntity
import inkspire.morphic.core.database.entity.WidgetContainerEntity
import inkspire.morphic.core.database.entity.WidgetContainerItemEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.layout.mapper.foldersOf
import inkspire.morphic.data.layout.mapper.iconContainersOf
import inkspire.morphic.data.layout.mapper.toEntity
import inkspire.morphic.data.layout.mapper.toEntry
import inkspire.morphic.data.layout.mapper.toIconItem
import inkspire.morphic.data.layout.mapper.toRow
import inkspire.morphic.data.layout.mapper.toWidgetInfo
import inkspire.morphic.data.layout.mapper.widgetContainersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [LayoutRepository] — the complete HOME layout store: placement of every [GridItem] kind across
 * the five `*_placement` tables, and the folder / icon-container / widget-container / widget definitions. Every
 * [LayoutChange] is handled, so [apply]'s `when` is exhaustive over the vocabulary.
 *
 * Writes hop to [AppDispatchers.io]; the DAOs' `Flow`s stay on Room's own executor.
 */
internal class LayoutRepositoryImpl(
    private val daos: LayoutDaos,
    private val dispatchers: AppDispatchers,
) : LayoutRepository {

    /** One map from the five per-type placement tables: each observed list maps to entries, concatenated. */
    override fun placements(arrangement: ArrangementKey): Flow<Map<GridItem, PlacedItem>> =
        combine(
            daos.appPlacement.observe(arrangement),
            daos.folderPlacement.observe(arrangement),
            daos.widgetPlacement.observe(arrangement),
            daos.iconContainerPlacement.observe(arrangement),
            daos.widgetContainerPlacement.observe(arrangement),
        ) { apps, folders, widgets, iconContainers, widgetContainers ->
            (apps.map { it.toEntry() } +
                folders.map { it.toEntry() } +
                widgets.map { it.toEntry() } +
                iconContainers.map { it.toEntry() } +
                widgetContainers.map { it.toEntry() }).toMap()
        }

    override suspend fun replacePlacements(arrangement: ArrangementKey, placements: Map<GridItem, PlacedItem>) {
        withContext(dispatchers.io) {
            // Cleared per table rather than item by item through `RemoveFromGrid`, which would also collect a
            // definition whose last placement this clear is about to restore. These five `clearArrangement` queries
            // existed unused until this; they are what makes "replace" a replace rather than a merge over whatever
            // the target happened to be holding.
            daos.appPlacement.clearArrangement(arrangement)
            daos.folderPlacement.clearArrangement(arrangement)
            daos.widgetPlacement.clearArrangement(arrangement)
            daos.iconContainerPlacement.clearArrangement(arrangement)
            daos.widgetContainerPlacement.clearArrangement(arrangement)
            placements.forEach { (item, placed) ->
                applyChange(arrangement, LayoutChange.Move(item, placed.placement, placed.zone))
            }
            // After the re-fill, never between: the clear above strips this arrangement's rows, so sweeping mid-way
            // would collect a folder that is about to be placed again by the very loop that cleared it.
            dropUnplacedDefinitions()
        }
    }

    override fun folders(): Flow<List<Folder>> =
        combine(daos.folder.observeAll(), daos.folderItem.observeAll()) { folders, items -> foldersOf(folders, items) }

    override fun iconContainers(): Flow<List<IconContainer>> =
        combine(daos.iconContainer.observeAll(), daos.iconContainerItem.observeAll()) { containers, items ->
            iconContainersOf(containers, items)
        }

    override fun widgetContainers(): Flow<List<WidgetContainer>> =
        combine(daos.widgetContainer.observeAll(), daos.widgetContainerItem.observeAll()) { containers, items ->
            widgetContainersOf(containers, items)
        }

    override fun widgets(): Flow<List<WidgetInfo>> =
        daos.widget.observeAll().map { widgets -> widgets.map { it.toWidgetInfo() } }

    override suspend fun apply(arrangement: ArrangementKey, changes: List<LayoutChange>) {
        withContext(dispatchers.io) {
            changes.forEach { applyChange(arrangement, it) }
            dropUnplacedDefinitions()
        }
    }

    /**
     * Destroys every folder and icon container that no arrangement places any more.
     *
     * **The other half of a per-arrangement removal.** `RemoveFromGrid` drops one posture's placement row, so the
     * definition has to outlive it — and something then has to notice when the *last* posture lets go, or a folder
     * survives holding apps that are reachable from nowhere. That state is not hypothetical: it is what a landscape
     * merge followed by a re-derive from a portrait that never heard about the folder actually produced.
     *
     * **A sweep rather than a check at the removal site**, because removal is not the only way the last placement
     * goes: [replacePlacements] clears an arrangement wholesale, which is how the sharing re-derive writes, and it
     * would otherwise strip the final row with nobody counting.
     *
     * Cheap enough to run after every batch — two deletes over tables holding at most a few dozen rows — and running
     * it unconditionally is what stops it being a thing callers must remember.
     */
    private suspend fun dropUnplacedDefinitions() {
        daos.folder.deleteUnplaced()
        daos.iconContainer.deleteUnplaced()
    }

    /**
     * Drops [item]'s placement in [arrangement] and no other.
     *
     * Taking something off the screen you are looking at says nothing about the screen you are not. While the
     * launcher had one arrangement the distinction did not exist; once L1 gave it several, a global delete meant
     * removing an icon in landscape silently took it off portrait too.
     *
     * The definition outlives the placement and is collected by [dropUnplacedDefinitions] once no posture places it
     * — a sweep rather than a check here, because [replacePlacements] can strip the last one as well.
     *
     * **Widgets and widget containers are deliberately still global.** Destroying a widget is only half the job —
     * the `AppWidgetHost` unbind is `data:widgets`' half — and no caller can decide whether to unbind without being
     * told whether another posture still holds it. See [LayoutChange.RemoveFromGrid].
     */
    private suspend fun removeFromGrid(arrangement: ArrangementKey, item: GridItem) = when (item) {
        is GridItem.App -> daos.appPlacement.delete(item.component, arrangement)
        is GridItem.Folder -> daos.folderPlacement.delete(item.folderId, arrangement)
        is GridItem.IconContainer -> daos.iconContainerPlacement.delete(item.containerId, arrangement)
        is GridItem.WidgetContainer -> daos.widgetContainer.delete(item.containerId)
        is GridItem.Widget -> daos.widget.delete(item.appWidgetId)
    }

    /**
     * Mints a folder over [change]'s apps and places it.
     *
     * The folded apps leave **this posture's** grid, scoped for [removeFromGrid]'s reason: an independent landscape
     * is not entitled to rearrange portrait. Where the two are kept in step the write-back replaces the reference
     * wholesale a moment later, so it ends up holding the folder rather than the loose apps.
     */
    private suspend fun createFolder(arrangement: ArrangementKey, change: LayoutChange.CreateFolder) {
        val folderId = daos.folder.insert(FolderEntity(label = change.label))
        daos.folderItem.detachAll(change.apps)
        daos.folderItem.upsert(change.apps.toFolderItems(folderId))
        change.apps.forEach { daos.appPlacement.delete(it, arrangement) }
        daos.folderPlacement.upsert(
            listOf(GridItem.Folder(folderId).toEntity(arrangement, change.zone, change.at)),
        )
    }

    /** Appends [change]'s app to its folder, and off this posture's grid — [createFolder]'s scoping, for its reason. */
    private suspend fun addToFolder(arrangement: ArrangementKey, change: LayoutChange.AddToFolder) {
        daos.folderItem.detachAll(listOf(change.app))
        val next = (daos.folderItem.maxSortOrder(change.folderId) ?: -1) + 1
        daos.folderItem.upsert(listOf(FolderItemEntity(change.folderId, change.app, next)))
        daos.appPlacement.delete(change.app, arrangement)
    }

    /**
     * Makes [change]'s apps the whole of its folder's membership, in the order given.
     *
     * **It detaches placements as well**, which a reorder appears not to need: this is also how the drag path *adds*
     * a member, since it carries the whole reported order. Before removal was scoped, that detach came from the
     * `RemoveFromGrid` beside it, which now rightly clears only the posture the drop happened on. A pure reorder
     * names apps that are already members and hold no cell, so the delete does nothing there.
     */
    private suspend fun reorderFolder(arrangement: ArrangementKey, change: LayoutChange.ReorderFolder) {
        daos.folderItem.clearFolder(change.folderId)
        daos.folderItem.detachAll(change.apps)
        daos.folderItem.upsert(change.apps.toFolderItems(change.folderId))
        change.apps.forEach { daos.appPlacement.delete(it, arrangement) }
    }

    private suspend fun applyChange(arrangement: ArrangementKey, change: LayoutChange) {
        when (change) {
            // ── Placement: upsert into the matching per-type table for this arrangement ──
            is LayoutChange.Move -> when (val item = change.item) {
                is GridItem.App ->
                    daos.appPlacement.upsert(listOf(item.toEntity(arrangement, change.zone, change.to)))

                is GridItem.Folder ->
                    daos.folderPlacement.upsert(listOf(item.toEntity(arrangement, change.zone, change.to)))

                is GridItem.Widget ->
                    daos.widgetPlacement.upsert(listOf(item.toEntity(arrangement, change.zone, change.to)))

                is GridItem.IconContainer ->
                    daos.iconContainerPlacement.upsert(listOf(item.toEntity(arrangement, change.zone, change.to)))

                is GridItem.WidgetContainer ->
                    daos.widgetContainerPlacement.upsert(listOf(item.toEntity(arrangement, change.zone, change.to)))
            }

            is LayoutChange.RemoveFromGrid -> removeFromGrid(arrangement, change.item)

            // ── A newly bound widget: its definition, then where it sits ──
            // In that order, because the placement is the row a surface joins *through* the definition — writing
            // it first would emit a placement the UI resolves to nothing for as long as the two writes are apart.
            is LayoutChange.PlaceWidget -> {
                daos.widget.upsert(change.widget.toEntity())
                daos.widgetPlacement.upsert(
                    listOf(
                        GridItem.Widget(change.widget.appWidgetId)
                            .toEntity(arrangement, change.zone, change.at),
                    ),
                )
            }

            // ── Folders ──
            is LayoutChange.CreateFolder -> createFolder(arrangement, change)

            is LayoutChange.AddToFolder -> addToFolder(arrangement, change)

            is LayoutChange.RemoveFromFolder -> daos.folderItem.remove(change.folderId, change.app)

            is LayoutChange.ReorderFolder -> reorderFolder(arrangement, change)

            // ── Icon containers ──
            is LayoutChange.CreateIconContainer -> {
                val id = daos.iconContainer.insert(IconContainerEntity(arrangementSpec = change.arrangement))
                setIconContainerItems(arrangement, id, change.items)
                daos.iconContainerPlacement.upsert(
                    listOf(GridItem.IconContainer(id).toEntity(arrangement, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToIconContainer -> addToIconContainer(arrangement, change)

            is LayoutChange.RemoveFromIconContainer -> when (val item = change.item) {
                is IconItem.App -> daos.iconContainerItem.removeByComponent(item.component)
                is IconItem.Folder -> daos.iconContainerItem.removeByFolder(item.folderId)
            }

            is LayoutChange.ReorderIconContainer ->
                setIconContainerItems(arrangement, change.containerId, change.items)

            is LayoutChange.SetIconContainerScales ->
                daos.iconContainer.setScales(change.containerId, change.iconScalePercent, change.spacingScalePercent)

            is LayoutChange.SetIconContainerArrangement ->
                daos.iconContainer.setArrangement(change.containerId, change.arrangement)

            // ── Widget containers ──
            is LayoutChange.CreateWidgetContainer -> {
                val id = daos.widgetContainer.insert(WidgetContainerEntity(axis = change.axis))
                change.widgetIds.forEach { detachWidget(arrangement, it) }
                daos.widgetContainerItem.upsert(
                    change.widgetIds.mapIndexed { i, w -> WidgetContainerItemEntity(id, w, i) },
                )
                daos.widgetContainerPlacement.upsert(
                    listOf(GridItem.WidgetContainer(id).toEntity(arrangement, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToWidgetContainer -> {
                // Definition first, then membership — `PlaceWidget`'s order and its reason: the membership row is
                // what a surface joins *through* the definition, so writing it first would emit a container holding
                // a widget that resolves to nothing for as long as the two writes are apart.
                daos.widget.upsert(change.widget.toEntity())
                detachWidget(arrangement, change.widget.appWidgetId)
                val next = (daos.widgetContainerItem.maxSortOrder(change.containerId) ?: -1) + 1
                daos.widgetContainerItem.upsert(
                    listOf(WidgetContainerItemEntity(change.containerId, change.widget.appWidgetId, next)),
                )
            }

            is LayoutChange.RemoveFromWidgetContainer -> daos.widgetContainerItem.removeByWidget(change.appWidgetId)

            // **Upserted in place, where the icon container's twin clears the container first.** Both would give the
            // right rows, and the difference is what is observable in between: `apply` is not one transaction, so a
            // cleared container can be emitted empty — which for *widgets* means every hosted view torn down and
            // rebuilt to change two numbers. `widget_container_item`'s primary key is (containerId, appWidgetId), so
            // an upsert updates each row's `sortOrder` by key and nothing is ever briefly missing. Safe because a
            // reorder names the members it already has: no row is added or removed by it.
            is LayoutChange.ReorderWidgetContainer -> daos.widgetContainerItem.upsert(
                change.appWidgetIds.mapIndexed { i, id -> WidgetContainerItemEntity(change.containerId, id, i) },
            )

            is LayoutChange.SetWidgetContainerOptions -> daos.widgetContainer.setOptions(
                id = change.containerId,
                axis = change.axis,
                autoRotate = change.autoRotate,
                resetOnReturn = change.resetOnReturn,
            )
        }
    }

    /**
     * Takes [item] out of wherever it currently lives, so the container write that follows leaves it in exactly one
     * place. Every op that makes an app or folder a container *member* runs it first — creating a container around
     * it, or adding it to one — which is the icon-container twin of [detachAll], and for the same two reasons.
     *
     * **It keeps the store's own invariant, which the index alone cannot.** `icon_container_item` is uniquely
     * indexed by `component` and by `folderId` — an item lives in at most one icon container — but without this the
     * rule was enforced only by the index *rejecting* the write, and Room's `@Upsert` makes that rejection silent:
     * it inserts, catches the constraint failure, then updates **by primary key**, which here is the synthetic
     * autogenerated `id` — `0` on a new row, so it matches nothing. Moving an item from one container to another
     * did nothing at all, with no error and no row changed. Same mechanism as [detachAll], one table over, with the
     * synthetic key making it worse: `folder_item`'s composite key at least matches when the app is already in the
     * target folder.
     *
     * **And it enforces "an item lives in exactly one place *on this posture*."** [LayoutChange.AddToFolder] deletes
     * the folded app's grid placement for this reason; the container ops did not, so an app dragged into one rendered
     * **twice** — in the container and still in the cell it came from. Scoped to the arrangement being applied, since
     * filing something here is not a statement about a posture the user may be arranging separately; membership
     * itself stays global, which is the bound on how independent two postures can be (see [LayoutChange]).
     *
     * A folder needs no folder-membership detach, since folders never nest. Neither kind is taken out of the
     * `Surface.APPS` stores: that arrangement is independent of HOME's, so an app may sit in both.
     */
    /**
     * Makes [items] the whole of icon container [containerId]'s membership, in the order given — what both filling a
     * new container and reordering an existing one come down to.
     *
     * **The detach is the part that is silent when wrong**, and it is owed even though the container was just
     * cleared: `icon_container_item` is uniquely indexed on `component` and on `folderId`, so an item arriving from
     * a *different* container still conflicts on the index, and Room's `@Upsert` answers that conflict by updating
     * by primary key — the synthetic autogenerated `id`, which is 0 for a new row and matches nothing. The write is
     * dropped with no error. [LayoutChange.ReorderFolder] carries the same pairing for the same reason.
     *
     * Clearing first is harmless on a container that has just been inserted and is what makes this a *set* rather
     * than an append, which is what a reorder needs.
     */
    /**
     * [LayoutChange.AddToIconContainer]: appends, or inserts at the requested slot.
     *
     * The two are one write because they differ only in *where*. Appending reads the current maximum and adds one,
     * which is a single row; inserting has to renumber everything after the new item, so it reads the container and
     * rewrites the whole order through [setIconContainerItems] — the same read-modify-write a reorder does, so
     * there is one place `sortOrder` is authored rather than two that could disagree about density.
     *
     * **Both detach first**, and for the append that ordering is load-bearing: re-adding an item this container
     * already holds must append it to what *remains*, not leave a gap where its old row was. The insert path gets
     * the same for free, since [setIconContainerItems] detaches every item it writes.
     */
    private suspend fun addToIconContainer(arrangement: ArrangementKey, change: LayoutChange.AddToIconContainer) {
        val index = change.index
        if (index == null) {
            detachIconItem(arrangement, change.item)
            val next = (daos.iconContainerItem.maxSortOrder(change.containerId) ?: -1) + 1
            daos.iconContainerItem.upsert(listOf(change.item.toRow(change.containerId, next)))
            return
        }
        val current = daos.iconContainerItem.getByContainer(change.containerId).map { it.toIconItem() }
        // Minus itself first, so an item moved *within* its own container lands at the index the user aimed at
        // rather than one past it — the list it was dropped onto is the one it is no longer part of.
        val without = current.filterNot { it == change.item }
        setIconContainerItems(
            arrangement = arrangement,
            containerId = change.containerId,
            items = without.toMutableList().also { it.add(index.coerceIn(0, it.size), change.item) },
        )
    }

    private suspend fun setIconContainerItems(arrangement: ArrangementKey, containerId: Long, items: List<IconItem>) {
        daos.iconContainerItem.clearContainer(containerId)
        items.forEach { detachIconItem(arrangement, it) }
        daos.iconContainerItem.upsert(items.mapIndexed { i, item -> item.toRow(containerId, i) })
    }

    private suspend fun detachIconItem(arrangement: ArrangementKey, item: IconItem) = when (item) {
        is IconItem.App -> {
            daos.appPlacement.delete(item.component, arrangement)
            daos.folderItem.removeByComponent(item.component)
            daos.iconContainerItem.removeByComponent(item.component)
        }

        is IconItem.Folder -> {
            daos.folderPlacement.delete(item.folderId, arrangement)
            daos.iconContainerItem.removeByFolder(item.folderId)
        }
    }

    /**
     * [detachIconItem] for a widget — same two jobs, on `widget_container_item` and `widget_placement`.
     *
     * The silent-drop here comes from the key and the index disagreeing: the primary key is
     * `(containerId, appWidgetId)` while the unique index is on `appWidgetId` alone, so re-homing a widget conflicts
     * on the index and is then updated by a key naming the *new* container — which matches nothing.
     *
     * The widget's **definition row is deliberately untouched**: it is still bound and still ours, it has only moved.
     * Destroying a widget is [LayoutChange.RemoveFromGrid] plus the host's own unbind, never this.
     */
    private suspend fun detachWidget(arrangement: ArrangementKey, appWidgetId: Int) {
        daos.widgetContainerItem.removeByWidget(appWidgetId)
        daos.widgetPlacement.delete(appWidgetId, arrangement)
    }
}

/**
 * Takes [apps] out of whatever folder currently holds them, so the upsert that follows can put them in this one.
 * Every op that makes an app a *member* runs it first — creating a folder around it, adding it to one, or setting a
 * whole folder's order.
 *
 * **This is the store keeping its own invariant**, not a convenience for callers. `folder_item` is uniquely indexed
 * by `component` — an app lives in at most one folder — and without this the rule was enforced only by the index
 * *rejecting* the write. Room's `@Upsert` makes that rejection silent: it inserts, catches the constraint failure,
 * and then updates **by primary key** — which here is `(folderId, component)`, so it matches nothing when the
 * conflicting row belongs to a *different* folder. The write was dropped with no error and no row changed.
 *
 * What that looked like: an app already in one folder, dropped on another app, produced a new folder holding only the
 * target; dropped on another folder, it stayed where it was and the target gained nothing. Both read as the drop
 * being ignored. Making membership displace the old row also makes the *order* of a batch stop mattering — a
 * `RemoveFromFolder` for the app's previous folder may come before or after the op that re-homes it, and several
 * callers emit it after.
 */
private suspend fun FolderItemDao.detachAll(apps: List<ComponentKey>) = apps.forEach { removeByComponent(it) }

/** Apps as dense `folder_item` rows (index = sortOrder). */
private fun List<ComponentKey>.toFolderItems(folderId: Long): List<FolderItemEntity> =
    mapIndexed { index, component -> FolderItemEntity(folderId, component, index) }
