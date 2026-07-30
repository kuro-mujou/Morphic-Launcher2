package inkspire.morphic.data.layout.di

import inkspire.morphic.data.layout.AppsOrderDaos
import inkspire.morphic.data.layout.AppsOrderRepository
import inkspire.morphic.data.layout.AppsOrderRepositoryImpl
import inkspire.morphic.data.layout.LayoutDaos
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.LayoutRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:layout`. Bundles each repository's DAOs (resolved from the database module) and binds the
 * repositories over them + `AppDispatchers` (from the common module).
 *
 * Two repositories, one module: [LayoutRepository] owns HOME's coordinate placements, [AppsOrderRepository] owns
 * the APPS surface's order stores. They are separate because the surfaces store different shapes (a cell versus a
 * page + slot), not because they belong to different modules — both are layout persistence, and both read the
 * shared folder tables.
 */
val layoutModule = module {
    single {
        LayoutDaos(
            get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(),
        )
    }
    single<LayoutRepository> { LayoutRepositoryImpl(get(), get()) }

    single { AppsOrderDaos(get(), get(), get(), get(), get()) }
    single<AppsOrderRepository> { AppsOrderRepositoryImpl(get(), get()) }
}
