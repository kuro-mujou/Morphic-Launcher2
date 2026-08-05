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
 * consumers. L1 kept the same two facts on its `DrawerSettings`, beside the grid profiles — which is exactly the
 * fusing this port exists to undo.
 *
 * **Two of L1's fields, and the types are `core:model`'s own.** `SearchPlacement` is layout-aware where L1's flat
 * `SearchPosition` was not (a standalone layout pins the field to an edge; the category pager embeds it in the header,
 * so it has no edge to choose), and the tab bar's placement *is* a [VerticalEdge] — which that enum's KDoc has said
 * since B0, naming this exact consumer.
 *
 * @property search where the search field sits, per layout family. See the caveat on [Default] about the default.
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
        /**
         * **Search hidden, which is where this departs from L1** — its `SearchPosition` defaults to `TOP`.
         *
         * The reason is that the APPS surface does not render a search field yet, and neither pager renders a tab bar:
         * both are settings the *editor preview* honours today and the surface will honour when those features land.
         * A default of `Pinned(TOP)` would therefore draw every APPS preview with a search bar the launcher has not
         * got, which is the one thing a preview must not do. Flipping this to L1's default is a one-line change on the
         * day search ships, and until then the default is the state that matches what is actually drawn.
         */
        val Default = AppsChrome()
    }
}
