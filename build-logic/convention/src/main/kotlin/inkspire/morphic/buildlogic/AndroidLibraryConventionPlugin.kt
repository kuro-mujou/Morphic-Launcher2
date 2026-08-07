package inkspire.morphic.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin `launcher.android.library`: a standard Android library module (`com.android.library`)
 * with the shared Kotlin/Android config from [configureKotlinAndroid] and consumer ProGuard rules. The base
 * for every non-Compose `core:*` / `data:*` module.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)

                // **No `consumerProguardFiles`.** It was declared here from the Android Studio template and
                // nothing ever created the files, so it went unnoticed until the first release build — which failed
                // on every library at once ("Supplied consumer proguard configuration does not exist").
                //
                // Ten empty files would have made it build again and said nothing true. Consumer rules exist so a
                // **published** library can tell an unknown consumer what to keep; every module here is consumed by
                // `:app` alone, and `:app` owns `proguard-rules.pro`, which is where any keep this launcher needs
                // belongs — one file a reader can check against the whole app rather than ten they have to collect.
                // If a module is ever published on its own, that is when it earns one.
            }
        }
    }
}
