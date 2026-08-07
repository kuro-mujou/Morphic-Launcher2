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
            is LayoutChange.RemoveFromGrid -> when (val item = change.item) {
                is GridItem.App -> daos.appPlacement.deleteByComponent(item.component)
                is GridItem.Folder -> daos.folder.delete(item.folderId)
                is GridItem.IconContainer -> daos.iconContainer.delete(item.containerId)
                is GridItem.WidgetContainer -> daos.widgetContainer.delete(item.containerId)
                is GridItem.Widget -> daos.widget.delete(item.appWidgetId)
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
                daos.iconContainerItem.upsert(change.items.mapIndexed { i, item -> item.toRow(id, i) })
                daos.iconContainerPlacement.upsert(
                    listOf(GridItem.IconContainer(id).toEntity(orientation, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToIconContainer -> {
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
                daos.widgetContainerItem.upsert(
                    change.widgetIds.mapIndexed { i, w -> WidgetContainerItemEntity(id, w, i) },
                )
                daos.widgetContainerPlacement.upsert(
                    listOf(GridItem.WidgetContainer(id).toEntity(orientation, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToWidgetContainer -> {
                val next = (daos.widgetContainerItem.maxSortOrder(change.containerId) ?: -1) + 1
                daos.widgetContainerItem.upsert(listOf(WidgetContainerItemEntity(change.containerId, change.appWidgetId, next)))
            }

            is LayoutChange.RemoveFromWidgetContainer -> daos.widgetContainerItem.removeByWidget(change.appWidgetId)
        }
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
