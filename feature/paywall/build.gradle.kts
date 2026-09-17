plugins {
    alias(libs.plugins.launcher.android.feature)
    // `PaywallRoute` is a `@Serializable` NavKey, declared here rather than in core:navigation.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.feature.paywall"
}

dependencies {
    // The feature convention plugin already wires core:model/common/designsystem, lifecycle-viewmodel,
    // koin-compose, coroutines and the Compose artifacts. This screen additionally needs:
    implementation(projects.data.billing) // the plans, the entitlement and the purchase command
    implementation(projects.core.navigation) // NavKey, for its own route

    // `LocalActivity` (Play's purchase sheet opens over an Activity) and `BackHandler`.
    implementation(libs.androidx.activity.compose)
    // The back arrow and the plan's check mark.
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
}
