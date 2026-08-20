package inkspire.morphic.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.component.NavRowInsetH
import inkspire.morphic.feature.settings.component.NavRowPadding
import inkspire.morphic.feature.settings.component.SettingsNavRow
import inkspire.morphic.feature.settings.component.SettingsSectionHeader


/**
 * The settings index: grouped rows of section → title, subtitle and glyph.
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
        .add(WindowInsets(top = NavRowPadding, bottom = NavRowPadding))
        .asPaddingValues()
    // **The row to mark is the listed ancestor**, since not every section has a row of its own: HOME's two zones are
    // reached through the Home hub, so a Dock pane showing beside this list must mark *Home*. Resolved here rather
    // than by the caller because which sections are listed is this file's business, and a caller passing an unlisted
    // section would otherwise highlight nothing — a two-pane screen with a detail and no marked row, which reads as
    // the list having lost its place.
    val marked = selected?.let { it.parent ?: it }
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        settingsGroups.forEach { group ->
            if (group.header != null) {
                item(key = "header-${group.header}") {
                    SettingsSectionHeader(group.header, Modifier.padding(horizontal = NavRowInsetH + NavRowPadding))
                }
            }
            items(group.sections, key = { it.name }) { section ->
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
