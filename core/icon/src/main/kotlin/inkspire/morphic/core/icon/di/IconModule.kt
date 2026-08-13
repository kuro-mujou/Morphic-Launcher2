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
    // `Resources` because the parser rasterizes what it measures — see `DrawableParser.rasterized`. Only the
    // density on the produced `BitmapDrawable` comes from it, and both renderers set bounds explicitly, so it is
    // the app's rather than anything the icon carries.
    single { DrawableParser(get<Context>().resources) }
    single { ParsedIconLoader(get(), get()) }
    single { IconRenderer(get<Context>()) }
    // `getOrNull` for the pack seam: `data:icons` binds it, and a build or a test without that module still
    // renders — a pack layer simply draws nothing, which is the same outcome as a pack that covers no apps.
    single { IconRenderManager(get(), get(), getOrNull() ?: IconPackImages { _, _, _ -> null }) }
}
