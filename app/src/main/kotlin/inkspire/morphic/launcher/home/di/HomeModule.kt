package inkspire.morphic.launcher.home.di

import inkspire.morphic.launcher.home.HomeStateHolder
import org.koin.dsl.module

/**
 * Koin module for the home surface. [HomeStateHolder] is a singleton — it owns the seeded first-run layout and
 * an app-lifetime subscription, so it should outlive individual compositions. Its `LayoutRepository`,
 * `AppRepository`, and `ApplicationScope` come from the layout / apps / common modules.
 */
val homeModule = module {
    single { HomeStateHolder(get(), get(), get()) }
}
