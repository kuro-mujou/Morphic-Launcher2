package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone
import inkspire.morphic.core.model.Orientation

/** Where an app ([component]) sits in a home [zone] for a given [orientation]; position embeds [GridPlacement]. */
@Entity(
    tableName = "app_placement",
    primaryKeys = ["component", "orientation"],
    indices = [Index(value = ["orientation", "page"])],
)
data class AppPlacementEntity(
    val component: ComponentKey,
    val orientation: Orientation,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
