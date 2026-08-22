package inkspire.morphic.data.apps

import android.graphics.Bitmap
import inkspire.morphic.core.common.dispatcher.AppDispatchers
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * One of an app's own shortcuts — the entries a user sees when long-pressing that app on a stock launcher
 * ("New message", "Scan a code"). Published by the app itself, statically in its manifest or dynamically at
 * runtime, and read through [android.content.pm.LauncherApps].
 *
 * **It is a handle, not a command**: [id] means something only to [packageName] under [userSerial], and only
 * for as long as the app keeps publishing it. So a stored one goes stale silently — which is why this type is
 * read fresh each time a menu opens and thrown away when it closes, and why it lives in `data:apps` rather
 * than `core:model` (it carries a rasterized [Bitmap], too).
 *
 * **One thing does store a handle, deliberately.** A gesture assigned to a shortcut has to outlive the menu it
 * was picked from, so `GestureAction.LaunchShortcut` keeps the three fields that identify one and accepts the
 * staleness: an app update that withdraws a shortcut leaves an assignment pointing at nothing, and firing it
 * does nothing. It stores those fields rather than this type, since a `Bitmap` has no business in a settings
 * blob.
 *
 * @property userSerial the profile the shortcut belongs to, carried for the same reason [ComponentKey] carries
 *   one: starting a work-profile app's shortcut under the personal user silently does nothing.
 * @property icon the shortcut's own icon, already rasterized at the device's density, or null when the platform
 *   would not give us one. Deliberately *not* run through `core:icon` — a shortcut icon is the app's badge-and-glyph
 *   composition and is not one of our layer stacks to restyle.
 */
data class AppShortcut(
    val id: String,
    val packageName: String,
    val userSerial: Long,
    val label: String,
    val icon: Bitmap?,
)

/**
 * Reads and starts an app's own shortcuts — what the item context menu shows before its launcher actions.
 *
 * **Both halves are one capability, which is why they are one type** even though the codebase otherwise splits
 * reads from commands ([AppRepository] vs [AppLauncher]). The split exists because the app *cache* is a store with
 * a lifetime, and a `launch()` hung off it would blur that; a shortcut list is neither cached nor observable — it
 * is a live platform question whose answer is only valid for the moment it was asked, and the [AppShortcut.id] you
 * start is meaningless except as an element of the list you just read. Separating them would put the two ends of
 * one handle in two types.
 *
 * **The launcher must be the active home app to see any of this.** `LauncherApps.hasShortcutHostPermission()` is
 * false otherwise, and both calls below then do nothing — an empty list and a no-op start. That is a normal state
 * (the APK is installed but not selected as home), not an error, so it is reported as emptiness rather than thrown.
 */
interface AppShortcuts {

    /**
     * The enabled shortcuts [component]'s package publishes, in the order the app ranked them. Empty when the app
     * publishes none, when it no longer resolves, or when this launcher is not the active home app.
     */
    suspend fun shortcuts(component: ComponentKey): List<AppShortcut>

    /**
     * Every shortcut published on the device, for a screen that offers all of them at once.
     *
     * Grouping by [AppShortcut.packageName] is the caller's, since only it knows how it wants them shown.
     */
    suspend fun allShortcuts(): List<AppShortcut>

    /**
     * Starts the shortcut [id] of [packageName] under [userSerial].
     *
     * **The three fields rather than an [AppShortcut]**, because a stored assignment has only these — it never
     * had the label or the icon, and re-reading the whole list to rebuild one would be a platform query to
     * reconstruct data the call does not use.
     *
     * Fire-and-forget like [AppLauncher.launch], and for the same reason: the shortcut may have been withdrawn
     * between being picked and being fired, and that must not surface as a crash.
     */
    fun start(id: String, packageName: String, userSerial: Long)

    /** [start] for one just read from [shortcuts], which is every caller that has the whole thing to hand. */
    fun start(shortcut: AppShortcut) = start(shortcut.id, shortcut.packageName, shortcut.userSerial)
}

/**
 * Default [AppShortcuts] over [LauncherAppsWrapper].
 *
 * The dispatcher hop is here rather than in the wrapper, matching [AppRepositoryImpl]: the wrapper stays a thin,
 * synchronous platform pass-through and each caller layer decides where its work runs. Reading shortcuts loads and
 * rasterizes an icon per entry, so it is emphatically not main-thread work.
 *
 * `internal` so only Koin constructs it — consumers depend on the [AppShortcuts] interface.
 */
internal class DefaultAppShortcuts(
    private val launcherApps: LauncherAppsWrapper,
    private val dispatchers: AppDispatchers,
) : AppShortcuts {

    override suspend fun shortcuts(component: ComponentKey): List<AppShortcut> =
        withContext(dispatchers.io) {
            try {
                launcherApps.shortcuts(component)
            } catch (t: Throwable) {
                // Not being the home app is the expected miss and the wrapper already answers it with an empty
                // list; anything reaching here is a genuine platform failure, and an empty menu stage beats a crash.
                Timber.w(t, "Failed to read shortcuts for %s", component.flatten())
                emptyList()
            }
        }

    override suspend fun allShortcuts(): List<AppShortcut> =
        withContext(dispatchers.io) {
            try {
                launcherApps.allShortcuts()
            } catch (t: Throwable) {
                Timber.w(t, "Failed to read all shortcuts")
                emptyList()
            }
        }

    override fun start(id: String, packageName: String, userSerial: Long) {
        try {
            launcherApps.startShortcut(id = id, packageName = packageName, userSerial = userSerial)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to start shortcut %s of %s", id, packageName)
        }
    }
}
