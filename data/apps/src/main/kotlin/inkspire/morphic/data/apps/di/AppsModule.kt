package inkspire.morphic.data.apps.di

import android.content.Context
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppInfoDao
import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.AppRepositoryImpl
import inkspire.morphic.data.apps.DefaultLauncherAppsWrapper
import inkspire.morphic.data.apps.LauncherAppsRawIconSource
import inkspire.morphic.data.apps.LauncherAppsWrapper
import org.koin.dsl.module

/**
 * Koin module for `data:apps`. Both bindings are singletons: the wrapper holds long-lived system services,
 * and the repository fronts the shared cache. [AppInfoDao] and [AppDispatchers] are resolved from the
 * database/common modules, and `Context` is provided by the app at Koin start (as `DatabaseModule` expects).
 */
val appsModule = module {
    single<LauncherAppsWrapper> { DefaultLauncherAppsWrapper(get<Context>()) }
    single<RawIconSource> { LauncherAppsRawIconSource(get()) }
    single<AppRepository> { AppRepositoryImpl(get(), get(), get()) }
}
