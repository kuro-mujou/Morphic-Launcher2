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
import inkspire.morphic.core.designsystem.cell.CategoryCardGutter
import inkspire.morphic.core.designsystem.cell.CategoryCardSpacing
import inkspire.morphic.core.designsystem.cell.fitRowHeight
import inkspire.morphic.core.designsystem.cell.toIconMetrics
import inkspire.morphic.core.designsystem.cell.wholeRowHeightRange
import inkspire.morphic.core.designsystem.component.slider.MorphicSliderRow
import inkspire.morphic.core.designsystem.grid.cardMinCell
import inkspire.morphic.core.designsystem.grid.derivedCell
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitCols
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.maxCells
import inkspire.morphic.core.designsystem.grid.minCellFor
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsCardGrid
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.CardChrome
import inkspire.morphic.core.model.CardChromeRanges
import inkspire.morphic.core.model.GridDefault
import inkspire.morphic.core.model.HorizontalPaddingRange
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.core.model.blueprint
import inkspire.morphic.feature.settings.component.EditorReset
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.SettingsChip
import inkspire.morphic.feature.settings.component.SettingsRowPadding
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsSwitchRow
import inkspire.morphic.feature.settings.component.SurfaceDetail
import inkspire.morphic.feature.settings.icons.IconSizingGroup
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import inkspire.morphic.feature.settings.label
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * The name the card preview's made-up category carries.
 *
 * Deliberately generic: the preview stands in for *a* category rather than reproducing one of the user's, so a real
 * name would invite the reading that this is their "Media" card and that changing a slider changes that one.
 */
private const val SampleCategoryName = "Category"

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
/**
 * Where each card control's reset goes: the blueprint's own chrome, which is all zeroes and a 1x title.
 *
 * Read from the blueprint rather than restated, because that is where a default lives — `data:settings` resolves every
 * card override against this same object, so a reset here lands exactly where an untouched launcher already is.
 */
private val CardChromeDefaults = AppsCardGrid.card ?: CardChrome()

private val RowGap = 8.dp
private val ChipGap = 8.dp

/**
 * **Apps**: how each arrangement of the app list is sized, and how its icons are drawn.
 *
 * **One section, a chip per layout** — the settings mirror of `feature:apps` being one module for five layouts. The
 * layouts differ only in arrangement, so what a user configures is "the paged one" or "the list", and every control
 * below addresses whichever is selected. Splitting the surface into two modules would need two of these details,
 * with "which one am I in?" answered before anything else.
 *
 * **Selecting a chip changes nothing.** Which layout a user actually gets is a property of the home edge they swipe
 * from, and lives in the surface register beside that binding; this row only says which one you are configuring. That
 * is the same distinction the icons section's chips draw, and the reason neither writes on selection.
 *
 * **Layout group, then icon group**, as in every other section here, because the dependency runs that way: the icon
 * size decides the smallest usable cell, which is what the column and row limits above are
 * computed from — and, for the list, what the row-height slider's range is computed from.
 *
 * **Two controls have no counterpart elsewhere**, and both belong to one layout: the list's **row height**, and the
 * category card's four chrome sliders. The card is also the one layout whose preview is a whole **card** rather than a
 * cell — half of what it configures is the tile around the icons, which a lone cell cannot show.
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
    val sampleCardApps by viewModel.sampleApps.collectAsStateWithLifecycle()

    val colors = LocalMorphicColors.current

    // Null only for the frame before the device is reported; there is no honest value to show until then, and a
    // placeholder would be a second source of truth for numbers the blueprint owns.
    val size = state.size ?: return
    val rowHeightDp = state.rowHeightDp ?: return
    val paddingDp = state.paddingDp ?: return

    val slot = state.layout.slot
    // Every APPS grid declares icon sizing, including the card — whose *slots* are icons even though the tile around
    // them is not. So a null here is only ever "not yet".
    val icon = state.icon ?: return
    val metrics = icon.toIconMetrics()
    // **Null means "this layout is not a card"**, the blueprint's own convention, and it selects the card group and
    // the whole-card preview below — `DockState.icon` does exactly this for the widget area.
    val card = state.card
    // **The floor a cell of this grid may not go below** — one value, so every fit, bound and cap below reads the same
    // number (the dock section's rule). For a card that floor is a *tile's*: two preview icons at their own guardrail
    // plus the paddings around and between them, which is why widening those paddings takes a lane away.
    val minCell = if (card == null) minCellFor(metrics) else cardMinCell(metrics, card)
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
    // **What the lane count actually divides.** For every other layout that is the margin-narrowed window; a card grid
    // also keeps its own gutter, and the surface subtracts it before fitting — so this must too, or the editor offers a
    // lane the grid has no room for. `cardMinCell` folds in the spacing between lanes, the other half of the same sum.
    val fitArea = if (card == null) window else {
        window.copy(widthDp = (window.widthDp - CategoryCardGutter.value * 2).coerceAtLeast(1f))
    }
    val storedRows = size.rows
    val drawn = if (storedRows == null) {
        size.copy(cols = slot.blueprint.fitCols(fitArea.widthDp, size.cols, minCell))
    } else {
        val fitted = slot.blueprint.fitGridConfig(fitArea, size.cols, storedRows, minCell)
        GridDefault(cols = fitted.visualCols, rows = fitted.visualRows)
    }

    var previewPadding by remember(paddingDp) { mutableStateOf<Int?>(null) }
    val shownPadding = previewPadding ?: paddingDp

    // The card's four controls preview live into the card below, exactly as the icon sliders do into their cell: a
    // corner radius or a slot gap is a thing you judge by looking at it, not by reading a number.
    var previewCard by remember(card) { mutableStateOf<CardChrome?>(null) }
    val shownCard = previewCard ?: card

    val isList = state.layout == AppsLayout.VERTICAL_LIST
    // **Its bounds are the icon guardrails plus the row's own inset**, so the range slider in the icon group governs
    // the row-height slider: widen the icon limits and it gains travel, narrow them and it loses it. **Unless the icons
    // are off**, in which case no guardrail applies at all — the floor becomes the label's own height and the ceiling
    // opens up, since a pure-text row can be as spacious as the user likes.
    // Whole dp, because that is what the store holds - see `wholeRowHeightRange`.
    val rowRange = wholeRowHeightRange(metrics)
    var previewRowHeight by remember(rowHeightDp) { mutableStateOf<Float?>(null) }
    val fittedRowHeight = fitRowHeight(rowHeightDp.dp, metrics).value
    val shownRowHeight = previewRowHeight ?: fittedRowHeight

    // A scrolling grid stores no rows, so the preview draws **how many fit**: the cell height this column count
    // implies (the same derivation the surface lays out with) divided into the screen. That is what makes adding a
    // column visibly gain rows as the cells narrow — the actual consequence of the press, which a fixed preview
    // number would hide.
    //
    // **How tall a cell is depends on what the grid holds**, which is the one place a tile grid parts company with an
    // icon one: an icon cell derives its height from its width through the icon and the label under it, where a card
    // is its **square preview** plus a title above it.
    //
    // The square is what the mockup draws, and the title's height is knowingly left out. It lives in the card's own
    // inset and gap constants over in `feature:apps`, which this module does not depend on and should not — so the
    // choice is between an approximation here and a shared constant that would have to be kept in step by hand, and
    // the codebase has been bitten by the second more than the first. The cost is that the row count can read one
    // high on a tall screen; the lane count, which is the number these buttons actually change, is exact.
    val cellWidthDp = fitArea.widthDp / drawn.cols
    // **What one card is actually drawn at.** A *lane* is `fitArea ÷ cols`, but the lanes are separated by
    // `CategoryCardSpacing`, so a card is that less its share of the gaps — the same sum the surface's `Row` of
    // weighted cards produces. The preview draws at this rather than at the lane, because a card 12dp wider than the
    // real one is exactly the kind of near-miss that makes a preview worth less than no preview.
    //
    // True size in portrait, where the pane is the screen's width. In **landscape** `SurfaceDetail` gives the preview
    // a fixed 220dp column, so a card wider than that is drawn narrower rather than overflowing — Compose clamps the
    // inner `width` to the incoming constraint. Worth knowing rather than worth fixing here: widening that column is
    // the shared scaffold's decision, and every other section's preview is a single cell that fits it comfortably.
    val cardWidthDp = ((fitArea.widthDp - (drawn.cols - 1) * CategoryCardSpacing.value) / drawn.cols)
        .coerceAtLeast(1f)
    val cellHeightDp = if (card == null) derivedCell(cellWidthDp.dp, metrics).height.value else cellWidthDp
    val derivedRows = maxCells(window.heightDp, cellHeightDp)


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
                val range = slot.blueprint.editableRangeIn(fitArea, minCell)
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
                        // Against the **stored** size rather than the `drawn` one beside it: a grid whose lanes were
                        // taken by larger icons has not been resized by the user. See [EditorReset].
                        reset = EditorReset(
                            changed = size != slot.blueprint.defaults.getValue(device),
                            onReset = viewModel::resetGrid,
                        ),
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
                                rowHeightDp = fittedRowHeight,
                            )
                        },
                        modifier = Modifier.padding(top = RowGap * 2),
                    )
                }
            }

            if (isList) {
                MorphicSliderRow(
                    label = "Row height",
                    what = "row height",
                    value = fittedRowHeight.roundToInt(),
                    valueRange = rowRange,
                    // Clamped into the same window, because the range's own bounds are the icon guardrails and those
                    // are the user's too: a blueprint row height outside today's limits is not somewhere a reset may
                    // land.
                    default = slot.blueprint.rowHeightDp!!.coerceIn(rowRange.first, rowRange.last),
                    valueLabel = { "$it dp" },
                    onPreview = { previewRowHeight = it.toFloat() },
                    onCommit = viewModel::setRowHeight,
                    onReset = { viewModel.setRowHeight(null) },
                    modifier = SettingsRowPadding,
                )
            }

            // Every layout has edges, so this slider is outside the list/grid branch — unlike the row height, which
            // only the list has, and the editor, which only a grid with an `editRange` has.
            MorphicSliderRow(
                label = "Side margin",
                what = "side margin",
                value = paddingDp,
                valueRange = HorizontalPaddingRange,
                default = slot.blueprint.horizontalPaddingDp,
                valueLabel = { "$it dp" },
                onPreview = { previewPadding = it },
                onCommit = viewModel::setPadding,
                onReset = { viewModel.setPadding(null) },
                modifier = SettingsRowPadding,
            )

            // **Only on the two layouts that page**, said by the state leaving `wraps` null rather than by a second
            // `state.layout ==` test here. Which of the two pagers it writes follows the chip, which is the whole
            // point: one global flag would govern both of these *and* home's, from a control appearing on one
            // screen.
            state.wraps?.let { wraps ->
                SettingsSectionHeader("Paging")
                SettingsSwitchRow(
                    title = "Infinite scroll",
                    // Named for the axis this surface is reached on: wrapping removes the pager's ends, and the
                    // surface swipe hands off *at* an end — so getting back to home sideways stops working with one
                    // finger. Worth saying, because it is not discoverable by trying the toggle once.
                    subtitle = "Pages wrap around at the edges. Returning home sideways then needs two fingers.",
                    checked = wraps,
                    onCheckedChange = viewModel::setWraps,
                )
            }

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
        },
        // **The card previews a whole card, where every other layout previews one cell**, and the difference is what
        // the thing being configured *is*. Elsewhere the question is "how big is the icon in this cell", which a cell
        // answers; here half the controls shape the tile around the icons — a corner, a title, two paddings — and none
        // of that is visible on a lone cell. So this draws a real `CategoryCardFace` at the width its lane count
        // produces, with the sliders feeding it live.
        //
        // It is the same face the surface draws, from `core:designsystem`, rather than a mockup assembled here: a
        // preview that can drift from the thing it previews is worth less than no preview.
        preview = { previewModifier ->
            // Its shape follows the layout: the list gets a real row at its row height, every other layout a real cell
            // at the size the fit above produced — which is what makes "the icons are 48dp" answerable as "in a cell
            // this size, that is what you get".
            //
            // A grid whose rows are *stored* (the pager) divides the screen and applies the fraction in its own cell;
            // one whose rows flow spends the fraction on the height instead, and is drawn with what `derivedCell` hands
            // back — a preview that spent it twice would show an icon those surfaces do not draw.
            if (shownCard != null) {
                // **A made-up category, filled with installed apps** — not one of the user's. Previewing a real
                // category would draw whatever that phone happens to hold, and a category of two apps leaves half the
                // slots empty, which is precisely the state in which the spacing and padding sliders show nothing. A
                // preview has to draw the full case for the controls to be readable. The dice rerolls all four.
                CategoryCardPreview(
                    title = SampleCategoryName,
                    apps = sampleCardApps,
                    chrome = shownCard,
                    metrics = shownIcon.toIconMetrics(),
                    cardWidth = cardWidthDp.dp,
                    modifier = previewModifier,
                )
                return@SurfaceDetail
            }
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
                    fittedRowHeight.dp
                } else {
                    // A paged grid divides the screen; a scrolling one derives its height from its width.
                    drawn.rows?.let { rows -> window.heightDp.dp / rows } ?: previewShown!!.height
                },
                asRow = isList,
                modifier = previewModifier,
            )
        },
        // **No icon group at all on the category card**, which is `AppsCardGrid.icon` being null reaching the screen:
        // a card is a tile, so there is no fraction, guardrail or label to set. The same branch the dock section takes
        // for the widget area.
        icons = if (icon == null) null else {
            {
                IconSizingGroup(
                    slot = slot,
                    sizing = icon,
                    edits = viewModel.icons,
                    onPreview = { previewIcon = it },
                )

                // **The card's own controls belong here, under the pinned preview**, not up in the layout group with
                // the lane buttons. `SurfaceDetail` pins the heading and the preview together and scrolls everything
                // in this slot beneath them — which is exactly what these four need: a corner radius or a slot gap is
                // judged by looking at the card while dragging, and a control that scrolls its own preview off the
                // screen cannot be judged at all. It is the same reason the icon sliders sit here rather than above.
                //
                // Drawn only for the layout that has tiles, said by the state leaving `card` null rather than by a
                // second `state.layout ==` test — the paging switch's shape. Everything starts at zero, so a card is a
                // plain rectangle of edge-to-edge icons until a user decides otherwise.
                card?.let { chrome ->
                    SettingsSectionHeader("Card")
                    // **Every reset here goes to `CardChrome()`'s own field**, read from the model rather than typed
                    // out: a card starts as a plain rectangle of edge-to-edge icons, and that decision lives in the
                    // constructor. A number restated here would be a second answer to drift from it.
                    MorphicSliderRow(
                        label = "Title text size",
                        what = "title text size",
                        value = chrome.titleScale,
                        valueRange = CardChromeRanges.TitleScale,
                        default = CardChromeDefaults.titleScale,
                        valueLabel = { "%.2fx".format(it) },
                        onPreview = { previewCard = chrome.copy(titleScale = it) },
                        onCommit = { viewModel.cardChrome.change(CardChromeField.TitleScale, it) },
                        onReset = { viewModel.cardChrome.clear(CardChromeField.TitleScale) },
                        modifier = SettingsRowPadding,
                    )
                    MorphicSliderRow(
                        label = "Corner radius",
                        what = "corner radius",
                        value = chrome.cornerRadiusDp,
                        valueRange = CardChromeRanges.CornerRadiusDp,
                        default = CardChromeDefaults.cornerRadiusDp,
                        valueLabel = { "$it dp" },
                        onPreview = { previewCard = chrome.copy(cornerRadiusDp = it) },
                        onCommit = { viewModel.cardChrome.change(CardChromeField.CornerRadius, it.toFloat()) },
                        onReset = { viewModel.cardChrome.clear(CardChromeField.CornerRadius) },
                        modifier = SettingsRowPadding,
                    )
                    MorphicSliderRow(
                        label = "Icon area padding",
                        what = "icon area padding",
                        value = chrome.outerPaddingDp,
                        valueRange = CardChromeRanges.PaddingDp,
                        default = CardChromeDefaults.outerPaddingDp,
                        valueLabel = { "$it dp" },
                        onPreview = { previewCard = chrome.copy(outerPaddingDp = it) },
                        onCommit = { viewModel.cardChrome.change(CardChromeField.OuterPadding, it.toFloat()) },
                        onReset = { viewModel.cardChrome.clear(CardChromeField.OuterPadding) },
                        modifier = SettingsRowPadding,
                    )
                    MorphicSliderRow(
                        label = "Icon spacing",
                        what = "icon spacing",
                        value = chrome.innerPaddingDp,
                        valueRange = CardChromeRanges.PaddingDp,
                        default = CardChromeDefaults.innerPaddingDp,
                        valueLabel = { "$it dp" },
                        onPreview = { previewCard = chrome.copy(innerPaddingDp = it) },
                        onCommit = { viewModel.cardChrome.change(CardChromeField.InnerPadding, it.toFloat()) },
                        onReset = { viewModel.cardChrome.clear(CardChromeField.InnerPadding) },
                        modifier = SettingsRowPadding,
                    )
                }
            }
        },
    )
}

/**
 * The search placements this [AppsLayout] can offer, as label → value.
 *
 * **Layout-dependent, which is `SearchPlacement`'s whole shape.** A standalone layout pins the field to an edge; the
 * category pager embeds it in the header beside the tabs, so it has no edge to choose. Offering all three everywhere
 * would let a user pick a state their layout cannot draw, which is what a flat placement enum allows.
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
