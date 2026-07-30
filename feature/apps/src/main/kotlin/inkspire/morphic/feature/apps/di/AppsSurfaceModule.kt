package inkspire.morphic.feature.apps.di

import inkspire.morphic.feature.apps.AppsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the APPS surface. [AppsViewModel] is bound with the `viewModel` DSL so Koin ties each instance
 * to the requesting screen's `ViewModelStore` (survives rotation, cleared with the screen) rather than living
 * forever like a `single`. Its `AppRepository` and `AppLauncher` come from `data:apps`, its `AppsOrderRepository` (the pager's
 * arrangement) and `LayoutRepository` (folder definitions, shared with home) from `data:layout`, its
 * `AppCategorizer` from `data:apps` again, and `AppDispatchers` from `core:common`.
 *
 * Named `appsSurfaceModule`, not `appsModule`, because `data:apps` already owns that name — and the two would sit
 * side by side in the application's `modules(…)` list, where an import alias to tell them apart is worse than one
 * honest name. "Surface" is the right distinction anyway: this is the screen, that is the data.
 */
val appsSurfaceModule = module {
    viewModel { AppsViewModel(get(), get(), get(), get(), get(), get()) }
}
