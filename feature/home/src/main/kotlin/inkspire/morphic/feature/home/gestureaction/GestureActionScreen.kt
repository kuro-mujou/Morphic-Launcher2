package inkspire.morphic.feature.home.gestureaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
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
            .uiInsetsPadding(),
    ) {
        Text(
            text = "Assign action to ${gesture.label}",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.content,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
        )
        GestureActionSearch(
            query = state.query,
            onQuery = viewModel::search,
            modifier = Modifier.padding(16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            SectionChip("Apps") { scope.launch { listState.animateScrollToItem(appsAt) } }
            SectionChip("Shortcuts") { scope.launch { listState.animateScrollToItem(shortcutsAt) } }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // **The clearing choice sits on a panel of its own**, above the two it is an alternative to. It is one
            // row, so a heading over it would name a group of one.
            item(key = "none") {
                Panel {
                    ChoiceRow(
                        label = "None",
                        selected = state.assigned == null,
                        onClick = { viewModel.choose(null); onChosen() },
                    )
                }
            }

            item(key = "apps-header") { SectionHeader("APPS") }
            item(key = "apps") {
                Panel {
                    // **One panel for the whole run, not one per row.** The panel is what says where the group ends,
                    // which is the same reason the settings index draws its sections this way.
                    state.apps.forEach { app ->
                        AppChoiceRow(
                            app = app,
                            selected = (state.assigned as? GestureAction.LaunchApp)?.component == app.componentKey,
                            onClick = { viewModel.chooseApp(app.componentKey); onChosen() },
                        )
                    }
                    if (state.apps.isEmpty()) SectionNote("No apps match.")
                }
            }

            item(key = "shortcuts-header") { SectionHeader("SHORTCUTS") }
            item(key = "shortcuts") {
                Panel {
                    when {
                        state.loadingShortcuts -> SectionNote("Reading shortcuts…")
                        // Empty has two very different causes and only one is worth acting on, so it says both.
                        state.shortcutGroups.isEmpty() ->
                            SectionNote("No shortcuts. Apps publish these, and only the active home app may read them.")

                        else -> state.shortcutGroups.forEachIndexed { index, group ->
                            // **A rule between blocks, not under every row.** One app's shortcuts are a unit; what
                            // needs separating is where one app ends and the next begins. A divider per row would
                            // draw the panel as a grid of equals and lose the grouping the header just made.
                            if (index > 0) BlockDivider()
                            GroupHeader(group.app)
                            group.shortcuts.forEach { shortcut ->
                                ShortcutChoiceRow(
                                    shortcut = shortcut,
                                    selected = state.assigned.isThis(shortcut),
                                    onClick = { viewModel.chooseShortcut(shortcut); onChosen() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler(onBack = onBack)
}

/** A group of rows on one rounded panel, inset from the screen edges — the launcher's grouped-list container. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    MorphicGroupPanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

/** An app, drawn as it is everywhere else in the launcher: its own icon beside its label. */
@Composable
private fun AppChoiceRow(app: AppInfo, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // **`AppRowCell` rather than a bitmap of our own**, so a picked icon is the one the user will actually see
        // on home — the same shaped, baked icon, through the same metrics. It draws its own label too.
        AppRowCell(
            app = app,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            // The fill is the launcher's accent, so the label has to switch with it — a label fixed to the theme's
            // content color vanished into the selected row, leaving the icon on a blank bar.
            labelColor = if (selected) colors.onAccent else null,
        )
        if (selected) SelectedMark()
    }
}

/** One of an app's shortcuts: the icon the app rasterized for it, and its own name. */
@Composable
private fun ShortcutChoiceRow(shortcut: AppShortcut, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    val content = if (selected) colors.onAccent else colors.content
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 40.dp, end = 8.dp)
            .padding(vertical = 4.dp),
    ) {
        // **Drawn as the app rasterized it, not run through `core:icon`.** A shortcut icon is the app's own
        // badge-and-glyph composition, which `AppShortcut` says in as many words — restyling it into one of our
        // layer stacks would produce something the app never published.
        val icon = shortcut.icon
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        } else {
            // A shortcut the platform gave us no icon for still needs its row to line up with the others.
            Box(Modifier.size(24.dp))
        }
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (selected) SelectedMark()
    }
}

/** The plain choice — no icon, because "None" is the absence of one. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onAccent else colors.content,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        if (selected) SelectedMark()
    }
}

/**
 * What marks the current choice *inside* a filled row.
 *
 * The fill already says "this one", so this is a second signal rather than the only one — worth having because a
 * picker is scrolled through, and a row that scrolls past half-visible reads by its mark before its color.
 */
@Composable
private fun SelectedMark() {
    val colors = LocalMorphicColors.current
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.onAccent),
    )
}

/**
 * A group of one app's shortcuts, headed by the app that publishes them — its icon beside its name.
 *
 * **The icon is the launcher's own**, through [AppIcon], so an app is recognized here exactly as it is on home. A
 * shortcut's own icon is the app's to draw; the *app* is ours, and the two sitting in one column is what makes the
 * indent read as "these belong to that".
 */
@Composable
private fun GroupHeader(app: AppInfo) {
    val colors = LocalMorphicColors.current
    val sizePx = with(LocalDensity.current) { 28.dp.roundToPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
    ) {
        AppIcon(
            component = app.componentKey,
            contentDescription = null,
            sizePx = sizePx,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.contentMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The hairline between two apps' blocks — the palette's own divider, not a faded content color. */
@Composable
private fun BlockDivider() {
    val colors = LocalMorphicColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(1.dp)
            .background(colors.divider),
    )
}

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalMorphicColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = colors.contentMuted,
        modifier = Modifier
            .padding(horizontal = 16.dp + 8.dp)
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

/** A line where rows would be, when a section is loading or genuinely has nothing. */
@Composable
private fun SectionNote(text: String) {
    val colors = LocalMorphicColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.contentMuted,
        modifier = Modifier.padding(8.dp),
    )
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
