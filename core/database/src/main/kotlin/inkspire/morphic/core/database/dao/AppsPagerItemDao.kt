package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppsPagerItemEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the APPS pager's saved order ([AppsPagerItemEntity]) — one list per [ArrangementKey], each
 * row an app or a folder at an explicit page + slot.
 *
 * Deletes come in two flavors because the row identifies its entry by *one of two* columns; a single
 * `deleteByItem` would have to take both and null one out, which reads worse than two honest queries. Deleting a
 * folder's *definition* needs neither: the entity's foreign key cascades its slot away.
 */
@Dao
interface AppsPagerItemDao {

    @Query("SELECT * FROM apps_pager_item WHERE arrangement = :arrangement ORDER BY page, positionInPage")
    fun observe(arrangement: ArrangementKey): Flow<List<AppsPagerItemEntity>>

    @Query("SELECT * FROM apps_pager_item WHERE arrangement = :arrangement ORDER BY page, positionInPage")
    suspend fun get(arrangement: ArrangementKey): List<AppsPagerItemEntity>

    @Upsert
    suspend fun upsert(items: List<AppsPagerItemEntity>)

    @Query("DELETE FROM apps_pager_item WHERE arrangement = :arrangement AND component = :component")
    suspend fun deleteApp(arrangement: ArrangementKey, component: ComponentKey)

    @Query("DELETE FROM apps_pager_item WHERE arrangement = :arrangement AND folderId = :folderId")
    suspend fun deleteFolder(arrangement: ArrangementKey, folderId: Long)

    @Query("DELETE FROM apps_pager_item WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)

    @Query("DELETE FROM apps_pager_item")
    suspend fun clear()
}
