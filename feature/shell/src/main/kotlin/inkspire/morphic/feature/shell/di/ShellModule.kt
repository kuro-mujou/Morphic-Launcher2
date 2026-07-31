package inkspire.morphic.feature.shell.di

import inkspire.morphic.feature.shell.ShellViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the launcher shell. [ShellViewModel] is bound with the `viewModel` DSL so Koin ties it to the
 * hosting screen's `ViewModelStore` — surviving rotation, cleared with the screen — rather than living forever as a
 * `single`. Its `SettingsRepository` comes from `data:settings`.
 */
val shellModule = module {
    viewModel { ShellViewModel(get()) }
}
