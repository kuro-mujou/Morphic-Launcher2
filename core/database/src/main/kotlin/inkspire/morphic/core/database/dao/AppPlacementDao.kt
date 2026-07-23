package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppPlacementEntity
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/** Reads and writes app placements ([AppPlacementEntity]) on the home surface, per orientation. */
@Dao
interface AppPlacementDao {

    @Query("SELECT * FROM app_placement WHERE orientation = :orientation")
    fun observe(orientation: Orientation): Flow<List<AppPlacementEntity>>

    @Query("SELECT * FROM app_placement WHERE orientation = :orientation AND page = :page")
    fun observePage(orientation: Orientation, page: Int): Flow<List<AppPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<AppPlacementEntity>)

    @Query("DELETE FROM app_placement WHERE component = :component")
    suspend fun deleteByComponent(component: ComponentKey)

    @Query("DELETE FROM app_placement WHERE component = :component AND orientation = :orientation")
    suspend fun delete(component: ComponentKey, orientation: Orientation)

    @Query("DELETE FROM app_placement WHERE component = :component AND orientation = :orientation AND zone = :zone")
    suspend fun deleteZone(component: ComponentKey, orientation: Orientation, zone: HomeZone)

    @Query("DELETE FROM app_placement WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)

    @Query("DELETE FROM app_placement")
    suspend fun clear()
}
