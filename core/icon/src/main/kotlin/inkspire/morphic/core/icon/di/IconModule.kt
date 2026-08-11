package inkspire.morphic.core.icon.di

import android.content.Context
import inkspire.morphic.core.icon.parse.DrawableParser
import inkspire.morphic.core.icon.parse.ParsedIconLoader
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.core.icon.render.IconRenderer
import org.koin.dsl.module

/**
 * Koin module for `core:icon`. The [ParsedIconLoader]'s `RawIconSource` is bound by the data layer
 * (`data:apps`) and resolved here via `get()`; `Context` is provided by the app at Koin start.
 *
 * [ParsedIconLoader] is bound rather than kept private to [IconRenderManager] because the **editor** needs it too:
 * the live render path starts from the same parsed layers the bake does, which is what stops the two drifting.
 */
val iconModule = module {
    single { DrawableParser() }
    single { ParsedIconLoader(get(), get()) }
    single { IconRenderer(get<Context>()) }
    single { IconRenderManager(get(), get()) }
}
