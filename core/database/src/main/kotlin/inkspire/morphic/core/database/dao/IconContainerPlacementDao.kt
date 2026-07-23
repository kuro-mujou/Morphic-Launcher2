package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.IconContainerPlacementEntity
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/** Reads and writes icon-container placements ([IconContainerPlacementEntity]) on home, per orientation. */
@Dao
interface IconContainerPlacementDao {

    @Query("SELECT * FROM icon_container_placement WHERE orientation = :orientation")
    fun observe(orientation: Orientation): Flow<List<IconContainerPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<IconContainerPlacementEntity>)

    @Query("DELETE FROM icon_container_placement WHERE containerId = :containerId")
    suspend fun deleteByContainerId(containerId: Long)

    @Query("DELETE FROM icon_container_placement WHERE containerId = :containerId AND orientation = :orientation AND zone = :zone")
    suspend fun deleteZone(containerId: Long, orientation: Orientation, zone: HomeZone)

    @Query("DELETE FROM icon_container_placement WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)
}
