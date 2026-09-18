package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import inkspire.morphic.core.database.entity.WidgetEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes the launcher's own widgets ([WidgetEntity]). */
@Dao
interface WidgetDao {

    @Query("SELECT * FROM widget")
    fun observeAll(): Flow<List<WidgetEntity>>

    /** @return the new widget's id. */
    @Insert
    suspend fun insert(widget: WidgetEntity): Long

    /**
     * Deletes every widget no arrangement places — the other half of removing one from a single posture, as
     * `FolderDao.deleteUnplaced` is for a folder.
     */
    @Query("DELETE FROM widget WHERE id NOT IN (SELECT widgetId FROM widget_placement)")
    suspend fun deleteUnplaced()
}
