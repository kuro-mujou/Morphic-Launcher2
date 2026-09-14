package inkspire.morphic.data.settings.di

import inkspire.morphic.data.settings.LookRepository
import inkspire.morphic.data.settings.SettingsRepository
import inkspire.morphic.data.settings.internal.LookRepositoryImpl
import inkspire.morphic.data.settings.internal.SettingsRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:settings`.
 *
 * `single`s, and that is load-bearing rather than idiomatic habit: `preferencesDataStore` permits only **one**
 * DataStore per file name in a process, so a second instance of either repository would be a runtime crash. Both reach
 * that one store through `settingsDataStore`.
 *
 * `Context` comes from `androidContext()` in the application's `startKoin`, as `databaseModule` and `iconModule`
 * already rely on.
 */
val settingsModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    single<LookRepository> { LookRepositoryImpl(get(), get()) }
}
