package inkspire.morphic.feature.settings.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.adaptive.currentDeviceConfiguration
import inkspire.morphic.core.designsystem.cell.fitRowHeight
import inkspire.morphic.core.designsystem.cell.rowHeightRange
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.grid.derivedCell
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitCols
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.maxCells
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.GridDefault
import inkspire.morphic.core.model.HorizontalPaddingRange
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsChip
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.label
import inkspire.morphic.feature.settings.component.SurfaceDetail
import inkspire.morphic.feature.settings.icons.IconSizingControls
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val RowGap = 8.dp
private val ChipGap = 8.dp

/**
 * **Apps**: how each arrangement of the app list is sized, and how its icons are drawn.
 *
 * **One section, a chip per layout** — the settings mirror of `feature:apps` being one module for five layouts. The
 * layouts differ only in arrangement, so what a user configures is "the paged one" or "the list", and every control
 * below addresses whichever is selected. L1 reached the same shape from the other direction: its drawer detail edited
 * `drawer.profile(layout)` — the profile of the selected layout — but it needed *two* details to do it, because the
 * drawer and the library were separate modules.
 *
 * **Selecting a chip changes nothing.** Which layout a user actually gets is a property of the home edge they swipe
 * from, and lives in the surface register beside that binding; this row only says which one you are configuring. That
 * is the same distinction the icons section's chips draw, and the reason neither writes on selection.
 *
 * **Layout group, then icon group**, as in every other section here and in every one of L1's, because the dependency
 * runs that way: the icon size decides the smallest usable cell, which is what the column and row limits above are
 * computed from — and, for the list, what the row-height slider's range is computed from.
 *
 * The one control that has no counterpart elsewhere is the list's **row height**, and the one grid missing from the
 * chips is the **category card** — see [ConfigurableLayouts] for why that gap is left open rather than filled with a
 * guessed bound.
 */
@Composable
internal fun AppsDetail(initialLayout: AppsLayout? = null, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<AppsSectionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }
    // **Arrived from the register's gear, on that edge's layout.**
    //
    // Keyed on `Unit`, so it runs once per *arrival* at this pane rather than once per distinct value. Keying on
    // `initialLayout` looked right and had a hole: gear on "Pages", tap the "List" chip, go back and gear "Pages"
    // again — the value never changed, so nothing re-fired and the pane opened on List. Leaving the pane disposes it
    // (both shells swap details through `AnimatedContent`), so re-entry is exactly the event this should follow.
    //
    // Null on every other route in, which leaves the ViewModel's own selection alone. It cannot name an
    // unconfigurable layout: that gear is not drawn — see `SurfaceRegisterCross`.
    LaunchedEffect(Unit) { initialLayout?.let(viewModel::selectLayout) }
    val sampleApp by viewModel.sample.app.collectAsStateWithLifecycle()

    val colors = LocalMorphicColors.current

    // Null only for the frame before the device is reported; there is no honest value to show until then, and a
    // placeholder would be a second source of truth for numbers the blueprint owns.
    val size = state.size ?: return
    val icon = state.icon ?: return
    val rowHeightDp = state.rowHeightDp ?: return
    val paddingDp = state.paddingDp ?: return

    val slot = state.layout.slot
    val metrics = icon.toIconMetrics()
    // What the preview draws while a slider is held — see the Home section for why it is keyed on the resolved value.
    var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
    val shownIcon = previewIcon ?: icon
    // The whole window, unlike home's: the APPS surface takes all of it, with no dock to subtract — minus the
    // selected layout's own margin, which every fit below divides. A section offering columns against the full
    // width would promise a grid its surface cannot draw, and for the pager it would be worse than cosmetic: that
    // fit is also the page **capacity**, so the two would paginate against different numbers.
    val fullWindow = usableWindowArea(uiInsets)
    val window = fullWindow.copy(widthDp = (fullWindow.widthDp - paddingDp * 2).coerceAtLeast(1f))

    // **Every grid here is shown as its surface draws it**, through the same fit that surface applies — which is
    // what makes the icon group below move both this editor and the preview. Raise the minimum icon dp and a
    // six-column grid becomes three, because a cell has to stay wide enough for the icon in it. The clamp is a read
    // on both sides: nothing is written, so the column returns when the icons shrink.
    //
    // Which fit depends on what the grid *has*, told apart by whether its stored size carries rows — exactly the two
    // kinds of grid on this surface. The pager is the fitted one that took work to earn: its rows x cols is also the
    // page **capacity** `AppsViewModel` paginates the store against, so it could not be clamped in a UI privately,
    // and `AppsScreen` now reports the fit to that ViewModel before anything is paginated.
    val storedRows = size.rows
    val drawn = if (storedRows == null) {
        size.copy(cols = slot.blueprint.fitCols(window.widthDp, size.cols, metrics))
    } else {
        val fitted = slot.blueprint.fitGridConfig(window, size.cols, storedRows, metrics)
        GridDefault(cols = fitted.visualCols, rows = fitted.visualRows)
    }

    var previewPadding by remember(paddingDp) { mutableStateOf<Int?>(null) }
    val shownPadding = previewPadding ?: paddingDp

    val isList = state.layout == AppsLayout.VERTICAL_LIST
    // **Its bounds are the icon guardrails plus the row's own inset**, so the range slider in the icon group governs
    // the row-height slider: widen the icon limits and it gains travel, narrow them and it loses it. **Unless the icons
    // are off**, in which case no guardrail applies at all — the floor becomes the label's own height and the ceiling
    // opens up, since a pure-text row can be as spacious as the user likes.
    val rowRange = rowHeightRange(metrics)
    var previewRowHeight by remember(rowHeightDp) { mutableStateOf<Float?>(null) }
    val shownRowHeight = previewRowHeight ?: fitRowHeight(rowHeightDp.dp, metrics).value

    // A scrolling grid stores no rows, so the preview draws **how many fit**: the cell height this column count
    // implies (the same derivation the surface lays out with) divided into the screen. That is what makes adding a
    // column visibly gain rows as the cells narrow — the actual consequence of the press, which a fixed preview
    // number would hide.
    val derived = derivedCell(cellWidth = window.widthDp.dp / drawn.cols, metrics = metrics)
    val derivedRows = maxCells(window.heightDp, derived.height.value)

    SurfaceDetail(
        title = "Apps",
        subtitle = "Per arrangement, for this screen orientation.",
        onReroll = viewModel.sample::reroll,
        modifier = modifier,
        layout = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ChipGap),
                verticalArrangement = Arrangement.spacedBy(ChipGap),
                modifier = Modifier.padding(top = ChipGap * 2),
            ) {
                ConfigurableLayouts.forEach { layout ->
                    SettingsChip(
                        label = layout.label,
                        selected = layout == state.layout,
                        onClick = { viewModel.selectLayout(layout) },
                    )
                }
            }

            if (isList) {
                // **The list gets an editor too, with no buttons on it.** One lane and a declared row height means
                // there is no count to press — but there are two things to *see*: the height its slider sets, and
                // which edge the search field is on. Both bounds are null, which makes `GridEditor` draw the frame
                // alone.
                GridEditor(
                    cols = 1,
                    rows = 1,
                    colBounds = null,
                    rowBounds = null,
                    aspectRatio = fullWindow.widthDp / fullWindow.heightDp.coerceAtLeast(1f),
                    horizontalInsetFraction = shownPadding / fullWindow.widthDp,
                    onEdit = { _, _ -> },
                    preview = {
                        AppsEditorPreview(
                            layout = state.layout,
                            cols = 1,
                            rows = 1,
                            metrics = metrics,
                            chrome = state.chrome,
                            areaWidthDp = window.widthDp,
                            insetFraction = shownPadding / fullWindow.widthDp,
                            edit = null,
                            rowHeightDp = shownRowHeight,
                        )
                    },
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            } else {
                // The same editor home and the dock use. No companion zone: an APPS layout fills the screen, so there
                // is no second area to draw at its share of it.
                val range = slot.blueprint.editableRangeIn(window, metrics)
                if (range != null) {
                    GridEditor(
                        cols = drawn.cols,
                        rows = drawn.rows ?: derivedRows,
                        colBounds = range.cols,
                        // Null for a scrolling grid, which hides the top and bottom pairs — its rows are however many
                        // its content reaches, so there is nothing there to offer.
                        rowBounds = range.rows,
                        // `fullWindow`, not the margin-narrowed `window`: the preview is the device's shape, and the
                        // margin is drawn inside it. Using the narrowed width made the box grow taller per dp of margin.
                        aspectRatio = fullWindow.widthDp / fullWindow.heightDp.coerceAtLeast(1f),
                        horizontalInsetFraction = shownPadding / fullWindow.widthDp,
                        // Counting from the drawn size, so a press moves the number the preview is showing.
                        onEdit = { edge, add -> viewModel.edit(edge, add, drawn) },
                        // **This layout's own mockup**, so each chip shows the surface it configures rather than one
                        // generic lattice — the reflective cells for the scrolling grids, the chrome for the ones that
                        // have it. `window` here (not `fullWindow`): the cell aspect comes from the width the grid is
                        // actually given.
                        preview = { edit ->
                            AppsEditorPreview(
                                layout = state.layout,
                                cols = drawn.cols,
                                rows = drawn.rows ?: derivedRows,
                                metrics = metrics,
                                chrome = state.chrome,
                                areaWidthDp = window.widthDp,
                                insetFraction = shownPadding / fullWindow.widthDp,
                                edit = edit,
                                // Read only by the list, which takes the other branch — the resolved value, since
                                // there is no row-height slider on this side to preview from.
                                rowHeightDp = fitRowHeight(rowHeightDp.dp, metrics).value,
                            )
                        },
                        modifier = Modifier.padding(top = RowGap * 2),
                    )
                }
            }

            if (isList) {
                SettingsCommitSlider(
                    title = "Row height",
                    subtitle = buildString {
                        append(
                            if (icon.showIcon) "How tall each row is; the icon fills it. "
                            else "How tall each row is. ",
                        )
                        append("${rowRange.start.roundToInt()}–${rowRange.endInclusive.roundToInt()} dp, ")
                        append(
                            if (icon.showIcon) "from the icon size limits below."
                            else "bounded by the label, not by the icon limits.",
                        )
                    },
                    value = fitRowHeight(rowHeightDp.dp, metrics).value,
                    valueRange = rowRange,
                    valueLabel = { "${it.roundToInt()} dp" },
                    onPreview = { previewRowHeight = it },
                    onCommit = { committed -> viewModel.setRowHeight(committed.roundToInt()) },
                )
            }

            // Every layout has edges, so this slider is outside the list/grid branch — unlike the row height, which
            // only the list has, and the editor, which only a grid with an `editRange` has.
            SettingsCommitSlider(
                title = "Side margin",
                subtitle = "Blank space at this layout's left and right edges.",
                value = paddingDp.toFloat(),
                valueRange = HorizontalPaddingRange.first.toFloat()..HorizontalPaddingRange.last.toFloat(),
                valueLabel = { "${it.roundToInt()} dp" },
                onPreview = { previewPadding = it.roundToInt() },
                onCommit = { viewModel.setPadding(it.roundToInt()) },
            )

            // The chrome choosers sit with the layout group rather than the icon group, because what they place is
            // drawn *around* the cells. One shared value for the surface, with the options depending on the layout.
            SettingsSectionHeader("Search")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ChipGap),
                verticalArrangement = Arrangement.spacedBy(ChipGap),
            ) {
                searchOptionsFor(state.layout).forEach { (label, placement) ->
                    SettingsChip(
                        label = label,
                        selected = state.chrome.search == placement,
                        onClick = { viewModel.setSearch(placement) },
                    )
                }
            }
            Text(
                text = "Search is not built on the Apps surface yet — this sets where it will sit, and the editor " +
                    "above shows it.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentMuted,
                modifier = Modifier.padding(top = RowGap),
            )

            // Tabs exist on one layout only, so the chooser appears on one chip only rather than being offered and
            // ignored.
            if (state.layout == AppsLayout.PAGER_WITH_CATEGORY) {
                SettingsSectionHeader("Category tabs")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ChipGap),
                    verticalArrangement = Arrangement.spacedBy(ChipGap),
                ) {
                    VerticalEdge.entries.forEach { edge ->
                        SettingsChip(
                            label = if (edge == VerticalEdge.TOP) "Top" else "Bottom",
                            selected = state.chrome.tabBarEdge == edge,
                            onClick = { viewModel.setTabBarEdge(edge) },
                        )
                    }
                }
            }

            MorphicButton(
                onClick = viewModel::resetSize,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = RowGap * 2),
            ) {
                Text(if (isList) "Reset row height" else "Reset grid")
            }
        },
        preview = { previewModifier ->
            // Its shape follows the layout: the list gets a real row at its row height, every other layout a real cell
            // at the size the fit above produced — which is what makes "the icons are 48dp" answerable as "in a cell
            // this size, that is what you get".
            //
            // A grid whose rows are *stored* (the pager) divides the screen and applies the fraction in its own cell;
            // one whose rows flow spends the fraction on the height instead, and is drawn with what `derivedCell` hands
            // back — a preview that spent it twice would show an icon those surfaces do not draw.
            val derivesHeight = !isList && drawn.rows == null
            val previewShown = if (derivesHeight) {
                derivedCell(cellWidth = window.widthDp.dp / drawn.cols, metrics = shownIcon.toIconMetrics())
            } else {
                null
            }
            IconSizingPreview(
                app = sampleApp,
                metrics = previewShown?.metrics ?: shownIcon.toIconMetrics(),
                cellWidth = if (isList) window.widthDp.dp else window.widthDp.dp / drawn.cols,
                cellHeight = if (isList) {
                    fitRowHeight(rowHeightDp.dp, metrics)
                } else {
                    // A paged grid divides the screen; a scrolling one derives its height from its width.
                    drawn.rows?.let { rows -> window.heightDp.dp / rows } ?: previewShown!!.height
                },
                asRow = isList,
                modifier = previewModifier,
            )
        },
        icons = {
            IconSizingControls(
                slot = slot,
                sizing = icon,
                onChange = viewModel.icons::change,
                onToggle = { label, showIcon -> viewModel.icons.toggle(label, showIcon) },
                onDpRange = viewModel.icons::changeDpRange,
                onPreview = { previewIcon = it },
            )
            MorphicButton(
                onClick = viewModel.icons::reset,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = RowGap * 2),
            ) {
                Text("Reset icons")
            }
        },
    )
}

/**
 * The search placements this [AppsLayout] can offer, as label → value.
 *
 * **Layout-dependent, which is `SearchPlacement`'s whole shape.** A standalone layout pins the field to an edge; the
 * category pager embeds it in the header beside the tabs, so it has no edge to choose. Offering all three everywhere
 * would let a user pick a state their layout cannot draw — L1's flat `SearchPosition` did exactly that.
 */
private fun searchOptionsFor(layout: AppsLayout): List<Pair<String, SearchPlacement>> =
    if (layout == AppsLayout.PAGER_WITH_CATEGORY) {
        listOf("In header" to SearchPlacement.InHeader, "Hidden" to SearchPlacement.Hidden)
    } else {
        listOf(
            "Top" to SearchPlacement.Pinned(VerticalEdge.TOP),
            "Bottom" to SearchPlacement.Pinned(VerticalEdge.BOTTOM),
            "Hidden" to SearchPlacement.Hidden,
        )
    }
