package inkspire.morphic.data.settings.di

import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.internal.SettingsRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:settings`.
 *
 * A `single`, and that is load-bearing rather than idiomatic habit: `preferencesDataStore` permits only **one**
 * DataStore per file name in a process, so a second repository instance would be a runtime crash. One binding, one
 * store.
 *
 * `Context` comes from `androidContext()` in the application's `startKoin`, as `databaseModule` and `iconModule`
 * already rely on.
 */
val settingsModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
}
