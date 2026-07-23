package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.AppInfoEntity
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/** Reads and writes cached app metadata ([AppInfoEntity]). */
@Dao
interface AppInfoDao {

    @Query("SELECT * FROM app_info")
    fun observeAll(): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM app_info WHERE component = :component")
    suspend fun get(component: ComponentKey): AppInfoEntity?

    @Upsert
    suspend fun upsert(entities: List<AppInfoEntity>)

    @Query("DELETE FROM app_info WHERE component IN (:components)")
    suspend fun delete(components: List<ComponentKey>)

    @Query("DELETE FROM app_info WHERE substr(component, 1, instr(component, '/') - 1) = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM app_info")
    suspend fun clear()
}
