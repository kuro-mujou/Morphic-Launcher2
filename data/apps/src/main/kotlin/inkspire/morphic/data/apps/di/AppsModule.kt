package inkspire.morphic.data.apps.di

import android.content.Context
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.common.scope.ApplicationScope
import inkspire.morphic.core.database.dao.AppInfoDao
import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.data.apps.AppInfoOpener
import inkspire.morphic.data.apps.AppLauncher
import inkspire.morphic.data.apps.AppRepository
import inkspire.morphic.data.apps.AppRepositoryImpl
import inkspire.morphic.data.apps.AppShortcuts
import inkspire.morphic.data.apps.AppUninstaller
import inkspire.morphic.data.apps.BakedIconInvalidator
import inkspire.morphic.data.apps.DefaultAppInfoOpener
import inkspire.morphic.data.apps.DefaultAppLauncher
import inkspire.morphic.data.apps.DefaultAppShortcuts
import inkspire.morphic.data.apps.DefaultAppUninstaller
import inkspire.morphic.data.apps.DefaultGestureActionRunner
import inkspire.morphic.data.apps.DefaultLauncherAppsWrapper
import inkspire.morphic.data.apps.DefaultLauncherRole
import inkspire.morphic.data.apps.GestureActionRunner
import inkspire.morphic.data.apps.GestureServiceAccess
import inkspire.morphic.data.apps.LauncherAppsRawIconSource
import inkspire.morphic.data.apps.LauncherAppsWrapper
import inkspire.morphic.data.apps.PlatformDefaultLauncherRole
import inkspire.morphic.data.apps.PlatformGestureServiceAccess
import inkspire.morphic.data.apps.PlatformScreenLock
import inkspire.morphic.data.apps.PlatformSystemShade
import inkspire.morphic.data.apps.ScreenLock
import inkspire.morphic.data.apps.SystemShade
import inkspire.morphic.data.apps.category.AppCategorizer
import inkspire.morphic.data.apps.category.AssetCategoryMapping
import inkspire.morphic.data.apps.category.CategoryMapping
import org.koin.dsl.module

/**
 * Koin module for `data:apps`. The bindings are singletons: the wrapper holds long-lived system services,
 * and the repository fronts the shared cache. [AppInfoDao] and [AppDispatchers] are resolved from the
 * database/common modules, and `Context` is provided by the app at Koin start (as `DatabaseModule` expects).
 * [AppLauncher], [AppUninstaller], [AppInfoOpener], [AppShortcuts], [SystemShade], [ScreenLock], [DefaultLauncherRole] and
 * [GestureActionRunner] are thin stateless commands — singletons only to avoid re-allocating them, as is
 * [AppCategorizer]. [GestureServiceAccess] is a singleton for a real reason: it holds the blocked action that the
 * runner reports and the shell reads, so two instances would each see half of it.
 */
val appsModule = module {
    single<LauncherAppsWrapper> { DefaultLauncherAppsWrapper(get<Context>()) }
    single<RawIconSource> { LauncherAppsRawIconSource(get()) }
    single<AppRepository> { AppRepositoryImpl(get(), get(), get(), get<ApplicationScope>()) }
    single<AppLauncher> { DefaultAppLauncher(get()) }
    single<AppUninstaller> { DefaultAppUninstaller(get<Context>(), get()) }
    single<AppInfoOpener> { DefaultAppInfoOpener(get()) }
    single<AppShortcuts> { DefaultAppShortcuts(get(), get()) }
    single<SystemShade> { PlatformSystemShade() }
    single<ScreenLock> { PlatformScreenLock() }
    single<GestureServiceAccess> { PlatformGestureServiceAccess(get<Context>()) }
    single<DefaultLauncherRole> { PlatformDefaultLauncherRole(get<Context>()) }
    single<GestureActionRunner> { DefaultGestureActionRunner(get(), get(), get(), get(), get()) }

    // **`createdAtStart` because nothing injects it — it exists to run.** Its whole job is a subscription, so
    // waiting for a first consumer would mean waiting forever; `startKoin` creates eager singletons for exactly
    // this shape. It is not in `iconModule` because the *signal* is this module's: `core:icon` knows nothing
    // about packages coming and going, and must not learn.
    single(createdAtStart = true) { BakedIconInvalidator(get(), get(), get<ApplicationScope>()) }

    // Classification: the curated table is a `Context`-backed asset, read once and cached, so it is a singleton;
    // the categorizer over it is stateless and singleton only to avoid re-allocating it per call site.
    single<CategoryMapping> { AssetCategoryMapping(get<Context>()) }
    single { AppCategorizer(get()) }
}
