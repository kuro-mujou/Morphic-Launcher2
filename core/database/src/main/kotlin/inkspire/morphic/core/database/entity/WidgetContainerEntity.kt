package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.WidgetContainerAxis

/** Room row for a widget container (a strip grouping widgets along an [axis]). */
@Entity(tableName = "widget_container")
data class WidgetContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val axis: WidgetContainerAxis = WidgetContainerAxis.HORIZONTAL,
)
