package inkspire.morphic.data.settings.internal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.data.settings.Look
import inkspire.morphic.data.settings.LookRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Default [LookRepository], over the same DataStore as [SettingsRepositoryImpl] — which is what lets a whole look land
 * in one `edit`, so no reader ever sees a register from one look beside the grid sizes of another.
 *
 * `internal` so only Koin constructs it; consumers depend on [LookRepository].
 */
internal class LookRepositoryImpl(
    context: Context,
    private val dispatchers: AppDispatchers,
) : LookRepository {

    private val dataStore = context.settingsDataStore

    override suspend fun apply(look: Look) {
        val writes = withContext(dispatchers.io) { lookWrites(look) }
        dataStore.edit { prefs -> writes.forEach { (name, value) -> prefs[stringPreferencesKey(name)] = value } }
    }

    override suspend fun capture(name: String): Look {
        val prefs = dataStore.data.first()
        return withContext(dispatchers.io) { captureLook(name, stored = { prefs[stringPreferencesKey(it)] }) }
    }
}
