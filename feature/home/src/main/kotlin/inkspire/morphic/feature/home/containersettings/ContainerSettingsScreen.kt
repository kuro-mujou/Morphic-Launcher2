package inkspire.morphic.feature.home.containersettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.picker.AppPicker
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.feature.home.ContainerIcon
import inkspire.morphic.feature.home.LauncherBottomSheet
import inkspire.morphic.feature.home.UnnamedWidget
import inkspire.morphic.feature.home.asIconItem
import inkspire.morphic.feature.home.listKey
import inkspire.morphic.feature.home.widgetpicker.WidgetPickerSheet
import inkspire.morphic.feature.home.widgetpicker.rememberWidgetAddFlow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** The size an icon is drawn at in a contents row — a list row, so the icon is a fraction of its height. */
private val RowIconSize = 40.dp

/** How tall one contents row is. A placeholder, on the "don't invent a dimension nothing owns yet" rule. */
private val ContentRowHeight = 64.dp

/**
 * **A container's settings** — what it holds, and how it behaves.
 *
 * One screen for both kinds, with a `when` over [ContainerSettings] wherever they genuinely differ, which is
 * `AppsScreen`'s and `HomeScreen`'s shape and the same argument: everything around the difference is shared (the
 * chrome, the contents list, the add affordance, the removal), so answering "which container?" once above all of it
 * is what stops the two drifting into two screens that look almost alike.
 *
 * **Reached from the container's own "+" and from its context menu**, which is why it is a destination and not a
 * sheet: the "+" used to open a picker directly, and that made adding the *only* thing an empty container could do —
 * with no way to reach the arrangement, the axis, or anything already inside it.
 *
 * **The order is the screenshot's, and it is not arbitrary.** The add affordance comes first because an empty
 * container is the state this screen is most often opened in; the contents follow, because they are what the add
 * button produces; the options come last, under a heading, because they describe a container that already has
 * something in it. A settings list that opened with switches would bury the one control that matters on first use.
 */
@Composable
fun ContainerSettingsScreen(
    route: ContainerSettingsRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ContainerSettingsViewModel = koinViewModel { parametersOf(route) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMorphicColors.current

    // Which sheet is up, if any. Three states rather than three booleans, so "the app picker and the widget picker
    // are both open" is unrepresentable.
    var sheet by remember { mutableStateOf<ContainerSheet?>(null) }
    // Which arrangement/axis chooser is up. A separate flag because a chooser is a dialog over the screen rather
    // than a sheet that replaces it.
    var chooserOpen by remember { mutableStateOf(false) }

    // The add flow, owned here now that the picker is opened from this screen rather than from the surface. The
    // widget always goes into *this* container, so unlike home's there is no target to remember and no free cell to
    // find — the container already has a placement, and each of its pages fills it.
    val addWidget = rememberWidgetAddFlow(koinInject<AppWidgetHostController>()) { bound ->
        viewModel.addWidget(
            WidgetInfo(
                appWidgetId = bound.appWidgetId,
                providerPackage = bound.provider.packageName,
                providerClass = bound.provider.className,
                label = bound.label,
            ),
        )
        true
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item("chrome") {
                Column(Modifier.uiInsetsPadding()) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.content)
                    }
                    Text(
                        text = route.title(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.content,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Text(
                        text = route.description(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.contentMuted,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            item("add") {
                AddRow(
                    label = route.addLabel(),
                    onClick = {
                        sheet = when (route) {
                            is ContainerSettingsRoute.Icon -> ContainerSheet.Apps
                            is ContainerSettingsRoute.Widget -> ContainerSheet.Widgets
                        }
                    },
                )
            }

            when (val settings = state.settings) {
                // Not yet, or gone. The chrome above is drawn either way, so neither flashes an error — see
                // [ContainerSettingsState.settings].
                null -> Unit
                is ContainerSettings.Icon -> {
                    items(settings.icons, key = { it.listKey() }) { icon ->
                        IconContentRow(icon = icon, onRemove = { viewModel.removeIcon(icon.asIconItem()) })
                    }
                    item("options") { OptionsHeading() }
                    item("arrangement") {
                        ChooserRow(
                            title = "Arrangement",
                            value = settings.arrangement.label,
                            onClick = { chooserOpen = true },
                        )
                    }
                }
                is ContainerSettings.Widget -> {
                    items(settings.widgets, key = { it.appWidgetId }) { widget ->
                        WidgetContentRow(widget = widget, onRemove = { viewModel.removeWidget(widget.appWidgetId) })
                    }
                    item("options") { OptionsHeading() }
                    item("axis") {
                        ChooserRow(
                            title = "Scroll orientation",
                            value = settings.axis.label,
                            onClick = { chooserOpen = true },
                        )
                    }
                    item("autoRotate") {
                        SwitchRow(
                            title = "Auto rotate widgets",
                            description = "Automatically switch to the next widget at regular intervals",
                            checked = settings.autoRotate,
                            onCheckedChange = {
                                viewModel.setWidgetOptions(settings.axis, it, settings.resetOnReturn)
                            },
                        )
                    }
                    item("resetOnReturn") {
                        SwitchRow(
                            title = "Reset on return",
                            description = "Return to the first widget when you come back to the home screen",
                            checked = settings.resetOnReturn,
                            onCheckedChange = {
                                viewModel.setWidgetOptions(settings.axis, settings.autoRotate, it)
                            },
                        )
                    }
                }
            }
        }

        val settings = state.settings
        if (chooserOpen && settings != null) {
            when (settings) {
                is ContainerSettings.Icon -> ChooserDialog(
                    title = "Arrangement",
                    options = IconArrangement.entries,
                    selected = settings.arrangement,
                    label = { it.label },
                    onPick = { viewModel.setArrangement(it) },
                    onDismiss = { chooserOpen = false },
                )
                is ContainerSettings.Widget -> ChooserDialog(
                    title = "Scroll orientation",
                    options = WidgetContainerAxis.entries,
                    selected = settings.axis,
                    label = { it.label },
                    onPick = { viewModel.setWidgetOptions(it, settings.autoRotate, settings.resetOnReturn) },
                    onDismiss = { chooserOpen = false },
                )
            }
        }

        when (sheet) {
            null -> Unit
            // **Multi-select**, which is what a container asks for: filling one is usually a single act of "put
            // these four in here", and a picker that closed after each app would make that four round trips. The
            // selection is held here rather than in the ViewModel because it is not committed until Add is pressed —
            // it is the sheet's own scratch state, and backing out must leave nothing behind.
            ContainerSheet.Apps -> AppSelectionSheet(
                apps = state.availableApps,
                onDismiss = { sheet = null },
                onAdd = { picked ->
                    viewModel.addApps(picked)
                    sheet = null
                },
            )
            ContainerSheet.Widgets -> WidgetPickerSheet(
                // **No grid**, which is the case that parameter became nullable for: every page of a container
                // fills the container, so a widget's footprint here is not a promise anyone could keep. The sheet
                // shows names without sizes rather than sizes computed against a grid this widget will never sit in.
                grid = null,
                cellWidthPx = 0f,
                cellHeightPx = 0f,
                onDismiss = { sheet = null },
                onAddWidget = { provider ->
                    sheet = null
                    addWidget.start(provider.component)
                },
            )
        }
    }
}

/** Which sheet is over the screen. */
private enum class ContainerSheet { Apps, Widgets }

/**
 * The multi-select app picker, with its selection and its commit.
 *
 * A sheet of its own rather than inline, so the scratch selection is scoped to the sheet being on screen: dismissing
 * disposes it, which is what makes backing out leave nothing behind without anything having to reset it.
 */
@Composable
private fun AppSelectionSheet(
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onAdd: (List<ComponentKey>) -> Unit,
) {
    val colors = LocalMorphicColors.current
    // A list, not a set, so the apps land in the order they were ticked — which is the only order the user has
    // expressed, and the container stores one.
    var picked by remember { mutableStateOf<List<ComponentKey>>(emptyList()) }

    LauncherBottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = "Add apps",
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            // Disabled rather than hidden: the button is what tells a user the ticks are not yet committed, so it
            // has to be visible before anything is ticked.
            TextButton(onClick = { onAdd(picked) }, enabled = picked.isNotEmpty()) {
                Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}")
            }
        }
        AppPicker(
            apps = apps,
            selected = picked.toSet(),
            onPick = { component ->
                picked = if (component in picked) picked - component else picked + component
            },
        )
    }
}

/** The big "+ Add …" affordance the screen opens with. */
@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accent)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.accent)
    }
}

/** The divider and label that separate the container's contents from how it behaves. */
@Composable
private fun OptionsHeading() {
    val colors = LocalMorphicColors.current
    Column {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = colors.outline, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            text = "OPTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = colors.contentMuted,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
        )
    }
}

/** A setting whose value is one of a few — the title, what it is set to, and a tap that opens the list. */
@Composable
private fun ChooserRow(title: String, value: String, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.content)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
    }
}

/** A setting that is on or off. The whole row toggles it, not just the switch — a switch is a small target. */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.content)
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        }
        // `onCheckedChange = null` so the row owns the tap: two overlapping targets would let a press land on the
        // switch and do nothing when it missed by 2dp. The same reason the multi-select picker's checkbox is inert.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The chooser itself: a radio list, which is what a handful of exclusive options is. */
@Composable
private fun <T> ChooserDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(option)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One app or nested folder in the container, with the button that takes it out. */
@Composable
private fun IconContentRow(icon: ContainerIcon, onRemove: () -> Unit) {
    when (icon) {
        is ContainerIcon.App -> ContentRow(label = icon.info.label, onRemove = onRemove) {
            AppIcon(
                component = icon.info.componentKey,
                contentDescription = null,
                sizePx = with(LocalDensity.current) { RowIconSize.roundToPx() },
                modifier = Modifier.size(RowIconSize),
            )
        }
        is ContainerIcon.Folder -> ContentRow(label = icon.folder.label.ifBlank { "Folder" }, onRemove = onRemove) {
            IconPreviewPlate(apps = icon.apps, size = RowIconSize)
        }
    }
}

/** One widget in the container. Named by its provider's label, which is all the layout store keeps about it. */
@Composable
private fun WidgetContentRow(widget: WidgetInfo, onRemove: () -> Unit) {
    val colors = LocalMorphicColors.current
    ContentRow(label = widget.label.ifBlank { UnnamedWidget }, onRemove = onRemove) {
        Box(
            modifier = Modifier.size(RowIconSize).clip(CircleShape).background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Widgets,
                contentDescription = null,
                tint = colors.contentMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The shared shape of a contents row: something drawn, a name, and a remove. */
@Composable
private fun ContentRow(label: String, onRemove: () -> Unit, leading: @Composable () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(ContentRowHeight).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove $label", tint = colors.contentMuted)
        }
    }
}
