package inkspire.morphic.feature.settings.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import inkspire.morphic.feature.settings.icons.IconSizingControls
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val ScreenPadding = 20.dp
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
internal fun AppsDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<AppsSectionViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }
    val sampleApp by viewModel.sample.app.collectAsStateWithLifecycle()

    val colors = LocalMorphicColors.current
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text("Apps", style = MaterialTheme.typography.headlineSmall, color = colors.content)
        Text(
            text = "Per arrangement, for this screen orientation.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentMuted,
        )

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

        // Null only for the frame before the device is reported; there is no honest value to show until then, and a
        // placeholder would be a second source of truth for numbers the blueprint owns.
        val size = state.size
        val icon = state.icon
        val rowHeightDp = state.rowHeightDp
        val paddingDp = state.paddingDp
        if (size == null || icon == null || rowHeightDp == null || paddingDp == null) return@Column
        val slot = state.layout.slot
        val metrics = icon.toIconMetrics()
        // What the preview draws while a slider is held — see the Home section for why it is keyed on the resolved value.
        var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
        val shownIcon = previewIcon ?: icon
        // The whole window, unlike home's: the APPS surface takes all of it, with no dock to subtract — minus the
        // selected layout's own margin, which every fit below divides. A section offering columns against the full
        // width would promise a grid its surface cannot draw, and for the pager it would be worse than cosmetic: that
        // fit is also the page **capacity**, so the two would paginate against different numbers.
        val fullWindow = usableWindowArea(safeInsets)
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
        //
        // Hoisted above the layout branch because the preview below needs it too, and the list — whose blueprint edits
        // no axis at all — passes through it harmlessly at its one column.
        val storedRows = size.rows
        val drawn = if (storedRows == null) {
            size.copy(cols = slot.blueprint.fitCols(window.widthDp, size.cols, metrics))
        } else {
            val fitted = slot.blueprint.fitGridConfig(window, size.cols, storedRows, metrics)
            GridDefault(cols = fitted.visualCols, rows = fitted.visualRows)
        }
        // Every layout has edges, so this slider is outside the list/grid branch below — unlike the row height, which
        // only the list has, and the editor, which only a grid with an `editRange` has.
        var previewPadding by remember(paddingDp) { mutableStateOf<Int?>(null) }
        val shownPadding = previewPadding ?: paddingDp
        SettingsCommitSlider(
            title = "Side margin",
            subtitle = "Blank space at this layout's left and right edges.",
            value = paddingDp.toFloat(),
            valueRange = HorizontalPaddingRange.first.toFloat()..HorizontalPaddingRange.last.toFloat(),
            valueLabel = { "${it.roundToInt()} dp" },
            onPreview = { previewPadding = it.roundToInt() },
            onCommit = { viewModel.setPadding(it.roundToInt()) },
        )

        // The chrome choosers sit with the layout group rather than the icon group, because what they place is drawn
        // *around* the cells. One shared value for the surface, with the options depending on the layout — see
        // `AppsSectionViewModel.setSearch`.
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
            text = "Search is not built on the Apps surface yet — this sets where it will sit, and the preview above " +
                "shows it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = RowGap),
        )

        // Tabs exist on one layout only, so the chooser appears on one chip only rather than being offered and ignored.
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

        // A scrolling grid stores no rows, so the preview draws **how many fit**: the cell height this column count
        // implies (the same derivation the surface lays out with) divided into the screen. That is what makes adding a
        // column visibly gain rows as the cells narrow — the actual consequence of the press, which a fixed preview
        // number would hide.
        // **The cell, and the metrics its cell is really drawn with.** A scrolling grid's height is derived by spending
        // `iconPercent` on it, so the surface hands the cell a fraction of 1 — and the preview has to do the same, or it
        // would show an icon those surfaces do not draw. Meaningless for the pager and the list (neither derives a
        // height), which is why the preview below picks per layout rather than using this unconditionally.
        val derived = derivedCell(cellWidth = window.widthDp.dp / drawn.cols, metrics = metrics)

        if (state.layout == AppsLayout.VERTICAL_LIST) {
            // A list is one lane, so it has no grid to edit — its size *is* the row height. **Its bounds are the icon
            // guardrails plus the row's own inset**, so the range slider below governs this slider: widen the icon
            // limits and this gains travel, narrow them and it loses it. The value shown is clamped by the same
            // `fitRowHeightDp` the list draws with, so the control cannot sit at a height the surface is not using —
            // and the clamp is never written, so the user's height returns when the guardrails widen again.
            //
            // The dependency runs this way round because the icon range is the coarser statement of intent ("icons in
            // this list are 28–72 dp") and a row is the box around one. It is also why a *taller* row is asked for by
            // raising the upper guardrail rather than here: past that, the row is whitespace the icon cannot fill.
            //
            // **Unless the icons are off**, in which case no guardrail applies at all: the floor becomes the label's own
            // height and the ceiling opens up, since a pure-text row can be as spacious as the user likes. The subtitle
            // says which of the two it is, because a slider whose ends moved for an invisible reason reads as a bug.
            val range = rowHeightRange(metrics)
            // Live, so the lanes in the editor below scale under the finger rather than jumping on release — the same
            // pair the icon and margin sliders use.
            var previewRowHeight by remember(rowHeightDp) { mutableStateOf<Float?>(null) }
            val shownRowHeight = previewRowHeight ?: fitRowHeight(rowHeightDp.dp, metrics).value
            SettingsCommitSlider(
                title = "Row height",
                subtitle = buildString {
                    append(if (icon.showIcon) "How tall each row is; the icon fills it. " else "How tall each row is. ")
                    append("${range.start.roundToInt()}–${range.endInclusive.roundToInt()} dp, ")
                    append(if (icon.showIcon) "from the icon size limits below." else "bounded by the label, not by the icon limits.")
                },
                value = fitRowHeight(rowHeightDp.dp, metrics).value,
                valueRange = range,
                valueLabel = { "${it.roundToInt()} dp" },
                onPreview = { previewRowHeight = it },
                onCommit = { committed -> viewModel.setRowHeight(committed.roundToInt()) },
            )

            // **The list gets an editor too, with no buttons on it.** One lane and a declared row height means there is
            // no count to press — but there are two things to *see*: the height this slider sets, and which edge the
            // search field is on. Both bounds are null, which is what makes `GridEditor` draw the frame alone.
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
            // The same editor home and the dock use. No companion zone: an APPS layout fills the screen, so there is
            // no second area to draw at its share of it.
            val range = slot.blueprint.editableRangeIn(window, metrics)
            if (range != null) {
                GridEditor(
                    cols = drawn.cols,
                    rows = drawn.rows ?: maxCells(window.heightDp, derived.height.value),
                    colBounds = range.cols,
                    // Null for a scrolling grid, which hides the top and bottom pairs — its rows are however many its
                    // content reaches, so there is nothing there to offer.
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
                            rows = drawn.rows ?: maxCells(window.heightDp, derived.height.value),
                            metrics = metrics,
                            chrome = state.chrome,
                            areaWidthDp = window.widthDp,
                            insetFraction = shownPadding / fullWindow.widthDp,
                            edit = edit,
                            // Read only by the list, which takes the other branch — the resolved value, since there is
                            // no row-height slider on this side to preview from.
                            rowHeightDp = fitRowHeight(rowHeightDp.dp, metrics).value,
                        )
                    },
                    modifier = Modifier.padding(top = RowGap * 2),
                )
            }
        }

        MorphicButton(
            onClick = viewModel::resetSize,
            style = MorphicButtonStyle.Text,
            modifier = Modifier.padding(top = RowGap * 2),
        ) {
            Text(if (state.layout == AppsLayout.VERTICAL_LIST) "Reset row height" else "Reset grid")
        }

        // The **live preview**, between the layout and icon groups as L1 places it. Its shape follows the layout: the
        // list gets a real row at its row height, every other layout a real cell at the size the fit above produced —
        // which is what makes "the icons are 48dp" answerable as "in a cell this size, that is what you get".
        val asRow = state.layout == AppsLayout.VERTICAL_LIST
        // A grid whose rows are *stored* (the pager) divides the screen and applies the fraction in its own cell; one
        // whose rows flow spends the fraction on the height instead, and is drawn with what `derivedCell` hands back.
        val derivesHeight = !asRow && drawn.rows == null
        val previewShown = if (derivesHeight) {
            derivedCell(cellWidth = window.widthDp.dp / drawn.cols, metrics = shownIcon.toIconMetrics())
        } else {
            null
        }
        IconSizingPreview(
            app = sampleApp,
            metrics = previewShown?.metrics ?: shownIcon.toIconMetrics(),
            cellWidth = if (asRow) window.widthDp.dp else window.widthDp.dp / drawn.cols,
            cellHeight = if (asRow) {
                fitRowHeight(rowHeightDp.dp, metrics)
            } else {
                // A paged grid divides the screen; a scrolling one derives its height from its width.
                drawn.rows?.let { rows -> window.heightDp.dp / rows } ?: previewShown!!.height
            },
            onReroll = viewModel.sample::reroll,
            asRow = asRow,
            modifier = Modifier.padding(top = RowGap * 2),
        )

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
    }
}

/**
 * A human label for a layout.
 *
 * Local to this screen rather than on the enum, for the reason the icons section keeps its own: `core:model` stays
 * free of display strings and of localisation. The names describe the *arrangement* a user sees rather than the enum
 * constant — L1 named the same four "Minimalist", "Classic", "Paged" and "Grouped", which named its own history more
 * than the layouts.
 */
private val AppsLayout.label: String
    get() = when (this) {
        AppsLayout.VERTICAL_LIST -> "List"
        AppsLayout.VERTICAL_GRID -> "Grid"
        AppsLayout.PAGER -> "Pages"
        AppsLayout.PAGER_WITH_CATEGORY -> "Category pages"
        AppsLayout.CATEGORY_CARD -> "Category cards"
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
