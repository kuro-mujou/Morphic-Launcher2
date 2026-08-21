package inkspire.morphic.data.settings

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
 * @property search where the search field sits, per layout family.
 * @property tabBarEdge which edge the category tab bar sits on. Meaningful only in `AppsLayout.PAGER_WITH_CATEGORY`,
 *   the one layout that has tabs; the others store it and ignore it, which is cheaper than a nullable that every
 *   reader has to branch on.
 */
@Serializable
data class AppsChrome(
    val search: SearchPlacement = SearchPlacement.Hidden,
    val tabBarEdge: VerticalEdge = VerticalEdge.TOP,
) {
    companion object {
        /** Search hidden: a visible default would draw a bar into previews of a surface that has none. */
        val Default = AppsChrome()
    }
}
