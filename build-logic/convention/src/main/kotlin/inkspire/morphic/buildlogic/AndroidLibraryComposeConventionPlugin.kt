package inkspire.morphic.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin `launcher.android.library.compose`: adds Jetpack Compose to a library module — the
 * Compose compiler plugin plus the shared Compose setup in [configureAndroidCompose].
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType(LibraryExtension::class.java)
            configureAndroidCompose(extension)
        }
    }
}
