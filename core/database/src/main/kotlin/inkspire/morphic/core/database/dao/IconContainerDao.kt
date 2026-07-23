package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import inkspire.morphic.core.database.entity.IconContainerEntity
import inkspire.morphic.core.model.IconArrangement
import kotlinx.coroutines.flow.Flow

/** Reads and writes icon containers ([IconContainerEntity]). */
@Dao
interface IconContainerDao {

    @Query("SELECT * FROM icon_container")
    fun observeAll(): Flow<List<IconContainerEntity>>

    @Query("SELECT * FROM icon_container WHERE id = :id")
    suspend fun get(id: Long): IconContainerEntity?

    @Insert
    suspend fun insert(container: IconContainerEntity): Long

    @Update
    suspend fun update(container: IconContainerEntity)

    @Query("UPDATE icon_container SET arrangement = :arrangement WHERE id = :id")
    suspend fun setArrangement(id: Long, arrangement: IconArrangement)

    @Query("DELETE FROM icon_container WHERE id = :id")
    suspend fun delete(id: Long)
}
