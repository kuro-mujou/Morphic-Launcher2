package inkspire.morphic.core.database.di

import android.content.Context
import androidx.room.Room
import inkspire.morphic.core.database.LauncherDatabase
import org.koin.dsl.module

/** Koin module providing the [LauncherDatabase] and each of its DAOs as singletons. */
val databaseModule = module {
    single {
        Room.databaseBuilder(
            context = get<Context>(),
            klass = LauncherDatabase::class.java,
            name = LauncherDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<LauncherDatabase>().appInfoDao() }
    single { get<LauncherDatabase>().appPlacementDao() }
    single { get<LauncherDatabase>().folderDao() }
    single { get<LauncherDatabase>().folderItemDao() }
    single { get<LauncherDatabase>().folderPlacementDao() }
    single { get<LauncherDatabase>().widgetDao() }
    single { get<LauncherDatabase>().widgetPlacementDao() }
    single { get<LauncherDatabase>().widgetContainerDao() }
    single { get<LauncherDatabase>().widgetContainerItemDao() }
    single { get<LauncherDatabase>().widgetContainerPlacementDao() }
    single { get<LauncherDatabase>().iconContainerDao() }
    single { get<LauncherDatabase>().iconContainerItemDao() }
    single { get<LauncherDatabase>().iconContainerPlacementDao() }
    single { get<LauncherDatabase>().iconOverrideDao() }
    single { get<LauncherDatabase>().appsPagerItemDao() }
    single { get<LauncherDatabase>().categoryDao() }
    single { get<LauncherDatabase>().categoryItemDao() }
    single { get<LauncherDatabase>().homeListItemDao() }
}
