package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.ArrangementKey

/** Room type converter between [ArrangementKey] and its enum name. */
class ArrangementKeyConverter {
    @TypeConverter
    fun toString(value: ArrangementKey): String = value.name

    @TypeConverter
    fun fromString(value: String): ArrangementKey = ArrangementKey.valueOf(value)
}
