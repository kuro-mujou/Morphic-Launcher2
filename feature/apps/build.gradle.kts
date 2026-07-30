plugins {
    alias(libs.plugins.launcher.android.feature)
}

android {
    namespace = "inkspire.morphic.feature.apps"
}

dependencies {
    // The feature convention plugin already wires core:model/common/designsystem, lifecycle-viewmodel,
    // koin-compose, coroutines, and the Compose artifacts. The APPS surface additionally needs:
    implementation(projects.data.apps) // AppRepository (the app collection) + AppLauncher (launch on tap)
    // Added with the pager, the first layout that stores an arrangement: AppsOrderRepository is the pager's
    // order store, and LayoutRepository supplies the folder definitions it shares with home.
    implementation(projects.data.layout)

    testImplementation(libs.junit)
}
