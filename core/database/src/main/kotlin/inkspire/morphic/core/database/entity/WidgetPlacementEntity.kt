package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/** Where one of the launcher's own widgets ([widgetId]) sits in a home [zone] for a given [arrangement]. */
@Entity(
    tableName = "widget_placement",
    primaryKeys = ["widgetId", "arrangement"],
    indices = [Index(value = ["arrangement", "page"])],
    foreignKeys = [
        ForeignKey(
            entity = WidgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["widgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WidgetPlacementEntity(
    val widgetId: Long,
    val arrangement: ArrangementKey,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
