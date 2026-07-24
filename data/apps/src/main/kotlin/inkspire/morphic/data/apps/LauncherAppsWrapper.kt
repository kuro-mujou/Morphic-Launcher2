package inkspire.morphic.data.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import inkspire.morphic.core.model.ComponentKey

/**
 * The single boundary between the app layer and Android's [LauncherApps] system service.
 *
 * Everything above this wrapper deals in [ComponentKey]s and plain data; only this type touches the
 * platform. It is an interface so the repository (and its tests) can depend on the abstraction and
 * substitute a fake — the real [DefaultLauncherAppsWrapper] wraps services that cannot be constructed in a
 * unit test. (Same rationale as `AppDispatchers` in `core:common`.)
 */
interface LauncherAppsWrapper {

    /**
     * Every launchable activity across all the current user's profiles (personal plus any managed/work
     * profile). Each [LauncherActivityInfo] is the platform's own record for one launcher entry; mapping it
     * to the app model happens one layer up, so this stays a thin platform read.
     */
    fun queryActivities(): List<LauncherActivityInfo>

    /**
     * Loads the raw, unstyled icon [Drawable] for [component] at [densityDpi] (pass `0` for the device's
     * default density). Returns `null` when the component no longer resolves — e.g. the app was uninstalled
     * or its profile removed between listing and loading. This is the raw drawable the `core:icon` parser
     * consumes; no shaping or styling is applied here.
     */
    fun loadIcon(component: ComponentKey, densityDpi: Int = 0): Drawable?

    /**
     * The stable serial number of [user] (from [UserManager]). Used to fill a [ComponentKey]'s `userSerial`
     * so an app is identified per profile, and it is the inverse of the lookup [loadIcon] does to turn a
     * serial back into the profile the platform APIs need.
     */
    fun serialForUser(user: UserHandle): Long
}

/**
 * Production [LauncherAppsWrapper] backed by the real [LauncherApps] and [UserManager] services.
 *
 * Profiles go through [UserManager] so work-profile apps are included, and a [ComponentKey]'s `userSerial`
 * can be turned back into the [android.os.UserHandle] the platform APIs require. Only the resolved services
 * are retained, never the [Context], so this is safe to hold as an application-scoped singleton.
 */
class DefaultLauncherAppsWrapper(context: Context) : LauncherAppsWrapper {

    private val launcherApps: LauncherApps =
        requireNotNull(context.getSystemService(LauncherApps::class.java)) {
            "LauncherApps service unavailable"
        }
    private val userManager: UserManager =
        requireNotNull(context.getSystemService(UserManager::class.java)) {
            "UserManager service unavailable"
        }

    override fun queryActivities(): List<LauncherActivityInfo> =
        userManager.userProfiles.flatMap { profile ->
            launcherApps.getActivityList(/* packageName = */ null, profile)
        }

    override fun loadIcon(component: ComponentKey, densityDpi: Int): Drawable? {
        val user = userManager.getUserForSerialNumber(component.userSerial) ?: return null
        return launcherApps.getActivityList(component.packageName, user)
            .firstOrNull { it.componentName.className == component.className }
            ?.getIcon(densityDpi)
    }

    override fun serialForUser(user: UserHandle): Long = userManager.getSerialNumberForUser(user)
}
