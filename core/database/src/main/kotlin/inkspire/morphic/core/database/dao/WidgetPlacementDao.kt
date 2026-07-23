package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetPlacementEntity
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget placements ([WidgetPlacementEntity]) on the home surface, per orientation. */
@Dao
interface WidgetPlacementDao {

    @Query("SELECT * FROM widget_placement WHERE orientation = :orientation")
    fun observe(orientation: Orientation): Flow<List<WidgetPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<WidgetPlacementEntity>)

    @Query("DELETE FROM widget_placement WHERE appWidgetId = :appWidgetId")
    suspend fun deleteByWidgetId(appWidgetId: Int)

    @Query("DELETE FROM widget_placement WHERE appWidgetId = :appWidgetId AND orientation = :orientation AND zone = :zone")
    suspend fun deleteZone(appWidgetId: Int, orientation: Orientation, zone: HomeZone)

    @Query("DELETE FROM widget_placement WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)
}
