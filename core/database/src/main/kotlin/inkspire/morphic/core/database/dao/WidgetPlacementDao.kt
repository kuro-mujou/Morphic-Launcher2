package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes where the launcher's own widgets sit ([WidgetPlacementEntity]), per arrangement. */
@Dao
interface WidgetPlacementDao {

    @Query("SELECT * FROM widget_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<WidgetPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<WidgetPlacementEntity>)

    @Query("DELETE FROM widget_placement WHERE widgetId = :widgetId AND arrangement = :arrangement")
    suspend fun delete(widgetId: Long, arrangement: ArrangementKey)

    @Query("DELETE FROM widget_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)
}
