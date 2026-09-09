package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import inkspire.morphic.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes folders ([FolderEntity]). */
@Dao
interface FolderDao {

    @Query("SELECT * FROM folder")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folder WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folder WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Destroys every folder **no posture places and no icon container holds** — what a per-arrangement removal needs
     * behind it, since dropping one arrangement's placement row can leave a folder reachable from nowhere.
     *
     * The icon-container clause is what stops this reaping a folder that is perfectly well held: a folder filed into
     * a container has no `folder_placement` row at all, its position being the container's. Without that clause the
     * first sweep would destroy every one of them.
     */
    @Query(
        """
        DELETE FROM folder
        WHERE id NOT IN (SELECT folderId FROM folder_placement)
          AND id NOT IN (SELECT folderId FROM icon_container_item WHERE folderId IS NOT NULL)
        """,
    )
    suspend fun deleteUnplaced()
}
