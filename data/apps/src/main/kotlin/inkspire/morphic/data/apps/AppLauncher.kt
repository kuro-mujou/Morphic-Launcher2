package inkspire.morphic.data.apps

import inkspire.morphic.core.model.ComponentKey
import timber.log.Timber

/**
 * Launches installed apps — the app-facing command for "open this app".
 *
 * Kept separate from [AppRepository] on purpose: the repository is offline-first *read/refresh* access to the
 * app cache, whereas launching is a fire-and-forget *command* with a platform side effect. Folding it into the
 * repository would blur that read/command split (as it was in Launcher 1), so consumers that only need to open
 * an app depend on this one-method abstraction instead.
 */
interface AppLauncher {

    /**
     * Opens [component]'s main activity. Fire-and-forget: it never throws, so a UI tap handler can call it
     * directly. A component that no longer resolves (uninstalled, or its profile removed since it was placed)
     * is a no-op rather than a crash.
     */
    fun launch(component: ComponentKey)
}

/**
 * Default [AppLauncher] backed by [LauncherAppsWrapper]. The guard lives here rather than in the wrapper so the
 * wrapper stays a thin platform pass-through and this layer owns the fire-and-forget contract: the launch can
 * fail for reasons entirely outside our control (the app was uninstalled between placement and tap, or the
 * profile is locked), and none of those should surface as a crash from tapping a home icon.
 *
 * `internal` so only Koin constructs it — consumers depend on the [AppLauncher] interface.
 */
internal class DefaultAppLauncher(
    private val launcherApps: LauncherAppsWrapper,
) : AppLauncher {

    override fun launch(component: ComponentKey) {
        try {
            launcherApps.launch(component)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to launch %s", component.flatten())
        }
    }
}
