package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppsPagerItemEntity
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/** Reads and writes the APPS-pager arrangement ([AppsPagerItemEntity]) — one list per orientation. */
@Dao
interface AppsPagerItemDao {

    @Query("SELECT * FROM apps_pager_item WHERE orientation = :orientation ORDER BY page, positionInPage")
    fun observe(orientation: Orientation): Flow<List<AppsPagerItemEntity>>

    @Query("SELECT * FROM apps_pager_item WHERE orientation = :orientation AND page = :page ORDER BY positionInPage")
    fun observePage(orientation: Orientation, page: Int): Flow<List<AppsPagerItemEntity>>

    @Upsert
    suspend fun upsert(items: List<AppsPagerItemEntity>)

    @Query("DELETE FROM apps_pager_item WHERE component = :component")
    suspend fun deleteByComponent(component: ComponentKey)

    @Query("DELETE FROM apps_pager_item WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)

    @Query("DELETE FROM apps_pager_item")
    suspend fun clear()
}
