package inkspire.morphic.feature.apps.layout.categorycard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.cell.AppCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.drag.DropFootprint
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.drag.launcherItemGestures
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.DropIntent
import inkspire.morphic.feature.apps.AppsCategory
import inkspire.morphic.feature.apps.layout.appsItemGestures

/**
 * One card's own inset, corner, tint and slot spacing — **placeholders, not design choices**, in the same sense as
 * the other APPS layouts' cell heights: these are surface metrics bound for the settings layer, and a flat constant
 * says so where derived arithmetic would read as a decision.
 *
 * They live beside [CategoryCard] rather than with [AppsCategoryCard] because a card is the only thing that reads
 * them; the *grid's* metrics (how many columns of cards, and the spacing between them) stay with the grid.
 */
private val CardInset = 12.dp
private val CardCorner = 24.dp
private val HeaderGap = 8.dp
private val SlotGap = 8.dp
private const val CardAlpha = 0.10f

/**
 * The preview's shape: [PreviewCols] × [PreviewCols] slots. The last one becomes the **overflow cluster** when the
 * category holds more than fits, so a fuller card shows [PreviewSlots] - 1 launchable icons plus a tile that opens
 * the rest.
 */
private const val PreviewCols = 2
private const val PreviewSlots = PreviewCols * PreviewCols

/**
 * The preview icons' metrics: **no labels**, and the icon filling its slot.
 *
 * A card is a thumbnail of a category, not a grid of it — four labels at this size would be four ellipsised words and
 * would eat the space the icons need to be recognisable. Recognising an icon *is* the preview's whole job, so the
 * label goes and [IconMetrics.iconPercent] goes to 1: the slot is already sized to be an icon.
 *
 * [IconMetrics.maxIconDp]'s default (72dp) is left alone even though it *will* bind on a tablet, where two columns of
 * cards give a slot far wider than that and the icon then floats in it. Raising it here would paper over the real
 * cause — [AppsCategoryCard]'s device-blind column count — with a number nothing owns either. Fix the columns, not
 * this.
 */
private val PreviewIconMetrics = IconMetrics(iconPercent = 1f, showLabel = false)

/**
 * One category's card: its name, then a [PreviewSlots]-slot thumbnail of the apps filed under it.
 *
 * The last slot is the **overflow cluster** when the category holds more than fits — an [IconPreviewPlate] of the next
 * four apps, i.e. the same tile a folder draws on a grid, which is the honest rendering of "and more in here". The
 * cluster opens the category; so does the header. A category with [PreviewSlots] apps or fewer shows them all and has
 * no cluster.
 *
 * Split from [AppsCategoryCard] because it is a leaf: it takes what it draws as parameters, reports its bounds back,
 * and reads none of the surface's drag state. The surface keeps the state, the planner and the drop.
 *
 * @param shadowed true while a dragged app is aimed at this card — drawn as a [DropIntent.MERGE] drop shadow over the
 *   whole card, the same affordance a folder's merge ring gets, because dropping here does the same thing.
 * @param onBounds reports this card's root-space bounds as it is laid out, and **null when it leaves composition** so
 *   the surface's hit-test map doesn't keep testing a card that has scrolled away.
 */
@Composable
internal fun CategoryCard(
    category: AppsCategory,
    gestureConfig: ItemGestureConfig,
    shadowed: Boolean,
    onLaunch: (ComponentKey) -> Unit,
    onExpand: () -> Unit,
    onBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    DisposableEffect(Unit) { onDispose { onBounds(null) } }
    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                // Square, so the four preview slots are square too and a card reads as one tile rather than a band.
                .fillMaxWidth()
                .aspectRatio(1f)
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .clip(RoundedCornerShape(CardCorner))
                .background(colors.surface.copy(alpha = CardAlpha))
                .padding(CardInset),
        ) {
            // The header is a full-width strip rather than just the glyphs: the target has to be reachable for a
            // one-word category name, and a header row *is* its own visible extent, the same way a list row's is.
            Text(
                text = category.category.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().categoryOpenGestures(gestureConfig, onExpand),
            )
            Spacer(Modifier.height(HeaderGap))
            // Slots are sized from the smaller bound and centred, so they stay square whichever way the leftover area
            // happens to be shaped (the header's height varies with the type scale).
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // Floored at zero because `Modifier.size` rejects a negative: a card squeezed below the gap's own
                // width draws nothing rather than crashing.
                val slot = ((minOf(maxWidth, maxHeight) - SlotGap) / 2).coerceAtLeast(0.dp)
                // The cluster takes the last slot only when it would hold *more* than the single icon that slot could
                // show on its own — a category of exactly [PreviewSlots] apps shows all four, no cluster.
                val overflow = category.apps.drop(PreviewSlots - 1)
                val cluster = overflow.take(PreviewSlots).takeIf { overflow.size > 1 }
                Column(verticalArrangement = Arrangement.spacedBy(SlotGap)) {
                    repeat(PreviewCols) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(SlotGap)) {
                            repeat(PreviewCols) { col ->
                                val index = row * PreviewCols + col
                                Box(Modifier.size(slot)) {
                                    PreviewSlot(
                                        app = category.apps.getOrNull(index),
                                        cluster = cluster?.takeIf { index == PreviewSlots - 1 },
                                        slot = slot,
                                        gestureConfig = gestureConfig,
                                        onLaunch = onLaunch,
                                        onExpand = onExpand,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Over the card rather than inside its `Column`, so it covers the header and preview both, and shaped to the
        // card so the shadow reads as "this whole collection" instead of a cell.
        if (shadowed) {
            DropFootprint(
                intent = DropIntent.MERGE,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(CardCorner),
            )
        }
    }
}

/**
 * One preview slot: the overflow [cluster] if this is the last slot and there is an overflow, otherwise [app]'s icon,
 * otherwise nothing.
 *
 * An icon launches; the cluster opens the category. Both go through the launcher's one item-gesture contract rather
 * than a `clickable`, so this surface can't drift from the rest of the launcher on long-press timing or slop.
 */
@Composable
private fun PreviewSlot(
    app: AppInfo?,
    cluster: List<AppInfo>?,
    slot: Dp,
    gestureConfig: ItemGestureConfig,
    onLaunch: (ComponentKey) -> Unit,
    onExpand: () -> Unit,
) {
    when {
        cluster != null -> IconPreviewPlate(
            apps = cluster,
            size = slot,
            modifier = Modifier.categoryOpenGestures(gestureConfig, onExpand),
        )
        app != null -> AppCell(
            app = app,
            modifier = Modifier.fillMaxSize(),
            metrics = PreviewIconMetrics,
            itemGestures = Modifier.appsItemGestures(gestureConfig) { onLaunch(app.componentKey) },
        )
        else -> Unit
    }
}

/**
 * A **category's** tap contract — the launcher's one item-gesture recogniser with only "open" connected.
 *
 * Separate from [appsItemGestures] rather than reusing it, though the two do the same thing today: that one is the
 * *app* contract, and its empty slots are an app's unfinished behaviours (the app options menu, drag-out-to-home).
 * Neither means anything for a category, whose long-press belongs to a different unbuilt feature. Sharing them would
 * make one of the two wrong the moment either is filled in.
 */
private fun Modifier.categoryOpenGestures(
    config: ItemGestureConfig,
    onOpen: () -> Unit,
): Modifier = launcherItemGestures(
    config = config,
    onOpen = onOpen,
    // TODO(category management): the category's menu — rename, delete, choose an icon. A `feature:settings` concern
    //  (see the card grid's TODO), so this stays empty until that op set exists.
    onShowMenu = {},
    onDismissMenu = {},
    onEdgeAction = {},
    // A card is not draggable: reordering the categories themselves is category management, deferred with the rest of
    // it. An app *inside* an expansion is what drags here.
    onBeginDrag = {},
    onDragTo = {},
    onDrop = {},
    onCancelDrag = {},
)
