package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppPlacementDao
import inkspire.morphic.core.database.dao.FolderDao
import inkspire.morphic.core.database.dao.FolderItemDao
import inkspire.morphic.core.database.dao.FolderPlacementDao
import inkspire.morphic.core.database.dao.IconContainerPlacementDao
import inkspire.morphic.core.database.dao.WidgetContainerPlacementDao
import inkspire.morphic.core.database.dao.WidgetPlacementDao
import inkspire.morphic.core.database.entity.FolderEntity
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.layout.mapper.foldersOf
import inkspire.morphic.data.layout.mapper.toEntity
import inkspire.morphic.data.layout.mapper.toEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Room-backed [LayoutRepository].
 *
 * Wired so far: **placement of every [GridItem] kind** (the five `*_placement` tables), and the **folder**
 * definition end-to-end (its flow, create/add/remove/reorder, and destroy). Icon containers, widget containers,
 * and widget metadata are the remaining verticals — their flows still stub empty and their ops no-op, following
 * the folder shape when built.
 *
 * Writes hop to [AppDispatchers.io]; the DAOs' `Flow`s stay on Room's own executor.
 */
internal class LayoutRepositoryImpl(
    private val appPlacementDao: AppPlacementDao,
    private val folderPlacementDao: FolderPlacementDao,
    private val widgetPlacementDao: WidgetPlacementDao,
    private val iconContainerPlacementDao: IconContainerPlacementDao,
    private val widgetContainerPlacementDao: WidgetContainerPlacementDao,
    private val folderDao: FolderDao,
    private val folderItemDao: FolderItemDao,
    private val dispatchers: AppDispatchers,
) : LayoutRepository {

    /** One map from the five per-type placement tables: each observed list maps to entries, concatenated. */
    override fun placements(orientation: Orientation): Flow<Map<GridItem, PlacedItem>> =
        combine(
            appPlacementDao.observe(orientation),
            folderPlacementDao.observe(orientation),
            widgetPlacementDao.observe(orientation),
            iconContainerPlacementDao.observe(orientation),
            widgetContainerPlacementDao.observe(orientation),
        ) { apps, folders, widgets, iconContainers, widgetContainers ->
            (apps.map { it.toEntry() } +
                folders.map { it.toEntry() } +
                widgets.map { it.toEntry() } +
                iconContainers.map { it.toEntry() } +
                widgetContainers.map { it.toEntry() }).toMap()
        }

    override fun folders(): Flow<List<Folder>> =
        combine(folderDao.observeAll(), folderItemDao.observeAll()) { folders, items -> foldersOf(folders, items) }

    // ── Remaining verticals: real flows once the icon/widget container + widget stores are wired ──
    override fun iconContainers(): Flow<List<IconContainer>> = flowOf(emptyList())
    override fun widgetContainers(): Flow<List<WidgetContainer>> = flowOf(emptyList())
    override fun widgets(): Flow<List<WidgetInfo>> = flowOf(emptyList())

    override suspend fun apply(orientation: Orientation, changes: List<LayoutChange>) {
        withContext(dispatchers.io) {
            changes.forEach { applyChange(orientation, it) }
        }
    }

    private suspend fun applyChange(orientation: Orientation, change: LayoutChange) {
        when (change) {
            // Place-or-move: upsert into the matching per-type placement table for this orientation.
            is LayoutChange.Move -> when (val item = change.item) {
                is GridItem.App ->
                    appPlacementDao.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.Folder ->
                    folderPlacementDao.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.Widget ->
                    widgetPlacementDao.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.IconContainer ->
                    iconContainerPlacementDao.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
                is GridItem.WidgetContainer ->
                    widgetContainerPlacementDao.upsert(listOf(item.toEntity(orientation, change.zone, change.to)))
            }

            // Remove from home = drop membership across *all* orientations. An app just detaches (stays
            // installed); a folder is destroyed — deleting the row cascades its items + placement (FK).
            is LayoutChange.RemoveFromGrid -> when (val item = change.item) {
                is GridItem.App -> appPlacementDao.deleteByComponent(item.component)
                is GridItem.Folder -> folderDao.delete(item.folderId)
                else -> Unit // icon/widget container + widget destroy — later verticals
            }

            // ── Folders ──
            is LayoutChange.CreateFolder -> {
                val folderId = folderDao.insert(FolderEntity(label = change.label))
                folderItemDao.upsert(change.apps.toItems(folderId))
                folderPlacementDao.upsert(
                    listOf(GridItem.Folder(folderId).toEntity(orientation, change.zone, change.at)),
                )
            }

            is LayoutChange.AddToFolder -> {
                val next = (folderItemDao.maxSortOrder(change.folderId) ?: -1) + 1
                folderItemDao.upsert(listOf(FolderItemEntity(change.folderId, change.app, next)))
            }

            is LayoutChange.RemoveFromFolder -> folderItemDao.remove(change.folderId, change.app)

            is LayoutChange.ReorderFolder -> {
                folderItemDao.clearFolder(change.folderId)
                folderItemDao.upsert(change.apps.toItems(change.folderId))
            }

            else -> Unit // icon/widget container ops — later verticals
        }
    }
}

/** The apps of a folder as dense `folder_item` rows (index = sortOrder). */
private fun List<ComponentKey>.toItems(folderId: Long): List<FolderItemEntity> =
    mapIndexed { index, component -> FolderItemEntity(folderId, component, index) }
