package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes folder membership ([FolderItemEntity]), ordered by `sortOrder`. */
@Dao
interface FolderItemDao {

    @Query("SELECT * FROM folder_item")
    fun observeAll(): Flow<List<FolderItemEntity>>

    @Query("SELECT * FROM folder_item WHERE folderId = :folderId ORDER BY sortOrder")
    suspend fun getByFolder(folderId: Long): List<FolderItemEntity>

    @Query("SELECT MAX(sortOrder) FROM folder_item WHERE folderId = :folderId")
    suspend fun maxSortOrder(folderId: Long): Int?

    @Upsert
    suspend fun upsert(items: List<FolderItemEntity>)

    @Query("DELETE FROM folder_item WHERE folderId = :folderId AND component = :component")
    suspend fun remove(folderId: Long, component: ComponentKey)

    @Query("DELETE FROM folder_item WHERE component = :component")
    suspend fun removeByComponent(component: ComponentKey)

    @Query("DELETE FROM folder_item WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)
}
