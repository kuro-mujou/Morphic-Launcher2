package inkspire.morphic.launcher.di

import android.content.Context
import inkspire.morphic.feature.settings.about.LicenseManifestSource
import inkspire.morphic.launcher.R
import org.koin.dsl.module

/**
 * Koin module for `:app` itself — **the bindings only the composition root can make.**
 *
 * There is exactly one today, and the module exists for it rather than in anticipation of more: the open-source
 * manifest is generated into *this* module's resources, so this is the only module in the build that can name it.
 * Every other binding in the graph belongs to the layer that owns the thing bound, and should stay there.
 */
val launcherModule = module {
    single<LicenseManifestSource> { RawLicenseManifest(get()) }
}

/**
 * The open-source manifest, read from the raw resource the AboutLibraries plugin generated at build time.
 *
 * **`R.raw.aboutlibraries` is named directly, and that is the whole reason this class is in `:app`.** The alternative
 * — `Resources.getIdentifier("aboutlibraries", …)` from inside `feature:settings` — compiles whether or not the
 * resource exists, and worse, a resource that nothing references *by symbol* is one `shrinkResources` is entitled to
 * strip from the release build. That failure appears only in the APK users install, as a licenses screen that is full
 * in debug and empty in release. Named here, a missing manifest does not compile.
 *
 * Reads the whole file into memory, which is the honest shape for what it is: ~80KB of JSON parsed once per visit to
 * a screen nobody opens twice. Streaming it would buy nothing and cost the ability to hand the parser a string.
 */
internal class RawLicenseManifest(private val context: Context) : LicenseManifestSource {
    override fun readJson(): String =
        context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
}
