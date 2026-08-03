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

    // Resizing a grid is *two* writes, and this module owns both. Changing a count is a settings write; moving the
    // items that count displaces is a placement write, and only the actor that knows **which edge** changed can make
    // it — a surface re-reading the new size later can reflow, but it cannot tell a removed left column from a
    // removed right one. So the grid editor holds `LayoutRepository` alongside `SettingsRepository`, as L1's dock
    // detail did. If a second caller ever needs the pair, they extract into a command of their own.
    implementation(projects.data.layout)

    // `NavKey`, for this module's own section destinations. Added now that sections *are* destinations — the previous
    // note here said to add it only when a settings screen genuinely needs to name one, which is now the case. `app`
    // still maps keys to screens; this module only declares them.
    implementation(projects.core.navigation)

    // `BackHandler`, so system back and a screen's own back affordance are the same action rather than two.
    implementation(libs.androidx.activity.compose)

    // Section icons in the settings list. The extended set because the sections need `Dock`, `Category`,
    // `Dashboard` and `AutoAwesome`, none of which are in the core icon subset — the same ones L1 picked.
    implementation(libs.androidx.compose.material.icons.extended)
}
