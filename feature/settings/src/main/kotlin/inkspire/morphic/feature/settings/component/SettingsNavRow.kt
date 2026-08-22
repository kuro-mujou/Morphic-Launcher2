package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.HomeLayout
import inkspire.morphic.feature.settings.SettingsSection
import inkspire.morphic.feature.settings.meta

/**
 * A row that names a section and opens it — the settings index's row, and now the **hub's** row too.
 *
 * Extracted from `SettingsList` on its second consumer, which is this module's standing rule (`IconPreviewPlate`,
 * `LanePreview`, `Modifier.repeatingPress`). What would have drifted is not the look but the *vocabulary*: both call
 * sites resolve their title, subtitle and glyph through [SettingsSection.meta], so a hand-rolled row in the Home hub
 * would eventually name a zone differently from the way the list names it — exactly the fault `LayoutLabels` exists to
 * fix, one level up.
 *
 * **A title and nothing under it.** Every row carried a second line describing its section, and read down a list they
 * were one sentence with the nouns shuffled — four of the seven ended in "and icons". A row in an index has one job,
 * which is to be recognized; what a section covers is answered by opening it, and answered better.
 *
 * **It fills its panel edge to edge**, taking neither inset nor rounding of its own — [SettingsGroupCard] separates
 * one run of rows from the next and cuts the corners its children paint into. That is what lets a selected row's fill
 * span the full width instead of floating inside it.
 *
 * Selection reads by **contrast rather than hue**, since the palette is grayscale by design and red is reserved for
 * `error`. A plain `clickable`: the shared `launcherItemGestures` contract exists so *launcher surfaces* cannot drift
 * on long-press timing, and settings is ordinary app chrome that should behave like the platform.
 *
 * @param selected marks the row whose pane is showing *beside* this list — two-pane only. In single-pane a highlight
 *   would mark a row the user has already left.
 * @param showChevron true where a tap opens a new pane, which is single-pane and every hub row. In two-pane it would
 *   promise a journey that does not happen.
 */
@Composable
internal fun SettingsNavRow(
    section: SettingsSection,
    homeLayout: HomeLayout,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val meta = section.meta(homeLayout)
    val content = if (selected) colors.onAccent else colors.content
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Icon(imageVector = meta.icon, contentDescription = null, tint = content)
        Spacer(Modifier.width(16.dp))
        Text(
            text = meta.title,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (showChevron) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.contentMuted,
            )
        }
    }
}
