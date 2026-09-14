package inkspire.morphic.data.apps

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Whether this app is the device's home app, and how to ask to become one.
 *
 * **A command with its own honest type**, beside [AppLauncher] — not a method bolted onto a repository, and not a
 * `RoleManager` call inside a composable. What it wraps is two platform facts that are each answered differently on
 * either side of API 29, and neither of them is a setting: nothing here is stored, and the answer changes because of
 * something the *user did in the system's own UI*.
 *
 * **An app cannot make itself the home app.** Only the user grants the role, so every caller can at most put the
 * request in front of them: the settings index as a row, and the launcher shell as a dialog when it comes back without
 * the role.
 */
interface DefaultLauncherRole {

    /**
     * Whether a press of the home button opens this launcher.
     *
     * **Resolved from the HOME intent rather than asked of [RoleManager]**, which is the answer on every supported API —
     * `isRoleHeld` exists only from 29, and minSdk here is 26. It is also the more direct question: what this asks is
     * exactly what the home button does.
     *
     * With no default chosen at all the system resolves its own chooser activity, which is not this package, so the
     * answer is false — correct, and the case that matters most, since it is the state a fresh install is in.
     *
     * **It depends on a `<queries>` declaration, and without one it does not fail — it lies.** Package visibility
     * filters this resolution down to HOME activities this app may see, and a launcher is one of the few kinds of app
     * that does not declare MAIN/LAUNCHER, so the launcher query beside it is no help: the only visible answer left is
     * *our own* HOME activity, and a launcher that is not the default reads back that it is. The HOME entry in this
     * module's manifest is what makes the question answerable; delete it and this function silently inverts.
     *
     * A binder call into the package manager: keep it off the main thread.
     */
    fun isDefault(): Boolean

    /**
     * The intent that asks the user to make this the home app, or null where the device offers no way to.
     *
     * **Two mechanisms, and the newer one is a dialog rather than a screen.** From API 29 the role request puts a
     * system chooser directly in front of the user, which is one tap and cannot be lost; below it, the only route is
     * the system's own "Default home app" settings screen, which the user has to complete and navigate back from.
     * Neither reports its outcome to us, which is why doneness is re-derived rather than remembered — see [isDefault].
     *
     * **The role intent must be started *for a result*, and this type cannot enforce that.** `RequestRoleActivity`
     * reads `getCallingPackage()` to learn who is asking, and the platform fills that in only for an activity started
     * for a result — started plainly it sees null and finishes before drawing anything, so the control appears inert
     * while the system log quietly says "Package name cannot be null or empty". Callers launch both branches through
     * an `ActivityResultContracts.StartActivityForResult` for that reason and ignore the result.
     *
     * Null is a real answer, not a guard: a device may have the role unavailable, and some heavily modified builds
     * ship without the home-settings screen at all. A caller's business is to not offer a request that would open
     * nothing, which is the standing rule about controls that do nothing.
     */
    fun requestIntent(): Intent?
}

/** Default [DefaultLauncherRole]. `internal` so only Koin constructs it — consumers depend on the interface. */
internal class PlatformDefaultLauncherRole(private val context: Context) : DefaultLauncherRole {

    override fun isDefault(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(home, PackageManager.ResolveInfoFlags.of(MatchDefaultOnly))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    override fun requestIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        return settings.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    private companion object {
        /**
         * `PackageManager.MATCH_DEFAULT_ONLY`, restated because the typed-flags overload takes a `Long` and the
         * constant it needs is the same number the deprecated overload takes as an `Int`.
         */
        const val MatchDefaultOnly = PackageManager.MATCH_DEFAULT_ONLY.toLong()
    }
}
