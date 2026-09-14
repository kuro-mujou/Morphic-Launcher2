package inkspire.morphic.data.settings.internal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single DataStore backing every settings slice — read slice by slice by [SettingsRepositoryImpl], and written a
 * whole look at a time by [LookRepositoryImpl].
 *
 * A `Context` extension because that is the only shape `preferencesDataStore` offers, and declared once at file scope
 * because two stores over one file throw at runtime — which is also why both repositories reach it through this one
 * property rather than each declaring their own.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")
