package inkspire.morphic.data.settings.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The JSON shape settings blobs are stored in.
 *
 * [Json.ignoreUnknownKeys] is what makes additive evolution safe in both directions: an older build reading a blob
 * written by a newer one drops the fields it doesn't know instead of throwing, and a newer build reading an older blob
 * fills the gaps from the slice's own defaults. Together with "every field defaulted", that removes the need for a
 * version int on each slice.
 *
 * Defaults are **not** encoded, so a blob holds only what differs from the defaults — which is what keeps
 * "the default lives in exactly one place" true rather than aspirational, and what will keep the per-surface metric
 * overrides sparse when they arrive.
 */
private val SettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * One named blob of settings: its storage key, how to serialize it, and what it is when absent.
 *
 * **Deliberately free of DataStore.** Everything here is a pure function of a `String?`, so the encode/decode rules —
 * including what happens to a corrupt blob — are unit-testable without Android or Robolectric. That is the same reason
 * `data:layout` keeps its paging and ordering arithmetic in plain files: the interesting behaviour should not need an
 * emulator to check. L1's equivalent was a 693-line `Preferences.kt` welded to `MutablePreferences`, and it is the one
 * file in its settings module with no tests at all.
 *
 * @param name the DataStore key this slice occupies. **This is the migration seam**: a change that alters what a field
 *   *means* writes to a new name, so the old and new formats coexist and the old one can be read once and dropped —
 *   which a version number inside a single key cannot offer.
 * @param default the value when nothing is stored, or when what is stored cannot be read.
 */
internal class SettingsSlice<T>(
    val name: String,
    private val serializer: KSerializer<T>,
    val default: T,
) {

    /** Serializes [value] for storage. */
    fun encode(value: T): String = SettingsJson.encodeToString(serializer, value)

    /**
     * Reads [stored], falling back to [default] when it is absent or unreadable.
     *
     * **A failed decode is logged, not swallowed.** L1's `runCatching{}.getOrNull()` meant a corrupt blob silently
     * reverted a user's settings with no way to find out why. Falling back is still the right behaviour — refusing to
     * start because one preference is malformed would be worse — but it is a bug worth seeing, so it is reported.
     */
    fun decode(stored: String?): T {
        if (stored == null) return default
        return runCatching { SettingsJson.decodeFromString(serializer, stored) }
            .onFailure { Timber.w(it, "Settings slice '%s' is unreadable; falling back to defaults", name) }
            .getOrDefault(default)
    }
}
