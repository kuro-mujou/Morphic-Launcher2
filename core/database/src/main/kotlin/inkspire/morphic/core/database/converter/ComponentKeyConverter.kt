package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.ComponentKey

/** Room type converter between [ComponentKey] and its flattened string form. */
class ComponentKeyConverter {
    @TypeConverter
    fun toString(value: ComponentKey): String = value.flatten()

    @TypeConverter
    fun fromString(value: String): ComponentKey =
        ComponentKey.parse(value) ?: error("Invalid ComponentKey in database: $value")
}
