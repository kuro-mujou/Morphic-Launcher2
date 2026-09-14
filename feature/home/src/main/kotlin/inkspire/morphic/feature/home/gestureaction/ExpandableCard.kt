package inkspire.morphic.feature.home.gestureaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/**
 * A card in the action picker that opens in place: a header that is always visible and, open, a divider and a row per
 * item.
 *
 * **Open on its own when closed would hide what the user came for** — [openByDefault], which the caller decides. A tap
 * inverts that default rather than setting an absolute, and is forgotten whenever the default changes, so a card a
 * search opened closes again when the search is cleared.
 *
 * The card's edge is what says where one group ends, which is why the rows inside can take a divider each without the
 * group dissolving into a list of equals.
 *
 * @param header what the header shows, ahead of the chevron the card adds. The whole header toggles.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun <T> ExpandableCard(
    items: List<T>,
    openByDefault: Boolean,
    header: @Composable RowScope.() -> Unit,
    row: @Composable (T) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val motion = MaterialTheme.motionScheme
    var flipped by rememberSaveable(openByDefault) { mutableStateOf(false) }
    val expanded = openByDefault != flipped

    MorphicGroupPanel(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        ExpandableCardHeader(expanded = expanded, onToggle = { flipped = !flipped }, content = header)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
        ) {
            Column {
                items.forEach { item ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.divider),
                    )
                    row(item)
                }
            }
        }
    }
}

/** The card's always-visible row: the caller's [content], then a chevron that turns with the card. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpandableCardHeader(expanded: Boolean, onToggle: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = LocalMorphicColors.current.contentMuted,
            modifier = Modifier
                .padding(start = 8.dp)
                .rotate(chevronTurn),
        )
    }
}
