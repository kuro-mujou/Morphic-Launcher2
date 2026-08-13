package inkspire.morphic.core.icon.source

import android.graphics.drawable.Drawable
import inkspire.morphic.core.model.ComponentKey

/**
 * Draws one app's icon from an installed icon pack — the seam `core:icon` needs so it can composite a pack layer
 * without knowing what an icon pack *is*.
 *
 * Declared here on the consumer side for [RawIconSource]'s reason: this module renders, and where artwork comes
 * from is the data layer's business. `data:icons` binds it to the real pack engine; the default binding draws
 * nothing, which is exactly right for a build with no pack support and for the dev harness.
 *
 * **Blocking**, like [RawIconSource.loadIcon] beside it, so the bake can call it inside its own bounded
 * dispatcher rather than having it hop for itself and escape the parallelism cap.
 */
fun interface IconPackImages {

    /**
     * [component]'s artwork from [packPackage] — the drawable the pack maps to it, or the pack's own treatment for an
     * app it does not theme (its plate, with the app's icon shrunk inside).
     *
     * `null` means the pack has **neither**, which is a narrower statement than "does not cover this app": the caller
     * reads it as "this pack has nothing to say here" and leaves the app's own artwork on the layer, rather than
     * dropping the layer and deleting the icon.
     *
     * @param drawableName one specific drawable, when the user has browsed the pack and chosen; `null` lets the
     *   pack's own mapping decide which drawable belongs to [component].
     */
    fun drawable(packPackage: String, component: ComponentKey, drawableName: String?): Drawable?
}
