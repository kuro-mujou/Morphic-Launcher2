plugins {
    alias(libs.plugins.launcher.android.application)
    alias(libs.plugins.launcher.android.application.compose)
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
    implementation(projects.data.apps)

    implementation(libs.koin.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
