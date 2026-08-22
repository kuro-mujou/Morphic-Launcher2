package inkspire.morphic.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * A run of rows on one rounded panel — **the launcher's grouped-list container**.
 *
 * **The panel is what separates one group from the next, so the rows inside it need nothing.** They sit flush
 * against each other with no gap, no inset and no rounding of their own: a group reads as one object, and where it
 * ends is the edge of the panel rather than a measured space the eye has to compare against the space between rows.
 *
 * **It owns the clip, which is what lets a row paint to the edge.** A selected row fills its whole width; at the top
 * or bottom of the panel that fill would square off the corner it sits in, so the corner is cut here, once, around
 * every child. A row that clipped itself instead would have to know where in the run it was.
 *
 * Written in `feature:settings` for the section list and the Home hub, and moved here on the third consumer — the
 * gesture action picker — which is the bar this codebase holds extraction to. What would have drifted is the
 * panel's own dress: a hand-rolled second one eventually disagrees about a corner or a gray, and these sit one tap
 * apart.
 */
@Composable
fun MorphicGroupPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner))
            .background(colors.surface),
        content = content,
    )
}

/** How far a group's panel sits from the pane's edges, and how round it is. */
val GroupInsetH = 16.dp
private val GroupCorner = 20.dp
