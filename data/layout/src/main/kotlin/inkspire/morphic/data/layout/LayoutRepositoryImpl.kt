package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppPlacementDao
import inkspire.morphic.core.database.dao.FolderPlacementDao
import inkspire.morphic.core.database.dao.IconContainerPlacementDao
import inkspire.morphic.core.database.dao.WidgetContainerPlacementDao
import inkspire.morphic.core.database.dao.WidgetPlacementDao
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.layout.mapper.toEntity
import inkspire.morphic.data.layout.mapper.toEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Room-backed [LayoutRepository].
 *
 * [placements] unions all five `*_placement` tables into the one map, and [apply] routes each [LayoutChange] to
 * the matching store. **Placement (Move) is wired for every [GridItem] kind**; the container/folder/widget
 * *definitions* (their flows and the Create/Add/Remove-membership ops) are still stubbed and land in the next
 * part, along with the destroy semantics of [LayoutChange.RemoveFromGrid] for the non-app kinds.
 *
 * Writes hop to [AppDispatchers.io]; the DAOs' `Flow`s stay on Room's own executor.
 */
internal class LayoutRepositoryImpl(
    private val appPlacementDao: AppPlacementDao,
    private val folderPlacementDao: FolderPlacementDao,
    private val widgetPlacementDao: WidgetPlacementDao,
    private val iconContainerPlacementDao: IconContainerPlacementDao,
    private val widgetContainerPlacementDao: WidgetContainerPlacementDao,
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

    // ── Next part: real definition flows once the folder / container / widget stores are wired ──
    override fun folders(): Flow<List<Folder>> = flowOf(emptyList())
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

            // Remove from home = drop membership across *all* orientations (position is per-orientation, being
            // on home is not). An app just detaches (stays installed).
            is LayoutChange.RemoveFromGrid -> when (val item = change.item) {
                is GridItem.App -> appPlacementDao.deleteByComponent(item.component)
                // Folder / Widget / *Container removal must *destroy the definition* (which cascades the
                // placement) — deleting only the placement would orphan the def. Deferred to the next part,
                // where the definition DAOs are wired.
                else -> Unit
            }

            else -> Unit // folder / container / widget membership + create ops — next part
        }
    }
}
