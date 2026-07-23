package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.IconArrangement

/** Room type converter between [IconArrangement] and its enum name. */
class IconArrangementConverter {
    @TypeConverter
    fun toString(value: IconArrangement): String = value.name

    @TypeConverter
    fun fromString(value: String): IconArrangement = IconArrangement.valueOf(value)
}
