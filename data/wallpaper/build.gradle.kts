plugins {
    alias(libs.plugins.launcher.android.library)
    // Its own state is stored as a JSON blob under one DataStore key, exactly as a settings slice is.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.data.wallpaper"
}

dependencies {
    implementation(projects.core.common) // AppDispatchers + Koin + coroutines (api-exposed)

    // `Bitmap.scale`, for the one place a cropped image is resized to the screen. The same dependency `core:icon`
    // takes for its own bitmap work.
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber) // setting a system wallpaper can fail for reasons we do not control

    testImplementation(libs.junit)
}
