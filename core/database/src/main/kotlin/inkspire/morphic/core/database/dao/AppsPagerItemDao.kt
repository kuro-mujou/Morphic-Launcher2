package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppsPagerItemEntity
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the APPS-pager arrangement ([AppsPagerItemEntity]) — one list per orientation, each row an
 * app or a folder at an explicit page + slot.
 *
 * Deletes come in two flavors because the row identifies its entry by *one of two* columns; a single
 * `deleteByItem` would have to take both and null one out, which reads worse than two honest queries. Deleting a
 * folder's *definition* needs neither: the entity's foreign key cascades its slot away.
 */
@Dao
interface AppsPagerItemDao {

    @Query("SELECT * FROM apps_pager_item WHERE orientation = :orientation ORDER BY page, positionInPage")
    fun observe(orientation: Orientation): Flow<List<AppsPagerItemEntity>>

    @Query("SELECT * FROM apps_pager_item WHERE orientation = :orientation ORDER BY page, positionInPage")
    suspend fun get(orientation: Orientation): List<AppsPagerItemEntity>

    /**
     * Inserts new rows and updates existing ones. Matching is by the surrogate `id`, so a row being *moved* must
     * carry the id it was read with — an id of 0 always inserts, which for an entry already in the list would
     * duplicate it (or, thanks to the unique indices, fail outright rather than corrupt the list).
     */
    @Upsert
    suspend fun upsert(items: List<AppsPagerItemEntity>)

    @Query("DELETE FROM apps_pager_item WHERE orientation = :orientation AND component = :component")
    suspend fun deleteApp(orientation: Orientation, component: ComponentKey)

    @Query("DELETE FROM apps_pager_item WHERE orientation = :orientation AND folderId = :folderId")
    suspend fun deleteFolder(orientation: Orientation, folderId: Long)

    @Query("DELETE FROM apps_pager_item WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)

    @Query("DELETE FROM apps_pager_item")
    suspend fun clear()
}
