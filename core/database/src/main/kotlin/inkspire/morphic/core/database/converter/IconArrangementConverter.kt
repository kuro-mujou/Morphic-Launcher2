package inkspire.morphic.core.database.converter

import androidx.room.TypeConverter
import inkspire.morphic.core.model.IconArrangement
import kotlinx.serialization.json.Json

/**
 * Room type converter between an [IconArrangement] and the JSON in `icon_container.arrangementSpec`.
 *
 * **A blob rather than a name, because the value is a shape plus that shape's parameters** — a name can only carry
 * a shape with no parameters, or one flat value per combination of them. The column it writes is named for the blob
 * and not for what the blob happens to say, so a shape growing a parameter is an additive change to the JSON and
 * nothing below this line moves.
 *
 * `ignoreUnknownKeys` is what makes that additive both ways: a build that has not heard of a newer parameter drops
 * it rather than throwing, exactly as the icon and settings blobs do.
 *
 * **An unreadable value falls back to a plain [IconArrangement.Grid] rather than failing the read**, which is
 * `IconAppearanceCodec`'s bargain one store over: the cost of a blob nobody can decode should be that one container
 * loses its shape — visible, and fixable from its settings — rather than every surface that draws it. The only way
 * to reach it is a value written by a build that knew a shape this one does not.
 */
class IconArrangementConverter {
    @TypeConverter
    fun toJson(value: IconArrangement): String = json.encodeToString(IconArrangement.serializer(), value)

    @TypeConverter
    fun fromJson(value: String): IconArrangement =
        runCatching { json.decodeFromString(IconArrangement.serializer(), value) }
            .getOrDefault(IconArrangement.Grid())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
