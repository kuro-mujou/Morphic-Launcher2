package inkspire.morphic.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
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
import inkspire.morphic.feature.settings.setup.SetupRow
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
 */
@Composable
internal fun SettingsList(
    homeLayout: HomeLayout,
    selected: SettingsSection?,
    onSelect: (SettingsSection) -> Unit,
    highlightSelected: Boolean,
    showChevron: Boolean,
    insetSides: WindowInsetsSides,
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

    // **The setup step is read here rather than passed in, unlike [homeLayout], and the asymmetry is the point.** The
    // pairing is a parameter because the *app bar* is named from it too, and a list that resolved it separately could
    // disagree with the title above it. Nothing else draws this row, so there is nothing for it to disagree with —
    // and threading it through both pane composables would have added an argument each that neither reads.
    val shell = koinViewModel<SettingsShellViewModel>()
    val defaultLauncherRequest by shell.defaultLauncherRequest.collectAsStateWithLifecycle()
    // Re-derived on every resume, because the ask is completed in a system dialog that reports nothing back: being
    // shown again is the only moment we can learn the answer changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { shell.refreshDefaultLauncher() }
    // **Started for a result, and the result is never read** — which is not ceremony, it is the only way the request
    // works. `RequestRoleActivity` identifies its asker through `getCallingPackage()`, and that is populated only for
    // an activity started *for a result*; launched plainly it reads null, logs "Package name cannot be null or
    // empty", and finishes before drawing. No dialog, no error, nothing to catch — the row simply appears to do
    // nothing. Re-deriving still belongs to the resume above, which is what also covers the pre-29 route, where the
    // user finishes in a settings screen that reports nothing either way.
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        // **Above the index, and only while it is needed.** The top of this list is the most valuable space the app
        // has, which is the argument against spending it on a permanent card: this row is a question, and a question
        // that has been answered stops being drawn.
        defaultLauncherRequest?.let { request ->
            item(key = "setup-default-launcher") {
                MorphicGroupPanel(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SetupRow(
                        icon = Icons.Outlined.Home,
                        title = "Set as default launcher",
                        supporting = "Press the home button and this launcher opens.",
                        onClick = { roleRequest.launchSafely(request) },
                    )
                }
            }
        }

        settingsGroups.forEachIndexed { index, group ->
            item(key = "group-${group.header ?: index}") {
                // **The break between two groups is paid here, not by the heading.** A heading that pays its own
                // separates nothing when a group has none: `Extras` is one unheaded row, and it sat welded to the
                // bottom of the Layout panel — a sixth Layout row wearing a different corner radius. What the eye is
                // reading is the gap between two *panels*, which is this list's to give whether or not a word sits in
                // it. The first group takes none when it is first — it is already under the app bar — and pays it
                // like any other once the setup row is above it.
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = if (index > 0 || defaultLauncherRequest != null) 16.dp else 0.dp),
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

/**
 * Launches [intent], or does nothing if no activity will take it.
 *
 * The request is checked for a receiver before the row that launches it is drawn — this covers the gap between that
 * check and the tap, which a package disabled in between is enough to open. A settings screen must not be able to
 * crash on a row it offered.
 */
private fun ActivityResultLauncher<Intent>.launchSafely(intent: Intent) {
    runCatching { launch(intent) }.onFailure { if (it !is ActivityNotFoundException) throw it }
}
