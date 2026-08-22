package inkspire.morphic.feature.home.gestureaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.data.apps.AppShortcut
import kotlinx.coroutines.launch

/**
 * Choosing what one gesture does — **one list with jump-to-section chips**, not a set of filtered tabs.
 *
 * The chips scroll rather than filter, which is the behavior worth copying from the launchers that do this well: it
 * keeps a single scroll from Apps into Shortcuts, and lets one search box narrow everything at once instead of
 * asking which tab the query applies to. As filters they would look identical in a mockup and read as four separate
 * screens in the hand.
 *
 * **The sections here are the ones we can actually perform.** System actions — screen off, notification shade,
 * recents — each need an `AccessibilityService`, which is a feature with its own permission flow; the section is
 * absent until that exists rather than present and inert, which is this codebase's standing rule for a verb with no
 * op behind it. Navigation actions are the same story one step behind.
 *
 * @param onBack returns to the sheet the gesture was chosen from.
 * @param onChosen called after a choice is written, so the caller can close this destination — the screen does not
 *   navigate itself, for `LauncherShell`'s reason: `app` owns the back stack.
 */
@Composable
internal fun GestureActionScreen(
    gesture: ItemGesture,
    viewModel: GestureActionViewModel,
    onBack: () -> Unit,
    onChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Where each section starts, rebuilt whenever the list does — a chip cannot scroll to an index it computed
    // against a different list, and the search box changes the list on every keystroke.
    val appsAt = 2
    val shortcutsAt by remember(state.apps.size) { derivedStateOf { appsAt + state.apps.size + 1 } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .uiInsetsPadding(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) {
        Text(
            text = "Assign action to ${gesture.label}",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.content,
            modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = ScreenPadding),
        )
        GestureActionSearch(
            query = state.query,
            onQuery = viewModel::search,
            modifier = Modifier.padding(ScreenPadding),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = ChipGap),
        ) {
            SectionChip("Apps") { scope.launch { listState.animateScrollToItem(appsAt) } }
            SectionChip("Shortcuts") { scope.launch { listState.animateScrollToItem(shortcutsAt) } }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "none") {
                // **First, not behind a chip of its own.** Clearing is the one choice every gesture can make, and a
                // "Suggested" tab holding a single row would be a filter for nothing.
                ChoiceRow(
                    label = "None",
                    selected = state.assigned == null,
                    onClick = { viewModel.choose(null); onChosen() },
                )
            }
            item(key = "apps-header") { SectionHeader("APPS") }
            items(state.apps, key = { it.componentKey.flatten() }) { app ->
                ChoiceRow(
                    label = app.label,
                    selected = (state.assigned as? GestureAction.LaunchApp)?.component == app.componentKey,
                    onClick = { viewModel.chooseApp(app.componentKey); onChosen() },
                )
            }
            item(key = "shortcuts-header") { SectionHeader("SHORTCUTS") }
            if (state.loadingShortcuts) {
                item(key = "shortcuts-loading") { SectionNote("Reading shortcuts…") }
            } else if (state.shortcutGroups.isEmpty()) {
                // Empty has two very different causes and only one is worth acting on, so it says both.
                item(key = "shortcuts-empty") {
                    SectionNote("No shortcuts. Apps publish these, and only the active home app may read them.")
                }
            }
            state.shortcutGroups.forEach { group ->
                item(key = "group-${group.app.componentKey.flatten()}") { GroupHeader(group.app) }
                items(group.shortcuts, key = { "${group.app.componentKey.packageName}#${it.id}" }) { shortcut ->
                    ChoiceRow(
                        label = shortcut.label,
                        selected = state.assigned.isThis(shortcut),
                        indented = true,
                        onClick = { viewModel.chooseShortcut(shortcut); onChosen() },
                    )
                }
            }
        }
    }

    BackHandler(onBack = onBack)
}

/**
 * The search box.
 *
 * **The `TextFieldState` is hoisted here rather than mirrored into the ViewModel**, which is `MorphicTextField`'s own
 * rule: its config-change survival needs the caller to own the state. What crosses into the ViewModel is the text,
 * pushed on every edit, because the filtering belongs where the lists are built.
 */
@Composable
private fun GestureActionSearch(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberTextFieldState(query)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect(onQuery)
    }
    MorphicTextField(state = state, placeholder = "Search", modifier = modifier.fillMaxWidth())
}

/** Whether this stored action is the given live shortcut — the three fields that identify one, never the label. */
private fun GestureAction?.isThis(shortcut: AppShortcut): Boolean {
    val stored = this as? GestureAction.LaunchShortcut ?: return false
    return stored.id == shortcut.id &&
        stored.packageName == shortcut.packageName &&
        stored.userSerial == shortcut.userSerial
}

/** One choosable action. Selection reads by fill, as every selected thing in this launcher does. */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    indented: Boolean = false,
) {
    val colors = LocalMorphicColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) colors.onAccent else colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = RowInsetV)
            .clip(RoundedCornerShape(RowCorner))
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) IndentedStart else RowPadding,
                top = RowPadding,
                bottom = RowPadding,
                end = RowPadding,
            ),
    )
}

/** A group of one app's shortcuts, headed by the app it belongs to. */
@Composable
private fun GroupHeader(app: AppInfo) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding + RowPadding, vertical = RowPadding),
    ) {
        Box(Modifier.size(GroupDot).clip(RoundedCornerShape(GroupDot / 2)).background(colors.contentMuted))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.contentMuted,
            modifier = Modifier.padding(start = RowPadding),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalMorphicColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = colors.contentMuted,
        modifier = Modifier
            .padding(horizontal = ScreenPadding + RowPadding)
            .padding(top = SectionGap, bottom = RowPadding),
    )
}

/** A line where a section would be, when it is loading or genuinely has nothing. */
@Composable
private fun SectionNote(text: String) {
    val colors = LocalMorphicColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.contentMuted,
        modifier = Modifier.padding(horizontal = ScreenPadding + RowPadding).padding(bottom = RowPadding),
    )
}

/** A jump-to-section chip. It scrolls; it does not filter — see this screen's own note. */
@Composable
private fun SectionChip(label: String, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = colors.content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = ChipPaddingH, vertical = ChipPaddingV),
    )
}

/** Named for the way the finger travels, matching the sheet that opened this. */
private val ItemGesture.label: String
    get() = when (this) {
        ItemGesture.SWIPE_UP -> "swipe up"
        ItemGesture.SWIPE_DOWN -> "swipe down"
        ItemGesture.SWIPE_LEFT -> "swipe left"
        ItemGesture.SWIPE_RIGHT -> "swipe right"
        ItemGesture.DOUBLE_TAP -> "double tap"
    }

private val ScreenPadding = 16.dp
private val RowPadding = 12.dp
private val RowInsetV = 2.dp
private val RowCorner = 12.dp
private val IndentedStart = 36.dp
private val SectionGap = 20.dp
private val GroupDot = 8.dp
private val ChipGap = 8.dp
private val ChipPaddingH = 14.dp
private val ChipPaddingV = 8.dp
