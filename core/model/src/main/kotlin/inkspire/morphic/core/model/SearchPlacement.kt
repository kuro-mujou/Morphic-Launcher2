package inkspire.morphic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where the search field sits on the [Surface.APPS] surface. The available options depend on the layout, so
 * this is a sealed type rather than a flat enum:
 *
 * - In the standalone layouts ([AppsLayout.VERTICAL_LIST], [AppsLayout.VERTICAL_GRID], [AppsLayout.PAGER],
 *   [AppsLayout.CATEGORY_CARD]) the field is a standalone element: [Pinned] to an edge, or [Hidden].
 * - In [AppsLayout.PAGER_WITH_CATEGORY] the field is embedded in the compact header beside the category
 *   tabs, so it has no edge choice: [InHeader], or [Hidden].
 *
 * Serialized polymorphically with short [SerialName]s, as `BackdropEffect` is and for the same reason: it is stored in
 * a settings slice, so the discriminator ends up in a user's blob and must survive a class rename.
 */
@Serializable
sealed interface SearchPlacement {
    /** A standalone search field pinned to the top or bottom [edge] (standalone Apps layouts). */
    @Serializable
    @SerialName("pinned")
    data class Pinned(val edge: VerticalEdge) : SearchPlacement

    /** Search embedded in the compact header of the [AppsLayout.PAGER_WITH_CATEGORY] layout. */
    @Serializable
    @SerialName("in_header")
    data object InHeader : SearchPlacement

    /** Search not shown. */
    @Serializable
    @SerialName("hidden")
    data object Hidden : SearchPlacement
}