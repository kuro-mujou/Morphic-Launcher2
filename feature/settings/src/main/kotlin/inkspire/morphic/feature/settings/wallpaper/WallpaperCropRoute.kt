package inkspire.morphic.feature.settings.wallpaper

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Framing a picked image before it becomes the wallpaper — [WallpaperCropScreen]'s destination.
 *
 * **Declared here rather than in `core:navigation`, and that is the rule rather than an exception.** That module's own
 * KDoc says a module may declare its own keys, because `entryProvider` in `app` is a *mapping* and not a registry; and
 * the reason to take it up here is the one L1 demonstrates by counterexample — it put every route in its navigation
 * module, which is how an eleven-value settings enum ended up on `feature:home`'s compile classpath. A crop screen is
 * this feature's business and nobody else's.
 *
 * **A destination, unlike a settings section.** The sections are panes — two of them share the screen on a tablet — so
 * "which section" is `SettingsScreen`'s state. This is full-screen, transient, and back out of it means "not that
 * image", which is a back-stack entry's job.
 *
 * @property uri the picked image, as a string: a key is `@Serializable` and `android.net.Uri` is not. The screen parses
 *   it back. It is a **grant**, not a path — which is why the crop screen has to be reached promptly and why the
 *   repository copies the bytes it keeps rather than storing this.
 */
@Serializable
data class WallpaperCropRoute(val uri: String) : NavKey
