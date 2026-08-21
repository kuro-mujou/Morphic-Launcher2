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
 * **Search is per layout, the tab bar is not**, and the asymmetry is a fact about the surface rather than an
 * inconsistency. Each arrangement draws its own chrome and a user picks per arrangement — a field pinned to the bottom
 * of the list is not a choice they made about the category cards. Tabs exist on `PAGER_WITH_CATEGORY` alone, so
 * "per layout" for [tabBarEdge] would be four entries no layout can read.
 *
 * @property searchByLayout where the search field sits, for each layout the user has placed it on. **Sparse**: an
 *   absent layout has not been chosen for, and resolves through [searchOn] rather than through a stored default, so a
 *   later change to that default reaches everyone who never touched it. The key is the seam this field renamed itself
 *   at — it held a single `SearchPlacement` under the name `search`, and re-reading that in place would have given
 *   every layout one value while claiming they were independent.
 * @property tabBarEdge which edge the category tab bar sits on. Meaningful only in `AppsLayout.PAGER_WITH_CATEGORY`,
 *   the one layout that has tabs; the others store it and ignore it, which is cheaper than a nullable that every
 *   reader has to branch on.
 */
@Serializable
data class AppsChrome(
    val searchByLayout: Map<AppsLayout, SearchPlacement> = emptyMap(),
    val tabBarEdge: VerticalEdge = VerticalEdge.TOP,
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
