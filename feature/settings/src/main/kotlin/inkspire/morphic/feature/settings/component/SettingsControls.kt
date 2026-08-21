package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** Provisional spacing — placeholders, like every other surface metric, until the settings layer owns its own. */
private val RowGapV = 12.dp
private val HeaderGapTop = 16.dp

/**
 * The settings surface's own row primitive: a group header, and the room between two rows.
 *
 * **Local to `feature:settings`, not in `core:designsystem`.** There is exactly one consumer, and the design-system
 * rule is to port a group only when the screen needing it exists. They move out if a second surface ever wants them;
 * until then this is the screen that owns its rows. Two things went the other way for that exact reason — the icon
 * studio is a second surface wanting both — so `MorphicSliderRow` and `MorphicSwitchRow` live in the design system
 * and this file keeps only how far apart two rows sit ([SettingsRowPadding]).
 */

/**
 * A group heading inside a settings screen, or above a run of rows in the section list.
 *
 * @param spaceAbove false where nothing is above it to be separated from, which is the **first** heading in a list.
 *   [HeaderGapTop] is a break between two groups; paid at the top of a pane it is just the app bar pushed away.
 */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier, spaceAbove: Boolean = true) {
    val colors = LocalMorphicColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.contentMuted,
        modifier = modifier.padding(top = if (spaceAbove) HeaderGapTop else 0.dp, bottom = RowGapV / 2),
    )
}

/**
 * The breathing room a slider row takes in a settings pane.
 *
 * **The row itself carries none**, deliberately: `MorphicSliderRow` is two stacked rows and the icon studio stacks six
 * of them in a rail where any extra would be a scroll. So the *surface* says how far apart they sit, and it says it
 * once — a `Modifier` shared by every call site rather than a dp repeated at a dozen of them, since the sections'
 * slots are plain `Column`s with no arrangement of their own.
 */
internal val SettingsRowPadding = Modifier.padding(vertical = RowGapV / 2)
