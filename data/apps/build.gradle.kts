plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.apps"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.icon)

    // `Drawable.toBitmap()`, for rasterizing a shortcut's icon — the platform hands one back as a Drawable and a
    // menu row draws a bitmap. Added here as the code that needs it lands, per the module rule.
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
