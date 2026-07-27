package inkspire.morphic.data.layout

import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppPlacementDao
import inkspire.morphic.core.model.Folder
import inkspire.morphic.core.model.GridItem
import inkspire.morphic.core.model.IconContainer
import inkspire.morphic.core.model.Orientation
import inkspire.morphic.core.model.WidgetContainer
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.layout.mapper.toEntity
import inkspire.morphic.data.layout.mapper.toEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [LayoutRepository].
 *
 * **This is the HOME app-placement slice** — the first end-to-end vertical: [placements] reads `app_placement`
 * and [apply] persists [LayoutChange.Move] / [LayoutChange.RemoveFromGrid] for [GridItem.App]. The remaining
 * grid-item kinds (folder / widget / the two container types) and their definition flows are stubbed to empty
 * and land in Part 4, when their placement + definition stores are wired; the [placements] flow will then
 * combine those tables into the same unified map.
 *
 * @param appPlacementDao the `app_placement` store.
 * @param dispatchers writes hop to [AppDispatchers.io]; the DAO's own `Flow`s stay on Room's executor.
 */
internal class LayoutRepositoryImpl(
    private val appPlacementDao: AppPlacementDao,
    private val dispatchers: AppDispatchers,
) : LayoutRepository {

    override fun placements(orientation: Orientation): Flow<Map<GridItem, PlacedItem>> =
        appPlacementDao.observe(orientation).map { entities -> entities.associate { it.toEntry() } }

    // ── Part 4: real definition flows once the folder / container / widget stores are wired ──
    override fun folders(): Flow<List<Folder>> = flowOf(emptyList())
    override fun iconContainers(): Flow<List<IconContainer>> = flowOf(emptyList())
    override fun widgetContainers(): Flow<List<WidgetContainer>> = flowOf(emptyList())
    override fun widgets(): Flow<List<WidgetInfo>> = flowOf(emptyList())

    override suspend fun apply(orientation: Orientation, changes: List<LayoutChange>) {
        withContext(dispatchers.io) {
            changes.forEach { applyChange(orientation, it) }
        }
    }

    /**
     * Applies one change. Only the [GridItem.App] cases of [LayoutChange.Move] / [LayoutChange.RemoveFromGrid]
     * are wired in this slice; every other item kind and op is a deliberate no-op until Part 4 (they can't
     * occur yet — no surface produces them). The `else` keeps the sink total so a future op fails loudly in
     * review, not silently, when we come to wire it.
     */
    private suspend fun applyChange(orientation: Orientation, change: LayoutChange) {
        when (change) {
            is LayoutChange.Move -> (change.item as? GridItem.App)?.let { app ->
                appPlacementDao.upsert(listOf(app.toEntity(orientation, change.zone, change.to)))
            }

            is LayoutChange.RemoveFromGrid -> (change.item as? GridItem.App)?.let { app ->
                appPlacementDao.delete(app.component, orientation)
            }

            else -> Unit // folder / container / widget ops — Part 4
        }
    }
}
