package inkspire.morphic.feature.settings.di

import inkspire.morphic.feature.settings.SettingsShellViewModel
import inkspire.morphic.feature.settings.apps.AppsSectionViewModel
import inkspire.morphic.feature.settings.dock.DockViewModel
import inkspire.morphic.feature.settings.effects.EffectsViewModel
import inkspire.morphic.feature.settings.grid.GridSizeViewModel
import inkspire.morphic.feature.settings.home.HomeHubViewModel
import inkspire.morphic.feature.settings.iconstudio.IconStudioRoute
import inkspire.morphic.feature.settings.iconstudio.IconStudioViewModel
import inkspire.morphic.feature.settings.iconstudio.IconsViewModel
import inkspire.morphic.feature.settings.folder.FolderViewModel
import inkspire.morphic.feature.settings.register.SurfaceRegisterViewModel
import inkspire.morphic.feature.settings.wallpaper.WallpaperViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the settings **surface** (the screens), distinct from `data:settings`' `settingsModule` (the store).
 * Named for the surface for the same reason `appsSurfaceModule` is: one name per concept, and "settingsModule" was
 * already taken by the layer that owns the preferences themselves.
 *
 * One ViewModel per section, bound with the `viewModel` DSL so each is scoped to its screen's `ViewModelStore` rather
 * than living forever. Sections are added here as they are ported — plus one for the shell itself, which has a single
 * setting of its own to read (see [SettingsShellViewModel]).
 */
val settingsSurfaceModule = module {
    viewModel { SettingsShellViewModel(get()) }
    viewModel { SurfaceRegisterViewModel(get()) }
    viewModel { WallpaperViewModel(get()) }
    viewModel { DockViewModel(get(), get(), get()) }
    viewModel { GridSizeViewModel(get(), get(), get()) }
    viewModel { HomeHubViewModel(get()) }
    viewModel { AppsSectionViewModel(get(), get()) }
    viewModel { FolderViewModel(get(), get()) }
    viewModel { EffectsViewModel(get(), get()) }
    viewModel { IconsViewModel(get(), get()) }

    // The one ViewModel here taking a parameter: the studio cannot work out *what it is editing* for itself, and
    // that arrives as the destination. Passed at `koinViewModel { parametersOf(route) }` rather than read from a
    // handle, so the screen and the graph agree by construction.
    viewModel { (route: IconStudioRoute) ->
        IconStudioViewModel(route, get(), get(), get(), get(), get(), get(), get())
    }
}
