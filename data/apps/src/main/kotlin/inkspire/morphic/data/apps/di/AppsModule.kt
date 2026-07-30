package inkspire.morphic.data.apps.di

import android.content.Context
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.database.dao.AppInfoDao
import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.AppRepositoryImpl
import inkspire.morphic.data.apps.DefaultAppLauncher
import inkspire.morphic.data.apps.DefaultLauncherAppsWrapper
import inkspire.morphic.data.apps.LauncherAppsRawIconSource
import inkspire.morphic.data.apps.LauncherAppsWrapper
import inkspire.morphic.data.apps.category.AppCategorizer
import inkspire.morphic.data.apps.category.AssetCategoryMapping
import inkspire.morphic.data.apps.category.CategoryMapping
import org.koin.dsl.module

/**
 * Koin module for `data:apps`. The bindings are singletons: the wrapper holds long-lived system services,
 * and the repository fronts the shared cache. [AppInfoDao] and [AppDispatchers] are resolved from the
 * database/common modules, and `Context` is provided by the app at Koin start (as `DatabaseModule` expects).
 * [AppLauncher] is a thin stateless command over the wrapper — singleton only to avoid re-allocating it, as is
 * [AppCategorizer].
 */
val appsModule = module {
    single<LauncherAppsWrapper> { DefaultLauncherAppsWrapper(get<Context>()) }
    single<RawIconSource> { LauncherAppsRawIconSource(get()) }
    single<AppRepository> { AppRepositoryImpl(get(), get(), get()) }
    single<AppLauncher> { DefaultAppLauncher(get()) }

    // Classification: the curated table is a `Context`-backed asset, read once and cached, so it is a singleton;
    // the categorizer over it is stateless and singleton only to avoid re-allocating it per call site.
    single<CategoryMapping> { AssetCategoryMapping(get<Context>()) }
    single { AppCategorizer(get()) }
}
