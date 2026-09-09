package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/** Where a folder ([folderId]) sits in a home [zone] for a given [arrangement]; position embeds [GridPlacement]. */
@Entity(
    tableName = "folder_placement",
    primaryKeys = ["folderId", "arrangement"],
    indices = [Index(value = ["arrangement", "page"])],
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
    val arrangement: ArrangementKey,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
