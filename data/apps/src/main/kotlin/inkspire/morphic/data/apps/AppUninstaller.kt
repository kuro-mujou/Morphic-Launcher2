package inkspire.morphic.data.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import inkspire.morphic.core.model.ComponentKey
import timber.log.Timber

/**
 * Asks the system to uninstall an app — the app-facing command for "get rid of this".
 *
 * The sibling of [AppLauncher], and separate from [AppRepository] for the same reason: the repository is
 * offline-first *read/refresh* access to the app cache, while this is a fire-and-forget *command* with a platform
 * side effect. That split is the rule this codebase keeps (L1 folded launch onto its repository; we don't).
 *
 * **It never uninstalls anything by itself, and cannot.** All it does is start the platform's uninstall activity,
 * which shows its own confirmation and does the work. So a mis-drag onto the launcher's Uninstall target costs the
 * user one dialog they dismiss — the destructive step is always theirs, and is never ours to skip. That also means
 * there is no result to report: the removal, if it happens, arrives later as an app-removed event and prunes the
 * layout like any other uninstall.
 *
 * **Not a `LayoutChange`.** Detaching an item from a grid and destroying a package are different acts on different
 * stores, which is why `LayoutChange` deliberately has no uninstall op — see its KDoc. The layout merely *reacts*.
 */
interface AppUninstaller {

    /**
     * Opens the system uninstall prompt for [component]'s package. Fire-and-forget: it never throws, so a drop
     * handler can call it directly. A package that no longer resolves, or a device policy that forbids uninstalling
     * it, is a no-op rather than a crash.
     */
    fun uninstall(component: ComponentKey)
}

/**
 * Default [AppUninstaller]: `Intent.ACTION_DELETE` on a `package:` URI, which is L1's `performTopAction` exactly.
 *
 * `FLAG_ACTIVITY_NEW_TASK` because the launcher is starting this from application context, not from an activity
 * result flow — we do not wait for an answer (see the interface).
 *
 * `internal` so only Koin constructs it — consumers depend on the [AppUninstaller] interface.
 */
internal class DefaultAppUninstaller(
    private val context: Context,
) : AppUninstaller {

    override fun uninstall(component: ComponentKey) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", component.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to open the uninstaller for %s", component.packageName)
        }
    }
}
