plugins {
    alias(libs.plugins.launcher.android.feature)
}

android {
    namespace = "inkspire.morphic.feature.onboarding"
}

dependencies {
    // The feature convention plugin already wires core:model/common/designsystem, lifecycle-viewmodel,
    // koin-compose, coroutines and the Compose artifacts. The gate additionally reads the onboarding flag, and a look
    // is applied as settings writes:
    implementation(projects.data.settings)
}
