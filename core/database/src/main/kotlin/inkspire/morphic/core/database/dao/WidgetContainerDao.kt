package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import inkspire.morphic.core.database.entity.WidgetContainerEntity
import inkspire.morphic.core.model.WidgetContainerAxis
import kotlinx.coroutines.flow.Flow

/** Reads and writes widget containers ([WidgetContainerEntity]). */
@Dao
interface WidgetContainerDao {

    @Query("SELECT * FROM widget_container")
    fun observeAll(): Flow<List<WidgetContainerEntity>>

    @Query("SELECT * FROM widget_container WHERE id = :id")
    suspend fun get(id: Long): WidgetContainerEntity?

    @Insert
    suspend fun insert(container: WidgetContainerEntity): Long

    @Update
    suspend fun update(container: WidgetContainerEntity)

    /**
     * Writes the container's three settings together, mirroring `IconContainerDao.setArrangement` for a holder that
     * has more than one.
     *
     * One statement rather than three, because they are edited on one screen that holds all three in state — so a
     * caller always has every value, and writing them together is what stops two controls changed in quick
     * succession racing into a lost update.
     */
    @Query(
        "UPDATE widget_container SET axis = :axis, autoRotate = :autoRotate, resetOnReturn = :resetOnReturn " +
            "WHERE id = :id",
    )
    suspend fun setOptions(id: Long, axis: WidgetContainerAxis, autoRotate: Boolean, resetOnReturn: Boolean)

    @Query("DELETE FROM widget_container WHERE id = :id")
    suspend fun delete(id: Long)
}
