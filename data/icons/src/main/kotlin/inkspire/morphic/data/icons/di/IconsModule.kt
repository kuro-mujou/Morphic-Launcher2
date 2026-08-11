package inkspire.morphic.data.icons.di

import android.content.Context
import inkspire.morphic.core.icon.source.IconPackImages
import inkspire.morphic.data.icons.CustomIconStore
import inkspire.morphic.data.icons.IconPackManager
import inkspire.morphic.data.icons.IconOverrideRepository
import inkspire.morphic.data.icons.internal.IconOverrideRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

/**
 * Koin module for `data:icons`.
 *
 * A singleton because the override map is read by every icon on screen through one composition-local, so a second
 * instance would mean a second Room flow answering the same question. `IconOverrideDao` and `AppDispatchers` come
 * from the database and common modules.
 *
 * [IconPackImages] is the seam `core:icon` declared so it can composite a pack layer without knowing what a pack
 * *is*; binding it here is what keeps that module free of `appfilter.xml`. It is bound **blocking** on purpose —
 * `runBlocking` around a suspending manager — because the bake calls it from inside its own bounded dispatcher,
 * and a lookup that hopped for itself would escape the parallelism cap that exists to keep cores free. The work
 * behind it is a map lookup against an already-parsed pack in all but the first call.
 */
val iconsModule = module {
    single<IconOverrideRepository> { IconOverrideRepositoryImpl(get(), get()) }
    single { CustomIconStore(get<Context>(), get()) }
    single { IconPackManager(get<Context>(), get()) }
    single<IconPackImages> {
        val packs = get<IconPackManager>()
        IconPackImages { packPackage, component -> runBlocking { packs.drawable(packPackage, component) } }
    }
}
