package inkspire.morphic.feature.settings.about

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One permission the installed package asks for, and whether the system has granted it.
 *
 * @property name the constant as Android reports it, in full. Shown in full deliberately: a shortened or prettified
 *   permission is one the reader cannot check against the manifest, and checking is the point of this screen.
 * @property note what the launcher uses it for, or null for a permission this build gained without this screen being
 *   told about it. Null renders as a bare row rather than being hidden — a permission nobody wrote a line for must
 *   still appear, and must look unexplained, or the list becomes a curated one.
 * @property granted null where the notion does not apply — an install-time or signature permission is never "granted"
 *   in the sense a user would recognize.
 */
internal data class AppPermission(
    val name: String,
    val note: String?,
    val granted: Boolean?,
)

/**
 * What the About section knows about the installed package.
 *
 * **Read from [PackageManager] rather than from `BuildConfig`.** Every value here therefore describes *the APK on this
 * device* — the one the reader is holding — rather than what some build file said at compile time. That is the
 * difference between a screen that reports and one that asserts, and it is the reason this section exists.
 *
 * **One snapshot serving two panes.** About shows the version; Permissions shows the list. They are one
 * `getPackageInfo` call and one ViewModel rather than two, because they are not two questions — they are two parts of
 * the same answer, taken at the same instant.
 *
 * @property label the app's own name, as Android shows it everywhere else. Read rather than written, so the About
 *   screen cannot end up calling the app something the home-app chooser does not.
 * @property debuggable whether this is a debug build. Drawn only when true — a release build is the ordinary case and
 *   has nothing to announce, while a debug one must not be mistakable for it.
 * @property permissions every permission the package declares, in the order Android reports them.
 */
internal data class AboutState(
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val debuggable: Boolean = false,
    val permissions: List<AppPermission> = emptyList(),
)

/**
 * Screen-level state holder for **About** and its permissions pane: what this build is, and what it asks the device
 * for.
 *
 * The one ViewModel in this surface with no repository behind it — there is no store, because nothing on either screen
 * is a setting. What it reads is the package manager's record of the running app, which is a snapshot rather than a
 * stream: it cannot change while the screen is open without the process being replaced first. So this is a
 * `MutableStateFlow` filled once, not a `stateIn` over a repository flow like every other section.
 *
 * @param context the application context from `androidContext()`, which is what Koin hands a `get()` here. Holding it
 *   past the screen would be a leak if it were an Activity; it is not.
 */
internal class AboutViewModel(private val context: Context) : ViewModel() {

    private val mutableState = MutableStateFlow(AboutState())
    val state: StateFlow<AboutState> = mutableState.asStateFlow()

    init {
        // Off the main thread: `getPackageInfo` with GET_PERMISSIONS parses the APK's manifest.
        viewModelScope.launch { mutableState.value = withContext(Dispatchers.Default) { readPackage() } }
    }

    private fun readPackage(): AboutState {
        val packages = context.packageManager
        val name = context.packageName
        val info = packages.packageInfo(name, PackageManager.GET_PERMISSIONS)
        val application = info.applicationInfo ?: context.applicationInfo

        return AboutState(
            label = packages.getApplicationLabel(application).toString(),
            versionName = info.versionName.orEmpty(),
            versionCode = info.versionCodeCompat(),
            debuggable = application.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            permissions = info.permissions(),
        )
    }
}

/**
 * What each permission this launcher declares is actually for.
 *
 * **Keyed by the constant, and a miss renders as no note rather than as no row** — see [AppPermission.note]. That is
 * the safety property of this map: it can only ever be *incomplete*, never wrong about what is declared, because the
 * list itself comes from the package manager. Two entries were added *after* seeing the screen on a device, which is
 * the mechanism working: both are permissions the platform attaches without anyone writing them down, and both
 * appeared unexplained until they were answered here.
 *
 * `READ_MEDIA_VISUAL_USER_SELECTED` is Android 14's partial photo access, granted by the system alongside
 * `READ_MEDIA_IMAGES`; `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is a signature permission `androidx.core` names
 * after the host package so its own dynamically-registered receivers can be non-exported. Neither is asked for by
 * this codebase, and both are the kind of entry a suspicious reader stops on.
 */
private val PermissionNotes = mapOf(
    android.Manifest.permission.SET_WALLPAPER to "Applies a wallpaper you have chosen. Used for nothing else.",
    android.Manifest.permission.READ_MEDIA_IMAGES to "Reads only the picture you pick as a wallpaper.",
    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to
        "Added by Android beside the one above, so you can share a few photos instead of your whole library.",
    android.Manifest.permission.READ_EXTERNAL_STORAGE to "The same, on Android 12 and older.",
    android.Manifest.permission.REQUEST_DELETE_PACKAGES to
        "Asks Android to uninstall an app you drag off the home screen. Android shows its own confirmation.",
)

/** @see PermissionNotes — the suffix of the signature permission androidx.core names after the host package. */
private const val DynamicReceiverPermission = "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

private const val DynamicReceiverNote =
    "Declared by the AndroidX libraries so their own internal broadcasts cannot be received by other apps. " +
        "It grants nothing outside this app."

/** The declared permissions, paired with their grant state where that means anything. */
private fun PackageInfo.permissions(): List<AppPermission> {
    val names = requestedPermissions ?: return emptyList()
    val flags = requestedPermissionsFlags
    return names.mapIndexed { index, name ->
        AppPermission(
            name = name,
            note = PermissionNotes[name] ?: DynamicReceiverNote.takeIf { name.endsWith(DynamicReceiverPermission) },
            // The flags array is parallel to the names array, but the bound check stays: both come from one Binder
            // reply, and a mismatch would be an out-of-range crash on a screen whose whole job is to report calmly.
            granted = flags?.getOrNull(index)?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 },
        )
    }
}

/**
 * `getPackageInfo` across the API 33 flag split.
 *
 * The typed-flags overload is the only one that is not deprecated from Tiramisu on, and the deprecated one is the only
 * one that exists below it. minSdk here is 26, so both branches ship.
 */
private fun PackageManager.packageInfo(name: String, flags: Int): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(name, flags)
    }

/** The version code, widened to 64 bits from API 28 and 32 bits below it. */
private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
