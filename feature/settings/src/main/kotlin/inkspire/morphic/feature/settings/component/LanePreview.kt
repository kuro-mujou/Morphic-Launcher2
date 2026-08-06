package inkspire.morphic.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import kotlin.math.ceil

/**
 * **A one-lane list, in miniature**: full-width lanes at the row height the user set, filling downward and clipped at
 * the fold.
 *
 * A list has no columns and no row count, so there is nothing to press — which is why a list's editor draws its frame
 * with no buttons at all. What it does need is somewhere to see the one thing it *can* change: the row height, which
 * scales these lanes.
 *
 * Each lane carries a leading square (the icon) and a bar (the label), because a row's icon sits *beside* its text —
 * the one structural fact that tells a list from a one-column grid at this size.
 *
 * The aspect is `rowHeight ÷ width` in real dp, so the mockup narrows as the margin widens and lengthens as the slider
 * rises, exactly as the surface does. Clipping the last lane rather than fitting it says the content scrolls.
 *
 * **Deliberately not [ReflectivePreview]**, which derives a cell's *height from its width* — that is what the two
 * scrolling grids do. A list is the third way a cell gets a height (declared), which is the split
 * `GridBlueprint.rowHeightDp` states and this is that split reaching the preview layer.
 *
 * It lives here rather than in the APPS section because there are now two one-lane lists to draw — the APPS drawer's
 * and HOME's — with two independent row heights and two editors. One drawing, extracted at the moment the second
 * consumer arrived, exactly as `IconPreviewPlate` was.
 */
@Composable
internal fun LanePreview(rowHeightDp: Float, areaWidthDp: Float, insetFraction: Float) {
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

/** Chrome that reads as present. Greyscale, like everything else these editors draw. */
@Composable
internal fun previewInk(): Color = LocalMorphicColors.current.contentMuted.copy(alpha = 0.45f)

/** Chrome that reads as secondary — an inactive tab, an action button. */
@Composable
internal fun previewFaint(): Color = LocalMorphicColors.current.contentMuted.copy(alpha = 0.22f)

private val LaneGap = 3.dp

/** How much of a lane's height the icon square takes. A row's icon fills it, less the row's own inset. */
private const val LaneIconFraction = 0.7f

/** The label bar's height as a fraction of the icon's — a line of text beside an icon, at this scale. */
private const val LaneLabelFraction = 0.4f

/** As the other previews' cap: a wide margin on a narrow screen must still leave a lane to draw. */
private const val MAX_LANE_INSET = 0.4f

private const val MIN_LANE_ASPECT = 0.01f
private const val MAX_LANE_ASPECT = 1f
