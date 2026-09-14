package inkspire.morphic.feature.home.gestureaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.data.apps.AppShortcut

/**
 * One app's shortcuts as an [ExpandableCard]: the app and how many shortcuts it has, and — open — each of them.
 *
 * **Closed by default, one card per app.** Apps publish two to four shortcuts each, so a section showing every row
 * buries the app being looked for under everyone else's; closed, it reads as an index of apps and costs one tap.
 *
 * **Open by default when a closed card would hide what the user came for**: while searching, because the rows *are*
 * the results and a match behind a chevron is a match not found; and when the card holds the current choice, because a
 * selected mark inside a closed card marks nothing.
 */
@Composable
internal fun ShortcutGroupCard(
    group: ShortcutGroup,
    assigned: GestureAction?,
    searching: Boolean,
    onChoose: (AppShortcut) -> Unit,
) {
    ExpandableCard(
        items = group.shortcuts,
        openByDefault = searching || group.shortcuts.any { assigned.isThis(it) },
        header = { ShortcutGroupHeader(group) },
        row = { shortcut ->
            ShortcutChoiceRow(
                shortcut = shortcut,
                selected = assigned.isThis(shortcut),
                onClick = { onChoose(shortcut) },
            )
        },
    )
}

/**
 * The card's header: the app and its shortcut count.
 *
 * **The icon is the launcher's own**, through [AppIcon], so an app is recognized here exactly as it is on home; the
 * shortcut icons below it are the app's to draw. The count is of what the card will show — under a search, the hits.
 */
@Composable
private fun RowScope.ShortcutGroupHeader(group: ShortcutGroup) {
    val colors = LocalMorphicColors.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    AppIcon(
        component = group.app.componentKey,
        contentDescription = null,
        sizePx = sizePx,
        modifier = Modifier.size(40.dp),
    )
    Text(
        text = group.app.label,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 16.dp),
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            // The page's own gray, sunk into the card: `surfaceElevated` is the card's white in the light theme.
            .background(colors.background),
    ) {
        Text(
            text = group.shortcuts.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.contentMuted,
        )
    }
}

/**
 * One of an app's shortcuts: the icon the app rasterized for it, and its own name.
 *
 * The icon sits in a 40dp column, the header's icon width, so every label in the card starts on one line.
 */
@Composable
private fun ShortcutChoiceRow(shortcut: AppShortcut, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            // **Drawn as the app rasterized it, not run through `core:icon`.** A shortcut icon is the app's own
            // badge-and-glyph composition, which `AppShortcut` says in as many words — restyling it into one of our
            // layer stacks would produce something the app never published. Absent, the empty box keeps the column.
            shortcut.icon?.let { icon ->
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onAccent else colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        if (selected) SelectedMark()
    }
}

/** Whether this stored action is the given live shortcut — the three fields that identify one, never the label. */
private fun GestureAction?.isThis(shortcut: AppShortcut): Boolean {
    val stored = this as? GestureAction.LaunchShortcut ?: return false
    return stored.id == shortcut.id &&
        stored.packageName == shortcut.packageName &&
        stored.userSerial == shortcut.userSerial
}
