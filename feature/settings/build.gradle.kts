plugins {
    alias(libs.plugins.launcher.android.feature)
    // Section destinations are `@Serializable` NavKeys, declared in this module rather than in core:navigation.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "inkspire.morphic.feature.settings"
}

dependencies {
    // The feature convention plugin already wires core:model/common/designsystem, lifecycle-viewmodel,
    // koin-compose, coroutines and the Compose artifacts. This surface additionally needs:

    // The store it edits.
    implementation(projects.data.settings)

    // `NavKey`, for this module's own section destinations. Added now that sections *are* destinations — the previous
    // note here said to add it only when a settings screen genuinely needs to name one, which is now the case. `app`
    // still maps keys to screens; this module only declares them.
    implementation(projects.core.navigation)

    // `BackHandler`, so system back and a screen's own back affordance are the same action rather than two.
    implementation(libs.androidx.activity.compose)
}
