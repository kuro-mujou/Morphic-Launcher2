package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/** Where a widget container ([containerId]) sits in a home [zone] per [arrangement]; position embeds [GridPlacement]. */
@Entity(
    tableName = "widget_container_placement",
    primaryKeys = ["containerId", "arrangement"],
    indices = [Index(value = ["arrangement", "page"])],
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
    val arrangement: ArrangementKey,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
