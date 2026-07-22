package com.morphic.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

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
