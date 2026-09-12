package inkspire.morphic.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import inkspire.morphic.core.model.HomeLayout

/**
 * A section of the settings surface — **the list's own vocabulary**, not a navigation destination.
 *
 * **It stays inside the surface that draws it.** Putting this enum in the navigation module — which a route carrying
 * a section invites — is how every module that touches navigation ends up importing the whole settings taxonomy.
 * `app` opens *settings*, and which pane is showing is this
 * screen's business.
 *
 * Values are added as sections are built. An empty destination is not worth a row.
 */
enum class SettingsSection {
    /** The wallpaper the launcher owns: choose an image, and apply it to the home screen, the lock screen or both. */
    WALLPAPER,

    /** How frosted surfaces render over the wallpaper: the global effect, and the strengths tuning it. */
    EFFECTS,

    /**
     * How app icons are drawn: the layer recipe every icon inherits, and the per-app overrides on top of it.
     *
     * **Shape, background and layers — the icon studio.** Grid and icon *sizing* live in each surface's own section
     * instead, which is what this name was held back for. This row is a hub rather than an editor: the editing
     * happens in a full-screen destination, because a creative workspace is the wrong thing to put in a pane that
     * shares a tablet screen with a list.
     */
    ICONS,

    /** Which surface each HOME edge opens, in which layout. */
    SURFACE_REGISTER,

    /**
     * What the launcher does when the device turns or folds.
     *
     * In the Layout group and next to [SURFACE_REGISTER] because it answers the same *kind* of question — not how a
     * surface is arranged, but which arrangement is on screen at all.
     */
    ORIENTATION,

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
     * folder grid housed, it had nothing left to show.
     */
    FOLDER,

    /**
     * **Extras**: what belongs to more than one surface and sizes none of them.
     *
     * The A–Z index strip is the first and, today, the only one. It is not the [APPS] section's because what it
     * attaches to is *content ordered A–Z* — the APPS derived layouts have that now and HOME's vertical list will
     * have it next — and a setting filed under one surface is one the other has to reach across for or lose to a
     * rename.
     *
     * **A bucket, and named as one on purpose.** Every other row here names a surface or a material; this one names
     * what it is, which is the honest label for a place things are put. The risk it carries is the one every bucket
     * carries — that it becomes where anything goes rather than where cross-surface things go — and the guard is
     * this sentence rather than the name.
     */
    EXTRAS,

    /**
     * **About**: what this build is, what it asks the device for, and the two documents that answer for it.
     *
     * The one section that configures nothing — every other value here names something the user can change. It is a
     * section anyway because it is reached the way the others are and drawn in the same panes, and giving it a
     * mechanism of its own would be a second kind of settings row for a single destination.
     *
     * A hub, with [PERMISSIONS], [PRIVACY] and [LICENSES] beneath it. None belongs in the index: a top-level row for
     * the licenses of a launcher's dependencies would sit at the same level as the home screen.
     */
    ABOUT,

    /**
     * Every permission the installed package declares, as the package manager reports them. Under [ABOUT].
     *
     * A destination rather than a block on the hub, which is where it started: it is a list, and it was the longest
     * thing on a screen whose job is to point elsewhere.
     */
    PERMISSIONS,

    /** The privacy policy, in full, in the app. Under [ABOUT]. */
    PRIVACY,

    /** Every open-source library this build ships, with its license. Under [ABOUT]. */
    LICENSES,
}

/**
 * A section's row in the list: what it is called, and the glyph that marks it.
 *
 * **No second line.** Every row used to carry one describing its section, and down a list of seven they read as a
 * single sentence with the nouns shuffled — four ended in "and icons". A row in an index has one job, which is to be
 * recognized. What a section covers is answered by opening it.
 */
internal data class SettingsSectionMeta(
    val title: String,
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
internal fun SettingsSection.meta(homeLayout: HomeLayout): SettingsSectionMeta =
    when (this) {
        SettingsSection.WALLPAPER -> SettingsSectionMeta(
            "Wallpaper", Icons.Outlined.Wallpaper,
        )

        SettingsSection.EFFECTS -> SettingsSectionMeta(
            "Effects", Icons.Outlined.AutoAwesome,
        )

        SettingsSection.ICONS -> SettingsSectionMeta(
            "Icons", Icons.Outlined.Palette,
        )

        SettingsSection.SURFACE_REGISTER -> SettingsSectionMeta(
            "Screen manager", Icons.Outlined.Dashboard,
        )

        SettingsSection.ORIENTATION -> SettingsSectionMeta(
            "Orientation", Icons.Outlined.ScreenRotation,
        )

        SettingsSection.HOME -> SettingsSectionMeta(
            // **The one row that does not rename itself**, which is the whole point of the hub: it names a surface,
            // and a surface does not change identity when its arrangement does.
            "Home screen", Icons.Outlined.Home,
        )
        SettingsSection.HOME_GRID -> mainAreaMeta(homeLayout)

        SettingsSection.DOCK -> sideZoneMeta(homeLayout)

        SettingsSection.APPS -> SettingsSectionMeta(
            "App screen", Icons.Outlined.Apps,
        )

        SettingsSection.FOLDER -> SettingsSectionMeta(
            "Folders", Icons.Outlined.Folder,
        )

        SettingsSection.EXTRAS -> SettingsSectionMeta(
            "Extras", Icons.Outlined.Tune,
        )

        SettingsSection.ABOUT -> SettingsSectionMeta(
            "About", Icons.Outlined.Info,
        )

        SettingsSection.PERMISSIONS -> SettingsSectionMeta(
            "Permissions", Icons.Outlined.Lock,
        )

        SettingsSection.PRIVACY -> SettingsSectionMeta(
            "Privacy policy", Icons.Outlined.PrivacyTip,
        )

        // Named for what it is rather than "Licenses": on its own that word reads as *this app's* license, which is
        // the one thing the pane does not show.
        SettingsSection.LICENSES -> SettingsSectionMeta(
            "Open-source licenses", Icons.Outlined.Code,
        )
    }

/**
 * HOME's **main area** row, named for what the current pairing makes it.
 *
 * Named for the zone rather than the surface, since this is a row *inside* the home screen — naming it "Home" again
 * would say one thing twice, and this string is the app bar's title once the pane is open.
 *
 * Split out of [meta] with [sideZoneMeta] because those two are the only rows that read the pairing at all: leaving
 * their branches inline made a lookup table of eleven entries read as a function with logic in it, and pushed it
 * past detekt's complexity bound the moment a twelfth section arrived.
 *
 * Each reads the pairing itself rather than taking an `isList` computed by [meta]. That is what leaves [meta] a bare
 * `when` and nothing else — the shape `CyclomaticComplexMethod.ignoreSingleWhenExpression` exempts, and the reason
 * this table can go on gaining a row per section without pressure toward an `else`.
 */
private fun mainAreaMeta(homeLayout: HomeLayout): SettingsSectionMeta =
    if (homeLayout == HomeLayout.LIST_WITH_WIDGET_AREA) {
        SettingsSectionMeta("List", Icons.AutoMirrored.Outlined.ViewList)
    } else {
        SettingsSectionMeta("Grid", Icons.Outlined.GridView)
    }

/** HOME's **side zone** row, named for what the current pairing makes it — [mainAreaMeta]'s twin. */
private fun sideZoneMeta(homeLayout: HomeLayout): SettingsSectionMeta =
    if (homeLayout == HomeLayout.LIST_WITH_WIDGET_AREA) {
        SettingsSectionMeta("Widget area", Icons.Outlined.Widgets)
    } else {
        SettingsSectionMeta("Dock", Icons.Outlined.Dock)
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
        SettingsSection.PERMISSIONS, SettingsSection.PRIVACY, SettingsSection.LICENSES -> SettingsSection.ABOUT
        SettingsSection.WALLPAPER,
        SettingsSection.EFFECTS,
        SettingsSection.ICONS,
        SettingsSection.SURFACE_REGISTER,
        SettingsSection.ORIENTATION,
        SettingsSection.HOME,
        SettingsSection.APPS,
        SettingsSection.FOLDER,
        SettingsSection.EXTRAS,
        SettingsSection.ABOUT,
            -> null
    }

/** How many hubs sit above this section — 0 for a list row, 1 for a child. Used to pick a slide direction. */
internal val SettingsSection.depth: Int
    get() = if (parent == null) 0 else 1

/** A titled run of sections in the list. A null [header] is a run with no heading above it. */
internal data class SettingsGroup(val header: String?, val sections: List<SettingsSection>)

/**
 * The list's order and grouping.
 *
 * **Two groups now that the wallpaper has landed**, which is what the note here promised: the sections that describe a
 * *surface* are the "Layout" group, and "Personalization" holds what a launcher looks like rather than how it is
 * arranged. The headers appear together, because a single unlabeled run needed none.
 *
 * The register comes first, since it decides what the others are *for*; then a section per surface, with folders
 * last — a folder is drawn over a surface rather than being one.
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
            SettingsSection.ORIENTATION,
            // **One row per surface.** `HOME_GRID` and `DOCK` sat here until the hub existed, which made the list
            // split HOME by *zone* while it split APPS not at all — and forced two of its rows to rename themselves
            // as a setting in another section changed. They are reached through `HOME` now.
            SettingsSection.HOME,
            SettingsSection.APPS,
            SettingsSection.FOLDER,
        ),
    ),
    // **Its own group, unheaded, and last.** A bucket under "Layout" would claim to arrange something, and under
    // "Personalization" it would claim to be a look; what it holds is neither, and a third heading over a single row
    // would be a label longer than the thing it labels.
    SettingsGroup(null, listOf(SettingsSection.EXTRAS)),
    // **About sits in a panel of its own rather than beside Extras**, though both are unheaded single rows and the
    // list would look tidier with one panel holding two. Every other row in this index changes the launcher; this one
    // only describes it, and the gap between two panels is the only thing in the list that can say so.
    SettingsGroup(null, listOf(SettingsSection.ABOUT)),
)
