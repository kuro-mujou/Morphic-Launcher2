package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.IconOverrideEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes per-app icon overrides ([IconOverrideEntity]). */
@Dao
interface IconOverrideDao {

    @Query("SELECT * FROM icon_override")
    fun observeAll(): Flow<List<IconOverrideEntity>>

    @Query("SELECT * FROM icon_override WHERE component = :component")
    suspend fun get(component: ComponentKey): IconOverrideEntity?

    @Upsert
    suspend fun upsert(entity: IconOverrideEntity)

    @Query("DELETE FROM icon_override WHERE component = :component")
    suspend fun delete(component: ComponentKey)
}
