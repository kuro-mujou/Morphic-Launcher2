plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.setup"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common) // AppDispatchers + Koin + coroutines (api-exposed)

    // **The one data module that reads other data modules**, and deliberately so. Which setup steps are unfinished is one
    // fact derived from four stores, and it has two readers — the settings hub and HOME's menu — in two feature modules
    // that cannot see each other. Deriving it in each would be two copies of the rules deciding when a step is done,
    // kept in step by intention.
    implementation(projects.data.settings) // the dismissals, the icon recipe, and SetupStep itself
    implementation(projects.data.apps) // whether this launcher holds the home role
    implementation(projects.data.layout) // whether any widget is placed
    implementation(projects.data.wallpaper) // whether a wallpaper was chosen through the launcher

    testImplementation(libs.junit)
}
