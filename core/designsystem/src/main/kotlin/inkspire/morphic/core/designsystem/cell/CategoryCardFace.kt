package inkspire.morphic.core.designsystem.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.icon.compose.LauncherIcon
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.CardChrome

/**
 * The preview's shape: [CategoryPreviewCols] × [CategoryPreviewCols] slots.
 *
 * Public because two very different callers count on the same number — the APPS surface, which decides from it which
 * apps are shown and which fall into the overflow cluster, and `CellFit`, which inverts it into the narrowest card an
 * icon guardrail allows. A card that drew a different number of slots than the fit assumed would offer lane counts it
 * could not honour.
 */
const val CategoryPreviewCols = 2

/** How many apps a card's preview shows at once. */
const val CategoryPreviewSlots = CategoryPreviewCols * CategoryPreviewCols

/**
 * The card **grid's** own gutter and the gap between lanes — placeholders, as the other APPS layouts' cell heights are,
 * and distinct from anything in [CardChrome]: these shape the grid of cards, not a card.
 *
 * They live here rather than beside the surface because the *fit* needs them too. `CellFit.cardMinCell` states the
 * narrowest usable card, but a column fit divides the grid's raw width — so a lane's share is a card **plus** its share
 * of this chrome, and a floor that ignored it would allow one more lane than the cards can actually fill. That is the
 * exact mistake the hand-picked constant before it made twice, in the other direction; one owner for the numbers is
 * what stops it being made a third time.
 */
val CategoryCardGutter = 16.dp

/** The gap between lanes of cards — see [CategoryCardGutter]. */
val CategoryCardSpacing = 12.dp

/**
 * The title's own inset — **fixed, and deliberately not [CardChrome.outerPaddingDp]**.
 *
 * That padding insets the *icon area*, which is allowed to reach zero so the four slots can sit flush against the
 * card's edge. A title given the same treatment would then start hard against the corner, which reads as a rendering
 * fault rather than as a choice — so the two are separate, and only one of them is offered as a control.
 */
private val TitleInset = 12.dp

/** The gap between the title and the icon area. Fixed, for [TitleInset]'s reason: it is the title's, not the grid's. */
private val TitleGap = 8.dp

/** How translucent the card's fill is over the surface behind it. */
private const val CardAlpha = 0.10f

/**
 * **One category card's face** — its fill, corner, title and the square icon area beneath — with the contents of each
 * slot left to the caller.
 *
 * It lives in `core:designsystem` rather than beside the APPS surface because it has two consumers that cannot share a
 * module: the surface draws it with draggable, launchable cells in the slots, and the **settings section** draws it as
 * the live preview of the controls that shape it. `feature:settings` cannot depend on `feature:apps`, so the
 * alternative was a second card hand-rolled next to the sliders — and a preview that drifts from the thing it previews
 * is worse than no preview. Same extraction, and the same reason, as [IconPreviewPlate] when the category card became
 * a folder tile's second consumer.
 *
 * **Two squares, one rectangle.** The icon area is square and takes the card's full width, so a slot is as large as the
 * lane allows; the title adds its height above it, which makes the card as a whole a portrait rectangle. The card used
 * to be the square instead, and the header then ate into it from the top — the leftover box came out wider than tall,
 * the slots sized themselves from its *height*, and the icons ended up the smallest thing on a tile whose whole job is
 * to make them recognisable.
 *
 * **Everything [chrome] carries starts at zero.** A card with no override is a square-cornered rectangle whose icons
 * are packed edge to edge and flush to its sides. That is not a placeholder awaiting taste — it is so that every piece
 * of decoration on this surface is something a user turned on, after a version in which the inset, gap and corner were
 * hardcoded numbers no control could reach and no screenshot could justify.
 *
 * @param chrome the resolved [CardChrome] — the title's scale, the corner, and the icon area's two paddings.
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
    slot: @Composable (index: Int, size: Dp) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val shape: Shape = RoundedCornerShape(chrome.cornerRadiusDp.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = CardAlpha)),
    ) {
        Text(
            text = title,
            style = cardTitleStyle(chrome),
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TitleInset, end = TitleInset, top = TitleInset)
                .then(titleGestures),
        )
        Spacer(Modifier.height(TitleGap))
        // **The square, at the card's full width.** `aspectRatio` before `padding`, so the icon area *including* its
        // outer padding is the square: a user widening that padding then shrinks the icons inside a block whose
        // footprint on the card does not move, which is what makes the two sliders independent of each other.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
                            Box(Modifier.size(size)) { slot(row * CategoryPreviewCols + col, size) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * **One preview slot's icon** — the app's icon alone, sized against the *whole* slot.
 *
 * Deliberately **not** an [AppCell], which is what both callers used first and what made
 * [CardChrome.innerPaddingDp] unable to reach zero. An `AppCell` is a grid *cell*: it wraps [IconLabelCell], which
 * insets its icon by `CellPadH`/`CellPadV` and reserves a label row. On a card that inset is 4dp a side, so two
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
        LauncherIcon(
            component = app.componentKey,
            contentDescription = app.label,
            sizePx = sizePx,
            modifier = Modifier.size(iconSize).then(itemGestures),
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
 * resolved to. Given the raw slot instead it stayed full-size while its neighbours shrank with the icon slider,
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
