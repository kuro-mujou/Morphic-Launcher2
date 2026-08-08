package inkspire.morphic.data.widgets.di

import android.content.Context
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.data.widgets.DefaultAppWidgetHostController
import inkspire.morphic.data.widgets.DefaultWidgetCatalog
import inkspire.morphic.data.widgets.WidgetCatalog
import org.koin.dsl.module

/**
 * Koin module for `data:widgets`.
 *
 * [WidgetCatalog] is a singleton because it holds resolved system services and the application context, not
 * because it caches anything — `installed()` re-reads the platform every call.
 *
 * [AppWidgetHostController] is a singleton for a stronger reason: it wraps **one** `AppWidgetHost` per process,
 * and the ids it allocates are keyed to that host. A second instance would be a second host with the same id,
 * which is not a thing the platform expects anyone to do.
 */
val widgetsModule = module {
    single<WidgetCatalog> { DefaultWidgetCatalog(get<Context>(), get()) }
    single<AppWidgetHostController> { DefaultAppWidgetHostController(get<Context>()) }
}
