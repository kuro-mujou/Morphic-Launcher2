package inkspire.morphic.feature.settings.grid

import androidx.compose.foundation.layout.padding
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
import inkspire.morphic.core.designsystem.grid.editableRangeIn
import inkspire.morphic.core.designsystem.grid.fitGridConfig
import inkspire.morphic.core.designsystem.grid.sideZoneFraction
import inkspire.morphic.core.designsystem.grid.splitForSideZone
import inkspire.morphic.core.designsystem.grid.usableWindowArea
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.model.GridSlot
import inkspire.morphic.core.model.HorizontalPaddingRange
import inkspire.morphic.core.model.HomePagerGrid
import inkspire.morphic.core.model.IconSizing
import inkspire.morphic.core.model.mainSlot
import inkspire.morphic.core.model.sideZoneEdge
import inkspire.morphic.feature.settings.component.CompanionSide
import inkspire.morphic.feature.settings.component.EditorCompanion
import inkspire.morphic.feature.settings.component.GridEditor
import inkspire.morphic.feature.settings.component.LanePreview
import inkspire.morphic.feature.settings.component.SettingsCommitSlider
import inkspire.morphic.feature.settings.component.SettingsSectionHeader
import inkspire.morphic.feature.settings.component.SettingsSwitchRow
import inkspire.morphic.feature.settings.component.SurfaceDetail
import inkspire.morphic.feature.settings.component.of
import inkspire.morphic.feature.settings.icons.IconSizingControls
import kotlin.math.roundToInt
import inkspire.morphic.feature.settings.icons.IconSizingPreview
import org.koin.androidx.compose.koinViewModel

/** Provisional spacing — placeholders, as everywhere else, until the settings layer owns its own metrics. */
private val RowGap = 8.dp

/**
 * **Home**: how HOME's main area is sized, and how its icons are sized — **whichever main area the current pairing
 * gives it**.
 *
 * One section for both layouts, which is the settings mirror of one `HomeScreen` for both surfaces and rests on the
 * same argument: what a user configures is "home", and whether that means a grid's rows and columns or a list's row
 * height is decided one screen over, in the surface register. Selecting a pairing there is what switches these
 * controls; nothing here writes it.
 *
 * **Layout group, then icon group**, which is L1's structure for every surface detail and the order the dependency
 * runs in: the icon size sets the smallest usable cell (and the shortest usable row), and that is what this screen's
 * limits are computed from. Within the layout group the **editor comes first and the sliders under it**, again as L1
 * has it — see [SurfaceDetail], which owns the arrangement and pins the icon heading and preview together.
 *
 * The counterpart of the side-zone section, and the difference between them is the whole shape of grid configuration
 * in this launcher: home is sized **by what it is given** — it takes the space the side zone leaves and divides it —
 * while the side zone is sized **by extent** as well as by counts.
 *
 * **The area it measures against is the window minus the side zone**, which is exactly what home gets — and *which*
 * dimension that zone takes is its edge's to say (`SideZoneEdge`): a strip takes height, a phone-landscape rail takes
 * width, so the main area is short in the first case and narrow in the second. `splitForSideZone` is that one
 * expression, read here and by the surface. The subtraction is this screen's arithmetic rather than part of measuring
 * a window ([usableWindowArea]) — L1 folded it into the measurement (`homeGridArea(..., dockVisible, dockThickness)`),
 * so every caller had to supply dock facts even when sizing something unrelated.
 *
 * **The editor shows what home will draw, not what is in storage** — so changing the icon size below recalculates it.
 * On the pager that is the row and column counts; on the list it is the row height, clamped to what the guardrails can
 * honor. Either way the clamp is **applied, never written back**: shrink the icons again and the fifth row (or the
 * taller row) comes back, because nothing wrote the clamp down. L1 did write it — a `LaunchedEffect` right here,
 * firing on every cause — so an icon tweak permanently destroyed a count that had nothing to do with icons, and only
 * while this screen happened to be open. The one write that reduces home's counts belongs to the side zone's extent
 * commit, which is a deliberate change to the space home is left with.
 *
 * **On the pager, editing names an edge rather than a number**, and each press is two writes — the count and the
 * placements it displaces. See [GridSizeViewModel]; the reason is that only the button press knows *which* side
 * changed, and a left column removed and a right column removed leave your apps in different places. The list has no
 * such press: one lane means there is nothing to add or remove, so its editor is a frame with no buttons on it and a
 * row-height slider underneath.
 */
@Composable
internal fun GridSizeDetail(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<GridSizeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val device = currentDeviceConfiguration()
    LaunchedEffect(device) { viewModel.setDevice(device) }
    val sampleApp by viewModel.sample.app.collectAsStateWithLifecycle()

    // Null only for the frame before the device is reported; there is no honest value to show until then.
    val main = state.main ?: return
    val icon = state.icon ?: return
    val sideExtentDp = state.sideExtentDp ?: return
    val paddingDp = state.paddingDp ?: return
    val slot = state.layout.mainSlot

    // **What the preview draws while a slider is held.** Keyed on the resolved sizing, so a commit clears it and the
    // preview falls back to what was stored — the same shape `SettingsCommitSlider` uses for its own label, and the
    // same reason: the write is asynchronous, so dropping the local value on release would flash the old one. The
    // *controls* still read `icon`, since a slider must show its committed position.
    var previewIcon by remember(icon) { mutableStateOf<IconSizing?>(null) }
    val shownIcon = previewIcon ?: icon
    val metrics = icon.toIconMetrics()
    val window = usableWindowArea(uiInsets)

    // Previewed per frame, written on release — the same pair the icon sliders use, so the editor's inset tracks the
    // drag instead of jumping on release.
    var previewPadding by remember(paddingDp) { mutableStateOf<Int?>(null) }
    val shownPadding = previewPadding ?: paddingDp

    // Home gets the window minus the side zone, which is why that zone's extent belongs in this screen's state even
    // though nothing here edits it: a thicker zone genuinely means less room. **Which axis it comes off is the edge's
    // to say** — a strip takes height, a phone-landscape rail takes width — through the same `splitForSideZone` the
    // surface itself draws with, so what this screen offers is what home will have. Then narrowed by the margin, for
    // the same reason: both are space the main area does not get.
    val edge = device.sideZoneEdge(state.layout)
    val split = window.splitForSideZone(sideExtentDp.toFloat(), edge)
    val homeArea = split.main.copy(widthDp = (split.main.widthDp - paddingDp * 2).coerceAtLeast(1f))

    // The side zone at its real share of the screen and **on the side it really occupies** — so growing it and
    // shrinking home read as the same picture from either section, and a phone in landscape shows the rail it will
    // actually get. Shared by both arms, since both layouts have one.
    val companion = EditorCompanion(
        fraction = window.sideZoneFraction(sideExtentDp.toFloat(), edge),
        side = CompanionSide.of(edge),
    )
    // The **whole** window's ratio: the preview is the device's shape, and the margin is an inset within it rather
    // than a reshaping of it. `homeArea` above is narrowed for the *bounds*, which is a different question — how much
    // fits — and passing it here stretched the box.
    val aspectRatio = window.widthDp / window.heightDp.coerceAtLeast(1f)
    val insetFraction = shownPadding / window.widthDp

    SurfaceDetail(
        title = if (main is MainAreaSize.Rows) "Home list" else "Home grid",
        subtitle = when (main) {
            is MainAreaSize.Rows -> "How tall each row is, and how its icons are sized."
            is MainAreaSize.Grid -> "Rows and columns on the home pages. The − and + on each edge change that side."
        },
        onReroll = viewModel.sample::reroll,
        modifier = modifier,
        layout = {
            when (main) {
                is MainAreaSize.Grid -> {
                    val range = HomePagerGrid.editableRangeIn(homeArea, metrics)
                    // **The grid as home will actually draw it**, through the same `fitGridConfig` the surface reads
                    // its own counts with — which is what makes the icon group below move this editor.
                    val fitted =
                        HomePagerGrid.fitGridConfig(homeArea, cols = main.cols, rows = main.rows, metrics = metrics)
                    if (range != null) {
                        GridEditor(
                            cols = fitted.visualCols,
                            rows = fitted.visualRows,
                            colBounds = range.cols,
                            rowBounds = range.rows,
                            aspectRatio = aspectRatio,
                            horizontalInsetFraction = insetFraction,
                            // Counting from the drawn grid rather than the stored one, so − and + mean what the
                            // preview shows.
                            onEdit = { e, add -> viewModel.edit(e, add, fitted.visualCols, fitted.visualRows) },
                            companion = companion,
                            modifier = Modifier.padding(top = RowGap * 2),
                        )
                    }
                }
                is MainAreaSize.Rows -> HomeListEditor(
                    rowHeightDp = main.heightDp,
                    icon = icon,
                    aspectRatio = aspectRatio,
                    insetFraction = insetFraction,
                    areaWidthDp = homeArea.widthDp,
                    companion = companion,
                    onSetRowHeight = viewModel::setRowHeight,
                )
            }

            // **Only on the pairing that has a pager**, which the state says by leaving `wraps` null rather than by
            // this screen asking the layout a second time. L1 gated the same control the same way — on
            // `homeSurface == PAGER_GRID` — but its single global flag then changed the app drawer's pagers too,
            // with no control there to show it. Here the toggle writes the grid this section is editing and nothing
            // else.
            state.wraps?.let { wraps ->
                SettingsSectionHeader("Paging")
                SettingsSwitchRow(
                    title = "Infinite scroll",
                    // The second sentence is the part a user cannot discover by trying it once: wrapping removes the
                    // end of the pager, and the surface swipe hands off *at* an end — so the side surfaces on this
                    // axis stop opening with one finger. A control that quietly takes a gesture away has to say so.
                    subtitle = "Pages wrap around at the edges. Side surfaces left and right then need two fingers.",
                    checked = wraps,
                    onCheckedChange = viewModel::setWraps,
                )
            }

            SettingsCommitSlider(
                title = "Side margin",
                subtitle = "Blank space at the main area's left and right edges.",
                value = paddingDp.toFloat(),
                valueRange = HorizontalPaddingRange.first.toFloat()..HorizontalPaddingRange.last.toFloat(),
                valueLabel = { "${it.roundToInt()} dp" },
                onPreview = { previewPadding = it.roundToInt() },
                onCommit = { viewModel.setPadding(it.roundToInt()) },
            )

            MorphicButton(
                onClick = viewModel::reset,
                style = MorphicButtonStyle.Text,
                modifier = Modifier.padding(top = RowGap * 2),
            ) {
                Text(if (main is MainAreaSize.Rows) "Reset row height" else "Reset grid")
            }
        },
        preview = { previewModifier ->
            // A home cell's size comes from dividing an area, so the fraction is the cell's to apply and these metrics
            // go straight through — unlike the two APPS grids whose height is derived from it. A **row** is the same
            // case for the same reason: its height is declared, and the icon then fills what that height bought.
            IconSizingPreview(
                app = sampleApp,
                metrics = shownIcon.toIconMetrics(),
                cellWidth = when (main) {
                    is MainAreaSize.Rows -> homeArea.widthDp.dp
                    is MainAreaSize.Grid -> {
                        val fitted = HomePagerGrid.fitGridConfig(homeArea, main.cols, main.rows, metrics)
                        homeArea.widthDp.dp / fitted.visualCols
                    }
                },
                cellHeight = when (main) {
                    is MainAreaSize.Rows -> fitRowHeight(main.heightDp.dp, metrics)
                    is MainAreaSize.Grid -> {
                        val fitted = HomePagerGrid.fitGridConfig(homeArea, main.cols, main.rows, metrics)
                        homeArea.heightDp.dp / fitted.visualRows
                    }
                },
                asRow = main is MainAreaSize.Rows,
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
 * The vertical list's half of the layout group: **a frame with no buttons on it, and the slider that moves it**.
 *
 * A list has one lane and a declared row height, so there is nothing to press — but there are two things to see, and
 * they are the reason it still gets an editor rather than a bare slider: the lanes at the height being set, and the
 * widget area beside them at its real share of the screen.
 *
 * **The slider's bounds are the icon guardrails plus the row's own inset** (`rowHeightRange`), which is what makes the
 * icon range slider *govern* this one: a row shorter than `minIconDp` + padding cannot honor the smallest icon
 * allowed, and one taller than `maxIconDp` + padding is height the largest cannot fill. So the way to ask for a taller
 * row is to raise the upper guardrail. With icons off the range changes shape — the floor becomes the label's own
 * height and the ceiling opens up — because both ends would otherwise describe an icon that is not there. This is the
 * APPS list's rule, and it is the same rule because it is the same kind of grid.
 *
 * @param areaWidthDp the width the list is really given, margin already subtracted — what [LanePreview] needs for a
 *   real lane aspect.
 */
@Composable
private fun HomeListEditor(
    rowHeightDp: Int,
    icon: IconSizing,
    aspectRatio: Float,
    insetFraction: Float,
    areaWidthDp: Float,
    companion: EditorCompanion,
    onSetRowHeight: (Int) -> Unit,
) {
    val metrics = icon.toIconMetrics()
    val range = rowHeightRange(metrics)
    // Previewed per frame so the lanes grow under the finger, committed on release — the same pair every other slider
    // that moves a preview uses.
    var previewRowHeight by remember(rowHeightDp) { mutableStateOf<Float?>(null) }
    val shownRowHeight = previewRowHeight ?: fitRowHeight(rowHeightDp.dp, metrics).value

    GridEditor(
        cols = 1,
        rows = 1,
        // Neither axis is the user's: a list is one lane by definition and its rows flow. That hides every button and
        // omits the caption too, since "1 column" would be a count masquerading as a choice.
        colBounds = null,
        rowBounds = null,
        aspectRatio = aspectRatio,
        horizontalInsetFraction = insetFraction,
        onEdit = { _, _ -> },
        preview = { LanePreview(rowHeightDp = shownRowHeight, areaWidthDp = areaWidthDp, insetFraction = insetFraction) },
        companion = companion,
        modifier = Modifier.padding(top = RowGap * 2),
    )

    SettingsCommitSlider(
        title = "Row height",
        subtitle = buildString {
            append(if (icon.showIcon) "How tall each row is; the icon fills it. " else "How tall each row is. ")
            append("${range.start.roundToInt()}–${range.endInclusive.roundToInt()} dp, ")
            append(
                if (icon.showIcon) "from the icon size limits below."
                else "bounded by the label, not by the icon limits.",
            )
        },
        value = fitRowHeight(rowHeightDp.dp, metrics).value,
        valueRange = range,
        valueLabel = { "${it.roundToInt()} dp" },
        onPreview = { previewRowHeight = it },
        onCommit = { committed -> onSetRowHeight(committed.roundToInt()) },
    )
}
