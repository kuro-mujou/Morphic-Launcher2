package inkspire.morphic.feature.settings

import androidx.compose.runtime.Composable
import inkspire.morphic.feature.settings.apps.AppsLayoutMenu

/**
 * What the settings app bar shows on the right for the section on screen.
 *
 * **A dispatcher rather than an `actions` parameter threaded from each pane**, because a pane is rendered *below* the
 * bar and cannot reach up into it. The shell asks this what the current section wants; the answer is a composable
 * belonging to that section's own package, so `SettingsScreen` learns that sections may have actions without learning
 * what any of them are.
 *
 * Exactly one section has one. It stays a `when` over every value rather than an `if`, so a section added later has to
 * say it has none — the same reason [SettingsSection.parent] is exhaustive.
 */
@Composable
internal fun SettingsSectionActions(section: SettingsSection?) {
    when (section) {
        SettingsSection.APPS -> AppsLayoutMenu()
        SettingsSection.WALLPAPER,
        SettingsSection.EFFECTS,
        SettingsSection.ICONS,
        SettingsSection.SURFACE_REGISTER,
        SettingsSection.HOME,
        SettingsSection.HOME_GRID,
        SettingsSection.DOCK,
        SettingsSection.FOLDER,
        null,
            -> Unit
    }
}
