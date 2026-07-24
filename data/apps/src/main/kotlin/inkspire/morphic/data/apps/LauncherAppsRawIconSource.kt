package inkspire.morphic.data.apps

import android.graphics.drawable.Drawable
import inkspire.morphic.core.icon.source.RawIconSource
import inkspire.morphic.core.model.ComponentKey

/**
 * [RawIconSource] backed by [LauncherAppsWrapper] — the platform icon read that feeds `core:icon`'s pipeline.
 * `internal` so only Koin (see `di/AppsModule`) constructs it; the icon layer depends on the [RawIconSource]
 * interface, not this class.
 */
internal class LauncherAppsRawIconSource(
    private val launcherApps: LauncherAppsWrapper,
) : RawIconSource {

    override fun loadIcon(component: ComponentKey, densityDpi: Int): Drawable? =
        launcherApps.loadIcon(component, densityDpi)
}
