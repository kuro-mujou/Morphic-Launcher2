package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetContainerPlacementEntity
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget-container placements ([WidgetContainerPlacementEntity]) on home, per orientation. */
@Dao
interface WidgetContainerPlacementDao {

    @Query("SELECT * FROM widget_container_placement WHERE orientation = :orientation")
    fun observe(orientation: Orientation): Flow<List<WidgetContainerPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<WidgetContainerPlacementEntity>)

    @Query("DELETE FROM widget_container_placement WHERE containerId = :containerId")
    suspend fun deleteByContainerId(containerId: Long)

    @Query("DELETE FROM widget_container_placement WHERE containerId = :containerId AND orientation = :orientation AND zone = :zone")
    suspend fun deleteZone(containerId: Long, orientation: Orientation, zone: HomeZone)

    @Query("DELETE FROM widget_container_placement WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)
}
