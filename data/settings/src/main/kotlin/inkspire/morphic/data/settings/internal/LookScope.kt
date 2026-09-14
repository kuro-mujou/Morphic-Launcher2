package inkspire.morphic.data.settings.internal

/**
 * Which settings slices a look may carry — **the one place that is decided**. A test holds it to every slice in
 * [SettingsSlices], so a new slice cannot arrive unclassified.
 *
 * A look carries the slices that describe an *arrangement*, never the ones that describe the user's own things or this
 * device. The per-slice reasons are in docs/ONBOARDING_PLAN.md ("The preset format"); the two that table does not name
 * are excluded for reasons that would fail silently:
 * - `home_gestures` holds actions naming this device's apps and shortcuts.
 * - `orientation_settings` holds the independent-layout flag, which is only correct changed together with placement
 *   writes `data:layout` owns; a look writing the flag alone would skip them.
 */
internal object LookScope {

    /** The icon recipe. Written by a look together with [IconAppliedPreset], or not at all. */
    const val IconAppearance = "icon_appearance"

    /**
     * The name of the icon preset in force. Carried, but **never written on its own**: exactly when [IconAppearance] is,
     * as `SettingsRepository.setIconAppearance` does, or the icon library would mark a preset whose look is not applied.
     */
    const val IconAppliedPreset = "icon_applied_preset"

    /** Written when a look is applied, and captured. */
    val carried: Set<String> = setOf(
        SurfaceRegisterKey,
        "surface_metrics",
        "apps_chrome",
        "alphabet_rails",
        "surface_paging",
        "backdrop_effect",
        IconAppearance,
        IconAppliedPreset,
    )

    /** Never written by a look, whatever it contains. */
    val excluded: Set<String> = setOf(
        "icon_presets",
        "icon_studio_background",
        "icon_studio_workspace",
        "default_launcher_ask",
        "home_item_gestures",
        "home_gestures",
        "orientation_settings",
        "onboarding",
        "setup_restart_look",
    )
}
