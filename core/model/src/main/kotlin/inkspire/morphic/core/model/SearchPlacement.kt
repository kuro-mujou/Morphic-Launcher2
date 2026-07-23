package inkspire.morphic.core.model

/**
 * Placement of the Apps search field (a text field):
 *  - standalone layouts (list / grid / pager / category cards): [Pinned] to the top or bottom edge, or [Hidden].
 *  - the pager-with-category layout: [InHeader] (embedded in the compact header) or [Hidden].
 */
sealed interface SearchPlacement {
    data class Pinned(val edge: VerticalEdge) : SearchPlacement
    data object InHeader : SearchPlacement
    data object Hidden : SearchPlacement
}