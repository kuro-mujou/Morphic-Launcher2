package inkspire.morphic.data.apps.role

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/**
 * Which **packages** on this device can do each [AppRole], best first.
 *
 * Packages rather than components, and that is the whole of the split: a role intent resolves to the activity that
 * *does the job* — `ACTION_DIAL` lands on `DialtactsActivity` — which is frequently not the MAIN/LAUNCHER activity
 * the user's icon opens. So this answers "who", and `AppRepository.rolesFor` turns that into something placeable by
 * looking the package up in the app cache, which holds launcher entries and nothing else.
 *
 * `internal` because that pairing is the only sensible way to use it: a package name on its own cannot be pinned,
 * launched or drawn.
 *
 * Calls into the package manager are blocking binder calls — the caller keeps them off the main thread.
 */
internal interface AppRoleResolver {

    /** The packages answering [role], the user's own default first; empty when this device has no answer. */
    fun packagesFor(role: AppRole): List<String>
}

/** Default [AppRoleResolver]. `internal` so only Koin constructs it. */
internal class PlatformAppRoleResolver(private val context: Context) : AppRoleResolver {

    override fun packagesFor(role: AppRole): List<String> =
        intentsFor(role).flatMap { packagesFor(it) }.distinct()

    /**
     * The packages that answer [intent], **the user's own default first**.
     *
     * Two platform calls rather than one, because they answer different questions: `resolveActivity` with
     * [PackageManager.MATCH_DEFAULT_ONLY] gives the app the *user chose* for this job, and `queryIntentActivities`
     * gives everything that can do it in the platform's priority order. Only the first is what a dock wants — pinning
     * a browser the user has already replaced is the failure this exists to avoid — but it does not always answer:
     * with no default set the platform returns its own chooser, whose package is [ResolverPackage] and which is not
     * an app at all. So the chooser is dropped and the query stands behind it.
     *
     * **No `<queries>` declaration of its own, and that is a consequence rather than an oversight.** Package
     * visibility filters this down to packages `data:apps` can already see, which is every package with a
     * MAIN/LAUNCHER activity — and the caller keeps only packages present in the app cache, which is that same set.
     * A role held by an invisible package is therefore one with no icon to pin.
     */
    private fun packagesFor(intent: Intent): List<String> {
        val packageManager = context.packageManager
        val preferred = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()?.activityInfo?.packageName?.takeUnless { it == ResolverPackage }

        val all = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
        }.getOrElse { emptyList() }.map { it.activityInfo.packageName }

        return (listOfNotNull(preferred) + all).distinct()
    }

    /**
     * The intents [role] is tried with, **most specific first** — the same ladder AOSP's `default_workspace.xml`
     * climbs, and for the same reason: the `CATEGORY_APP_*` declaration is the app saying "I am the browser", while
     * the data intent below it catches an app that only ever declared what it can *open*.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun intentsFor(role: AppRole): List<Intent> = when (role) {
        AppRole.PHONE -> listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
        )

        AppRole.MESSAGING -> listOf(
            main(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
        )

        AppRole.BROWSER -> listOf(
            main(Intent.CATEGORY_APP_BROWSER),
            Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com/")),
        )

        AppRole.CAMERA -> listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )

        AppRole.STORE -> listOf(
            main(Intent.CATEGORY_APP_MARKET),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=android")),
        )

        AppRole.EMAIL -> listOf(
            main(Intent.CATEGORY_APP_EMAIL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
        )

        AppRole.GALLERY -> listOf(
            main(Intent.CATEGORY_APP_GALLERY),
            Intent(Intent.ACTION_VIEW).setType("image/*"),
        )

        // No category exists for the clock, so both rungs are alarm actions: the one that *shows* alarms before the
        // one that sets one, since a third-party app that can set an alarm is not therefore the clock.
        AppRole.CLOCK -> listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(AlarmClock.ACTION_SET_ALARM),
        )

        AppRole.CALENDAR -> listOf(
            main(Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time")),
        )

        AppRole.CALCULATOR -> listOf(main(Intent.CATEGORY_APP_CALCULATOR))

        AppRole.MAPS -> listOf(
            main(Intent.CATEGORY_APP_MAPS),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")),
        )

        AppRole.MUSIC -> listOf(
            main(Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_VIEW).setType("audio/*"),
        )

        AppRole.SETTINGS -> listOf(Intent(Settings.ACTION_SETTINGS))
    }

    /** `MAIN` + one of the platform's `CATEGORY_APP_*` declarations — how an app names the job it holds. */
    private fun main(category: String): Intent = Intent(Intent.ACTION_MAIN).addCategory(category)

    private companion object {
        /**
         * The package the platform's own disambiguation chooser lives in. `resolveActivity` returns it in place of an
         * app whenever the user has not picked a default, and pinning it would put the chooser on the home screen.
         */
        const val ResolverPackage = "android"
    }
}
