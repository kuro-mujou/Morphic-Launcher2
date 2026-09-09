package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget placements ([WidgetPlacementEntity]) on the home surface, per arrangement. */
@Dao
interface WidgetPlacementDao {

    @Query("SELECT * FROM widget_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<WidgetPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<WidgetPlacementEntity>)

    @Query("DELETE FROM widget_placement WHERE appWidgetId = :appWidgetId")
    suspend fun deleteByWidgetId(appWidgetId: Int)

    @Query("DELETE FROM widget_placement WHERE appWidgetId = :appWidgetId AND arrangement = :arrangement AND zone = :zone")
    suspend fun deleteZone(appWidgetId: Int, arrangement: ArrangementKey, zone: HomeZone)

    @Query("DELETE FROM widget_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)
}
