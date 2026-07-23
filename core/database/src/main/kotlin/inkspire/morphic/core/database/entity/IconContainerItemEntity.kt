package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ComponentKey

/**
 * Membership row in icon container [containerId]: exactly one of an app [component] or a nested [folderId],
 * at [sortOrder]. An app/folder belongs to at most one icon container (enforced by the unique indices — SQLite
 * ignores NULLs, so the two nullable columns coexist); deleting the parent container or the nested folder
 * cascades this row away.
 */
@Entity(
    tableName = "icon_container_item",
    indices = [
        Index(value = ["containerId"]),
        Index(value = ["component"], unique = true),
        Index(value = ["folderId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = IconContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["containerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class IconContainerItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val containerId: Long,
    val component: ComponentKey? = null,
    val folderId: Long? = null,
    val sortOrder: Int,
) {
    init {
        require((component == null) != (folderId == null)) {
            "icon_container_item needs exactly one of component/folderId"
        }
    }
}
