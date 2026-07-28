plugins {
    alias(libs.plugins.launcher.android.feature)
}

android {
    namespace = "inkspire.morphic.feature.home"
}

dependencies {
    // The feature convention plugin already wires core:model/common/designsystem, lifecycle-viewmodel,
    // koin-compose, coroutines, and the Compose artifacts. Home additionally reads/writes through the data layer:
    implementation(projects.data.apps)   // AppRepository (apps) + AppLauncher (launch on tap)
    implementation(projects.data.layout) // LayoutRepository + FreeGridPlanner (coordinate placement engine)

    testImplementation(libs.junit)
}
