package inkspire.morphic.data.widgets.di

import android.content.Context
import inkspire.morphic.data.widgets.DefaultWidgetCatalog
import inkspire.morphic.data.widgets.WidgetCatalog
import org.koin.dsl.module

/**
 * Koin module for `data:widgets`. A singleton because it holds resolved system services and the application
 * context, not because it caches anything — [WidgetCatalog.installed] re-reads the platform every call.
 */
val widgetsModule = module {
    single<WidgetCatalog> { DefaultWidgetCatalog(get<Context>(), get()) }
}
