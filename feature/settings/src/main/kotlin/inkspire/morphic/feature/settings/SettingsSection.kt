package inkspire.morphic.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A section of the settings surface — **the list's own vocabulary**, not a navigation destination.
 *
 * L1 put its equivalent enum in the navigation module because a route carried it, which is how `feature:home` ended
 * up importing `SettingsSection.WALLPAPER` and every module that touched navigation could see the whole settings
 * taxonomy. Here it stays inside the surface that draws it: `app` opens *settings*, and which pane is showing is this
 * screen's business.
 *
 * Values are added as sections are ported. L1 has eleven, two of which (THEME, GESTURE) are 12-line "Coming soon"
 * placeholders — an empty destination is not worth a row.
 */
enum class SettingsSection {
    /** Which surface each HOME edge opens, in which layout. L1 called this "Layout". */
    SURFACE_REGISTER,

    /**
     * Icon sizing for the grids whose surface has no section of its own yet — and, once B9 lands, the icon studio.
     *
     * Shrinking by design: a grid's icon size belongs beside its grid, so each new surface section takes its slot
     * with it. Home and the dock already have.
     */
    ICONS,

    /** HOME's main grid: its rows and columns, and its icon sizing. */
    HOME_GRID,

    /** The dock: its height, the grid inside it, and its icon sizing. */
    DOCK,
}

/** A section's row in the list: what it is called, what it covers, and the glyph that marks it. */
internal data class SettingsSectionMeta(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

internal val SettingsSection.meta: SettingsSectionMeta
    get() = when (this) {
        SettingsSection.SURFACE_REGISTER -> SettingsSectionMeta(
            "Layout", "Surfaces and transitions", Icons.Outlined.Dashboard,
        )
        SettingsSection.ICONS -> SettingsSectionMeta(
            "Icons", "Sizing for the remaining grids", Icons.Outlined.Widgets,
        )
        SettingsSection.HOME_GRID -> SettingsSectionMeta(
            "Home", "Grid and icons", Icons.Outlined.Home,
        )
        SettingsSection.DOCK -> SettingsSectionMeta(
            "Dock", "Height, grid and icons", Icons.Outlined.Dock,
        )
    }

/** A titled run of sections in the list. A null [header] is a run with no heading above it. */
internal data class SettingsGroup(val header: String?, val sections: List<SettingsSection>)

/** The list's order and grouping — L1's shape, with the sections that exist. */
internal val settingsGroups: List<SettingsGroup> = listOf(
    SettingsGroup("Personalization", listOf(SettingsSection.ICONS)),
    SettingsGroup(
        "Layout",
        listOf(
            SettingsSection.SURFACE_REGISTER,
            SettingsSection.HOME_GRID,
            SettingsSection.DOCK,
        ),
    ),
)
