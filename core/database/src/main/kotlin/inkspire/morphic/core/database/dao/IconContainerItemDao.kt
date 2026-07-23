package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.IconContainerItemEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes icon-container membership ([IconContainerItemEntity]), ordered by `sortOrder`. */
@Dao
interface IconContainerItemDao {

    @Query("SELECT * FROM icon_container_item")
    fun observeAll(): Flow<List<IconContainerItemEntity>>

    @Query("SELECT * FROM icon_container_item WHERE containerId = :containerId ORDER BY sortOrder")
    suspend fun getByContainer(containerId: Long): List<IconContainerItemEntity>

    @Query("SELECT MAX(sortOrder) FROM icon_container_item WHERE containerId = :containerId")
    suspend fun maxSortOrder(containerId: Long): Int?

    @Upsert
    suspend fun upsert(items: List<IconContainerItemEntity>)

    @Query("DELETE FROM icon_container_item WHERE component = :component")
    suspend fun removeByComponent(component: ComponentKey)

    @Query("DELETE FROM icon_container_item WHERE folderId = :folderId")
    suspend fun removeByFolder(folderId: Long)

    @Query("DELETE FROM icon_container_item WHERE containerId = :containerId")
    suspend fun clearContainer(containerId: Long)
}
