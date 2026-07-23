package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation

/** Where a folder ([folderId]) sits in a home [zone] for a given [orientation]; position embeds [GridPlacement]. */
@Entity(
    tableName = "folder_placement",
    primaryKeys = ["folderId", "orientation"],
    indices = [Index(value = ["orientation", "page"])],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FolderPlacementEntity(
    val folderId: Long,
    val orientation: Orientation,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
