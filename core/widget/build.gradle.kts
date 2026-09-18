plugins {
    alias(libs.plugins.launcher.android.library)
    alias(libs.plugins.launcher.android.library.compose)
}

android {
    namespace = "inkspire.morphic.core.widget"

    // `WidgetRenderHarness` draws recipes on a device and saves what it drew, for a person to look at.
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.widgetscript)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    // The placement arithmetic, which the editor's drag will have to invert and so must be exactly right.
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // The Compose test rule brings espresso 3.5, which reaches for an `InputManager` method Android 16 removed and
    // fails before the first frame. 3.7 does not.
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
