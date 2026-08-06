package inkspire.morphic.data.layout.di

import inkspire.morphic.data.layout.AppsOrderDaos
import inkspire.morphic.data.layout.AppsOrderRepository
import inkspire.morphic.data.layout.AppsOrderRepositoryImpl
import inkspire.morphic.data.layout.HomeListRepository
import inkspire.morphic.data.layout.HomeListRepositoryImpl
import inkspire.morphic.data.layout.LayoutDaos
import inkspire.morphic.data.layout.LayoutRepository
import inkspire.morphic.data.layout.LayoutRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:layout`. Bundles each repository's DAOs (resolved from the database module) and binds the
 * repositories over them + `AppDispatchers` (from the common module).
 *
 * Three repositories, one module: [LayoutRepository] owns HOME's coordinate placements, [AppsOrderRepository] owns
 * the APPS surface's order stores, and [HomeListRepository] owns HOME's vertical-list order. They are separate
 * because the surfaces store different shapes (a cell, a page + slot, a bare index), not because they belong to
 * different modules — all three are layout persistence, and the first two read the shared folder tables.
 *
 * [HomeListRepository] takes its one DAO directly rather than through a bundle: a bundle exists to stop a repository
 * growing six constructor parameters, and one is not six.
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

    single<HomeListRepository> { HomeListRepositoryImpl(get(), get()) }
}
