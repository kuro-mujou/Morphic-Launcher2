plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.widgets"
}

dependencies {
    implementation(projects.core.common) // AppDispatchers + Koin (api-exposed)

    // `Drawable.toBitmap()`, for rasterizing a provider's preview — the platform hands one back as a Drawable and
    // the picker draws a bitmap. The same dependency `data:apps` takes for shortcut icons.
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber) // another app's preview drawable can fail to inflate, and one must not empty the list

    testImplementation(libs.junit)
}
