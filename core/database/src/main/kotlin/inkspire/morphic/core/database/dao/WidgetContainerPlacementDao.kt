package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetContainerPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget-container placements ([WidgetContainerPlacementEntity]) on home, per arrangement. */
@Dao
interface WidgetContainerPlacementDao {

    @Query("SELECT * FROM widget_container_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<WidgetContainerPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<WidgetContainerPlacementEntity>)

    @Query("DELETE FROM widget_container_placement WHERE containerId = :containerId")
    suspend fun deleteByContainerId(containerId: Long)

    @Query(
        "DELETE FROM widget_container_placement " +
            "WHERE containerId = :containerId AND arrangement = :arrangement AND zone = :zone",
    )
    suspend fun deleteZone(containerId: Long, arrangement: ArrangementKey, zone: HomeZone)

    @Query("DELETE FROM widget_container_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)
}
