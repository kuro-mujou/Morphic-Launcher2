plugins {
    alias(libs.plugins.launcher.android.library)
    // Per-app overrides are stored as one serialized `IconLayerSet` per row, so this module owns that encoding —
    // `core:database` is handed a string and `core:icon` never learns that icons are persisted at all.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.data.icons"
}

dependencies {
    implementation(projects.core.model)    // the IconLayerSet being stored
    implementation(projects.core.common)   // AppDispatchers + Koin (api-exposed)
    implementation(projects.core.database)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber) // an unreadable row is reported rather than silently dropped

    testImplementation(libs.junit)
}
