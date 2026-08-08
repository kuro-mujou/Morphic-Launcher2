package inkspire.morphic.data.apps

import inkspire.morphic.core.model.ComponentKey
import timber.log.Timber

/**
 * Opens the system's "App info" screen for an installed app — the third app-facing command, beside [AppLauncher]
 * and [AppUninstaller], and separate from both for the same reason: it is a fire-and-forget side effect on the
 * platform, not access to a store.
 *
 * Like [AppUninstaller] it hands the user to a system screen and does nothing itself; everything that can be done
 * from there (force stop, permissions, storage) is the platform's, and none of it reports back to us.
 */
interface AppInfoOpener {

    /**
     * Opens the app-details screen for [component]. Fire-and-forget: a component that no longer resolves, or a
     * profile that has gone away, is a no-op rather than a crash.
     */
    fun openAppInfo(component: ComponentKey)
}

/**
 * Default [AppInfoOpener], via [LauncherAppsWrapper.showAppDetails].
 *
 * **`LauncherApps.startAppDetailsActivity`, not L1's `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent.** The
 * intent form names a *package*, so it can only ever open the personal profile's copy; a work-profile app's icon
 * would open the details of the personal one — or nothing, when only the work profile has it installed. The
 * launcher API takes the component **and the user**, which is the same per-profile correction [AppLauncher] makes
 * to L1's hardcoded `Process.myUserHandle()`. It is also the API the platform intends a home app to use.
 *
 * `internal` so only Koin constructs it — consumers depend on the [AppInfoOpener] interface.
 */
internal class DefaultAppInfoOpener(
    private val launcherApps: LauncherAppsWrapper,
) : AppInfoOpener {

    override fun openAppInfo(component: ComponentKey) {
        try {
            launcherApps.showAppDetails(component)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to open app info for %s", component.flatten())
        }
    }
}
