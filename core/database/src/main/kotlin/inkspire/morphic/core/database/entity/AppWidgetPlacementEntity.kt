package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/** Where a widget ([appWidgetId]) sits in a home [zone] for a given [arrangement]; position embeds [GridPlacement]. */
@Entity(
    tableName = "app_widget_placement",
    primaryKeys = ["appWidgetId", "arrangement"],
    indices = [Index(value = ["arrangement", "page"])],
    foreignKeys = [
        ForeignKey(
            entity = AppWidgetEntity::class,
            parentColumns = ["appWidgetId"],
            childColumns = ["appWidgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppWidgetPlacementEntity(
    val appWidgetId: Int,
    val arrangement: ArrangementKey,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
