package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.FolderPlacementEntity
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.HomeZone
import kotlinx.coroutines.flow.Flow

/** Reads and writes folder placements ([FolderPlacementEntity]) on the home surface, per arrangement. */
@Dao
interface FolderPlacementDao {

    @Query("SELECT * FROM folder_placement WHERE arrangement = :arrangement")
    fun observe(arrangement: ArrangementKey): Flow<List<FolderPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<FolderPlacementEntity>)

    @Query("DELETE FROM folder_placement WHERE folderId = :folderId")
    suspend fun deleteByFolderId(folderId: Long)

    @Query("DELETE FROM folder_placement WHERE folderId = :folderId AND arrangement = :arrangement")
    suspend fun delete(folderId: Long, arrangement: ArrangementKey)

    @Query("DELETE FROM folder_placement WHERE folderId = :folderId AND arrangement = :arrangement AND zone = :zone")
    suspend fun deleteZone(folderId: Long, arrangement: ArrangementKey, zone: HomeZone)

    @Query("DELETE FROM folder_placement WHERE arrangement = :arrangement")
    suspend fun clearArrangement(arrangement: ArrangementKey)
}
