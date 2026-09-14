package inkspire.morphic.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * **A look**: a named arrangement of the launcher — HOME's pairing, what sits behind its edges, how the grids are sized,
 * the frost, the icon recipe — applied in one step, captured from a device, and kept as a file.
 *
 * **It is the storage format, not a mapping of it.** Each entry is one settings slice exactly as the store holds it,
 * keyed by the slice's name, so a look cannot fall behind the settings it describes: a field added to a slice is in
 * every look captured after it. Which slices a look may carry is decided once, inside this module.
 *
 * **Opaque outside `data:settings`.** A look is made by capturing a configured device or by reading a file, never by
 * writing blobs by hand: slices do not encode their defaults, so a hand-written one silently disagrees with the settings
 * screen.
 *
 * @property name what the look is called where it is offered.
 */
@Serializable
class Look internal constructor(
    val name: String,
    internal val slices: Map<String, JsonElement> = emptyMap(),
) {

    /** This look as a file, pretty-printed so that a captured look reviews as a readable diff. */
    fun toJson(): String = LookJson.encodeToString(Look.serializer(), this)

    companion object {
        /** Reads a look file. Throws on text that is not one: a shipped look that does not parse is a build fault. */
        fun parse(json: String): Look = LookJson.decodeFromString(Look.serializer(), json)
    }
}

/** The file format. Unknown keys are ignored so a file from a newer build still reads. */
private val LookJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}
