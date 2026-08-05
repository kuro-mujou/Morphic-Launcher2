package inkspire.morphic.feature.settings.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.ceil
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.data.settings.AppsChrome
import inkspire.morphic.feature.settings.component.GridPreview
import inkspire.morphic.feature.settings.component.PreviewEdit
import inkspire.morphic.feature.settings.component.ReflectivePreview

private val BarHeight = 10.dp
private val TabHeight = 8.dp
private val ChromeGap = 6.dp
private val ActionSquareSize = 12.dp
private val BarCorner = 5.dp

/** Enough tabs to read as a row, without implying a count the user chose — the categories decide that. */
private const val TabCount = 4

/**
 * **The APPS editor's per-layout mockup**: the chrome a layout draws, around the lattice it draws.
 *
 * L1 fills its editor's `preview` slot the same way — a `ClassicGridEditPreview` (search bar + reflective cells) for
 * its plain grid, a `GroupedGridEditPreview` (header + tab row + reflective cells) for its category pager. This is
 * that pair generalised to L2's five layouts and driven by [chrome] rather than by constants.
 *
 * **Which lattice each layout gets is the FIXED_PAGER / SCROLL_GRID split, not a style choice.** A pager really does
 * divide its page evenly, so [GridPreview] is exact for it; a scrolling grid's cell height is *derived* from its
 * width, so only [ReflectivePreview] shows the shape the surface will actually draw — and only it makes adding a
 * column visibly gain rows.
 *
 * **Two pieces of chrome are stored and previewed before the surface honours them.** Search is unbuilt in
 * `feature:apps`, and neither pager draws a tab bar (the category pager draws a per-page header instead). `AppsChrome`
 * exists anyway, so what is drawn here is a real preference — see that type for why its search default is `Hidden`
 * where L1's is `TOP`.
 *
 * @param areaWidthDp the width the grid is really given, margin already subtracted — what [ReflectivePreview] needs to
 *   compute a real cell aspect.
 * @param edit the live edit, threaded through so a layout-specific mockup still plays the add/remove flash.
 * @param rowHeightDp the list's row height — read only by [AppsLayout.VERTICAL_LIST], the one layout whose row height
 *   is declared rather than derived. Passed live from the slider's preview so the lanes scale under the finger.
 */
@Composable
internal fun AppsEditorPreview(
    layout: AppsLayout,
    cols: Int,
    rows: Int,
    metrics: IconMetrics,
    chrome: AppsChrome,
    areaWidthDp: Float,
    insetFraction: Float,
    edit: PreviewEdit?,
    rowHeightDp: Float,
) {
    when (layout) {
        // **Not `ReflectivePreview`, and the difference is the point.** That derives a cell's height from its width,
        // which is what the two scrolling *grids* do. A list is the third way a cell gets a height — declared — so its
        // rows are drawn at the height the slider sets, and the aspect comes from `rowHeightDp` rather than from an
        // icon-and-label derivation. See `GridBlueprint.rowHeightDp` for the three-way split.
        AppsLayout.VERTICAL_LIST -> Standalone(chrome) {
            LanePreview(rowHeightDp = rowHeightDp, areaWidthDp = areaWidthDp, insetFraction = insetFraction)
        }

        AppsLayout.VERTICAL_GRID -> Standalone(chrome) {
            ReflectivePreview(cols, metrics, areaWidthDp, insetFraction, edit)
        }

        AppsLayout.PAGER -> Standalone(chrome) {
            GridPreview(cols, rows, edit, insetFraction, Modifier.fillMaxSize())
        }

        // The one layout with tabs — and the one whose search sits *in* the header rather than on an edge, which is
        // exactly the distinction `SearchPlacement` models and L1's flat enum could not.
        AppsLayout.PAGER_WITH_CATEGORY -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ChromeGap),
        ) {
            HeaderRow(searchInHeader = chrome.search == SearchPlacement.InHeader)
            if (chrome.tabBarEdge == VerticalEdge.TOP) TabRow()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ReflectivePreview(cols, metrics, areaWidthDp, insetFraction, edit)
            }
            if (chrome.tabBarEdge == VerticalEdge.BOTTOM) TabRow()
        }

        // Cards are *tiles*: a card's height is its width, so there is no icon-and-label height to derive and the even
        // lattice is the truthful mockup rather than a simplification. Same reason `AppsCardGrid` declares no icon
        // sizing at all.
        AppsLayout.CATEGORY_CARD -> Standalone(chrome) {
            GridPreview(cols, rows, edit, insetFraction, Modifier.fillMaxSize())
        }
    }
}

/**
 * A **standalone** layout's chrome: the search field pinned to an edge, or nothing.
 *
 * `InHeader` draws nothing here rather than falling back to an edge, and that is the sealed type being honest: a header
 * exists only on the category pager, so a standalone layout holding `InHeader` has nowhere to put it. The chooser does
 * not offer it for these layouts, so the state is only reachable by switching chips — and then the right answer is to
 * show what would really be drawn, which is no search bar.
 */
@Composable
private fun Standalone(chrome: AppsChrome, content: @Composable () -> Unit) {
    val pinned = (chrome.search as? SearchPlacement.Pinned)?.edge
    if (pinned == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ChromeGap)) {
        if (pinned == VerticalEdge.TOP) SearchBar()
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        if (pinned == VerticalEdge.BOTTOM) SearchBar()
    }
}

@Composable
private fun SearchBar() {
    Box(Modifier.fillMaxWidth().height(BarHeight).clip(RoundedCornerShape(BarCorner)).background(previewInk()))
}

/** The category pager's compact header: a title, and the actions beside it — search only when it lives here. */
@Composable
private fun HeaderRow(searchInHeader: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChromeGap),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(BarHeight)
                .clip(RoundedCornerShape(BarCorner))
                .background(previewInk()),
        )
        if (searchInHeader) ActionSquare()
        ActionSquare()
    }
}

@Composable
private fun ActionSquare() {
    Box(Modifier.size(ActionSquareSize).clip(RoundedCornerShape(3.dp)).background(previewFaint()))
}

/** The category tabs: the first solid, the rest faint — which is the whole of what a tab row reads as at this size. */
@Composable
private fun TabRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(TabCount) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(TabHeight)
                    .clip(RoundedCornerShape(TabHeight / 2))
                    .background(if (index == 0) previewInk() else previewFaint()),
            )
        }
    }
}

/** Chrome that reads as present. Greyscale, like everything else this editor draws. */
@Composable
private fun previewInk(): Color = LocalMorphicColors.current.contentMuted.copy(alpha = 0.45f)

/** Chrome that reads as secondary — an inactive tab, an action button. */
@Composable
private fun previewFaint(): Color = LocalMorphicColors.current.contentMuted.copy(alpha = 0.22f)

/**
 * The **vertical list**: full-width lanes at the row height the user set, filling downward and clipped at the fold.
 *
 * A list has no columns and no row count, so there is nothing here to press — which is why its editor draws this frame
 * with no buttons at all. What it does need is somewhere to see the two things it *can* change: the row height, which
 * scales these lanes, and the search field's edge, which [Standalone] puts above or below them.
 *
 * Each lane carries a leading square (the icon) and a bar (the label), because a row's icon sits *beside* its text —
 * the one structural fact that tells a list from a one-column grid at this size.
 *
 * The aspect is `rowHeight ÷ width` in real dp, so the mockup narrows as the margin widens and lengthens as the slider
 * rises, exactly as the surface does. Clipping the last lane rather than fitting it says the content scrolls.
 */
@Composable
private fun LanePreview(rowHeightDp: Float, areaWidthDp: Float, insetFraction: Float) {
    val inset = insetFraction.coerceIn(0f, MAX_LANE_INSET)
    val usableWidthDp = (areaWidthDp * (1f - inset * 2)).coerceAtLeast(1f)
    val aspect = (rowHeightDp / usableWidthDp).coerceIn(MIN_LANE_ASPECT, MAX_LANE_ASPECT)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val laneWidth = maxWidth * (1f - inset * 2)
        val laneHeight = laneWidth * aspect
        // One more lane than fits, so the bottom one is cut by the clip rather than stopping short of it.
        val laneCount = ceil((maxHeight + LaneGap) / (laneHeight + LaneGap)).toInt().coerceAtLeast(1)
        Column(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            verticalArrangement = Arrangement.spacedBy(LaneGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(laneCount) {
                Row(
                    modifier = Modifier.width(laneWidth).height(laneHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LaneGap),
                ) {
                    val glyph = (laneHeight * LaneIconFraction).coerceAtLeast(1.dp)
                    Box(Modifier.size(glyph).clip(RoundedCornerShape(2.dp)).background(previewInk()))
                    Box(
                        Modifier
                            .weight(1f)
                            .height((glyph * LaneLabelFraction).coerceAtLeast(1.dp))
                            .clip(RoundedCornerShape(2.dp))
                            .background(previewFaint()),
                    )
                }
            }
        }
    }
}

private val LaneGap = 3.dp

/** How much of a lane's height the icon square takes. A row's icon fills it, less the row's own inset. */
private const val LaneIconFraction = 0.7f

/** The label bar's height as a fraction of the icon's — a line of text beside an icon, at this scale. */
private const val LaneLabelFraction = 0.4f

/** As the other previews' cap: a wide margin on a narrow screen must still leave a lane to draw. */
private const val MAX_LANE_INSET = 0.4f

/** Guards on arithmetic, not taste: an extreme height must not draw one sliver or one lane taller than the frame. */
private const val MIN_LANE_ASPECT = 0.01f
private const val MAX_LANE_ASPECT = 1f
