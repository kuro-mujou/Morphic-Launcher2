package inkspire.morphic.launcher

import android.app.Application
import inkspire.morphic.core.common.di.commonModule
import inkspire.morphic.core.database.di.databaseModule
import inkspire.morphic.core.icon.di.iconModule
import inkspire.morphic.data.apps.di.appsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * The launcher [Application]: starts Koin with the app [android.content.Context] and every module's DI graph.
 * Registered as `android:name` in the manifest so it runs before any Activity, which is what lets
 * `databaseModule` and `iconModule` resolve `Context` via `get<Context>()`.
 */
class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@LauncherApplication)
            modules(
                commonModule,
                databaseModule,
                appsModule,
                iconModule,
            )
        }
    }
}
