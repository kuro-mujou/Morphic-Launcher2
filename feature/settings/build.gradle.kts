plugins {
    alias(libs.plugins.launcher.android.feature)
}

android {
    namespace = "inkspire.morphic.feature.settings"
}

dependencies {
    // The feature convention plugin already wires core:model/common/designsystem, lifecycle-viewmodel,
    // koin-compose, coroutines and the Compose artifacts. This surface additionally needs:
    //
    // `BackHandler`, so system back and the screen's own back affordance are the same action rather than two.
    implementation(libs.androidx.activity.compose)
    //
    // Deliberately *not* here yet: `core:navigation`. This screen takes an `onBack` lambda and its extra rows as
    // parameters, so it neither knows nor names a destination — which is what keeps the settings taxonomy out of the
    // navigation module (L1's `core:navigation` ended up exporting an 11-value `SettingsSection` to every consumer).
    // Add it only if a settings screen genuinely needs to navigate somewhere it, and not its host, chooses.
}
