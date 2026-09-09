plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.layout"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)   // AppDispatchers + Koin + coroutines (api-exposed)
    implementation(projects.core.database)  // placement DAOs + entities

    testImplementation(libs.junit)
    // `ArrangementSync` is the module's first suspending API, so its spec is the first to need `runTest`.
    testImplementation(libs.kotlinx.coroutines.test)
}
