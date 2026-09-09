package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.IconContainerPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow

/** Reads and writes icon-container placements ([IconContainerPlacementEntity]) on home, per arrangement. */
@Dao
interface IconContainerPlacementDao {

    @Query("SELECT * FROM icon_container_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<IconContainerPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<IconContainerPlacementEntity>)

    @Query("DELETE FROM icon_container_placement WHERE containerId = :containerId")
    suspend fun deleteByContainerId(containerId: Long)

    @Query("DELETE FROM icon_container_placement WHERE containerId = :containerId AND arrangement = :arrangement")
    suspend fun delete(containerId: Long, arrangement: ArrangementKey)

    @Query(
        "DELETE FROM icon_container_placement " +
            "WHERE containerId = :containerId AND arrangement = :arrangement AND zone = :zone",
    )
    suspend fun deleteZone(containerId: Long, arrangement: ArrangementKey, zone: HomeZone)

    @Query("DELETE FROM icon_container_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)
}
