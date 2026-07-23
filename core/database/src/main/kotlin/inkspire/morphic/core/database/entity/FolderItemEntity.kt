package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.ComponentKey

/** Membership row: app [component] belongs to folder [folderId] at [sortOrder]. */
@Entity(
    tableName = "folder_item",
    primaryKeys = ["folderId", "component"],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["component"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FolderItemEntity(
    val folderId: Long,
    val component: ComponentKey,
    val sortOrder: Int,
)
