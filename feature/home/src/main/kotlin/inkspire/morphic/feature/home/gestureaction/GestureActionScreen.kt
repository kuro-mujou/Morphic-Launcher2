package inkspire.morphic.feature.home.gestureaction

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.component.MorphicGroupPanel
import inkspire.morphic.core.designsystem.component.field.MorphicTextField
import inkspire.morphic.core.designsystem.gesture.describeGestureAction
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.GestureAction
import inkspire.morphic.core.model.ItemGesture
import inkspire.morphic.core.model.asItemGesture
import kotlinx.coroutines.launch

/**
 * Choosing what one gesture does — **one list with jump-to-section chips**, not a set of filtered tabs.
 *
 * The chips scroll rather than filter, which is the behavior worth copying from the launchers that do this well: it
 * keeps a single scroll from Apps into Shortcuts, and lets one search box narrow everything at once instead of
 * asking which tab the query applies to. As filters they would look identical in a mockup and read as four separate
 * screens in the hand.
 *
 * **The System section is offered on every gesture**, with Lock screen on HOME's own only — turning the screen off from
 * an icon is not a gesture this launcher has — and the by-side panel pull on HOME's vertical swipes only, the one
 * gesture whose starting side is the user's choice. Its actions run through Morphic gestures and are saved whether or
 * not that is on: the gesture asks for it as it fires, the one moment that also catches a service switched off later.
 *
 * @param onBack returns to the sheet the gesture was chosen from.
 * @param onChosen called after a choice is written, so the caller can close this destination — the screen does not
 *   navigate itself, for `LauncherShell`'s reason: `app` owns the back stack.
 */
@Composable
internal fun GestureActionScreen(
    target: GestureTarget,
    viewModel: GestureActionViewModel,
    onBack: () -> Unit,
    onChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Lazy-item indices of the section headers the chips scroll to: None, then System and Apps at exactly two items each
    // — a header and one panel, however many rows the panel holds. Counting rows instead of items overshoots the end of
    // the list, silently.
    val appsHeaderAt = 3
    val shortcutsHeaderAt = appsHeaderAt + 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // A search field over a scrolling list of actions: this screen *should* give up height to the keyboard,
            // and has to say so itself — the window is edge-to-edge under `adjustResize`, so the IME arrives as an
            // inset that only the surface reading it answers.
            .imePadding()
            .uiInsetsPadding(),
    ) {
        Text(
            text = "Assign action to ${target.gestureLabel}",
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
            SectionChip("Apps") { scope.launch { listState.animateScrollToItem(appsHeaderAt) } }
            SectionChip("Shortcuts") { scope.launch { listState.animateScrollToItem(shortcutsHeaderAt) } }
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

            systemSection(state) { action ->
                viewModel.choose(action)
                onChosen()
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
            when {
                state.loadingShortcuts -> item(key = "shortcuts-note") { Panel { SectionNote("Reading shortcuts…") } }
                // Empty has two very different causes and only one is worth acting on, so it says both.
                state.shortcutGroups.isEmpty() -> item(key = "shortcuts-note") {
                    Panel { SectionNote("No shortcuts. Apps publish these, and only the active home app may read them.") }
                }

                // **One lazy item per app**, where the section used to be a single item holding every row: a card
                // opening changes only its own height, and the cards below it are composed as they scroll in.
                else -> items(state.shortcutGroups, key = { "shortcuts:${it.app.componentKey.packageName}" }) { group ->
                    ShortcutGroupCard(
                        group = group,
                        assigned = state.assigned,
                        searching = state.query.isNotBlank(),
                        onChoose = { shortcut -> viewModel.chooseShortcut(shortcut); onChosen() },
                    )
                }
            }
        }
    }

    BackHandler(onBack = onBack)
}

/**
 * The System section: its header, then one item holding the panel card and Lock screen — the two list items
 * `appsHeaderAt` counts past, whichever of them are shown.
 */
private fun LazyListScope.systemSection(state: GestureActionState, onChoose: (GestureAction) -> Unit) {
    item(key = "system-header") { SectionHeader("SYSTEM") }
    item(key = "system") {
        Column {
            SystemPanelCard(assigned = state.assigned, offersBySide = state.offersPanelBySide, onChoose = onChoose)
            if (state.offersLockScreen) {
                Panel {
                    ChoiceRow(
                        label = describeGestureAction(GestureAction.LockScreen, emptyMap()),
                        selected = state.assigned == GestureAction.LockScreen,
                        onClick = { onChoose(GestureAction.LockScreen) },
                    )
                }
            }
        }
    }
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
internal fun SelectedMark() {
    val colors = LocalMorphicColors.current
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.onAccent),
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
/** The gesture being assigned, as the title names it. A HOME swipe reads as the item swipe it looks like. */
private val GestureTarget.gestureLabel: String
    get() = when (this) {
        is GestureTarget.Item -> gesture.label
        is GestureTarget.HomeSwipe -> direction.asItemGesture().label
        GestureTarget.HomeDoubleTap -> ItemGesture.DOUBLE_TAP.label
        is GestureTarget.WidgetLayer -> "Tap"
    }

private val ItemGesture.label: String
    get() = when (this) {
        ItemGesture.SWIPE_UP -> "swipe up"
        ItemGesture.SWIPE_DOWN -> "swipe down"
        ItemGesture.SWIPE_LEFT -> "swipe left"
        ItemGesture.SWIPE_RIGHT -> "swipe right"
        ItemGesture.DOUBLE_TAP -> "double tap"
    }
