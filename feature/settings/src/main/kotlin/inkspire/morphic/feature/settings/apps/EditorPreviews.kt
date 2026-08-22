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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.model.AppsLayout
import inkspire.morphic.core.model.SearchPlacement
import inkspire.morphic.core.model.VerticalEdge
import inkspire.morphic.data.settings.AppsChrome
import inkspire.morphic.feature.settings.component.GridPreview
import inkspire.morphic.feature.settings.component.LanePreview
import inkspire.morphic.feature.settings.component.PreviewEdit
import inkspire.morphic.feature.settings.component.ReflectivePreview
import inkspire.morphic.feature.settings.component.previewFaint
import inkspire.morphic.feature.settings.component.previewInk

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
 * Each layout fills the editor's `preview` slot with its own mockup — a search bar over reflective cells for the
 * plain grid, a header and tab row over them for the category pager. This is
 * that pair generalized to L2's five layouts and driven by [chrome] rather than by constants.
 *
 * **Which lattice each layout gets is the FIXED_PAGER / SCROLL_GRID split, not a style choice.** A pager really does
 * divide its page evenly, so [GridPreview] is exact for it; a scrolling grid's cell height is *derived* from its
 * width, so only [ReflectivePreview] shows the shape the surface will actually draw — and only it makes adding a
 * column visibly gain rows.
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
    // Resolved once for the layout being drawn, and handed down — so the mockup and the control that sets it read the
    // same entry through the same accessor.
    val search = chrome.searchOn(layout)
    when (layout) {
        // **Not `ReflectivePreview`, and the difference is the point.** That derives a cell's height from its width,
        // which is what the two scrolling *grids* do. A list is the third way a cell gets a height — declared — so its
        // rows are drawn at the height the slider sets, and the aspect comes from `rowHeightDp` rather than from an
        // icon-and-label derivation. See `GridBlueprint.rowHeightDp` for the three-way split.
        AppsLayout.VERTICAL_LIST -> Standalone(search) {
            LanePreview(rowHeightDp = rowHeightDp, areaWidthDp = areaWidthDp, insetFraction = insetFraction)
        }

        AppsLayout.VERTICAL_GRID -> Standalone(search) {
            ReflectivePreview(cols, metrics, areaWidthDp, insetFraction, edit)
        }

        AppsLayout.PAGER -> Standalone(search) {
            GridPreview(cols, rows, edit, insetFraction, Modifier.fillMaxSize())
        }

        // The one layout with tabs — and the one whose search sits *in* the header rather than on an edge, which is
        // exactly the distinction `SearchPlacement` models and a flat position enum could not.
        AppsLayout.PAGER_WITH_CATEGORY -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ChromeGap),
        ) {
            HeaderRow(searchInHeader = search == SearchPlacement.InHeader)
            if (chrome.categoryTabEdge == VerticalEdge.TOP) TabRow()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ReflectivePreview(cols, metrics, areaWidthDp, insetFraction, edit)
            }
            if (chrome.categoryTabEdge == VerticalEdge.BOTTOM) TabRow()
        }

        // Cards are *tiles*: a card's height is its width, so there is no icon-and-label height to derive and the even
        // lattice is the truthful mockup rather than a simplification. Same reason `AppsCardGrid` declares no icon
        // sizing at all.
        AppsLayout.CATEGORY_CARD -> Standalone(search) {
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
private fun Standalone(search: SearchPlacement, content: @Composable () -> Unit) {
    val pinned = (search as? SearchPlacement.Pinned)?.edge
    if (pinned == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ChromeGap)) {
        if (pinned == VerticalEdge.TOP) SearchBar()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { content() }
        if (pinned == VerticalEdge.BOTTOM) SearchBar()
    }
}

@Composable
private fun SearchBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarCorner))
            .background(previewInk())
    )
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
    Box(
        modifier = Modifier
            .size(ActionSquareSize)
            .clip(RoundedCornerShape(3.dp))
            .background(previewFaint())
    )
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
