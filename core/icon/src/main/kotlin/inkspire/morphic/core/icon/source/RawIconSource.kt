package inkspire.morphic.core.icon.source

import android.graphics.drawable.Drawable
import inkspire.morphic.core.model.ComponentKey

/**
 * Supplies the raw, unstyled [Drawable] for an app icon — the input to the icon pipeline (parse → render).
 *
 * The interface lives here, on the consumer side, so `core:icon` stays independent of *where* icons come from;
 * a data-layer implementation backs it with the platform (`LauncherApps`). A later pack-aware source can
 * decorate this one to fold in icon packs.
 */
interface RawIconSource {

    /**
     * The raw icon [Drawable] for [component] at [densityDpi] (`0` = the device's default density), or `null`
     * when the component no longer resolves.
     */
    fun loadIcon(component: ComponentKey, densityDpi: Int = 0): Drawable?
}
