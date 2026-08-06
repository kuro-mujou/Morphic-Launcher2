package inkspire.morphic.core.model

/**
 * **Which grid an [AppsLayout] pages through**, the APPS twin of [HomeLayout.pagerSlot].
 *
 * Here in `core:model` rather than beside `AppsLayout.scrollAxes` in `feature:apps`, unlike the scroll axes it feeds:
 * two modules that cannot see each other need it. `feature:apps` draws the pager, and `feature:settings` has to know
 * which grid its toggle writes when the user picks a chip — and settings does not depend on the apps feature. A
 * layout→grid mapping is a fact about the taxonomy, which is what this module is for; how that grid *behaves* under a
 * finger stays with the module that draws it.
 */

/**
 * The grid whose [GridBlueprint.wraps] setting governs this layout's paging, or **null** for the three layouts that
 * do not page.
 *
 * Note the two pagers answer with *different* grids rather than sharing one: `PAGER_WITH_CATEGORY` pages through
 * categories, and whether that should loop is a different question from whether pages of loose apps should. They also
 * happen to be the two grids `AppsScreen` already sizes separately.
 */
val AppsLayout.pagerSlot: GridSlot?
    get() = when (this) {
        AppsLayout.PAGER -> GridSlot.APPS_PAGER
        AppsLayout.PAGER_WITH_CATEGORY -> GridSlot.APPS_CATEGORY
        // Listed rather than folded into an `else`, as every `when` over this enum is: a sixth layout must say
        // whether it pages before it will compile.
        AppsLayout.VERTICAL_LIST, AppsLayout.VERTICAL_GRID, AppsLayout.CATEGORY_CARD -> null
    }
