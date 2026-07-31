plugins {
    alias(libs.plugins.launcher.android.library)
    alias(libs.plugins.launcher.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.core.navigation"
}

dependencies {
    // `api`, not `implementation`: a consumer declaring its own destination needs `NavKey` on its compile
    // classpath, and the host needs `NavBackStack` to build a navigator. Both are part of this module's surface.
    api(libs.androidx.navigation3.runtime)
    // `navigation3-ui` (NavDisplay) is deliberately absent — that belongs to whoever *hosts* the graph (`app`),
    // not to the module that defines what the destinations are.

    // Routes are `@Serializable` so the back stack survives process death.
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.ui)
}
