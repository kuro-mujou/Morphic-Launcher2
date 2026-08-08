package inkspire.morphic.feature.home.di

import inkspire.morphic.feature.home.HomeViewModel
import inkspire.morphic.feature.home.widgetpicker.WidgetPickerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the home surface. [HomeViewModel] is bound with the `viewModel` DSL so Koin ties each instance
 * to the requesting screen's `ViewModelStore` (survives rotation, cleared with the screen) rather than living
 * forever like a `single`. Its `LayoutRepository`, `HomeListRepository`, `AppRepository`, and `AppLauncher` come
 * from the layout / apps modules, and its `SettingsRepository` from the settings module.
 *
 * [WidgetPickerViewModel] is bound the same way and for the same reason, even though the picker is a sheet rather
 * than a destination: it owns a one-shot platform read whose result should outlive the sheet being closed and
 * reopened, and die with the screen.
 */
val homeModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { WidgetPickerViewModel(get()) }
}
