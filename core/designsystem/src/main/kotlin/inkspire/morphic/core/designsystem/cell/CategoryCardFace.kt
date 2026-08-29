package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CardChrome

/**
 * The preview's shape: [CategoryPreviewCols] × [CategoryPreviewCols] slots.
 *
 * Public because two very different callers count on the same number — the APPS surface, which decides from it which
 * apps are shown and which fall into the overflow cluster, and `CellFit`, which inverts it into the narrowest card an
 * icon guardrail allows. A card that drew a different number of slots than the fit assumed would offer lane counts it
 * could not honor.
 */
const val CategoryPreviewCols = 2

/** How many apps a card's preview shows at once. */
const val CategoryPreviewSlots = CategoryPreviewCols * CategoryPreviewCols

/**
 * How translucent a fill is over the frost behind it — what the launcher's own cards are drawn at.
 *
 * **Named for the card because that is what set it, and shared because the situation repeats.** The widget picker's
 * preview boxes are the second consumer: a translucent tile over a frosted surface, exactly this one, and the two
 * looking alike is wanted rather than coincidental. A settings preview overrides it *upward* — see this file's
 * `fillAlpha`, which owns why raw wallpaper needs more than frost does.
 */
const val CardAlpha = 0.10f

/**
 * **One category card's face** — a square tile of app icons with the category's name beneath it — with the contents of
 * each slot left to the caller.
 *
 * It lives in `core:designsystem` rather than beside the APPS surface because it has two consumers that cannot share a
 * module: the surface draws it with draggable, launchable cells in the slots, and the **settings section** draws it as
 * the live preview of the controls that shape it. `feature:settings` cannot depend on `feature:apps`, so the
 * alternative was a second card hand-rolled next to the sliders — and a preview that drifts from the thing it previews
 * is worse than no preview. Same extraction, and the same reason, as [IconPreviewPlate] when the category card became
 * a folder tile's second consumer.
 *
 * **The tile is the square, and the label is outside it** — iOS's App Library shape, and the second correction to this
 * layout. The card was originally the square, with the title *inside* eating into it from the top: the leftover box came
 * out wider than tall, the slots sized themselves from its *height*, and the icons ended up the smallest thing on a tile
 * whose whole job is to make them recognizable. Making the icon area the square fixed the icons but left the title
 * sharing the fill, which reads as a header bar rather than as a label. Now the background, the corner and the padding
 * are all the **tile's**, and the name sits under it centered on nothing — so the fill traces the icons exactly, and the
 * card as a whole is a portrait rectangle: a square plus one line of text.
 *
 * **Everything [chrome] carries starts at zero.** A card with no override is a square-cornered rectangle whose icons
 * are packed edge to edge and flush to its sides. That is not a placeholder awaiting taste — it is so that every piece
 * of decoration on this surface is something a user turned on, after a version in which the inset, gap and corner were
 * hardcoded numbers no control could reach and no screenshot could justify.
 *
 * @param chrome the resolved [CardChrome] — the title's scale, the corner, and the icon area's two paddings.
 * @param fillAlpha how opaque the tile's fill is. Defaults to [CardAlpha], which is what the **surface** draws.
 *
 *   A settings preview overrides it upward, and that is a deliberate departure from "a preview shows what the surface
 *   draws". The surface's 10% reads well because a card there sits on the *frosted* backdrop — blurred wallpaper under
 *   a wash. The preview punches through to **raw** wallpaper, where the same 10% all but disappears and a
 *   corner-radius slider has no visible corner to act on. Two less invasive fixes were tried and both failed: a wash
 *   behind the card cut the contrast instead of adding it (in a dark theme the wash and the card are both near-black),
 *   and an outline blended into the wallpaper it was drawn over. A control you cannot see the effect of is worth more
 *   than a preview that matches to the percentage point.
 * @param titleGestures applied to the title row, which is a full-width strip: a one-word category name still has to be
 *   reachable, and a header row *is* its own visible extent, the same way a list row's is. Empty in a preview, where
 *   nothing is tappable.
 * @param slot draws the content of preview slot [0, [CategoryPreviewSlots]), given the exact size it must fill. The
 *   caller decides what a slot *is* — a draggable app, an overflow cluster, or nothing — because that is the half that
 *   differs between the surface and the preview.
 */
@Composable
fun CategoryCardFace(
    title: String,
    chrome: CardChrome,
    modifier: Modifier = Modifier,
    titleGestures: Modifier = Modifier,
    fillAlpha: Float = CardAlpha,
    slot: @Composable (index: Int, size: Dp) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val shape: Shape = RoundedCornerShape(chrome.cornerRadiusDp.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // **The tile is the square, and the background is the tile's** — the title sits below it, outside the fill.
        // `aspectRatio` before `padding`, so the icon area *including* its outer padding is the square: a user
        // widening that padding then shrinks the icons inside a block whose footprint does not move, which is what
        // makes the two sliders independent of each other.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(colors.surface.copy(alpha = fillAlpha))
                .padding(chrome.outerPaddingDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            val gap = chrome.innerPaddingDp.dp
            // Floored at zero because `Modifier.size` rejects a negative: a card squeezed below the gap's own width
            // draws nothing rather than crashing. `minOf` though the bounds are equal by construction — it is what
            // keeps this correct rather than merely correct today.
            val size = ((minOf(maxWidth, maxHeight) - gap) / CategoryPreviewCols).coerceAtLeast(0.dp)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(CategoryPreviewCols) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(CategoryPreviewCols) { col ->
                            Box(modifier = Modifier.size(size)) {
                                slot(row * CategoryPreviewCols + col, size)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = cardTitleStyle(chrome),
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .then(titleGestures),
        )
    }
}

/**
 * **One preview slot's icon** — the app's icon alone, sized against the *whole* slot.
 *
 * Deliberately **not** an [AppCell], which is what both callers used first and what made
 * [CardChrome.innerPaddingDp] unable to reach zero. An `AppCell` is a grid *cell*: it wraps [IconLabelCell], which
 * insets its icon by 4dp on each axis and reserves a label row. On a card that meant two
 * adjacent slots kept an 8dp gap however far the spacing slider was dragged down — a control that could not express
 * the thing it was named for. A card slot has no label and no chrome of its own; it *is* the icon's box, which is
 * what `iconPercent = 1f` in this grid's blueprint means literally.
 *
 * The sizing controls still apply, and now mean what they say: [IconMetrics.iconPercent] is the fraction of the slot
 * the icon fills — 100% is flush — and the two guardrails clamp it, which is also what sets the lane ceiling through
 * `CellFit.cardMinCell`.
 *
 * @param itemGestures hung on the **icon**, not the slot, so the slack around a smaller icon stays free for the card
 *   beneath it — the launcher's standing "a touch target is its visible extent" rule.
 */
@Composable
fun CategoryPreviewIcon(
    app: AppInfo,
    slotSize: Dp,
    metrics: IconMetrics,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
) {
    val iconSize = metrics.resolveIconSize(slotSize, slotSize)
    Box(modifier.size(slotSize), contentAlignment = Alignment.Center) {
        val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
        AppIcon(
            component = app.componentKey,
            contentDescription = app.label,
            sizePx = sizePx,
            modifier = Modifier
                .size(iconSize)
                .then(itemGestures),
        )
    }
}

/**
 * The **overflow cluster** for a card showing [apps], or null when they all fit.
 *
 * The last slot becomes a cluster only when it would hold *more* than the single icon it could show on its own, so a
 * category of exactly [CategoryPreviewSlots] shows all four and no cluster.
 *
 * Shared rather than inlined at each call site because there are now two: the surface's cards and the settings
 * preview of one. A preview that split the apps differently from the thing it previews would be worse than none —
 * the same reason [CategoryCardFace] itself lives here.
 */
fun <T> categoryOverflowCluster(apps: List<T>): List<T>? {
    val overflow = apps.drop(CategoryPreviewSlots - 1)
    return overflow.take(CategoryPreviewSlots).takeIf { overflow.size > 1 }
}

/**
 * The overflow cluster's tile — an [IconPreviewPlate] of the next few apps, i.e. the same tile a folder draws on a
 * grid, which is the honest rendering of "and more in here".
 *
 * **Sized exactly like [CategoryPreviewIcon]**, and that is the whole point of it being a function rather than a
 * plate built at each call site: it stands in for one of the four apps, so it has to be the size the other three
 * resolved to. Given the raw slot instead it stayed full-size while its neighbors shrank with the icon slider,
 * which read as the cluster ignoring the setting rather than as the one tile that is not an app.
 */
@Composable
fun CategoryClusterTile(
    apps: List<AppInfo>,
    slotSize: Dp,
    metrics: IconMetrics,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
) {
    Box(modifier.size(slotSize), contentAlignment = Alignment.Center) {
        IconPreviewPlate(
            apps = apps,
            size = metrics.resolveIconSize(slotSize, slotSize),
            modifier = itemGestures,
            // **No plate here.** A cluster sits inside a tile that already has a fill, so a second rounded backing is
            // a box within a box — and it made this the one slot on the card with a visible container while its three
            // neighbors were bare icons. Without it the four mini-icons fill the cluster edge to edge, which is what
            // makes it read as "more of the same" rather than as a different kind of thing.
            backing = false,
        )
    }
}

/**
 * The card title's type: `titleSmall` scaled by [CardChrome.titleScale].
 *
 * Shaped like `IconLabelCell`'s and `AppRowCell`'s label styles, including carrying the line height through the same
 * multiplier — a title whose glyphs grew while its line box did not would clip its own descenders.
 */
@Composable
private fun cardTitleStyle(chrome: CardChrome) = with(MaterialTheme.typography.titleSmall) {
    copy(
        fontSize = fontSize * chrome.titleScale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * chrome.titleScale else lineHeight,
    )
}
