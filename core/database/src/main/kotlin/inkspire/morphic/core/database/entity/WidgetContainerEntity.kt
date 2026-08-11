package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.WidgetContainerAxis

/**
 * Room row for a widget container — the widgets it holds are `widget_container_item` rows; this is the container's
 * own settings.
 *
 * [axis] is the direction the user **swipes** to reach the next widget, not a direction they stack in; [autoRotate]
 * pages it on a timer; [resetOnReturn] sends it back to its first page when home is returned to. See
 * `WidgetContainer`, whose fields these mirror one for one.
 */
@Entity(tableName = "widget_container")
data class WidgetContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val axis: WidgetContainerAxis = WidgetContainerAxis.HORIZONTAL,
    val autoRotate: Boolean = false,
    val resetOnReturn: Boolean = false,
)
