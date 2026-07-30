package inkspire.morphic.data.layout

import inkspire.morphic.core.model.Category
import inkspire.morphic.core.model.ComponentKey

/**
 * One category and the apps filed under it, in order — what a category layout renders as a page (the pager) or a
 * card (the card layout), which is why the type is named for the *contents* rather than for either look.
 *
 * @property category the definition: its id, its display name, and where it sorts among categories.
 * @property apps the apps filed under it, in the user's order. **Empty is a normal state**: a category the user
 *   emptied by dragging its last app away keeps its row, so its page stays and can receive apps again. A page that
 *   vanished when emptied could never be dragged back into.
 */
data class CategoryContents(
    val category: Category,
    val apps: List<ComponentKey>,
)
