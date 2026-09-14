package inkspire.morphic.data.settings.internal

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import inkspire.morphic.data.settings.SideBinding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Where the surface register is stored. */
internal const val SurfaceRegisterKey = "surface_register_v2"

/** Where it was stored while `SideBinding.Apps` carried its class name as its discriminator. */
internal const val LegacySurfaceRegisterKey = "surface_register"

/** The discriminator `SideBinding.Apps` was stored under before it had a `@SerialName`. */
private const val LegacyAppsDiscriminator = "inkspire.morphic.data.settings.SideBinding.Apps"

/**
 * Carries a register stored under [LegacySurfaceRegisterKey] across to [SurfaceRegisterKey], once, and deletes the old
 * key.
 *
 * **A `DataMigration` rather than a fallback in every read**, because DataStore runs it before it serves any read or
 * edit. So every reader of the store — the repository's flows and writes, the onboarding flag's resolution, a look's
 * capture and apply — only ever sees the new key. A fallback in the repository's `read` alone would have lost the edges
 * on the first edit, whose transaction decodes only the key it writes.
 */
internal object SurfaceRegisterKeyMigration : DataMigration<Preferences> {

    private val legacyKey = stringPreferencesKey(LegacySurfaceRegisterKey)
    private val currentKey = stringPreferencesKey(SurfaceRegisterKey)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = legacyKey in currentData

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        migratedSurfaceRegister(legacy = get(legacyKey), current = get(currentKey))?.let { set(currentKey, it) }
        remove(legacyKey)
    }.toPreferences()

    override suspend fun cleanUp() = Unit
}

/**
 * What [SurfaceRegisterKey] should be given: [legacy] with its discriminators upgraded, or null when there is nothing to
 * write — no old register, or a new one already stored, which is never overwritten.
 */
internal fun migratedSurfaceRegister(legacy: String?, current: String?): String? =
    if (legacy == null || current != null) null else upgradeSurfaceRegister(legacy)

/**
 * [stored] with every side binding's class-name discriminator replaced by its serial name.
 *
 * **Rewritten as JSON, not as text**, so whitespace or key order in the stored blob cannot make a match fail silently.
 * A blob that is not a JSON object is returned as it was, to fall back to the default where it is read — the treatment
 * any unreadable slice gets.
 */
internal fun upgradeSurfaceRegister(stored: String): String {
    val root = runCatching { Json.parseToJsonElement(stored) as? JsonObject }.getOrNull() ?: return stored
    val sides = root["sides"] as? JsonObject ?: return stored
    val appsName = SideBinding.Apps.serializer().descriptor.serialName
    val upgraded = sides.mapValues { (_, binding) ->
        val obj = binding as? JsonObject
        if (obj?.get("type")?.jsonPrimitive?.contentOrNull == LegacyAppsDiscriminator) {
            JsonObject(obj + ("type" to JsonPrimitive(appsName)))
        } else {
            binding
        }
    }
    return JsonObject(root + ("sides" to JsonObject(upgraded))).toString()
}
