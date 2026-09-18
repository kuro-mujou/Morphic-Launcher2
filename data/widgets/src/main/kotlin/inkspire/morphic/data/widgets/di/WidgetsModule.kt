package inkspire.morphic.data.widgets.di

import inkspire.morphic.data.widgets.WidgetDataRepository
import inkspire.morphic.data.widgets.internal.DefaultWidgetDataRepository
import org.koin.dsl.module

/**
 * Koin module for `data:widgets`. The repository holds nothing between collections, so one instance serves every
 * widget; each collection opens its own listeners.
 */
val widgetsModule = module {
    single<WidgetDataRepository> { DefaultWidgetDataRepository(get()) }
}
