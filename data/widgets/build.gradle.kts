plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.widgets"

    // `WidgetDataDeviceTest` checks against real broadcasts that a widget wakes only as often as it reads.
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.widgetscript)
    implementation(projects.core.common) // Koin (api-exposed)

    // `ContextCompat.registerReceiver`, for the battery and clock broadcasts.
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
}
