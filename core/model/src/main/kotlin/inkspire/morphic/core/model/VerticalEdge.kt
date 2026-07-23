package inkspire.morphic.core.model

/**
 * A top-or-bottom placement for a horizontal element on the [Surface.APPS] surface. Shared by the category
 * tab bar (which lives only in the [AppsLayout.PAGER_WITH_CATEGORY] layout and is always visible, so its
 * position is exactly a [VerticalEdge]) and the standalone search field (see [SearchPlacement.Pinned]).
 */
enum class VerticalEdge { TOP, BOTTOM }
