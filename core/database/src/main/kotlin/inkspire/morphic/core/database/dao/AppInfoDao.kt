package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Replaces the whole cache with what the platform currently reports — **in one transaction**, which is the
     * point, and the same reason `HomeListItemDao.replaceAll` is one.
     *
     * The cache is a mirror, not a log: an app that has been uninstalled has to *leave*, and a diff would mean
     * reading every row back to work out which. Done as two calls the [clear] is *observable* — [observeAll]
     * re-runs on that invalidation and emits an empty list — so every surface drawn from this would blank for a
     * frame on every refresh, which on a launcher means the home screen and the drawer both flashing empty.
     */
    @Transaction
    suspend fun replaceAll(entities: List<AppInfoEntity>) {
        clear()
        upsert(entities)
    }
}
