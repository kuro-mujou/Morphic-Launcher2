package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.WidgetContainerItemEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget-container membership ([WidgetContainerItemEntity]), ordered by `sortOrder`. */
@Dao
interface WidgetContainerItemDao {

    @Query("SELECT * FROM widget_container_item")
    fun observeAll(): Flow<List<WidgetContainerItemEntity>>

    @Query("SELECT * FROM widget_container_item WHERE containerId = :containerId ORDER BY sortOrder")
    suspend fun getByContainer(containerId: Long): List<WidgetContainerItemEntity>

    @Query("SELECT MAX(sortOrder) FROM widget_container_item WHERE containerId = :containerId")
    suspend fun maxSortOrder(containerId: Long): Int?

    @Upsert
    suspend fun upsert(items: List<WidgetContainerItemEntity>)

    @Query("DELETE FROM widget_container_item WHERE appWidgetId = :appWidgetId")
    suspend fun removeByWidget(appWidgetId: Int)

    @Query("DELETE FROM widget_container_item WHERE containerId = :containerId")
    suspend fun clearContainer(containerId: Long)
}
