package inkspire.morphic.data.apps.mapper

import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.Process
import inkspire.morphic.core.database.entity.AppInfoEntity
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * Conversions between the platform's [LauncherActivityInfo], the [AppInfo] domain model, and the cached
 * [AppInfoEntity]. Kept as free functions (not a class) — they are pure and stateless; the profile serial a
 * [ComponentKey] needs is passed in rather than resolved here, so this file never touches a system service.
 */

/**
 * Builds an [AppInfo] from a launcher activity. [userSerial] identifies the activity's profile (obtained via
 * `LauncherAppsWrapper.serialForUser`) so work-profile and personal copies of the same package stay distinct.
 *
 * Install times are intentionally left at `0` ("unknown", per [AppInfo]'s contract): no consumer needs them
 * yet, and sourcing `lastUpdateTime` correctly across profiles needs `PackageManager` work we defer until a
 * sort-by-date feature asks for it.
 */
fun LauncherActivityInfo.toAppInfo(userSerial: Long): AppInfo {
    val flags = applicationInfo.flags
    return AppInfo(
        componentKey = ComponentKey(
            packageName = componentName.packageName,
            className = componentName.className,
            userSerial = userSerial,
        ),
        label = label.toString(),
        // Heuristic: anything not in the launcher's own profile is treated as a work profile. Precise
        // managed-profile detection needs UserManager per-user access; deferred until it actually matters.
        isWorkProfile = user != Process.myUserHandle(),
        isSuspended = (flags and ApplicationInfo.FLAG_SUSPENDED) != 0,
        isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        category = applicationInfo.category,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
    )
}

/** Maps a domain [AppInfo] to its cache row. */
fun AppInfo.toEntity(): AppInfoEntity = AppInfoEntity(
    component = componentKey,
    label = label,
    isWorkProfile = isWorkProfile,
    isSuspended = isSuspended,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    category = category,
    isSystem = isSystem,
)

/** Maps a cache row back to the domain [AppInfo]. */
fun AppInfoEntity.toAppInfo(): AppInfo = AppInfo(
    componentKey = component,
    label = label,
    isWorkProfile = isWorkProfile,
    isSuspended = isSuspended,
    isSystem = isSystem,
    category = category,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
)
