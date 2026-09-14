package inkspire.morphic.data.settings.internal

import inkspire.morphic.data.settings.Look
import kotlinx.serialization.json.Json

/**
 * The stored values applying [look] writes, by slice name. Nothing outside the returned map changes.
 *
 * - Only [LookScope.carried] slices are written; an excluded or unknown name is dropped.
 * - A carried slice the look does not hold is left as stored.
 * - A value that does not read as its slice is dropped, since writing it would reset that setting to its default.
 * - [LookScope.IconAppliedPreset] is written exactly when [LookScope.IconAppearance] is — the look's own value, or null
 *   when it holds none — and never otherwise.
 */
internal fun lookWrites(look: Look, slices: Map<String, SettingsSlice<*>> = SettingsSlices): Map<String, String> {
    fun valueOf(name: String): String? {
        val element = look.slices[name] ?: return null
        return slices[name]?.reencodeOrNull(element.toString())
    }

    val writes = LookScope.carried
        .filter { it != LookScope.IconAppliedPreset }
        .mapNotNull { name -> valueOf(name)?.let { name to it } }
        .toMap()
    if (LookScope.IconAppearance !in writes) return writes
    val preset = valueOf(LookScope.IconAppliedPreset) ?: slices.getValue(LookScope.IconAppliedPreset).canonical(null)
    return writes + (LookScope.IconAppliedPreset to preset)
}

/**
 * The look in force, called [name]: every [LookScope.carried] slice read through [stored], **its default spelled out
 * when nothing is stored**.
 *
 * Explicit rather than sparse so a capture reproduces the device it came from. A sparse one would leave every slice the
 * capturing device never changed at whatever the receiving device holds — one look landing differently everywhere.
 */
internal fun captureLook(
    name: String,
    stored: (String) -> String?,
    slices: Map<String, SettingsSlice<*>> = SettingsSlices,
): Look = Look(
    name = name,
    slices = LookScope.carried.associateWith { slice ->
        Json.parseToJsonElement(slices.getValue(slice).canonical(stored(slice)))
    },
)
