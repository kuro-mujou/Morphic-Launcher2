package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.FolderItemDao
import inkspire.morphic.core.database.entity.FolderEntity
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.database.entity.IconContainerEntity
import inkspire.morphic.core.database.entity.WidgetContainerEntity
import inkspire.morphic.core.database.entity.WidgetContainerItemEntity
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.layout.mapper.foldersOf
import inkspire.morphic.data.layout.mapper.iconContainersOf
import inkspire.morphic.data.layout.mapper.toEntity
import inkspire.morphic.data.layout.mapper.toEntry
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
    override fun placements(orientation: Orientation): Flow<Map<GridItem, PlacedItem>> =
        combine(
            daos.appPlacement.observe(orientation),
            daos.folderPlacement.observe(orientation),
            daos.widgetPlacement.observe(orientation),
            daos.iconContainerPlacement.observe(orientation),
            daos.widgetContainerPlacement.observe(orientation),
        ) { apps, folders, widgets, iconContainers, widgetContainers ->
            (apps.map { it.toEntry() } +
                folders.map { it.toEntry() } +
                widgets.map { it.toEntry() } +
                iconContainers.map { it.toEntry() } +
                widgetContainers.map { it.toEntry() }).toMap()
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

    override suspend fun apply(orientation: Orientation, changes: List<LayoutChange>) {
        withContext(dispatchers.io) {
            changes.forEach { applyChange(orientation, it) }
        }
    }

    private suspend fun applyChange(orientation: Orientation, change: LayoutChange) {
        when (change) {
            // ── Placement: upsert into the matching per-type table for this orientation ──
            is LayoutChange.Move -> when (val item = change.item) {
                is GridItem.App ->
                    daos.appPlacement.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.Folder ->
                    daos.folderPlacement.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.Widget ->
                    daos.widgetPlacement.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.IconContainer ->
                    daos.iconContainerPlacement.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.WidgetContainer ->
                    daos.widgetContainerPlacement.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
            }

            // ── Remove from home = drop membership across all orientations ──
            // An app detaches (stays installed); a folder/container/widget is destroyed — deleting the parent
            // row FK-cascades its items and placement. (The AppWidgetHost *unbind* of a destroyed widget is a
            // data:widgets system action; this only drops our records.)
            //
            // A **widget container** cascades its membership rows and nothing else: `widget_container_item` has no
            // foreign key to the `widget` table, so each contained widget's definition row — and the allocated
            // appWidgetId behind it — survives its container with nothing left pointing at it. The caller must
            // remove each contained widget too; see RemoveFromGrid's KDoc.
            is LayoutChange.RemoveFromGrid -> when (val item = change.item) {
                is GridItem.App -> daos.appPlacement.deleteByComponent(item.component)
                is GridItem.Folder -> daos.folder.delete(item.folderId)
                is GridItem.IconContainer -> daos.iconContainer.delete(item.containerId)
                is GridItem.WidgetContainer -> daos.widgetContainer.delete(item.containerId)
                is GridItem.Widget -> daos.widget.delete(item.appWidgetId)
            }

            // ── A newly bound widget: its definition, then where it sits ──
            // In that order, because the placement is the row a surface joins *through* the definition — writing
            // it first would emit a placement the UI resolves to nothing for as long as the two writes are apart.
            is LayoutChange.PlaceWidget -> {
                daos.widget.upsert(change.widget.toEntity())
                daos.widgetPlacement.upsert(
                    listOf(
                        GridItem.Widget(change.widget.appWidgetId)
                            .toEntity(orientation, change.zone, change.at),
                    ),
                )
            }

            // ── Folders ──
            is LayoutChange.CreateFolder -> {
                val folderId = daos.folder.insert(FolderEntity(label = change.label))
                daos.folderItem.detachAll(change.apps)
                daos.folderItem.upsert(change.apps.toFolderItems(folderId))
                // The folded apps now live inside the folder, so they leave the grid (an app is in one place).
                change.apps.forEach { daos.appPlacement.deleteByComponent(it) }
                daos.folderPlacement.upsert(
                    listOf(GridItem.Folder(folderId).toEntity(orientation, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToFolder -> {
                daos.folderItem.detachAll(listOf(change.app))
                val next = (daos.folderItem.maxSortOrder(change.folderId) ?: -1) + 1
                daos.folderItem.upsert(listOf(FolderItemEntity(change.folderId, change.app, next)))
                // The app moved into the folder, so it leaves the grid (no-op if it came from another folder).
                daos.appPlacement.deleteByComponent(change.app)
            }

            is LayoutChange.RemoveFromFolder -> daos.folderItem.remove(change.folderId, change.app)

            is LayoutChange.ReorderFolder -> {
                daos.folderItem.clearFolder(change.folderId)
                daos.folderItem.detachAll(change.apps)
                daos.folderItem.upsert(change.apps.toFolderItems(change.folderId))
            }

            // ── Icon containers ──
            is LayoutChange.CreateIconContainer -> {
                val id = daos.iconContainer.insert(IconContainerEntity(arrangement = change.arrangement))
                change.items.forEach { detachIconItem(it) }
                daos.iconContainerItem.upsert(change.items.mapIndexed { i, item -> item.toRow(id, i) })
                daos.iconContainerPlacement.upsert(
                    listOf(GridItem.IconContainer(id).toEntity(orientation, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToIconContainer -> {
                // Detach *before* reading the next sort order, so re-adding an item already in this container
                // appends it to what remains rather than leaving a gap where its old row was.
                detachIconItem(change.item)
                val next = (daos.iconContainerItem.maxSortOrder(change.containerId) ?: -1) + 1
                daos.iconContainerItem.upsert(listOf(change.item.toRow(change.containerId, next)))
            }

            is LayoutChange.RemoveFromIconContainer -> when (val item = change.item) {
                is IconItem.App -> daos.iconContainerItem.removeByComponent(item.component)
                is IconItem.Folder -> daos.iconContainerItem.removeByFolder(item.folderId)
            }

            is LayoutChange.SetIconContainerArrangement ->
                daos.iconContainer.setArrangement(change.containerId, change.arrangement)

            // ── Widget containers ──
            is LayoutChange.CreateWidgetContainer -> {
                val id = daos.widgetContainer.insert(WidgetContainerEntity(axis = change.axis))
                change.widgetIds.forEach { detachWidget(it) }
                daos.widgetContainerItem.upsert(
                    change.widgetIds.mapIndexed { i, w -> WidgetContainerItemEntity(id, w, i) },
                )
                daos.widgetContainerPlacement.upsert(
                    listOf(GridItem.WidgetContainer(id).toEntity(orientation, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToWidgetContainer -> {
                // Definition first, then membership — `PlaceWidget`'s order and its reason: the membership row is
                // what a surface joins *through* the definition, so writing it first would emit a container holding
                // a widget that resolves to nothing for as long as the two writes are apart.
                daos.widget.upsert(change.widget.toEntity())
                detachWidget(change.widget.appWidgetId)
                val next = (daos.widgetContainerItem.maxSortOrder(change.containerId) ?: -1) + 1
                daos.widgetContainerItem.upsert(
                    listOf(WidgetContainerItemEntity(change.containerId, change.widget.appWidgetId, next)),
                )
            }

            is LayoutChange.RemoveFromWidgetContainer -> daos.widgetContainerItem.removeByWidget(change.appWidgetId)

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
     * **And it enforces "an item lives in exactly one place."** [LayoutChange.AddToFolder] deletes the folded app's
     * grid placement for this reason; the container ops did not, so an app dragged into one rendered **twice** — in
     * the container and still in the cell it came from.
     *
     * A folder needs no folder-membership detach, since folders never nest. Neither kind is taken out of the
     * `Surface.APPS` stores: that arrangement is independent of HOME's, so an app may sit in both.
     */
    private suspend fun detachIconItem(item: IconItem) = when (item) {
        is IconItem.App -> {
            daos.appPlacement.deleteByComponent(item.component)
            daos.folderItem.removeByComponent(item.component)
            daos.iconContainerItem.removeByComponent(item.component)
        }
        is IconItem.Folder -> {
            daos.folderPlacement.deleteByFolderId(item.folderId)
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
    private suspend fun detachWidget(appWidgetId: Int) {
        daos.widgetContainerItem.removeByWidget(appWidgetId)
        daos.widgetPlacement.deleteByWidgetId(appWidgetId)
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
