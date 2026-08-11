plugins {
    alias(libs.plugins.launcher.android.library)
    alias(libs.plugins.launcher.android.library.compose)
    // No `kotlin.serialization`: the icon *recipe* (the layer set) lives in `core:model` and is persisted by the
    // modules that store it, so nothing here is serialized. This module only renders.
}

android {
    namespace = "inkspire.morphic.core.icon"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    // The one thing in this module that is pure arithmetic — and the one thing two renderers have to agree on.
    testImplementation(libs.junit)
}
