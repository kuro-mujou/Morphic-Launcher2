package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.IconArrangement

/** Room row for an icon container (a group of apps/folders laid out by [arrangement]). */
@Entity(tableName = "icon_container")
data class IconContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val arrangement: IconArrangement = IconArrangement.GRID,
)
