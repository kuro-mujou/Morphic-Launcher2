package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppWidgetEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes bound widgets ([AppWidgetEntity]). */
@Dao
interface AppWidgetDao {

    @Query("SELECT * FROM app_widget")
    fun observeAll(): Flow<List<AppWidgetEntity>>

    @Upsert
    suspend fun upsert(widget: AppWidgetEntity)

    @Query("DELETE FROM app_widget WHERE appWidgetId = :appWidgetId")
    suspend fun delete(appWidgetId: Int)
}
