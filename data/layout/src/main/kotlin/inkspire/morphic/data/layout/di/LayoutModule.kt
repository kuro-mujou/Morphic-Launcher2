package inkspire.morphic.data.layout.di

import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.LayoutRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:layout`. Binds the [LayoutRepository] as a singleton; its `AppPlacementDao` and
 * `AppDispatchers` are resolved from the database and common modules (which the app starts alongside this one).
 */
val layoutModule = module {
    single<LayoutRepository> {
        LayoutRepositoryImpl(get(), get(), get(), get(), get(), get())
    }
}
