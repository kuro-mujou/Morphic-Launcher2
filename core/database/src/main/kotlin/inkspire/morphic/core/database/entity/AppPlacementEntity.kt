package inkspire.morphic.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import inkspire.morphic.core.model.ArrangementKey
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.GridPlacement
import inkspire.morphic.core.model.HomeZone

/** Where an app ([component]) sits in a home [zone] for a given [arrangement]; position embeds [GridPlacement]. */
@Entity(
    tableName = "app_placement",
    primaryKeys = ["component", "arrangement"],
    indices = [Index(value = ["arrangement", "page"])],
)
data class AppPlacementEntity(
    val component: ComponentKey,
    val arrangement: ArrangementKey,
    val zone: HomeZone = HomeZone.MAIN,
    @Embedded val placement: GridPlacement,
)
