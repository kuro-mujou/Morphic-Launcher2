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
    // Resolved per-grid icon sizing: a blueprint's default with the user's overrides merged in.
    implementation(projects.data.settings)
    // BackHandler: the category pager's search is a mode, and back has to leave it before it leaves the surface.
    implementation(libs.androidx.activity.compose)
    // Two vectors, both the search mode's: the button that opens it and the one that closes it. The extended set for
    // `feature:settings`' reason — it is the artifact this project already ships, and R8 keeps only what is used.
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
}
