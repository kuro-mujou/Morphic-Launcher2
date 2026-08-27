package inkspire.morphic.feature.home.containersettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.backdrop.PunchThroughLayer
import inkspire.morphic.core.designsystem.backdrop.punchThroughHole
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
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
import inkspire.morphic.feature.home.ArrangementPicker
import inkspire.morphic.feature.home.ContainerIcon
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
 * **The order is not arbitrary, and it is the reverse of what it was.** The options come first, then the add
 * affordance, then the contents. The old order led with the add row because an empty container is the state this
 * screen is first opened in — but that is one visit, and every later one is about the options, which a container
 * holding fifty apps buried under fifty rows: opening the screen showed no control at all. **A list is unbounded,
 * so nothing may sit after it.** The add row is still the second block and still on screen without scrolling on
 * the empty container the old argument was about, so that case lost nothing.
 *
 * The sliders make it sharper than mere reachability: each one exists to move the preview above it, so a control
 * that cannot share the screen with its own result is not usable while it is being used.
 *
 * **Two things are pinned and everything else scrolls: the toolbar and the preview**, each for its own reason.
 *
 * The **toolbar** carries the title, which used to be a `headlineMedium` row inside the list with a paragraph of
 * description under it. Both are gone. A screen reached by pressing *Settings* on a container the user has just
 * placed does not have to say what a container is, and that paragraph cost more height than the first two controls
 * it was pushing off the screen — while saying in prose what the preview below now shows. The bar is a sibling of
 * the list rather than an item in it, which is the whole of "pinned": no scroll behavior to configure, and nothing
 * that can be scrolled away.
 *
 * **The theme is applied here and was not before**, which is the one thing on this screen that was wrong in a way
 * no layout change would have fixed: every other destination in `LauncherNavHost` opens a [LauncherTheme] of its
 * own and this one did not, so `MaterialTheme` fell through to M3's baseline — a purple slider track and a purple
 * switch on a monochrome screen. It was quiet because `LocalMorphicColors` has a `MorphicColors.Dark` *default*, so
 * every `colors.*` read here resolved to something plausible while every stock M3 component did not, and in light
 * mode the two would have disagreed outright.
 *
 * The **preview** is pinned for the opposite reason. Every control under it exists to change how the container
 * looks, so one that scrolls its own result off the screen cannot be judged while it is being used — which is what
 * the arrangement chooser and both scale sliders were doing. That is the property `ICON_CONTAINER_PLAN` §2e wanted
 * from the reference's floating panel, reached without a panel.
 *
 * **The whole screen is a [PunchThroughLayer], which is what lets the preview show the real wallpaper.** A container
 * is a frosted panel that exists to sit on a wallpaper, so judging one against a flat settings background is judging
 * a different object; the screen therefore composites itself offscreen with its background *inside* that layer, and
 * the preview clears a hole through it. Nothing opaque may be painted behind the layer — see [PunchThroughLayer]'s
 * four clauses — which is why the background moved onto it rather than staying on a `Box` around it.
 */
@Composable
fun ContainerSettingsScreen(
    route: ContainerSettingsRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        ContainerSettingsContent(route = route, onBack = onBack, modifier = modifier)
    }
}

/**
 * The screen itself, inside the theme.
 *
 * Split from [ContainerSettingsScreen] so the theme wraps everything that reads a color — `LocalMorphicColors` is
 * read on the very first line, and a `LauncherTheme` opened after that would not reach it. `SettingsScreen` is
 * shaped the same way, wrapping its two panes rather than sitting inside one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerSettingsContent(
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

    PunchThroughLayer(background = colors.background, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            ContainerSettingsTopBar(title = route.title(), onBack = onBack)

            // **Layout padding here, not the content padding the rest of the launcher uses, and the departure is
            // the point.** A launcher *surface* pads its content so rows pass under the system bars, because what
            // is behind the bar there is the wallpaper and stopping short would leave a hole through to it. This is
            // a settings list on a solid background, and the thing that scrolled under the navigation bar was a
            // slider — which cannot be finished, or started, by a finger the navigation bar is taking. The
            // background stays full-bleed on the `Box` above, so nothing opens a hole; only the content stops
            // short. The top edge is the bar's and is not asked for twice.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ) {
                IconContainerPreviewTile(settings = state.settings, shownScales = shownScales)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // **The controls come before the contents, and that is a reversal.** The old order put the add
                    // affordance first because an empty container is what this screen is most often opened on. What
                    // that missed is every later visit: a container with fifty apps in it put every control behind
                    // fifty rows, so opening the screen showed no control at all. A list is unbounded, so nothing
                    // may sit after it — and a slider in particular has to be on screen at the same time as the
                    // preview it moves, which is the whole reason that preview is up there.
                    // **Emitted unconditionally, even before the stores answer**, and that is not defensiveness —
                    // a `settings?.let { item(…) }` here *prepends* an item once the answer arrives, and a keyed
                    // lazy list holds its anchor: the first visible item stays where it is, so the new one lands
                    // above the viewport and is never seen. It only looked like a routing bug because it needs a
                    // list long enough to scroll — a short one clamps back to the top and shows it — so a container
                    // with a few apps in it was fine and the same container with twenty-four was not.
                    item("options") {
                        ContainerOptions(
                            settings = state.settings,
                            shownScales = shownScales,
                            onArrangement = viewModel::setArrangement,
                            onChooseOption = { chooserOpen = true },
                            onPreviewScales = { icons, spacing -> scaleOverride = icons to spacing },
                            onCommitScales = viewModel::setScales,
                            onWidgetOptions = viewModel::setWidgetOptions,
                        )
                    }

                    item("add") {
                        Column {
                            SectionSeparator()
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
                    }

                    containerContents(
                        settings = state.settings,
                        onRemoveIcon = viewModel::removeIcon,
                        onRemoveWidget = viewModel::removeWidget,
                    )
                }
            }
        }

        (state.settings as? ContainerSettings.Widget)?.let { settings ->
            if (chooserOpen) {
                AxisChooser(
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

/**
 * The screen's pinned toolbar — the title and the way back, and nothing else.
 *
 * **M3's `TopAppBar` rather than a `Row` of our own**, for the design system's stated rule: build on the M3
 * component and restyle it, since the scheme is bridged monochrome and the Expressive motion comes free. The
 * settings screen's two bars are the same call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerSettingsTopBar(title: String, onBack: () -> Unit) {
    val colors = LocalMorphicColors.current
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            titleContentColor = colors.content,
        ),
        // **`uiInsets`, not M3's default of `systemBars` alone**, which would seat the back button under a
        // landscape notch. Taking the top and the horizontal edges here is also what lets everything below ask
        // only for the other three.
        windowInsets = uiInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.content)
            }
        },
    )
}

/** Which sheet is over the screen. */
private enum class ContainerSheet { Apps, Widgets }

/**
 * How a container behaves — its arrangement and scaling, or its axis and the two things it does on its own.
 *
 * **One bounded block rather than list items**, which is what lets it sit above an unbounded list: these are a
 * fixed handful of rows whichever kind of container this is, so nothing is lost by composing them together, and a
 * `LazyListScope` extension would have had to be emitted from two places to get the ordering right.
 *
 * @param shownScales the scaling to draw the sliders at — the dragged value while one is in flight rather than the
 *   stored one. Null before the store has answered.
 * @param onArrangement sets the icon container's arrangement in place — there is no dialog; see
 *   [ArrangementSetting].
 * @param onChooseOption opens the widget container's axis chooser, the one dialog left on this screen.
 */
@Composable
private fun ContainerOptions(
    settings: ContainerSettings?,
    shownScales: Pair<Int, Int>?,
    onArrangement: (IconArrangement) -> Unit,
    onChooseOption: () -> Unit,
    onPreviewScales: (Int, Int) -> Unit,
    onCommitScales: (Int, Int) -> Unit,
    onWidgetOptions: (WidgetContainerAxis, Boolean, Boolean) -> Unit,
) {
    Column {
        // **Drawn before the stores answer, which is what keeps this item in the list from the first frame.** The
        // rows below cannot be, since they have nothing to show yet — but the item must not *appear* late, and the
        // heading is what gives it a height to hold. See the emit site for what happens when it does appear late.
        OptionsHeading()
        when (settings) {
            null -> Unit
            is ContainerSettings.Icon -> {
                ArrangementSetting(arrangement = settings.arrangement, onArrangement = onArrangement)
                ScaleRows(
                    iconScalePercent = shownScales?.first ?: settings.iconScalePercent,
                    spacingScalePercent = shownScales?.second ?: settings.spacingScalePercent,
                    onPreview = onPreviewScales,
                    onCommit = onCommitScales,
                )
            }

            is ContainerSettings.Widget -> {
                ChooserRow(title = "Scroll orientation", value = settings.axis.label, onClick = onChooseOption)
                SwitchRow(
                    title = "Auto rotate widgets",
                    description = "Automatically switch to the next widget at regular intervals",
                    checked = settings.autoRotate,
                    onCheckedChange = { onWidgetOptions(settings.axis, it, settings.resetOnReturn) },
                )
                SwitchRow(
                    title = "Reset on return",
                    description = "Return to the first widget when you come back to the home screen",
                    checked = settings.resetOnReturn,
                    onCheckedChange = { onWidgetOptions(settings.axis, settings.autoRotate, it) },
                )
            }
        }
    }
}

/**
 * What the container holds, one row each — the only unbounded part of the screen, and therefore the last.
 *
 * A `LazyListScope` extension rather than a composable, so these stay *items* of the screen's one list instead of
 * a block inside one: a container with fifty apps in it is exactly the case a nested column would compose in full.
 */
private fun LazyListScope.containerContents(
    settings: ContainerSettings?,
    onRemoveIcon: (IconItem) -> Unit,
    onRemoveWidget: (Int) -> Unit,
) {
    when (settings) {
        // Not yet, or gone. The chrome around this is drawn either way, so neither flashes an error — see
        // [ContainerSettingsState.settings].
        null -> Unit
        is ContainerSettings.Icon -> items(settings.icons, key = { it.listKey() }) { icon ->
            IconContentRow(icon = icon, onRemove = { onRemoveIcon(icon.asIconItem()) })
        }

        is ContainerSettings.Widget -> items(settings.widgets, key = { it.appWidgetId }) { widget ->
            WidgetContentRow(widget = widget, onRemove = { onRemoveWidget(widget.appWidgetId) })
        }
    }
}


/**
 * Whichever picker the container's add affordance opened, or nothing.
 *
 * Out of the screen for [AxisChooser]'s reason: these are surfaces *over* the list rather than part of it, and
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
 * The arrangement, set **in the list rather than through a dialog** — its name, and the picker its shape is chosen
 * with.
 *
 * A dialog was right while this screen had nothing else to show: seven radio rows were the only way to see the
 * options at all. It is wrong now that a live preview is pinned above, because a dialog covers exactly the thing
 * worth watching while choosing — which is the argument the widget picker's row was built on, applied where it is
 * stronger. Both places now share [ArrangementPicker], and neither of them hides the result.
 *
 * The name stays even though the tiles are pictures, because a corner is quicker to see than to read but a *shape*
 * still wants saying — and this is the row a heading belongs to.
 */
@Composable
private fun ArrangementSetting(arrangement: IconArrangement, onArrangement: (IconArrangement) -> Unit) {
    val colors = LocalMorphicColors.current
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(text = "Arrangement", style = MaterialTheme.typography.bodyLarge, color = colors.content)
        Text(
            text = arrangement.label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ArrangementPicker(arrangement = arrangement, onArrangement = onArrangement)
    }
}

/**
 * The widget container's scroll axis — **the last chooser dialog on this screen**, and it stays one because two
 * directions have no picture worth showing: the words are the whole of the choice.
 *
 * Split out of the screen because it is a dialog *over* it rather than part of its list.
 */
@Composable
private fun AxisChooser(
    settings: ContainerSettings.Widget,
    viewModel: ContainerSettingsViewModel,
    onDismiss: () -> Unit,
) {
    ChooserDialog(
        title = "Scroll orientation",
        options = WidgetContainerAxis.entries,
        selected = settings.axis,
        label = { it.label },
        onPick = { viewModel.setWidgetOptions(it, settings.autoRotate, settings.resetOnReturn) },
        onDismiss = onDismiss,
    )
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
 * The container as it will look, drawn by **the cell the home screen draws** rather than by a picture of it — over
 * the real wallpaper, and at true scale.
 *
 * `IconContainerCell` over the real contents and the real arrangement, so this cannot show a shape or a spacing the
 * container does not have — the standing rule that two implementations of one thing are kept honest by a shared
 * derivation, with the strongest form of it available here, which is not having a second implementation at all.
 *
 * **A fixed pane, and the container drawn at its real dp size and then scaled into it.** This is the whole of why
 * the preview used to be a near-likeness rather than a likeness, and the two halves are separate ideas:
 *
 * - **Fixed pane**, so the space the preview costs does not depend on how big the container happens to be. A pane
 *   that sized itself to the container would move the controls under it every time one was resized.
 * - **Real size, then a graphics scale**, because two things inside a container are absolute rather than
 *   proportional: the gap between icons is a flat 8dp, and an icon is capped at the user's own `maxIconDp`. Drawn
 *   into a 148dp box, a 180×190dp container gave its icons a *larger* share of their slots (the cap stopped
 *   binding) and its gaps a smaller one, and a `GRID` picked a different column count outright, since `gridSlots`
 *   reads the box's aspect ratio. Laying out at the true size and scaling the finished drawing makes every one of
 *   those differences a single scale factor instead — the panel's corner, the gaps, the icons and the arrangement
 *   all shrink together, which is what "the same, smaller" means. [rememberContainerFootprint] is where that size
 *   comes from, and it comes from the same functions the surface uses.
 * - **[ContainerFootprint.metrics] rather than the ambient ones**, for the same reason: a settings screen's
 *   `LocalIconMetrics` are not the zone's, and an icon capped at a different `maxIconDp` is exactly how a preview
 *   looks nearly right.
 *
 * **The pane is the hole** ([punchThroughHole]) and holds nothing but the container: a container is a frosted panel
 * whose whole appearance is what it is over, so a gray box behind it is not a preview of it. Text inside a hole
 * clears its own background, which is the other reason nothing but the tile is in here.
 *
 * **Nothing at all unless there is something to show**, which is why it takes the whole [ContainerSettings] rather
 * than an icon list: a widget container has no arrangement to preview, and an empty icon container's preview is a
 * large picture of a "+" the add row already offers. Asking here keeps that rule in one place — and pinned, drawing
 * it anyway would spend the height for as long as the screen is open rather than until it was scrolled past.
 *
 * The pane survives a placement or a settings store that has not answered yet, drawing empty rather than
 * collapsing: it is pinned above a list, so a pane that appeared a frame late would move every control under it.
 *
 * `containerId` is deliberately left null: that is what stops the cell publishing a drop zone. A zone here would be
 * a second target for the id the real container on home already answers for, and at `z = 1` it would outrank it.
 */
@Composable
private fun IconContainerPreviewTile(settings: ContainerSettings?, shownScales: Pair<Int, Int>?) {
    val icon = (settings as? ContainerSettings.Icon)?.takeIf { it.icons.isNotEmpty() } ?: return
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            // **After the punch, so the inset is inside the hole**, and therefore wallpaper rather than background:
            // the ring draws no content, so `BlendMode.Src` clears it exactly as it clears around the container.
            // That is the whole point — the preview is *over* the wallpaper, so what separates it from the hole's
            // edge has to be wallpaper too, or the band stops reading as a window onto home.
            //
            // The height carries the inset on top of what the container gets, so raising this does not shrink it.
            .punchThroughHole()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val placed = icon.placed ?: return@BoxWithConstraints
        val footprint = rememberContainerFootprint(placed.placement, placed.zone) ?: return@BoxWithConstraints
        // Uniform, and the smaller of the two ratios, so the container keeps its aspect ratio and stays inside the
        // pane on both axes. Not capped at 1: a container smaller than the pane is scaled *up*, which is still a
        // faithful scale model and is the only way a 1×1 one would be legible here.
        val scale = minOf(maxWidth / footprint.size.width, maxHeight / footprint.size.height)
        Box(
            modifier = Modifier
                // **`requiredSize`, not `size`** — the point is to lay the container out at its home size even
                // though the pane is smaller, and `size` would be constrained back down to the pane, which is the
                // very thing this is avoiding. The scale is a draw-time transform, so nothing upstream sees it and
                // the layout inside still believes it is the size it is on home.
                .requiredSize(footprint.size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            IconContainerCell(
                icons = icon.icons,
                arrangement = icon.arrangement,
                iconScalePercent = shownScales?.first ?: icon.iconScalePercent,
                spacingScalePercent = shownScales?.second ?: icon.spacingScalePercent,
                metrics = footprint.metrics,
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
    Text(
        text = "OPTIONS",
        style = MaterialTheme.typography.labelMedium,
        color = colors.contentMuted,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
    )
}

/**
 * The rule between **how a container behaves** and **what it holds**.
 *
 * It used to open [OptionsHeading], back when the options came last and the thing above them was the contents. With
 * the two swapped it would have been a rule across the top of the list dividing nothing, so it moved to the seam it
 * was always actually drawing.
 */
@Composable
private fun SectionSeparator() {
    val colors = LocalMorphicColors.current
    Column {
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = colors.outline, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(20.dp))
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
