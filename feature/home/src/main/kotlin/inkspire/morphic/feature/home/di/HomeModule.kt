package inkspire.morphic.feature.home.di

import inkspire.morphic.feature.home.HomeViewModel
import inkspire.morphic.feature.home.containersettings.ContainerSettingsRoute
import inkspire.morphic.feature.home.containersettings.ContainerSettingsViewModel
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
 *
 * [ContainerSettingsViewModel] takes its route as a **parameter**, which makes it the second per-instance ViewModel
 * in the launcher after the icon studio's — and the second to depend on `NavDisplay` being given
 * `rememberViewModelStoreNavEntryDecorator`. Without that decorator every entry shares the Activity's store and a
 * `koinViewModel` keyed on the type would hand the second container's screen the first container's instance.
 */
val homeModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { WidgetPickerViewModel(get()) }
    viewModel { (route: ContainerSettingsRoute) -> ContainerSettingsViewModel(route, get(), get(), get()) }
}
