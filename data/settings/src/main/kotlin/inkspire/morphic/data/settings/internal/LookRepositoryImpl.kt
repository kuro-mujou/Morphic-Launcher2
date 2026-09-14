package inkspire.morphic.data.settings.internal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.data.settings.Look
import inkspire.morphic.data.settings.LookRepository
import inkspire.morphic.data.settings.Onboarding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What a captured current setup is called, before the first-run screen names its row. */
private const val CurrentSetupName = "Current setup"

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

    override val restartLook: Flow<Look?> = dataStore.data
        .map { RestartLookSlice.decode(it[stringPreferencesKey(RestartLookSlice.name)]) }
        .flowOn(dispatchers.io)

    // The capture is taken **inside** the transaction that stores it and re-opens setup, so nothing written in between
    // can make the setup offered back differ from the one the user left.
    override suspend fun startOver() {
        dataStore.edit { prefs ->
            val current = captureLook(CurrentSetupName, stored = { prefs[stringPreferencesKey(it)] })
            prefs[stringPreferencesKey(RestartLookSlice.name)] = RestartLookSlice.encode(current)
            prefs[stringPreferencesKey(OnboardingSlice.name)] = OnboardingSlice.encode(Onboarding.Default)
        }
    }

    override suspend fun clearRestartLook() {
        dataStore.edit { it.remove(stringPreferencesKey(RestartLookSlice.name)) }
    }
}
