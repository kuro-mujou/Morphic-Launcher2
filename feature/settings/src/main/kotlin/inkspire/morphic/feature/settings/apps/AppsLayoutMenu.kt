package inkspire.morphic.feature.settings.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.feature.settings.label
import org.koin.androidx.compose.koinViewModel

/**
 * The APPS section's arrangement selector, living in the **app bar** rather than in the pane.
 *
 * **A pane-wide scope selector cannot be pane content.** Every control in `AppsDetail` is addressed to whichever
 * arrangement this names — the grid counts, the margin, the search placement, the icon sizing — so scrolled off the
 * top it leaves five settings wearing one face. It was a pinned chip row for exactly one build, and landscape is what
 * ruled that out: five chips wrap to two lines, and two lines of chips above a short viewport left the grid editor
 * with nothing. A button costs one slot in a bar that is already there, in either orientation.
 *
 * **It shares `AppsDetail`'s ViewModel rather than owning state.** `koinViewModel` keys on the type and the settings
 * route is one `NavEntry`, so the bar and the pane below it resolve the same instance — which is the whole mechanism
 * here: choosing in this menu moves the pane. That only holds because `NavDisplay` is given
 * `rememberViewModelStoreNavEntryDecorator()`; without it every entry shares the Activity's store and this would work
 * by accident rather than by design.
 *
 * **A menu says in words what a chip had no room for.** Which arrangements a home edge actually opens is a second
 * fact about each one, independent of which is being edited, and a user can spend a long time tuning a grid nothing
 * on their device can reach. As chips that had to be a dot with no legend; as menu rows it is two plain words.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppsLayoutMenu() {
    val viewModel = koinViewModel<AppsSectionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        // Both halves open the menu. A split button's halves are normally two verbs — L1's wallpaper button acts on
        // the left and picks a target on the right — and there is no action to perform here, only a choice; the split
        // is kept for the affordance, which reads as "this opens something" where a bare label does not.
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = { expanded = true }) {
                    Text(text = state.layout.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(checked = expanded, onCheckedChange = { expanded = it }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Choose an arrangement",
                        modifier = Modifier.size(TrailingGlyph),
                    )
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ConfigurableLayouts.forEach { layout ->
                val selected = layout == state.layout
                // **Filled, not ticked**, which is this codebase's one way of saying "selected" — `SettingsNavRow`
                // marks the open section exactly like this, and the segmented control and the chips it replaced both
                // read by fill too. A leading check also cost the menu its alignment: the icon slot indents the one
                // row that has it, so a five-row menu had four labels on one margin and a fifth on another.
                val content = if (selected) colors.onAccent else colors.content
                DropdownMenuItem(
                    text = { Text(text = layout.label, color = content) },
                    // The fill says "you are editing this"; the words say "your device opens this". The pair worth
                    // spotting is a filled row with no words beside it — a pane of controls for an arrangement
                    // nothing reaches.
                    trailingIcon = if (layout !in state.boundLayouts) {
                        null
                    } else {
                        {
                            Text(
                                text = "In use",
                                style = MaterialTheme.typography.labelSmall,
                                // Muted against the menu, but on a filled row the fill *is* the emphasis and a second
                                // gray would only be hard to read.
                                color = if (selected) content else colors.contentMuted,
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = RowInsetH, vertical = RowInsetV)
                        .clip(RoundedCornerShape(RowCorner))
                        .background(if (selected) colors.accent else Color.Transparent),
                    onClick = {
                        expanded = false
                        viewModel.selectLayout(layout)
                    },
                )
            }
        }
    }
}

/** Short of the trailing button's own slot, as every glyph in this module is. */
private val TrailingGlyph = 20.dp

/** A menu row's selected fill, inset and rounded as `SettingsNavRow`'s is so the two read as one idea. */
private val RowInsetH = 8.dp
private val RowInsetV = 2.dp
private val RowCorner = 12.dp
