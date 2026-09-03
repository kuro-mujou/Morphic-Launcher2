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

    @Query("SELECT * FROM category ORDER BY sortOrder")
    suspend fun getAll(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(categories: List<CategoryEntity>)

    /**
     * Renames one category, leaving its order and membership alone.
     *
     * A targeted `UPDATE` rather than an upsert of the whole row: a rename knows the name and nothing else, and
     * reading the row back only to write it again would let a concurrent reorder be undone by the copy it carried.
     */
    @Query("UPDATE category SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: String)
}
