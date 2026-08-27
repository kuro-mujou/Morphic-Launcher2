package inkspire.morphic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import inkspire.morphic.core.model.IconArrangement

/**
 * Room row for an icon container (a group of apps/folders laid out by [arrangementSpec], at its own icon and gap
 * scaling).
 *
 * **[arrangementSpec] is a serialized shape, and the column is named for that** rather than for the enum name it
 * used to hold: the stored value changed meaning, and re-reading it in place would decode every `GRID` as a
 * corrupt blob and silently reset it. See `IconArrangementConverter`.
 *
 * The two scales are **percentages against what the surface would otherwise give**, defaulted to 100 so a
 * container that has never been adjusted stores the same thing as one that has been reset. See [IconContainer].
 */
@Entity(tableName = "icon_container")
data class IconContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val arrangementSpec: IconArrangement = IconArrangement.Grid,
    val iconScalePercent: Int = 100,
    val spacingScalePercent: Int = 100,
)
