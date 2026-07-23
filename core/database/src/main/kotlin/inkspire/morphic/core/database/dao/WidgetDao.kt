package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes bound widgets ([WidgetEntity]). */
@Dao
interface WidgetDao {

    @Query("SELECT * FROM widget")
    fun observeAll(): Flow<List<WidgetEntity>>

    @Upsert
    suspend fun upsert(widget: WidgetEntity)

    @Query("DELETE FROM widget WHERE appWidgetId = :appWidgetId")
    suspend fun delete(appWidgetId: Int)
}
