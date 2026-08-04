package inkspire.morphic.feature.settings.wallpaper

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Taking a picture of the wallpaper — [WallpaperCaptureScreen]'s destination.
 *
 * Argument-free, unlike [WallpaperCropRoute]: a capture has no input, since the image does not exist until the user
 * takes it.
 *
 * A destination for the same reason the crop screen is one, and one more of its own: this screen **hides the system
 * bars and makes the window show the wallpaper**, which is a window-wide change that has to be undone when it goes
 * away. A back-stack entry has a lifetime; a pane inside another screen does not.
 */
@Serializable
data object WallpaperCaptureRoute : NavKey
