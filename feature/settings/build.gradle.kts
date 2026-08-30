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

    // One installed app, for the live icon preview to draw. The preview's whole point is a real icon at a real cell
    // size — a placeholder shape would answer a question nobody asked — so this surface needs the app cache, exactly as
    // L1's detail screens injected their `AppRepository` for the same preview.
    implementation(projects.data.apps)

    // The wallpaper section is a vertical over this service: it decodes, writes a file and calls `WallpaperManager`,
    // none of which is a preference — which is why it is its own module rather than a slice of `data:settings`.
    implementation(projects.data.wallpaper)

    // `NavKey`, for this module's own section destinations. Added now that sections *are* destinations — the previous
    // note here said to add it only when a settings screen genuinely needs to name one, which is now the case. `app`
    // still maps keys to screens; this module only declares them.
    implementation(projects.core.navigation)

    // `BackHandler`, so system back and a screen's own back affordance are the same action rather than two.
    implementation(libs.androidx.activity.compose)

    // Section icons in the settings list. The extended set because the sections need `Dock`, `Category`,
    // `Dashboard` and `AutoAwesome`, none of which are in the core icon subset — the same ones L1 picked.
    implementation(libs.androidx.compose.material.icons.extended)

    // The icon studio: it edits per-app recipes, and it renders them live rather than baked.
    implementation(projects.data.icons)
    implementation(projects.core.icon)

    // The wallpaper studio's generative engine.
    implementation(projects.core.graphics)

    // Haze — the studio's floating surfaces, and **the launcher's only other blur system**, which is worth a word
    // because a near-copy of an existing mechanism is normally the mistake this rewrite keeps un-making.
    //
    // They do not overlap. `wallpaperBackdrop` samples a *pre-blurred wallpaper bitmap* by position — one blur for
    // the whole screen, shared, so a panel sliding over it continues the picture — and it can only ever show the
    // wallpaper. The studio's canvas is deliberately not the wallpaper (it is black / white / a checkerboard, and
    // the icon being edited), so it is the one screen in the launcher whose backdrop is content the launcher itself
    // draws, and the one screen `wallpaperBackdrop` structurally cannot serve. Haze blurs whatever is really there.
    //
    // That "no wallpaper in the studio" decision is also what *guarantees* this works: Haze needs a real drawn node
    // to sample, and the transparent punch-through every settings preview uses would leave it nothing.
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)

    // This module's first test, for `IconStudioState.canUseFixedSource` — a pure rule about which sources a layer may
    // take, and the sort of decision this codebase tests rather than eyeballs (`CellFit`, `MenuAnchoring`,
    // `AppCollectionHostState`). It is worth pinning because getting it wrong is silent in both directions: too strict
    // hides a control with no error, too loose lets one global edit replace every icon on the device.
    testImplementation(libs.junit)
}
