package inkspire.morphic.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.component.SettingsNavRow
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.setup.SetupHub
import inkspire.morphic.feature.settings.setup.SetupHubViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The settings index: a titled panel per group, holding one row per section.

 * **A group is one `item`, not one per row.** Its rows share a panel, so they are measured and clipped together —
 * and with seven rows in the whole index there is nothing for laziness to save. The `LazyColumn` stays for its
 * `contentPadding`, which is what lets rows scroll under the navigation bar while the pane's background still
 * reaches the window edge.
 *
 * **One list for both panes**, differing only in how it marks position: a two-pane layout *highlights* the section
 * showing beside it, a single-pane one shows a chevron because tapping goes somewhere. L1 had the same two flags for
 * the same reason, and they are worth keeping — a highlight in single-pane would mark a row the user has already left,
 * and a chevron in two-pane would promise a journey that does not happen.
 *
 * **The bars are content padding, not layout padding**, which is what lets rows scroll *under* the navigation bar
 * while the pane's background still reaches the window edge behind it. The caller says which edges apply, because only
 * it knows whether this list has the screen to itself or a detail pane beside it.
 *
 * @param homeLayout HOME's pairing, which two of the rows are named for — its main area is a grid or a list, and its
 *   side zone is a dock or a widget area. A row that contradicted the pane it opens would be worse than a generic one.
 * @param selected the section being shown, or null when the list is the whole screen.
 * @param highlightSelected true in two-pane, where [selected] is on screen beside this.
 * @param showChevron true in single-pane, where a tap opens a new pane.
 * @param insetSides the edges whose system bars / cutout this list keeps its rows clear of.
 * @param onOpenPaywall opens the subscription screen, a destination outside settings.
 */
@Composable
internal fun SettingsList(
    homeLayout: HomeLayout,
    selected: SettingsSection?,
    onSelect: (SettingsSection) -> Unit,
    highlightSelected: Boolean,
    showChevron: Boolean,
    insetSides: WindowInsetsSides,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding = uiInsets
        .only(insetSides)
        .add(WindowInsets(top = 8.dp, bottom = 12.dp))
        .asPaddingValues()
    // **The row to mark is the listed ancestor**, since not every section has a row of its own: HOME's two zones are
    // reached through the Home hub, so a Dock pane showing beside this list must mark *Home*. Resolved here rather
    // than by the caller because which sections are listed is this file's business, and a caller passing an unlisted
    // section would otherwise highlight nothing — a two-pane screen with a detail and no marked row, which reads as
    // the list having lost its place.
    val marked = selected?.let { it.parent ?: it }

    // **The setup hub is read here rather than passed in, unlike [homeLayout], and the asymmetry is the point.** The
    // pairing is a parameter because the *app bar* is named from it too, and a list that resolved it separately could
    // disagree with the title above it. Nothing else draws the hub, so there is nothing for it to disagree with — and
    // threading it through both pane composables would have added an argument each that neither reads.
    val setup = koinViewModel<SetupHubViewModel>()
    val hub by setup.state.collectAsStateWithLifecycle()
    // Re-derived on every resume, because the home-role step is completed in a system dialog that reports nothing
    // back: being shown again is the only moment we can learn the answer changed. Here rather than in the hub, which is
    // not composed while it is empty — and an empty hub is exactly the one that must learn the role was lost.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setup.refresh() }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        // **Above the index, and drawn only while it has a row.** The top of this list is the most valuable space the
        // app has, which is the argument against spending it on a permanent card: the hub is a list of questions, and
        // one with none left stops being drawn.
        //
        // **But the item itself is always there, first, even empty** — and that is what keeps the hub on screen. The
        // steps arrive a moment after the list composes, and a lazy list keeps its first visible item where it is when
        // an item is inserted *above* it: added only once it had rows, the hub landed above the viewport, and a user
        // arriving from "Finish setup" opened on a list scrolled past the very thing they came for. An item present
        // from the first frame grows downward instead.
        item(key = "setup-hub") {
            if (hub.steps.isNotEmpty()) {
                SetupHub(
                    state = hub,
                    homeLayout = homeLayout,
                    onOpenSection = onSelect,
                    onDismiss = setup::dismiss,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Its own panel above the groups: it opens a screen outside settings rather than a section of it.
        item(key = "premium") {
            MorphicGroupPanel(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (hub.steps.isNotEmpty()) 16.dp else 0.dp),
            ) {
                SettingsNavRow(
                    title = "Morphic Premium",
                    icon = Icons.Outlined.WorkspacePremium,
                    selected = false,
                    showChevron = showChevron,
                    onClick = onOpenPaywall,
                )
            }
        }

        settingsGroups.forEach { group ->
            item(key = "group-${group.header ?: group.sections.first()}") {
                // **The break between two groups is paid here, not by the heading.** A heading that pays its own separates
                // nothing when a group has none: `About` is one unheaded row, and welded to the bottom of the panel above it
                // would read as one more of that panel's rows wearing a different corner radius. What the eye is reading is
                // the gap between two *panels*, which is this list's to give whether or not a word sits in it. The first group
                // is never first: the Premium panel is always above it.
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                ) {
                    if (group.header != null) {
                        SettingsSectionHeader(group.header, spaceAbove = false)
                    }
                    MorphicGroupPanel {
                        group.sections.forEach { section ->
                            SettingsNavRow(
                                section = section,
                                homeLayout = homeLayout,
                                selected = highlightSelected && section == marked,
                                showChevron = showChevron,
                                onClick = { onSelect(section) },
                            )
                        }
                    }
                }
            }
        }
    }
}
