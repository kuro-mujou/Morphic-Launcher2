package inkspire.morphic.feature.settings.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.CategoryCardFace
import inkspire.morphic.core.designsystem.cell.CategoryClusterTile
import inkspire.morphic.core.designsystem.cell.CategoryPreviewIcon
import inkspire.morphic.core.designsystem.cell.CategoryPreviewSlots
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.categoryOverflowCluster
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CardChrome
import kotlin.math.min
import kotlin.math.roundToInt

/** Provisional spacing — placeholders, matching `IconSizingPreview`'s. */
private val PreviewPadding = 12.dp
private val CaptionGap = 6.dp

/** How much of the box a card may fill before it is scaled down — `IconSizingPreview`'s `CELL_FIT`, same job. */
private const val CARD_FIT = 0.94f

/**
 * How opaque the previewed card's fill is — **heavier than the surface's own `CardAlpha`, deliberately.**
 *
 * The surface draws 10% because a card there sits on the frosted backdrop: blurred wallpaper under a wash, which a
 * faint tint reads clearly against. This preview punches through to **raw** wallpaper, where 10% all but disappears —
 * and the corner-radius slider then has no visible corner to act on, which is the one thing this preview exists for.
 *
 * So it is the one number here that does not match the surface. Two less invasive fixes were tried first and both
 * failed: a wash drawn behind the card *cut* the contrast rather than adding it (in a dark theme the wash and the card
 * are both near-black), and an outline blended into the wallpaper it was drawn over. What the preview still shows
 * truthfully is everything the controls actually change — the card's width, its corner, its two paddings and its icon
 * sizes; only the fill's opacity is exaggerated so those are legible.
 */
private const val PreviewFillAlpha = 0.5f

/**
 * **The live category-card preview** — a real [CategoryCardFace] at its real dp width, updating as the sliders below
 * it move.
 *
 * The card's counterpart of `IconSizingPreview`, and a separate composable for the reason that one is: half of what
 * the card's controls shape is the *tile* — its corner, its title, the padding around and between its icons — and none
 * of that is visible on the lone cell every other section previews. It draws no guardrail outlines, because the
 * question here is what the card looks like rather than which of three icon limits is binding; the icon group's own
 * preview answers that one, and this sits in the same pinned header.
 *
 * **It punches through to the wallpaper**, exactly as the cell preview does and for the same reason. `BlendMode.Src`
 * makes the box *replace* the pixels under it rather than blend, so wherever the card does not draw, the pane's
 * background is cleared to transparent — and the pane is an offscreen layer over a window showing the wallpaper
 * (`PunchThroughPane`, plus `windowShowWallpaper` in `app`'s theme). A card is translucent by design, so judging its
 * fill against a flat settings panel would be judging it against something it never sits on. The caption stays outside
 * the punched box, or it would be cleared along with the background.
 *
 * @param cardWidth the width one card is really drawn at on the surface — the lane less its share of the spacing
 *   between lanes, which only the section can compute. Applied with `requiredWidth`, so it escapes the fixed
 *   full-width constraint the pinned header hands down; without that the card silently rendered at the pane's width,
 *   which is the one thing a size preview must not do.
 */
@Composable
internal fun CategoryCardPreview(
    title: String,
    apps: List<AppInfo>,
    chrome: CardChrome,
    metrics: IconMetrics,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { blendMode = BlendMode.Src }
                .padding(vertical = PreviewPadding),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                if (cardWidth > 0.dp) {
                    // Scaled *down* only, never up — a card drawn larger than life would misrepresent the very thing
                    // being measured, while one too wide for the pane (a tablet's, or a landscape pane's fixed 220dp
                    // column) still has to be shown somehow. `IconSizingPreview`'s rule, and L1's before it.
                    val fit = min(1f, maxWidth.value * CARD_FIT / cardWidth.value)
                    CategoryCardFace(
                        title = title,
                        chrome = chrome,
                        modifier = Modifier
                            .requiredWidth(cardWidth)
                            .graphicsLayer { scaleX = fit; scaleY = fit },
                        fillAlpha = PreviewFillAlpha,
                    ) { index, slotSize ->
                        // The surface's own split, shared rather than restated: the last slot becomes an overflow
                        // cluster when the category holds more than fits, which is the arrangement any category big
                        // enough to be worth configuring actually has.
                        val cluster = categoryOverflowCluster(apps)
                        if (cluster != null && index == CategoryPreviewSlots - 1) {
                            CategoryClusterTile(apps = cluster, slotSize = slotSize, metrics = metrics)
                        } else {
                            apps.getOrNull(index)?.let { app ->
                                CategoryPreviewIcon(app = app, slotSize = slotSize, metrics = metrics)
                            }
                        }
                    }
                }
            }
        }

        // The number behind the picture, as the cell preview carries its own. The width is the interesting one: it is
        // what the lane count and the margin between them resolve to, and it is what the icon guardrails bound.
        Text(
            text = "Card ${cardWidth.value.roundToInt()} dp wide · icons ${metrics.minIconDp.value.roundToInt()}–" +
                "${metrics.maxIconDp.value.roundToInt()} dp",
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = CaptionGap),
        )
    }
}
