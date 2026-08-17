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

    // The blur, shared with `data:wallpaper` — see `BitmapBlur` for why neither module owns it.
    implementation(projects.core.graphics)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    // Diagnostics for the size measurement, which is the one thing here that cannot be seen from its output: a
    // wrong scale looks like a wrong icon, and every cause of one looks like every other. Debug builds only —
    // `LauncherApplication` plants the tree.
    implementation(libs.timber)

    // The one thing in this module that is pure arithmetic — and the one thing two renderers have to agree on.
    testImplementation(libs.junit)
}
