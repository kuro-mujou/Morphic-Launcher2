package inkspire.morphic.feature.home.di

import inkspire.morphic.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the home surface. [HomeViewModel] is bound with the `viewModel` DSL so Koin ties each instance
 * to the requesting screen's `ViewModelStore` (survives rotation, cleared with the screen) rather than living
 * forever like a `single`. Its `LayoutRepository`, `AppRepository`, and `AppLauncher` come from the layout /
 * apps modules.
 */
val homeModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get()) }
}
