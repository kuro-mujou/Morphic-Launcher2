package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/*
 * The vertical rhythm a settings pane is written in: how far apart two rows sit, and how far a group heading stands
 * off the run above it.
 *
 * **One file because there is one number.** A row pays [RowGapV] halved above and below, and a heading pays the same
 * half beneath itself — so a heading sits exactly as far from its first row as two rows sit from each other, and the
 * run reads as one column rather than as a heading with its own opinion. Split across two files that agreement holds
 * by intention, which is the way this codebase keeps rediscovering that it does not hold. [HeaderGapTop] is the only
 * number here that is genuinely its own, being a *break* rather than a rhythm.
 *
 * **Local to `feature:settings`, not `core:designsystem`.** The rule is that a thing moves into the design system
 * when a *second surface* wants it — not a second call site, of which the heading has plenty. `MorphicSliderRow` and
 * `MorphicSwitchRow` were both here and both left the day the icon studio wanted them; these two have stayed because
 * nothing outside settings lays out a settings pane.
 *
 * A plain comment rather than KDoc, as `StepGrid` is: it describes the file, and a KDoc block ahead of the first
 * declaration documents nothing at all.
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
        modifier = modifier.padding(top = if (spaceAbove) 16.dp else 0.dp, bottom = 12.dp / 2),
    )
}

/**
 * The breathing room a control row takes in a settings pane — a slider, a range, a switch.
 *
 * **The rows themselves carry none**, deliberately: `MorphicSliderRow` is two stacked rows and the icon studio stacks
 * six of them in a rail where any extra would be a scroll. So the *surface* says how far apart they sit, and it says
 * it once — a `Modifier` shared by every call site rather than a dp repeated at a dozen of them, since the sections'
 * slots are plain `Column`s with no arrangement of their own.
 */
internal val SettingsRowPadding = Modifier.padding(vertical = 12.dp / 2)
