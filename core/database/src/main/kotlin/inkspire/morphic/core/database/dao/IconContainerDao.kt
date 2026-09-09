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

    @Query("UPDATE icon_container SET arrangementSpec = :arrangement WHERE id = :id")
    suspend fun setArrangement(id: Long, arrangement: IconArrangement)

    /** Both scaling in one statement, because the settings screen writes them from one control group. */
    @Query("UPDATE icon_container SET iconScalePercent = :icon, spacingScalePercent = :spacing WHERE id = :id")
    suspend fun setScales(id: Long, icon: Int, spacing: Int)

    @Query("DELETE FROM icon_container WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Destroys every icon container **no posture places** — [FolderDao.deleteUnplaced]'s counterpart, and simpler
     * for lacking its second clause: a container is always a standalone grid item, so a placement row is the only
     * thing that can hold one.
     */
    @Query("DELETE FROM icon_container WHERE id NOT IN (SELECT containerId FROM icon_container_placement)")
    suspend fun deleteUnplaced()
}
