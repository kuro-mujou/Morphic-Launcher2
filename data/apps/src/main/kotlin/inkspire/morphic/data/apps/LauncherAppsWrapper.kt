package inkspire.morphic.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    /**
     * The profile [serial] names, or null when that profile no longer exists — the inverse of [serialForUser], and
     * the same lookup every call on this wrapper does internally before touching the platform.
     *
     * Public because one command is **not** a [LauncherApps] call and so has to do it itself: uninstalling is an
     * `Intent`, and the profile has to ride along on it ([AppUninstaller]). Everything else here resolves the
     * profile inside the method that needs it, which is why this had no reason to exist until now.
     */
    fun userFor(serial: Long): UserHandle?

    /**
     * Launches [component]'s main activity in the profile named by its `userSerial`.
     *
     * The profile matters: resolving the [android.os.UserHandle] from `userSerial` (the same lookup [loadIcon]
     * does) is what makes a work-profile app launch under the *work* user rather than the personal one. May
     * throw platform exceptions (e.g. the activity was uninstalled between listing and tapping); the caller
     * layer ([AppLauncher]) is responsible for guarding the call — this stays a thin platform pass-through.
     */
    fun launch(component: ComponentKey)

    /**
     * Opens the system's app-details screen for [component], in its own profile. The launcher-specific form of
     * this, so a work-profile icon opens the *work* app's details rather than the personal copy's — see
     * [AppInfoOpener] for why that distinction is the whole reason this is here rather than an intent.
     */
    fun showAppDetails(component: ComponentKey)

    /**
     * The enabled shortcuts [component]'s package publishes, in the app's own rank order, with icons rasterized
     * at the device density.
     *
     * **Empty is the answer whenever we may not ask**: the platform grants shortcut access only to the *active*
     * home app, so an installed-but-not-selected launcher gets nothing. That is a state, not a failure, so it is
     * answered here rather than thrown — which also keeps the caller from having to know the rule. Blocking, like
     * every other read on this wrapper; [AppShortcuts] owns the dispatcher hop.
     */
    fun shortcuts(component: ComponentKey): List<AppShortcut>

    /**
     * Starts [shortcut] in the profile named by its [AppShortcut.userSerial]. May throw for the usual platform
     * reasons (withdrawn since it was listed, profile locked); [AppShortcuts] owns the guard.
     */
    fun startShortcut(shortcut: AppShortcut)

    /**
     * Emits the **packages touched** every time the set of installed apps may have changed — added, removed,
     * changed, or made (un)available with a profile or an SD card.
     *
     * **Which packages, never what happened to them.** "What is installed now?" has exactly one answer and it is
     * [queryActivities]; reporting adds and removes here would be a second source of truth about it that could
     * disagree. But *which* packages moved is a different question, and one that re-reading cannot answer: an app
     * that updates its icon keeps the same component, so nothing about the new state says its baked bitmap is
     * stale. So consumers differ — [AppRepository] ignores the payload and re-reads, `BakedIconInvalidator` uses
     * it to drop exactly the right bitmaps.
     *
     * **Not conflated**, for that second consumer: collapsing two events would lose the first one's package names.
     * A consumer that only needs "something changed" conflates on its own side, which [AppRepository] does.
     *
     * Registers a [LauncherApps.Callback] while collected and unregisters when the collector stops, so nothing is
     * listening when nothing is watching.
     */
    fun packageChanges(): Flow<Set<String>>
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

    /**
     * Held for one number: the density to rasterize shortcut icons at. Retaining the application context's
     * [android.content.res.Resources] keeps the "resolved services only, never the Context" rule of this class —
     * it is not a view context and cannot leak an activity — while still reading the *current* density rather
     * than one frozen at construction.
     */
    private val resources = context.applicationContext.resources

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

    override fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    override fun launch(component: ComponentKey) {
        // Resolve the profile the same way loadIcon does — a serial with no live user means the profile was
        // removed, so there is nothing to launch.
        val user = userManager.getUserForSerialNumber(component.userSerial) ?: return
        val componentName = ComponentName(component.packageName, component.className)
        launcherApps.startMainActivity(componentName, user, /* sourceBounds = */ null, /* opts = */ null)
    }

    override fun showAppDetails(component: ComponentKey) {
        val user = userManager.getUserForSerialNumber(component.userSerial) ?: return
        val componentName = ComponentName(component.packageName, component.className)
        launcherApps.startAppDetailsActivity(componentName, user, /* sourceBounds = */ null, /* opts = */ null)
    }

    override fun shortcuts(component: ComponentKey): List<AppShortcut> {
        // The permission check first: without it `getShortcuts` throws a SecurityException on every call, and
        // "this launcher is not the active home app" is the ordinary reason for that rather than a fault.
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val user = userManager.getUserForSerialNumber(component.userSerial) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            // All three kinds a launcher shows: the app's manifest shortcuts, the ones it publishes at runtime,
            // and any the user has pinned. L1 queried the same three.
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            .setPackage(component.packageName)
        val shortcuts = launcherApps.getShortcuts(query, user).orEmpty()
        val densityDpi = resources.displayMetrics.densityDpi
        return shortcuts
            .filter { it.isEnabled }
            // The app's own ranking, which is the order it means them to be shown in.
            .sortedBy { it.rank }
            .map { info ->
                AppShortcut(
                    id = info.id,
                    packageName = info.`package`,
                    userSerial = component.userSerial,
                    // `longLabel` where the app bothered to supply one — a menu row has room for it, and it is
                    // the more descriptive of the pair ("Compose a message" over "Compose").
                    label = (info.longLabel ?: info.shortLabel)?.toString().orEmpty(),
                    icon = runCatching {
                        launcherApps.getShortcutIconDrawable(info, densityDpi)?.toBitmap()
                    }.getOrNull(),
                )
            }
    }

    override fun packageChanges(): Flow<Set<String>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = signal(packageName)
            override fun onPackageRemoved(packageName: String, user: UserHandle) = signal(packageName)
            override fun onPackageChanged(packageName: String, user: UserHandle) = signal(packageName)

            // Whole *sets* going in and out at once: a work profile unlocking, external storage mounting.
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = signal(*packageNames)

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = signal(*packageNames)

            private fun signal(vararg packageNames: String) {
                trySend(packageNames.toSet()).getOrNull()
            }
        }
        // **The handler is not optional here, and leaving it out is silent.** `registerCallback(callback)` builds a
        // `Handler()` for the *calling thread*, which throws when that thread has no Looper — and this flow is
        // collected on `ApplicationScope`, i.e. `Dispatchers.Default`, which never has one. The exception dies in
        // the scope's `CoroutineExceptionHandler`, so the effect is not a crash: it is a listener that was never
        // registered, and an uninstall that updates nothing. Naming the main Looper makes the thread this runs on
        // a decision rather than an accident of where it happened to be collected.
        //
        // Delivering on the main thread costs nothing because the callback does one thing — hand the package names
        // to the channel. Everything real happens on the collector's own dispatcher.
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    override fun startShortcut(shortcut: AppShortcut) {
        val user = userManager.getUserForSerialNumber(shortcut.userSerial) ?: return
        launcherApps.startShortcut(
            shortcut.packageName,
            shortcut.id,
            /* sourceBounds = */ null,
            /* startActivityOptions = */ null,
            user,
        )
    }
}
