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
    // Resolved per-zone icon sizing: the pager's and the dock's blueprints, with the user's overrides merged in.
    implementation(projects.data.settings)
    // The widget picker's catalogue, and the AppWidgetHost that binds and draws what it offers.
    implementation(projects.data.widgets)
    // `LauncherIcon`, for an icon container's slots. Drawn directly rather than through a designsystem cell because
    // a container's *arrangement* decides how big each icon is — see `IconContainerCell`.
    implementation(projects.core.icon)

    // The picker's chrome: a close, a back and a chevron. The extended set for `feature:settings`' reason — it is
    // the artifact this project already ships, and R8 keeps only the vectors actually referenced.
    implementation(libs.androidx.compose.material.icons.extended)

    // The widget add flow logs the paths that release an allocated id — a leak the user cannot see or clear, so
    // it is worth a line when it happens.
    implementation(libs.timber)

    testImplementation(libs.junit)
}
