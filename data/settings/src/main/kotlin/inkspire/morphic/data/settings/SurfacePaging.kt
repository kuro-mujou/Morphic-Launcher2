package inkspire.morphic.data.settings

import inkspire.morphic.core.model.GridSlot
import kotlinx.serialization.Serializable

/**
 * **How the launcher's pagers page** — two behaviors, each asked of the grids that have it: do the pages wrap around at
 * the ends ([wraps]), and does the surface reopen on the page it was left on ([remembersPage])?
 *
 * The fourth settings slice. Its own rather than a field in [SurfaceMetrics], because that slice is per-grid
 * *metrics* — every map in it is a size, and every one is keyed `slot × device` because a size is something a posture
 * can change. Neither behavior here is: turning the phone on its side is no reason for the pages to stop looping, or
 * for a remembered page to be forgotten. Folding either in would have meant a map shaped unlike its neighbors living
 * under a name that says "metrics".
 *
 * **Slot-keyed, where L1 had a single global flag.** L1's `pager.infiniteScroll` was one boolean read by home's pager
 * *and* both drawer pagers, with its only control in the Home settings screen — so turning it on to make the home
 * pages loop silently changed the app drawer too, and a user configuring the drawer had no control to find. Here each
 * pager owns its own answer, which is what lets the toggle appear in the section that configures that surface. The
 * three grids are genuinely different questions: home is a handful of pages, the APPS pager may be many, and the
 * category pager's pages *are* the categories.
 *
 * **Sparse**, like every override in this module: an absent slot follows its blueprint, and clearing a toggle removes
 * the entry rather than storing the default back. Which is what keeps "a default lives in exactly one place" true —
 * see [GridBlueprint.wraps] and [GridBlueprint.remembersPage], where the defaults are declared and where "does this
 * grid offer the setting at all" is decided.
 */
@Serializable
data class SurfacePaging(
    val wraps: Map<GridSlot, Boolean> = emptyMap(),
    val remembersPage: Map<GridSlot, Boolean> = emptyMap(),
) {
    /**
     * Whether [slot]'s pages wrap: [base] — its blueprint's — unless the user has set one here.
     *
     * Consumers never see this; they ask [SettingsRepository.pagerWraps] for resolved answers, and the keying stays
     * inside this module exactly as [SurfaceMetrics]' does.
     */
    fun wrapsFor(slot: GridSlot, base: Boolean): Boolean = wraps[slot] ?: base

    /**
     * Whether [slot] reopens on the page it was left on: [base] — its blueprint's — unless the user has set one here.
     *
     * [wrapsFor]'s twin, over the other map. Only the two APPS pagers ever ask, since only they carry a
     * [GridBlueprint.remembersPage]; home never consults this and always remembers.
     */
    fun remembersPageFor(slot: GridSlot, base: Boolean): Boolean = remembersPage[slot] ?: base

    /**
     * A copy with [slot]'s wrapping set to [value], or **cleared** when it is null — after which that pager follows
     * its blueprint again.
     *
     * No pruning helper is shared with [SurfaceMetrics]'s: that one prunes two levels because its maps are keyed
     * `slot × device`, and this map has only the one level to remove from.
     */
    fun withWrap(slot: GridSlot, value: Boolean?): SurfacePaging =
        copy(wraps = if (value == null) wraps - slot else wraps + (slot to value))

    /** [withWrap]'s twin, over the [remembersPage] map. */
    fun withRememberPage(slot: GridSlot, value: Boolean?): SurfacePaging =
        copy(remembersPage = if (value == null) remembersPage - slot else remembersPage + (slot to value))

    companion object {
        /** Nothing overridden: every pager follows its blueprint. */
        val Default = SurfacePaging()
    }
}
