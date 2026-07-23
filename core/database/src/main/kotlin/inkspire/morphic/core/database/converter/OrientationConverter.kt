package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.Orientation

/** Room type converter between [Orientation] and its enum name. */
class OrientationConverter {
    @TypeConverter
    fun toString(value: Orientation): String = value.name

    @TypeConverter
    fun fromString(value: String): Orientation = Orientation.valueOf(value)
}
