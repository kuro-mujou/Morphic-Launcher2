plugins {
    alias(libs.plugins.launcher.android.library)
    alias(libs.plugins.launcher.android.library.compose)
}

android {
    namespace = "inkspire.morphic.core.designsystem"
}

dependencies {
    implementation(projects.core.model)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
}
