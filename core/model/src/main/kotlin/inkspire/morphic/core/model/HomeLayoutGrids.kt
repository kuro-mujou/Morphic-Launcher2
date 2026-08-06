package inkspire.morphic.core.model

/**
 * **Which grids a [HomeLayout] is made of** — the mapping from "what is HOME" to the two blueprints that draw it.
 *
 * It is three one-line properties rather than three fields on [HomeLayout] for the reason [GridSlot] lives on
 * [GridBlueprint]: a layout is a *taxonomy* value in `Surface.kt`, and hanging grid identities off it there would make
 * the pure enum depend on the blueprint registry. Here the dependency runs the honest way round, and the `when`s are
 * exhaustive, so a third layout cannot be added without saying what it is made of.
 *
 * **This is what makes every consumer layout-agnostic.** The home surface, the two settings sections and the
 * ViewModel all ask "the main slot" and "the side slot" rather than naming `HOME_MAIN`/`HOME_DOCK`, which is what
 * lets one `GridEditor`, one icon-sizing block and one extent control serve both layouts. L1 instead branched on
 * `settings.homeSurface == VERTICAL_LIST` at every one of those points — nine times in its home screen alone — and
 * each branch re-decided which store to read.
 */

/** The grid HOME's **main area** draws in this layout — the paged pager, or the vertical list. */
val HomeLayout.mainSlot: GridSlot
    get() = when (this) {
        HomeLayout.PAGER_WITH_DOCK -> GridSlot.HOME_MAIN
        HomeLayout.LIST_WITH_WIDGET_AREA -> GridSlot.HOME_LIST
    }

/**
 * The grid whose [GridBlueprint.wraps] setting governs this layout's paging, or **null** when it does not page.
 *
 * Deliberately *not* derived as "[mainSlot] if its blueprint declares `wraps`". That expression would be true today
 * and quietly wrong the day a side zone pages, or a pairing draws a pager somewhere other than its main area — and it
 * would hide the question inside a `takeIf` rather than answering it. Exhaustive, so a third pairing has to say
 * whether it pages.
 */
val HomeLayout.pagerSlot: GridSlot?
    get() = when (this) {
        HomeLayout.PAGER_WITH_DOCK -> GridSlot.HOME_MAIN
        HomeLayout.LIST_WITH_WIDGET_AREA -> null
    }

/** The grid HOME's **side zone** draws in this layout — the dock, or the widget area. */
val HomeLayout.sideSlot: GridSlot
    get() = when (this) {
        HomeLayout.PAGER_WITH_DOCK -> GridSlot.HOME_DOCK
        HomeLayout.LIST_WITH_WIDGET_AREA -> GridSlot.HOME_WIDGET_AREA
    }

/**
 * The [HomeZone] this layout's side zone stores its items against.
 *
 * The persistence half of [sideSlot]: the slot names the *grid* (how it is sized and drawn), the zone names the
 * *store* (which rows in the placement tables belong to it). They are one-to-one and always will be — a zone with no
 * grid could not be drawn and a grid with no zone would have nothing in it — but they are read by different layers,
 * so both names exist.
 *
 * Items keep their zone when the layout changes, which is why the enum has three values while a layout uses two: a
 * dock full of apps is still a dock full of apps while the user is looking at the list, and switching back finds it
 * as it was. That is [HomeZone]'s own KDoc, made operational here.
 */
val HomeLayout.sideZone: HomeZone
    get() = when (this) {
        HomeLayout.PAGER_WITH_DOCK -> HomeZone.DOCK
        HomeLayout.LIST_WITH_WIDGET_AREA -> HomeZone.WIDGET_AREA
    }
