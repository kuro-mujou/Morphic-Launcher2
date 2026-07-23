package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.ComponentKey

/** Membership row in icon container [containerId]: either an app [component] or a nested [folderId], at [sortOrder]. */
@Entity(
    tableName = "icon_container_item",
    indices = [Index(value = ["containerId"])],
    foreignKeys = [
        ForeignKey(
            entity = IconContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["containerId"],
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
)
