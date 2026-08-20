package inkspire.morphic.feature.settings.wallpaper

import androidx.navigation3.runtime.NavKey
import inkspire.morphic.core.model.Orientation
import kotlinx.serialization.Serializable

/**
 * **What a framed image becomes** — which of the launcher's three wallpaper slots the crop screen is filling.
 *
 * One value rather than a pair of booleans (`forRotate` + `landscape`), which between them can express a state that
 * does not exist ("not for rotate, but landscape") and have to be read together to mean anything. It decides the
 * two things the crop screen needs and cannot otherwise know: the **shape** it frames against, and the **size** it
 * stores at.
 */
@Serializable
enum class CropTarget {

    /** The single image — the one that can be set on the system. Framed and stored at this screen's own size. */
    SINGLE,

    /** The portrait half of the rotating pair. */
    ROTATING_PORTRAIT,

    /**
     * The landscape half of the rotating pair, **framed landscape-shaped whatever way the phone is being held**.
     *
     * Pinning the activity's orientation while framing is one answer; this letterboxes a landscape frame into the
     * portrait screen instead. Smaller to look at, and it neither fights the system nor leaves an activity in an
     * orientation the user did not ask for — while the *stored* image is full landscape resolution either way, because
     * the frame decides the shape and the target screen decides the size.
     */
    ROTATING_LANDSCAPE,
    ;

    /** The rotating half this fills, or null for [SINGLE] — which is also the "is this the pair?" test. */
    val orientation: Orientation?
        get() = when (this) {
            SINGLE -> null
            ROTATING_PORTRAIT -> Orientation.PORTRAIT
            ROTATING_LANDSCAPE -> Orientation.LANDSCAPE
        }
}

/**
 * Framing a picked image before it becomes the wallpaper — [WallpaperCropScreen]'s destination.
 *
 * **Declared here rather than in `core:navigation`, and that is the rule rather than an exception.** That module's own
 * KDoc says a module may declare its own keys, because `entryProvider` in `app` is a *mapping* and not a registry; and
 * the reason to take it up here is what putting every route in the navigation module costs: that is how a settings
 * enum ends up on `feature:home`'s compile classpath. A crop screen is this feature's business and nobody else's.
 *
 * **A destination, unlike a settings section.** The sections are panes — two of them share the screen on a tablet — so
 * "which section" is `SettingsScreen`'s state. This is full-screen, transient, and back out of it means "not that
 * image", which is a back-stack entry's job.
 *
 * @property uri the picked image, as a string: a key is `@Serializable` and `android.net.Uri` is not. The screen parses
 *   it back. It is a **grant**, not a path — which is why the crop screen has to be reached promptly and why the
 *   repository copies the bytes it keeps rather than storing this.
 * @property target which slot is being filled, defaulting to the single image so the common call passes only a `uri`.
 */
@Serializable
data class WallpaperCropRoute(
    val uri: String,
    val target: CropTarget = CropTarget.SINGLE,
) : NavKey
