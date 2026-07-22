package inkspire.morphic.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Shared Jetpack Compose setup used by both the application- and library-Compose plugins: enables the
 * Compose build feature, points the Compose compiler at the root `compose_stability.conf`, and adds the
 * Compose BOM (main + androidTest) plus the debug UI-tooling artifact.
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension,
) {
    commonExtension.buildFeatures.compose = true

    extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("compose_stability.conf"),
        )
    }

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
