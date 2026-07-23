package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.CategoryItemEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes app→category membership + order ([CategoryItemEntity]). */
@Dao
interface CategoryItemDao {

    @Query("SELECT * FROM category_item")
    fun observeAll(): Flow<List<CategoryItemEntity>>

    @Query("SELECT * FROM category_item WHERE categoryId = :categoryId ORDER BY sortOrder")
    fun observeCategory(categoryId: String): Flow<List<CategoryItemEntity>>

    @Query("SELECT MAX(sortOrder) FROM category_item WHERE categoryId = :categoryId")
    suspend fun maxSortOrder(categoryId: String): Int?

    @Upsert
    suspend fun upsert(items: List<CategoryItemEntity>)

    @Query("DELETE FROM category_item WHERE component = :component")
    suspend fun removeByComponent(component: ComponentKey)

    @Query("DELETE FROM category_item WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: String)
}
