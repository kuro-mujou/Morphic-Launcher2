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

    private fun plantLogging() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
