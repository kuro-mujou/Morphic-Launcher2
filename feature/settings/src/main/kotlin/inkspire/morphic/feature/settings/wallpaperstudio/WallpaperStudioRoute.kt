package inkspire.morphic.feature.settings.wallpaperstudio

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The full-screen wallpaper studio — a destination, not a settings pane, on `IconStudioRoute`'s precedent.
 *
 * **Argument-free, unlike the icon studio's route.** The icon studio opens *onto a specific app's* icon, so its route
 * carries which one; the wallpaper studio opens onto a fresh generative recipe with nothing to point at. When editing
 * a *saved* wallpaper becomes a thing, that is the argument this grows — a recipe id — the same way the icon studio's
 * route names its subject. For now there is one way in and it needs nothing.
 */
@Serializable
data object WallpaperStudioRoute : NavKey
