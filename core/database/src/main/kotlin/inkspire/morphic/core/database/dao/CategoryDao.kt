package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes category definitions ([CategoryEntity]), ordered by `sortOrder`. */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(categories: List<CategoryEntity>)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: String)
}
