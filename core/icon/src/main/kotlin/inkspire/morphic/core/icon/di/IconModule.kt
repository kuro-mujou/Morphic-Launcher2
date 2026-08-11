package inkspire.morphic.core.icon.di

import android.content.Context
import inkspire.morphic.core.icon.parse.DrawableParser
import inkspire.morphic.core.icon.parse.ParsedIconLoader
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.core.icon.render.IconRenderer
import inkspire.morphic.core.icon.source.IconPackImages
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
    // `getOrNull` for the pack seam: `data:icons` binds it, and a build or a test without that module still
    // renders — a pack layer simply draws nothing, which is the same outcome as a pack that covers no apps.
    single { IconRenderManager(get(), get(), getOrNull() ?: IconPackImages { _, _ -> null }) }
}
