package inkspire.morphic.data.appwidgets.di

import android.content.Context
import inkspire.morphic.data.appwidgets.AppWidgetCatalog
import inkspire.morphic.data.appwidgets.AppWidgetHostController
import inkspire.morphic.data.appwidgets.DefaultAppWidgetCatalog
import inkspire.morphic.data.appwidgets.DefaultAppWidgetHostController
import org.koin.dsl.module

/**
 * Koin module for `data:appwidgets`.
 *
 * [AppWidgetCatalog] is a singleton because it holds resolved system services and the application context, not
 * because it caches anything — `installed()` re-reads the platform every call.
 *
 * [AppWidgetHostController] is a singleton for a stronger reason: it wraps **one** `AppWidgetHost` per process,
 * and the ids it allocates are keyed to that host. A second instance would be a second host with the same id,
 * which is not a thing the platform expects anyone to do.
 */
val appWidgetsModule = module {
    single<AppWidgetCatalog> { DefaultAppWidgetCatalog(get<Context>(), get()) }
    single<AppWidgetHostController> { DefaultAppWidgetHostController(get<Context>()) }
}
