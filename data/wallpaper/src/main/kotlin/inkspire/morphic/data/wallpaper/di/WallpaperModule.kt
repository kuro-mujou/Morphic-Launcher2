package inkspire.morphic.data.wallpaper.di

import inkspire.morphic.data.wallpaper.WallpaperRepository
import inkspire.morphic.data.wallpaper.internal.WallpaperRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:wallpaper`.
 *
 * A `single` for the reason `settingsModule`'s is, and it is load-bearing rather than habit: `preferencesDataStore`
 * permits one DataStore per file name in a process, so a second repository would be a runtime crash. One binding, one
 * store.
 *
 * `Context` comes from `androidContext()` in the application's `startKoin`, as every other data module already relies on.
 */
val wallpaperModule = module {
    single<WallpaperRepository> { WallpaperRepositoryImpl(get(), get()) }
}
