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
}
