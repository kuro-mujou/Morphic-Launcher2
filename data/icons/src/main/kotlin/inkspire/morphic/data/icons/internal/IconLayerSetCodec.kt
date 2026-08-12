package inkspire.morphic.data.icons.internal

import inkspire.morphic.core.model.icon.IconLayerSet
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * How a per-app [IconLayerSet] becomes the string in `icon_override.layerSet`, and back.
 *
 * Kept a plain object with no Room and no Android in sight, for the reason `data:settings` keeps `SettingsSlice`
 * free of DataStore: the interesting behavior — what happens to a blob that cannot be read — is then testable
 * without an emulator.
 *
 * **The rules deliberately match `data:settings`' slice codec**, because both stores serialize *the same type*:
 * [Json.ignoreUnknownKeys] so a build that has not heard of a newer effect drops it instead of throwing, and
 * `encodeDefaults = false` so a three-layer set stores three short objects rather than thirty fields of nothing.
 * They stay two configurations rather than one shared helper because what crosses between the stores is a decoded
 * **value** — the studio snapshots the default set and writes it here — never the bytes, so the two can never
 * disagree about anything that matters.
 */
internal object IconLayerSetCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** Serializes [layerSet] for storage. */
    fun encode(layerSet: IconLayerSet): String = json.encodeToString(IconLayerSet.serializer(), layerSet)

    /**
     * Reads [stored], or `null` if it cannot be read.
     *
     * **Null means "this row is unusable", and the caller drops it** so the app falls back to the global default —
     * which is a visible, fixable state, where letting the failure escape would take down every surface drawing that
     * icon. Two things reach this path: a genuinely corrupt blob, and a well-formed one describing an **illegal**
     * stack (two foregrounds, or a foreground below its background), which `IconLayerSet`'s own `init` rejects. The
     * second is the reason this catches rather than merely parses.
     *
     * A failure is logged, not swallowed: falling back is right, but it is still a bug worth being able to see.
     */
    fun decode(stored: String): IconLayerSet? =
        runCatching { json.decodeFromString(IconLayerSet.serializer(), stored) }
            .onFailure { Timber.w(it, "Unreadable icon override; falling back to the global default") }
            .getOrNull()
}
