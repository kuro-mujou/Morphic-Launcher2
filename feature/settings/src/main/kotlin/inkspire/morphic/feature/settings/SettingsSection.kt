package inkspire.morphic.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
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

    /** HOME's main grid: its rows and columns, and its icon sizing. */
    HOME_GRID,

    /** The dock: its height, the grid inside it, and its icon sizing. */
    DOCK,

    /** The APPS surface: each arrangement's grid — or the list's row height — and its icon sizing. */
    APPS,

    /**
     * An opened folder — and an expanded category card, which is the same overlay on the same grid: its icon sizing.
     *
     * The last section the icon-sizing waiting room was holding a grid for, which is why that room is gone: with the
     * folder grid housed, it had nothing left to show. L1's own `Icons` section is a different concern — shape,
     * background and layers, the **icon studio** — and will take the name back when B9 lands.
     */
    FOLDER,
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
        SettingsSection.HOME_GRID -> SettingsSectionMeta(
            "Home", "Grid and icons", Icons.Outlined.Home,
        )
        SettingsSection.DOCK -> SettingsSectionMeta(
            "Dock", "Height, grid and icons", Icons.Outlined.Dock,
        )
        SettingsSection.APPS -> SettingsSectionMeta(
            "Apps", "Arrangements, grids and icons", Icons.Outlined.Apps,
        )
        // L1's wording for this row, kept: "Icon and text size" is exactly what a folder has to configure.
        SettingsSection.FOLDER -> SettingsSectionMeta(
            "Folders", "Icon and text size", Icons.Outlined.Folder,
        )
    }

/** A titled run of sections in the list. A null [header] is a run with no heading above it. */
internal data class SettingsGroup(val header: String?, val sections: List<SettingsSection>)

/**
 * The list's order and grouping — L1's shape, with the sections that exist.
 *
 * One group for now, and that is the shape of the port rather than a simplification: every section here belongs to a
 * *surface*, which is L1's "Layout" group, and its "Personalization" group holds the things L2 has not built (theme,
 * wallpaper, effects, and the icon studio). The header comes back when the first of them does; a group with one row
 * under it is a heading doing no work.
 *
 * Order follows L1's: the register first (it decides what the others are *for*), then a section per surface, with
 * folders last — a folder is drawn over a surface rather than being one.
 */
internal val settingsGroups: List<SettingsGroup> = listOf(
    SettingsGroup(
        null,
        listOf(
            SettingsSection.SURFACE_REGISTER,
            SettingsSection.HOME_GRID,
            SettingsSection.DOCK,
            SettingsSection.APPS,
            SettingsSection.FOLDER,
        ),
    ),
)
