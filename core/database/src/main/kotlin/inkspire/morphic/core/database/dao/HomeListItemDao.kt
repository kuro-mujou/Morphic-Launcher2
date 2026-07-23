package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
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
}
