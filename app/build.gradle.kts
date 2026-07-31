plugins {
    alias(libs.plugins.launcher.android.application)
    alias(libs.plugins.launcher.android.application.compose)
    // `app` declares its own `@Serializable` nav key (the dev harness), so it needs the plugin itself.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.launcher"

    defaultConfig {
        applicationId = "inkspire.morphic.launcher"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.icon)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.data.apps)
    implementation(projects.data.layout)
    implementation(projects.core.navigation)
    implementation(projects.feature.shell)
    implementation(projects.feature.settings)
    // Still direct deps despite `feature:shell` composing them: the dev harness hosts both screens itself, and the
    // playgrounds reach into `core:designsystem` primitives the shell doesn't expose.
    implementation(projects.feature.home)
    implementation(projects.feature.apps)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Navigation 3: `core:navigation` exposes the runtime (NavKey/NavBackStack) via `api`; hosting the graph
    // additionally needs `NavDisplay`, which is the ui artifact. `app` is the only module that hosts one.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
