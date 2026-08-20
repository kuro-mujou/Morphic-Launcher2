package inkspire.morphic.feature.settings

import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.HomeLayout

/**
 * A human name for an [AppsLayout] — **the settings feature's display vocabulary, in one place**.
 *
 * It lived in two files with *different strings* until a third screen wanted it: the APPS section said
 * "Pages"/"Category pages"/"Category cards" while the surface register said "Pager"/"Pager + category"/"Cards", so the
 * same five layouts had two names depending on which screen you were on. Both copies carried a KDoc promising to move
 * here when a second screen needed them; the register's picker is the third.
 *
 * Here rather than on the enum, which is the reason both copies gave: `core:model` stays free of display strings and of
 * localization, the same way `Category` carries an id and the UI resolves the name.
 *
 * The names describe the **arrangement a user sees** rather than the enum constant — L1 called the same four
 * "Minimalist", "Classic", "Paged" and "Grouped", which named its own history more than the layouts.
 */
internal val AppsLayout.label: String
    get() = when (this) {
        AppsLayout.VERTICAL_LIST -> "List"
        AppsLayout.VERTICAL_GRID -> "Grid"
        AppsLayout.PAGER -> "Pages"
        AppsLayout.PAGER_WITH_CATEGORY -> "Category pages"
        AppsLayout.CATEGORY_CARD -> "Category cards"
    }

/**
 * A human name for a [HomeLayout] — HOME's pairing, in the same one place and for the same reasons.
 *
 * **Named for the arrangement, not for L1's history.** L1 called these two "Classic" and "Minimalist", which are names
 * for *eras* of that launcher rather than descriptions: neither tells a first-time reader that one pages a grid and the
 * other lists apps under a panel of widgets. [subtitle] carries the second half of that, since a pairing is two zones
 * and a name this short can only gesture at them.
 *
 * **Both zones, in three words, because a pairing is two zones and the shortest honest name says so.** It read "Pages
 * with a dock" / "List with a widget area" while its only consumers were a radio row and a card that wraps to two
 * lines. The Home hub's segmented control is a two-up row on a phone pane, where those wrap under the finger — and
 * rather than add a second, shorter vocabulary (the fault this file exists to fix, one type up), the one name got
 * shorter everywhere. The register card reads better for it too: "Pages + dock" under a glyph is a formula for what
 * home is made of.
 */
internal val HomeLayout.label: String
    get() = when (this) {
        HomeLayout.PAGER_WITH_DOCK -> "Pages + dock"
        HomeLayout.LIST_WITH_WIDGET_AREA -> "List + widgets"
    }

/** What each pairing puts where — the half a name cannot carry, for the picker's second line. */
internal val HomeLayout.subtitle: String
    get() = when (this) {
        HomeLayout.PAGER_WITH_DOCK -> "Swipeable pages of icons, over a dock"
        HomeLayout.LIST_WITH_WIDGET_AREA -> "A list of apps, under a panel for widgets"
    }
