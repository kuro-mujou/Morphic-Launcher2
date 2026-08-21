package inkspire.morphic.data.settings

import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.VerticalEdge
import kotlinx.serialization.Serializable

/**
 * The APPS surface's **chrome**: the elements drawn around its grid rather than in it.
 *
 * A third slice rather than fields on [SurfaceRegister] or [SurfaceMetrics], because it is neither of those things.
 * The register says *which surface a home edge opens and in what layout*; the metrics say *how big a grid's cells are*.
 * This says what is drawn beside the cells, which changes on its own schedule and is read by a different set of
 * consumers. Keeping them beside the grid profiles fuses two unrelated concerns into one record.
 *
 * **The types are `core:model`'s own.** `SearchPlacement` is layout-aware where a flat position enum is not (a
 * standalone layout pins the field to an edge; the category pager embeds it in the header,
 * so it has no edge to choose), and the tab bar's placement *is* a [VerticalEdge] — which that enum's KDoc has said
 * since B0, naming this exact consumer.
 *
 * **How many layouts can hold a setting decides its shape, and its name says which answer it got.** More than one,
 * and it is a sparse map keyed by layout, named `…ByLayout`. Exactly one, and it is a plain field named for the
 * *owner* rather than for the surface — because a map would then permit four keys nothing can read or write, while a
 * surface-wide name would claim a property of APPS that four of its five arrangements silently ignore. Search took the
 * first answer, the category pager's tabs the second, and the pair are not inconsistent: they are the same rule with
 * different counts.
 *
 * The rule is also what makes a second tabbed layout a *visible* change rather than a quiet one. A field named for the
 * arrangement it belongs to cannot absorb a second one by being written to; it has to become a map, and renaming the
 * key is how a stored shape announces that its meaning moved.
 *
 * @property searchByLayout where the search field sits, for each layout the user has placed it on. **Sparse**: an
 *   absent layout has not been chosen for, and resolves through [searchOn] rather than through a stored default, so a
 *   later change to that default reaches everyone who never touched it. The key is the seam this field renamed itself
 *   at — it held a single `SearchPlacement` under the name `search`, and re-reading that in place would have given
 *   every layout one value while claiming they were independent.
 * @property categoryTabEdge which edge the **category pager's** tab bar sits on — `AppsLayout.PAGER_WITH_CATEGORY`,
 *   the one arrangement that draws tabs, and the reason this is a field rather than an entry in a map.
 */
@Serializable
data class AppsChrome(
    val searchByLayout: Map<AppsLayout, SearchPlacement> = emptyMap(),
    val categoryTabEdge: VerticalEdge = VerticalEdge.TOP,
) {
    /**
     * Where search sits on [layout].
     *
     * **The one place an absent entry is resolved**, so the preview and the control that sets it cannot disagree about
     * what an untouched layout shows.
     */
    fun searchOn(layout: AppsLayout): SearchPlacement = searchByLayout[layout] ?: SearchPlacement.Hidden

    /** This chrome with [layout]'s search moved to [placement]; every other layout untouched. */
    fun withSearch(layout: AppsLayout, placement: SearchPlacement): AppsChrome =
        copy(searchByLayout = searchByLayout + (layout to placement))

    companion object {
        /** Search hidden: a visible default would draw a bar into previews of a surface that has none. */
        val Default = AppsChrome()
    }
}
