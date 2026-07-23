package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation

/** Where a widget container ([containerId]) sits in a home [zone] per [orientation]; position embeds [GridPlacement]. */
@Entity(
    tableName = "widget_container_placement",
    primaryKeys = ["containerId", "orientation"],
    indices = [Index(value = ["orientation", "page"])],
    foreignKeys = [
        ForeignKey(
            entity = WidgetContainerEntity::class,
            parentColumns = ["id"],
            childColumns = ["containerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WidgetContainerPlacementEntity(
    val containerId: Long,
    val orientation: Orientation,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
