package inkspire.morphic.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import inkspire.morphic.core.database.entity.FolderPlacementEntity
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation
import kotlinx.coroutines.flow.Flow

/** Reads and writes folder placements ([FolderPlacementEntity]) on the home surface, per orientation. */
@Dao
interface FolderPlacementDao {

    @Query("SELECT * FROM folder_placement WHERE orientation = :orientation")
    fun observe(orientation: Orientation): Flow<List<FolderPlacementEntity>>

    @Upsert
    suspend fun upsert(entities: List<FolderPlacementEntity>)

    @Query("DELETE FROM folder_placement WHERE folderId = :folderId")
    suspend fun deleteByFolderId(folderId: Long)

    @Query("DELETE FROM folder_placement WHERE folderId = :folderId AND orientation = :orientation")
    suspend fun delete(folderId: Long, orientation: Orientation)

    @Query("DELETE FROM folder_placement WHERE folderId = :folderId AND orientation = :orientation AND zone = :zone")
    suspend fun deleteZone(folderId: Long, orientation: Orientation, zone: HomeZone)

    @Query("DELETE FROM folder_placement WHERE orientation = :orientation")
    suspend fun clearOrientation(orientation: Orientation)
}
