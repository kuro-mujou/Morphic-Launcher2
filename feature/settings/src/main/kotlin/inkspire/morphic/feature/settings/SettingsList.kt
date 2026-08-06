package inkspire.morphic.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.component.SettingsSectionHeader

/** Provisional spacing — placeholders, as everywhere else in this module. */
private val RowInsetH = 12.dp
private val RowInsetV = 4.dp
private val RowPadding = 12.dp
private val IconGap = 16.dp

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
        .add(WindowInsets(top = RowPadding, bottom = RowPadding))
        .asPaddingValues()
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        settingsGroups.forEach { group ->
            if (group.header != null) {
                item(key = "header-${group.header}") {
                    SettingsSectionHeader(group.header, Modifier.padding(horizontal = RowInsetH + RowPadding))
                }
            }
            items(group.sections, key = { it.name }) { section ->
                SettingsNavRow(
                    section = section,
                    homeLayout = homeLayout,
                    selected = highlightSelected && section == selected,
                    showChevron = showChevron,
                    onClick = { onSelect(section) },
                )
            }
        }
    }
}

/**
 * One row.
 *
 * Selection reads by **contrast rather than hue** — the palette is greyscale by design, so a selected row takes the
 * accent as its background where L1 used `secondaryContainer`. A plain `clickable`, as everywhere in settings: the
 * shared `launcherItemGestures` contract exists so *launcher surfaces* cannot drift on long-press timing, and settings
 * is ordinary app chrome that should behave like the platform.
 */
@Composable
private fun SettingsNavRow(
    section: SettingsSection,
    homeLayout: HomeLayout,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    val meta = section.meta(homeLayout)
    val content = if (selected) colors.onAccent else colors.content
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInsetH, vertical = RowInsetV)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(RowPadding),
    ) {
        Icon(imageVector = meta.icon, contentDescription = null, tint = content)
        Spacer(Modifier.width(IconGap))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(meta.title, style = MaterialTheme.typography.bodyLarge, color = content)
            Text(
                text = meta.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) content else colors.contentMuted,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(RowPadding))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.contentMuted,
            )
        }
    }
}
