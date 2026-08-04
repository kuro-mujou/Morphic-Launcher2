package inkspire.morphic.feature.settings.di

import inkspire.morphic.feature.settings.apps.AppsSectionViewModel
import inkspire.morphic.feature.settings.dock.DockViewModel
import inkspire.morphic.feature.settings.grid.GridSizeViewModel
import inkspire.morphic.feature.settings.folder.FolderViewModel
import inkspire.morphic.feature.settings.register.SurfaceRegisterViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the settings **surface** (the screens), distinct from `data:settings`' `settingsModule` (the store).
 * Named for the surface for the same reason `appsSurfaceModule` is: one name per concept, and "settingsModule" was
 * already taken by the layer that owns the preferences themselves.
 *
 * One ViewModel per section, bound with the `viewModel` DSL so each is scoped to its screen's `ViewModelStore` rather
 * than living forever. Sections are added here as they are ported.
 */
val settingsSurfaceModule = module {
    viewModel { SurfaceRegisterViewModel(get()) }
    viewModel { DockViewModel(get(), get()) }
    viewModel { GridSizeViewModel(get(), get()) }
    viewModel { AppsSectionViewModel(get()) }
    viewModel { FolderViewModel(get()) }
}
