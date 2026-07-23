package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation

/** Where a widget ([appWidgetId]) sits in a home [zone] for a given [orientation]; position embeds [GridPlacement]. */
@Entity(
    tableName = "widget_placement",
    primaryKeys = ["appWidgetId", "orientation"],
    indices = [Index(value = ["orientation", "page"])],
    foreignKeys = [
        ForeignKey(
            entity = WidgetEntity::class,
            parentColumns = ["appWidgetId"],
            childColumns = ["appWidgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WidgetPlacementEntity(
    val appWidgetId: Int,
    val orientation: Orientation,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
