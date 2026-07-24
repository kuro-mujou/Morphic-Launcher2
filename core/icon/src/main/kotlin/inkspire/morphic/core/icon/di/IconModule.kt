package inkspire.morphic.core.icon.di

import android.content.Context
import inkspire.morphic.core.icon.parse.DrawableParser
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.core.icon.render.IconRenderer
import org.koin.dsl.module

/**
 * Koin module for `core:icon`. The [IconRenderManager]'s `RawIconSource` is bound by the data layer
 * (`data:apps`) and resolved here via `get()`; `Context` is provided by the app at Koin start.
 */
val iconModule = module {
    single { DrawableParser() }
    single { IconRenderer(get<Context>()) }
    single { IconRenderManager(get(), get(), get()) }
}
