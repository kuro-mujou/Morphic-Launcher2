package inkspire.morphic.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import inkspire.morphic.core.model.HomeLayout

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
    /** The wallpaper the launcher owns: choose an image, and apply it to the home screen, the lock screen or both. */
    WALLPAPER,

    /** How frosted surfaces render over the wallpaper: the global effect, and the strengths tuning it. */
    EFFECTS,

    /**
     * How app icons are drawn: the layer recipe every icon inherits, and the per-app overrides on top of it.
     *
     * **The name L1 used, finally meaning what it said there.** Its `Icons` section was shape, background and
     * layers — the icon studio — and this codebase has been holding the name back for it, with grid and icon
     * *sizing* living in each surface's own section instead. This row is a hub rather than an editor: the editing
     * happens in a full-screen destination, because a creative workspace is the wrong thing to put in a pane that
     * shares a tablet screen with a list.
     */
    ICONS,

    /** Which surface each HOME edge opens, in which layout. L1 called this "Layout". */
    SURFACE_REGISTER,

    /**
     * The HOME surface — **a hub over its two zones**, and the pairing switch that decides what they are.
     *
     * The one row HOME gets in the list, for the reason `APPS` gets one: what a user configures is *home*, not half
     * of it. [HOME_GRID] and [DOCK] are its children ([parent]) and stay panes of their own, because two zones mean
     * two icon groups and `SurfaceDetail` can pin exactly one preview — see
     * [docs/HOME_SETTINGS_HUB_PLAN.md](../../../../../../../../docs/HOME_SETTINGS_HUB_PLAN.md).
     */
    HOME,

    /**
     * HOME's **main area**: its size, and its icon sizing.
     *
     * What that means depends on HOME's pairing — a grid's rows and columns, or a list's row height — which is why
     * [meta] takes the pairing and this value does not name one.
     *
     * **Reached through [HOME], not from the list.** It was a top-level row until the hub existed, which is what made
     * `meta` have to rename it under the user as a setting in *another* section changed.
     */
    HOME_GRID,

    /** HOME's **side zone**: its extent, the grid inside it, and (when it holds icons) their sizing. Under [HOME]. */
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

/**
 * A section's row, given HOME's current pairing.
 *
 * **A function rather than a property, and [homeLayout] is the only reason.** Two rows change name with it — HOME's
 * main area is a grid or a list, and its side zone is a dock or a widget area — and a row that said "Dock" while its
 * pane said "Widget area" would be worse than either. Every other row ignores the argument, which is the honest cost
 * of keeping one vocabulary rather than two.
 */
internal fun SettingsSection.meta(homeLayout: HomeLayout): SettingsSectionMeta {
    val isList = homeLayout == HomeLayout.LIST_WITH_WIDGET_AREA
    return when (this) {
        SettingsSection.WALLPAPER -> SettingsSectionMeta(
            "Wallpaper", "Image, and where to apply it", Icons.Outlined.Wallpaper,
        )
        SettingsSection.EFFECTS -> SettingsSectionMeta(
            "Effects", "Frosted surfaces over the wallpaper", Icons.Outlined.AutoAwesome,
        )
        SettingsSection.ICONS -> SettingsSectionMeta(
            "Icons", "Shape, background and layers", Icons.Outlined.Palette,
        )
        SettingsSection.SURFACE_REGISTER -> SettingsSectionMeta(
            "Layout", "Surfaces and transitions", Icons.Outlined.Dashboard,
        )
        SettingsSection.HOME -> SettingsSectionMeta(
            // **The one row that does not rename itself**, which is the whole point of the hub: it names a surface,
            // and a surface does not change identity when its arrangement does. Deliberately worded as `APPS` is —
            // both are one row over a surface arranged more than one way.
            "Home", "Arrangement, grids and icons", Icons.Outlined.Home,
        )
        // Named for the zone rather than the surface, since this is a row *inside* Home now — "Home" under a pane
        // titled "Home" would say one thing twice. The pane's own heading still reads "Home grid" / "Home list"
        // (`GridSizeDetail`), so nothing is lost when it is the app bar's title on a phone.
        SettingsSection.HOME_GRID -> SettingsSectionMeta(
            if (isList) "List" else "Grid",
            if (isList) "Row height and icons" else "Rows, columns and icons",
            if (isList) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
        )
        SettingsSection.DOCK -> if (isList) {
            SettingsSectionMeta("Widget area", "Size and grid", Icons.Outlined.Widgets)
        } else {
            SettingsSectionMeta("Dock", "Height, grid and icons", Icons.Outlined.Dock)
        }
        SettingsSection.APPS -> SettingsSectionMeta(
            "Apps", "Arrangements, grids and icons", Icons.Outlined.Apps,
        )
        // L1's wording for this row, kept: "Icon and text size" is exactly what a folder has to configure.
        SettingsSection.FOLDER -> SettingsSectionMeta(
            "Folders", "Icon and text size", Icons.Outlined.Folder,
        )
    }
}

/**
 * The section this one is reached *through*, or null when it is a row in the list.
 *
 * **A property, not a back stack.** The hierarchy is one level deep and has no reason to grow: sections are panes,
 * and a pane that shares a tablet screen with the list is not a destination — the same argument that kept
 * `SettingsSection` out of the navigation graph in the first place. Two things read it: system back, which closes to
 * the parent rather than all the way out, and the two-pane list, which highlights the parent of whatever is showing.
 *
 * Exhaustive rather than `else -> null`, so a section added later has to say whether it nests. That is one line of
 * cost against the failure it prevents, which is silent: a nested pane with no parent backs out to the list and skips
 * the hub it was opened from.
 */
internal val SettingsSection.parent: SettingsSection?
    get() = when (this) {
        SettingsSection.HOME_GRID, SettingsSection.DOCK -> SettingsSection.HOME
        SettingsSection.WALLPAPER,
        SettingsSection.EFFECTS,
        SettingsSection.ICONS,
        SettingsSection.SURFACE_REGISTER,
        SettingsSection.HOME,
        SettingsSection.APPS,
        SettingsSection.FOLDER,
        -> null
    }

/** How many hubs sit above this section — 0 for a list row, 1 for a child. Used to pick a slide direction. */
internal val SettingsSection.depth: Int
    get() = if (parent == null) 0 else 1

/** A titled run of sections in the list. A null [header] is a run with no heading above it. */
internal data class SettingsGroup(val header: String?, val sections: List<SettingsSection>)

/**
 * The list's order and grouping — L1's shape, with the sections that exist.
 *
 * **Two groups now that the wallpaper has landed**, which is what the note here promised: the sections that describe a
 * *surface* are L1's "Layout" group, and "Personalization" is the one holding what a launcher looks like rather than
 * how it is arranged. It has one row today and the rest of L1's — theme, effects, and the icon studio — join it as they
 * are built. The headers appear together because a single unlabeled run needed none.
 *
 * Order follows L1's: the register first (it decides what the others are *for*), then a section per surface, with
 * folders last — a folder is drawn over a surface rather than being one.
 */
internal val settingsGroups: List<SettingsGroup> = listOf(
    SettingsGroup(
        "Personalization",
        listOf(SettingsSection.WALLPAPER, SettingsSection.EFFECTS, SettingsSection.ICONS),
    ),
    SettingsGroup(
        "Layout",
        listOf(
            SettingsSection.SURFACE_REGISTER,
            // **One row per surface.** `HOME_GRID` and `DOCK` sat here until the hub existed, which made the list
            // split HOME by *zone* while it split APPS not at all — and forced two of its rows to rename themselves
            // as a setting in another section changed. They are reached through `HOME` now.
            SettingsSection.HOME,
            SettingsSection.APPS,
            SettingsSection.FOLDER,
        ),
    ),
)
