package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppWidgetPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget placements ([AppWidgetPlacementEntity]) on the home surface, per arrangement. */
@Dao
interface AppWidgetPlacementDao {

    @Query("SELECT * FROM app_widget_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<AppWidgetPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<AppWidgetPlacementEntity>)

    @Query("DELETE FROM app_widget_placement WHERE appWidgetId = :appWidgetId")
    suspend fun deleteByAppWidgetId(appWidgetId: Int)

    @Query("DELETE FROM app_widget_placement WHERE appWidgetId = :appWidgetId AND arrangement = :arrangement")
    suspend fun delete(appWidgetId: Int, arrangement: ArrangementKey)

    @Query("DELETE FROM app_widget_placement WHERE appWidgetId = :appWidgetId AND arrangement = :arrangement AND zone = :zone")
    suspend fun deleteZone(appWidgetId: Int, arrangement: ArrangementKey, zone: HomeZone)

    @Query("DELETE FROM app_widget_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)
}
