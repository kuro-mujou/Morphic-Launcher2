package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.IconArrangement

/**
 * Room row for an icon container (a group of apps/folders laid out by [arrangement], at its own icon and gap
 * scaling).
 *
 * The two scales are **percentages against what the surface would otherwise give**, defaulted to 100 so a
 * container that has never been adjusted stores the same thing as one that has been reset. See [IconContainer].
 */
@Entity(tableName = "icon_container")
data class IconContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val arrangement: IconArrangement = IconArrangement.GRID,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
)
