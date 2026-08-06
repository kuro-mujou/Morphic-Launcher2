package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.HomeListItemEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes the home vertical-list arrangement ([HomeListItemEntity]) — apps only, one shared list. */
@Dao
interface HomeListItemDao {

    @Query("SELECT * FROM home_list_item ORDER BY sortOrder")
    fun observe(): Flow<List<HomeListItemEntity>>

    @Upsert
    suspend fun upsert(items: List<HomeListItemEntity>)

    @Query("DELETE FROM home_list_item WHERE component = :component")
    suspend fun deleteByComponent(component: ComponentKey)

    @Query("DELETE FROM home_list_item")
    suspend fun clear()

    /**
     * Replaces the whole list, densely renumbered by the caller — **in one transaction**, which is the point.
     *
     * A reorder changes almost every row's ordinal, so the store rewrites rather than diffing. Done as two calls the
     * clear is *observable*: [observe] re-runs on the invalidation and emits an empty list, so the surface blanks for
     * a frame and the dragged row appears to snap back to where it started before the new order arrives. One
     * transaction means one invalidation, and the flow only ever sees a complete list.
     */
    @Transaction
    suspend fun replaceAll(items: List<HomeListItemEntity>) {
        clear()
        upsert(items)
    }
}
