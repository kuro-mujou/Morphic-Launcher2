package inkspire.morphic.data.layout.di

import inkspire.morphic.data.layout.LayoutDaos
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.LayoutRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:layout`. Bundles the layout [LayoutDaos] (resolved from the database module) and binds
 * the [LayoutRepository] singleton over them + `AppDispatchers` (from the common module).
 */
val layoutModule = module {
    single {
        LayoutDaos(
            get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(),
        )
    }
    single<LayoutRepository> { LayoutRepositoryImpl(get(), get()) }
}
