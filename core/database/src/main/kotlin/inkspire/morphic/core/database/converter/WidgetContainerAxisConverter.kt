package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.WidgetContainerAxis

/** Room type converter between [WidgetContainerAxis] and its enum name. */
class WidgetContainerAxisConverter {
    @TypeConverter
    fun toString(value: WidgetContainerAxis): String = value.name

    @TypeConverter
    fun fromString(value: String): WidgetContainerAxis = WidgetContainerAxis.valueOf(value)
}
