plugins {
    alias(libs.plugins.launcher.android.library)
    // Slices are stored as JSON blobs, so this module declares its own serializable types.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.data.settings"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common) // AppDispatchers + Koin + coroutines (api-exposed)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber) // an unreadable slice is reported rather than silently reset

    testImplementation(libs.junit)
}
