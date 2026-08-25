package inkspire.morphic.feature.home.containersettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.LaunchedEffect
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
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.IconItem
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo
import inkspire.morphic.data.widgets.AppWidgetHostController
import inkspire.morphic.data.widgets.WidgetProvider
import inkspire.morphic.feature.home.AppSelectionSheet
import inkspire.morphic.feature.home.ContainerIcon
import inkspire.morphic.feature.home.IconArrangementSwatch
import inkspire.morphic.feature.home.IconContainerCell
import inkspire.morphic.feature.home.UnnamedWidget
import inkspire.morphic.feature.home.asIconItem
import inkspire.morphic.feature.home.listKey
import inkspire.morphic.feature.home.widgetpicker.WidgetPickerSheet
import inkspire.morphic.feature.home.widgetpicker.rememberWidgetAddFlow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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

    // **What the preview shows while a slider is being dragged**, which is not yet what the store holds: a slider
    // commits on release, so without this the tile would sit still through the whole gesture and jump at the end —
    // the one moment the control has nothing to say. Cleared when the store catches up rather than on release, so
    // the value never flickers back through the round trip. `AppCollectionOverlay`'s `orderOverride` is the same
    // shape for the same reason.
    var scaleOverride by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val storedScales = (state.settings as? ContainerSettings.Icon)?.let { it.iconScalePercent to it.spacingScalePercent }
    LaunchedEffect(storedScales) { if (scaleOverride == storedScales) scaleOverride = null }
    val shownScales = scaleOverride ?: storedScales

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
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

            // **Above the add affordance, and only once there is something to show.** The screen's order argues that
            // adding comes first because an empty container is what it is most often opened on — which is exactly
            // when a preview would be a large picture of a "+" the row below already offers. With contents it is
            // the subject of the screen, and the arrangement chooser at the bottom is otherwise a name with no
            // consequence anyone can see from here.
            (state.settings as? ContainerSettings.Icon)?.takeIf { it.icons.isNotEmpty() }?.let { icon ->
                item("preview") {
                    IconContainerPreviewTile(
                        icons = icon.icons,
                        arrangement = icon.arrangement,
                        iconScalePercent = shownScales?.first ?: icon.iconScalePercent,
                        spacingScalePercent = shownScales?.second ?: icon.spacingScalePercent,
                    )
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
                is ContainerSettings.Icon -> iconContainerItems(
                    settings = settings,
                    shownScales = shownScales,
                    onRemove = viewModel::removeIcon,
                    onPreviewScales = { icons, spacing -> scaleOverride = icons to spacing },
                    onCommitScales = viewModel::setScales,
                    onChooseArrangement = { chooserOpen = true },
                )

                is ContainerSettings.Widget -> widgetContainerItems(
                    settings = settings,
                    onRemove = viewModel::removeWidget,
                    onOptions = viewModel::setWidgetOptions,
                    onChooseAxis = { chooserOpen = true },
                )
            }
        }

        state.settings?.let { settings ->
            if (chooserOpen) {
                ContainerChooser(
                    settings = settings,
                    viewModel = viewModel,
                    onDismiss = { chooserOpen = false },
                )
            }
        }

        ContainerSheets(
            sheet = sheet,
            availableApps = state.availableApps,
            onAddApps = { picked ->
                viewModel.addApps(picked)
                sheet = null
            },
            onAddWidget = { provider ->
                sheet = null
                addWidget.start(provider.component)
            },
            onDismiss = { sheet = null },
        )
    }
}

/** Which sheet is over the screen. */
private enum class ContainerSheet { Apps, Widgets }

/**
 * An icon container's rows: what it holds, then how it is laid out and how densely.
 *
 * A `LazyListScope` extension for [widgetContainerItems]' reason — these are items of the screen's one list, not a
 * block inside it.
 *
 * @param shownScales the scaling to draw the sliders at, which is the dragged value while one is in flight rather
 *   than the stored one. Null before the store has answered.
 */
private fun LazyListScope.iconContainerItems(
    settings: ContainerSettings.Icon,
    shownScales: Pair<Int, Int>?,
    onRemove: (IconItem) -> Unit,
    onPreviewScales: (Int, Int) -> Unit,
    onCommitScales: (Int, Int) -> Unit,
    onChooseArrangement: () -> Unit,
) {
    items(settings.icons, key = { it.listKey() }) { icon ->
        IconContentRow(icon = icon, onRemove = { onRemove(icon.asIconItem()) })
    }
    item("options") { OptionsHeading() }
    item("arrangement") {
        ChooserRow(title = "Arrangement", value = settings.arrangement.label, onClick = onChooseArrangement)
    }
    item("scales") {
        ScaleRows(
            iconScalePercent = shownScales?.first ?: settings.iconScalePercent,
            spacingScalePercent = shownScales?.second ?: settings.spacingScalePercent,
            onPreview = onPreviewScales,
            onCommit = onCommitScales,
        )
    }
}

/**
 * A widget container's rows: what it holds, then how it pages.
 *
 * A `LazyListScope` extension rather than a composable, so these stay *items* of the screen's one list instead of
 * a block inside one — a nested column would lose the list's own keying and recycling. Split out for
 * [ContainerChooser]'s reason: the screen had outgrown being read in one pass.
 */
private fun LazyListScope.widgetContainerItems(
    settings: ContainerSettings.Widget,
    onRemove: (Int) -> Unit,
    onOptions: (WidgetContainerAxis, Boolean, Boolean) -> Unit,
    onChooseAxis: () -> Unit,
) {
                    items(settings.widgets, key = { it.appWidgetId }) { widget ->
                        WidgetContentRow(widget = widget, onRemove = { onRemove(widget.appWidgetId) })
                    }
                    item("options") { OptionsHeading() }
                    item("axis") {
                        ChooserRow(
                            title = "Scroll orientation",
                            value = settings.axis.label,
                            onClick = onChooseAxis,
                        )
                    }
                    item("autoRotate") {
                        SwitchRow(
                            title = "Auto rotate widgets",
                            description = "Automatically switch to the next widget at regular intervals",
                            checked = settings.autoRotate,
                            onCheckedChange = {
                                onOptions(settings.axis, it, settings.resetOnReturn)
                            },
                        )
                    }
                    item("resetOnReturn") {
                        SwitchRow(
                            title = "Reset on return",
                            description = "Return to the first widget when you come back to the home screen",
                            checked = settings.resetOnReturn,
                            onCheckedChange = {
                                onOptions(settings.axis, settings.autoRotate, it)
                            },
                        )
                    }

}

/**
 * Whichever picker the container's add affordance opened, or nothing.
 *
 * Out of the screen for [ContainerChooser]'s reason: these are surfaces *over* the list rather than part of it, and
 * the two sheets between them carry most of what the screen was doing.
 */
@Composable
private fun ContainerSheets(
    sheet: ContainerSheet?,
    availableApps: List<AppInfo>,
    onAddApps: (List<ComponentKey>) -> Unit,
    onAddWidget: (WidgetProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        null -> Unit
        // **Multi-select**, which is what a container asks for: filling one is usually a single act of "put these
        // four in here", and a picker that closed after each app would make that four round trips. The selection is
        // the sheet's own scratch state — it is not committed until Add is pressed, and backing out leaves nothing.
        ContainerSheet.Apps -> AppSelectionSheet(apps = availableApps, onDismiss = onDismiss, onAdd = onAddApps)

        ContainerSheet.Widgets -> WidgetPickerSheet(
            // **No grid**, which is the case that parameter became nullable for: every page of a container fills
            // the container, so a widget's footprint here is not a promise anyone could keep. The sheet shows names
            // without sizes rather than sizes computed against a grid this widget will never sit in.
            grid = null,
            cellWidthPx = 0f,
            cellHeightPx = 0f,
            onDismiss = onDismiss,
            onAddWidget = onAddWidget,
        )
    }
}

/**
 * The one chooser a container has, whichever kind it is — an arrangement or a scroll axis.
 *
 * Split out of the screen because it is a dialog *over* it rather than part of its list, and because the screen had
 * grown past the point where one more `when` could be read at a glance.
 */
@Composable
private fun ContainerChooser(
    settings: ContainerSettings,
    viewModel: ContainerSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = LocalMorphicColors.current
        when (settings) {
            is ContainerSettings.Icon -> ChooserDialog(
                title = "Arrangement",
                options = IconArrangement.entries,
                selected = settings.arrangement,
                label = { it.label },
                onPick = { viewModel.setArrangement(it) },
                onDismiss = onDismiss,
                // The same swatch the picker chooses by, so the two places an arrangement is set show the same
                // shape. Names alone ("Fan from top left") are what this list had, and four of the seven differ
                // only by a direction that is far quicker to see than to read.
                leading = { option ->
                    IconArrangementSwatch(
                        arrangement = option,
                        color = colors.contentMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(end = 0.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )

            is ContainerSettings.Widget -> ChooserDialog(
                title = "Scroll orientation",
                options = WidgetContainerAxis.entries,
                selected = settings.axis,
                label = { it.label },
                onPick = { viewModel.setWidgetOptions(it, settings.autoRotate, settings.resetOnReturn) },
                onDismiss = onDismiss,
            )
        }
}

/**
 * The container's own icon and gap scaling.
 *
 * **Two rows rather than one**, though they write together: lowering the spacing is how the icons are given room
 * to grow, so a user who wants bigger icons in a full container needs both, and a single control could not say
 * that. The write is one op regardless — see `LayoutChange.SetIconContainerScales`.
 *
 * Both report a live [onPreview] as well as an [onCommit], because a slider commits on release and the tile above
 * is the only reason to move one at all.
 */
@Composable
private fun ScaleRows(
    iconScalePercent: Int,
    spacingScalePercent: Int,
    onPreview: (Int, Int) -> Unit,
    onCommit: (Int, Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        MorphicSliderRow(
            value = iconScalePercent,
            valueRange = 25..200,
            default = 100,
            what = "Icon size",
            label = "Icon size",
            valueLabel = { "$it %" },
            onPreview = { onPreview(it, spacingScalePercent) },
            onCommit = { onCommit(it, spacingScalePercent) },
        )
        MorphicSliderRow(
            value = spacingScalePercent,
            // Never zero: icons that touch read as one shape, and the slot is what bounds an icon, so a container
            // with no gap could not be told from a denser one.
            valueRange = 50..200,
            default = 100,
            what = "Item spacing",
            label = "Item spacing",
            valueLabel = { "$it %" },
            onPreview = { onPreview(iconScalePercent, it) },
            onCommit = { onCommit(iconScalePercent, it) },
        )
    }
}

/**
 * The container as it will look, drawn by **the cell the home screen draws** rather than by a picture of it.
 *
 * `IconContainerCell` over the real contents and the real arrangement, so this cannot show a shape or a spacing the
 * container does not have — the standing rule that two implementations of one thing are kept honest by a shared
 * derivation, with the strongest form of it available here, which is not having a second implementation at all.
 *
 * **Square, because a container lands square** — the same 2×2 footprint the picker previews it at.
 *
 * `containerId` is deliberately left null: that is what stops the cell publishing a drop zone. A zone here would be
 * a second target for the id the real container on home already answers for, and at `z = 1` it would outrank it.
 */
@Composable
private fun IconContainerPreviewTile(
    icons: List<ContainerIcon>,
    arrangement: IconArrangement,
    iconScalePercent: Int,
    spacingScalePercent: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f),
        ) {
            IconContainerCell(
                icons = icons,
                arrangement = arrangement,
                iconScalePercent = iconScalePercent,
                spacingScalePercent = spacingScalePercent,
            )
        }
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
        HorizontalDivider(
            color = colors.outline,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
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
    leading: @Composable (T) -> Unit = {},
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
                        leading(option)
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
                sizePx = with(LocalDensity.current) { 40.dp.roundToPx() },
                modifier = Modifier.size(40.dp),
            )
        }

        is ContainerIcon.Folder -> ContentRow(label = icon.folder.label.ifBlank { "Folder" }, onRemove = onRemove) {
            IconPreviewPlate(apps = icon.apps, size = 40.dp)
        }
    }
}

/** One widget in the container. Named by its provider's label, which is all the layout store keeps about it. */
@Composable
private fun WidgetContentRow(widget: WidgetInfo, onRemove: () -> Unit) {
    val colors = LocalMorphicColors.current
    ContentRow(label = widget.label.ifBlank { UnnamedWidget }, onRemove = onRemove) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated),
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
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 24.dp),
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
