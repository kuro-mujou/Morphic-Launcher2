package inkspire.morphic.launcher

import android.app.Application
import android.content.pm.ApplicationInfo
import inkspire.morphic.core.common.di.commonModule
import inkspire.morphic.core.database.di.databaseModule
import inkspire.morphic.core.icon.di.iconModule
import inkspire.morphic.data.apps.di.appsModule
import inkspire.morphic.data.icons.di.iconsModule
import inkspire.morphic.data.layout.di.layoutModule
import inkspire.morphic.data.settings.di.settingsModule
import inkspire.morphic.data.wallpaper.di.wallpaperModule
import inkspire.morphic.data.widgets.di.widgetsModule
import inkspire.morphic.feature.apps.di.appsSurfaceModule
import inkspire.morphic.feature.home.di.homeModule
import inkspire.morphic.feature.settings.di.settingsSurfaceModule
import inkspire.morphic.feature.shell.di.shellModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

/**
 * The launcher [Application]: starts Koin with the app [android.content.Context] and every module's DI graph.
 * Registered as `android:name` in the manifest so it runs before any Activity, which is what lets
 * `databaseModule` and `iconModule` resolve `Context` via `get<Context>()`.
 */
class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        plantLogging()
        startKoin {
            androidLogger()
            androidContext(this@LauncherApplication)
            modules(
                commonModule,
                databaseModule,
                appsModule,
                iconModule,
                iconsModule,
                layoutModule,
                settingsModule,
                wallpaperModule,
                widgetsModule,
                homeModule,
                appsSurfaceModule,
                settingsSurfaceModule,
                shellModule,
            )
        }
    }

    /**
     * Gives Timber somewhere to write — **without which every `Timber.w` in this codebase is a no-op**, which is not
     * a theoretical gap: it is why a failing uninstall looked like a button that did nothing rather than a warning
     * naming the package. Every module logs through Timber and nothing had ever planted a tree.
     *
     * Debuggable builds only, so a release APK does not narrate itself into logcat. Read from [ApplicationInfo]
     * rather than `BuildConfig.DEBUG` because that field only exists where the build-config feature is switched on,
     * and this is the same fact from the manifest the platform itself uses.
     */
    private fun plantLogging() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
