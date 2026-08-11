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

    /** [component]'s artwork from [packPackage], or `null` when that pack does not cover the app. */
    fun drawable(packPackage: String, component: ComponentKey): Drawable?
}
