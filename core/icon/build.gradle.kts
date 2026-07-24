plugins {
    alias(libs.plugins.launcher.android.library)
    alias(libs.plugins.launcher.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.core.icon"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
}
