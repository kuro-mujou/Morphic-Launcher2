package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.HomeZone

/** Room type converter between [HomeZone] and its enum name. */
class HomeZoneConverter {
    @TypeConverter
    fun toString(value: HomeZone): String = value.name

    @TypeConverter
    fun fromString(value: String): HomeZone = HomeZone.valueOf(value)
}
