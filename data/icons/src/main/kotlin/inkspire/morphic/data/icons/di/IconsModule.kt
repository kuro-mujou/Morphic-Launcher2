package inkspire.morphic.data.icons.di

import inkspire.morphic.data.icons.IconOverrideRepository
import inkspire.morphic.data.icons.internal.IconOverrideRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for `data:icons`.
 *
 * A singleton because the override map is read by every icon on screen through one composition-local, so a second
 * instance would mean a second Room flow answering the same question. `IconOverrideDao` and `AppDispatchers` come
 * from the database and common modules.
 *
 * Custom-image storage and the icon-pack engine will join this module as their slices land (S7 and S8 of the icon
 * studio plan); the repository is the whole of it today.
 */
val iconsModule = module {
    single<IconOverrideRepository> { IconOverrideRepositoryImpl(get(), get()) }
}
