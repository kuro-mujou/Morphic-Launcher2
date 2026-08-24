package inkspire.morphic.core.designsystem.collection

import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * What a collection's **Add** cell offers, and where its choices go.
 *
 * **One type rather than two parameters**, because they are meaningless apart — a list of apps with nowhere to send
 * them, or a commit with nothing to commit — and because `AppCollectionOverlay` is already at this codebase's
 * parameter-count limit, where a pair costs twice what a pair-shaped value does.
 *
 * Its nullability is what says whether a collection can be filled from a picker at all: **null offers no cell**,
 * which is this launcher's absent-not-disabled rule applied to a whole affordance rather than to a row.
 *
 * @property offered every app that could be added, in the order to offer them — **unfiltered**. The overlay
 *   subtracts what the collection already holds, because it is already holding it: `apps` is exactly that list, for a
 *   folder's membership and a category's alike. Leaving the subtraction here means one sorted list serves every
 *   collection on a surface, built once, rather than a filtered copy per open collection.
 * @property onAdd the chosen apps, in the order they were ticked. Never called with an empty list.
 */
class AppAdditions(
    val offered: List<AppInfo>,
    val onAdd: (List<ComponentKey>) -> Unit,
)
