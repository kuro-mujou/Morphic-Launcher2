package inkspire.morphic.feature.apps.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import inkspire.morphic.core.designsystem.insets.uiInsets

/**
 * The insets a scrolling APPS layout gives its content: the system bars and cutout, plus the grid's own
 * [horizontal] padding on each side.
 *
 * **One helper because the two are added in the same place and must be**, not because they mean the same thing. The
 * bar inset is a system constraint — content may pass *under* the bars but must not come to rest beneath them — and
 * the padding is the user's margin. Both end up as **content** padding rather than padding on the list, which is what
 * keeps the scrolling content running under the bars instead of stopping short of them, and what keeps a fling
 * gesture usable at the very edge of the screen.
 *
 * **Which sides is the caller's answer, not this helper's.** A search field pinned to an edge takes that edge's bar
 * inset for itself, and content that also reserved it would leave a phantom band under the field — the same division
 * of responsibility the settings shell states as "which sides is the shell's answer, not the pane's". Everything but
 * that one edge stays the content's.
 *
 * **The width arithmetic depends on this being one value.** A grid divides "whatever is left after the content
 * padding" into columns, so a layout that measured against the bar inset alone and then added a margin would size its
 * cells for a width it does not have. Callers that need that number take it from the returned value's own
 * start/end — see [horizontalExtent] — rather than re-deriving it.
 */
@Composable
internal fun appsContentPadding(
    horizontal: Dp,
    sides: WindowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
): PaddingValues {
    val bars = uiInsets.only(sides).asPaddingValues()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = bars.calculateStartPadding(direction) + horizontal,
        top = bars.calculateTopPadding(),
        end = bars.calculateEndPadding(direction) + horizontal,
        bottom = bars.calculateBottomPadding(),
    )
}

/** How much width these insets consume in total — what a grid must subtract before dividing by its columns. */
@Composable
internal fun PaddingValues.horizontalExtent(): Dp {
    val direction = LocalLayoutDirection.current
    return calculateStartPadding(direction) + calculateEndPadding(direction)
}
