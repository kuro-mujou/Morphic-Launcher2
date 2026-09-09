package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow

/** Reads and writes app placements ([AppPlacementEntity]) on the home surface, per arrangement. */
@Dao
interface AppPlacementDao {

    @Query("SELECT * FROM app_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<AppPlacementEntity>>

    @Query("SELECT * FROM app_placement WHERE arrangement = :arrangement AND page = :page")
    fun observePage(arrangement: ArrangementKey, page: Int): Flow<List<AppPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<AppPlacementEntity>)

    @Query("DELETE FROM app_placement WHERE component = :component")
    suspend fun deleteByComponent(component: ComponentKey)

    @Query("DELETE FROM app_placement WHERE component = :component AND arrangement = :arrangement")
    suspend fun delete(component: ComponentKey, arrangement: ArrangementKey)

    @Query("DELETE FROM app_placement WHERE component = :component AND arrangement = :arrangement AND zone = :zone")
    suspend fun deleteZone(component: ComponentKey, arrangement: ArrangementKey, zone: HomeZone)

    @Query("DELETE FROM app_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)

    @Query("DELETE FROM app_placement")
    suspend fun clear()
}
