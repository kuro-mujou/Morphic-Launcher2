package inkspire.morphic.data.icons.di

import android.content.Context
import inkspire.morphic.data.icons.CustomIconStore
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
 * The icon-pack engine joins this module when that slice lands (S8 of the icon studio plan).
 */
val iconsModule = module {
    single<IconOverrideRepository> { IconOverrideRepositoryImpl(get(), get()) }
    single { CustomIconStore(get<Context>(), get()) }
}
