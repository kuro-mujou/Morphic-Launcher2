package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Membership row: widget [appWidgetId] belongs to widget container [containerId] at [sortOrder]. */
@Entity(
    tableName = "widget_container_item",
    primaryKeys = ["containerId", "appWidgetId"],
    indices = [
        Index(value = ["containerId"]),
        Index(value = ["appWidgetId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = WidgetContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["containerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WidgetContainerItemEntity(
    val containerId: Long,
    val appWidgetId: Int,
    val sortOrder: Int,
)
