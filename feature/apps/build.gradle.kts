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
    // No data:layout — the derived layouts (list, grid) store nothing, and the ordered ones (pager, category)
    // get their own order repository, which isn't built. Add it when the first ordered layout lands.

    testImplementation(libs.junit)
}
