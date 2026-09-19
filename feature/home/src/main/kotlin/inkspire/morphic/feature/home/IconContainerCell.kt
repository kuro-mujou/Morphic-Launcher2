package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import inkspire.morphic.core.designsystem.backdrop.OnPanel
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.resolveIconSizeUnfloored
import inkspire.morphic.core.designsystem.container.ArrangementSlot
import inkspire.morphic.core.designsystem.drag.LocalDragCoordinator
import inkspire.morphic.core.designsystem.grid.LocalInnerCellPull
import inkspire.morphic.core.designsystem.grid.animatePlacement
import inkspire.morphic.core.designsystem.grid.rememberLandingGlide
import inkspire.morphic.core.designsystem.surface.LocalSurfacePresented
import inkspire.morphic.core.model.IconArrangement
import inkspire.morphic.core.model.IconItem
import kotlin.math.roundToInt

/**
 * One placed **icon container** — a group of app and folder icons sharing a single cell, laid out by the
 * container's [arrangement].
 *
 * **A slot draws the icon alone, never an [inkspire.morphic.core.designsystem.cell.AppCell].** Same rule and same
 * reason as the APPS category card: a cell wraps `IconLabelCell`, which insets by `CellPadH`/`CellPadV` and reserves
 * a label row — so seven of them inside one container cell would be seven smudges under unreadable labels, and the
 * spacing the arrangement carefully computed would be eaten by per-cell padding. A slot *is* the icon's box. L1 used
 * `AppCell`/`FolderCell` here; that is the one thing from its version not carried.
 *
 * **One gesture for the whole cell, which decides what it acts on from where the press landed** — a slot's icon,
 * or the container itself for the slack between and around them. The container's [itemGestures] go on the whole
 * cell because it fills its cell the way a widget does, which is `LauncherDragCell`'s stated exception to the
 * icon+label rule; `innerItemAt` is how the press is then resolved within it, and `iconContainerSlots` is the
 * geometry that resolution and the drawing below both read.
 *
 * **A slot carries no `clickable`, and that is not an omission.** It had one, and the bug it caused is the reason
 * this rule exists everywhere else in the launcher (CLAUDE.md: cells carry no `onClick`; taps arrive through the
 * one gesture contract): `clickable` fires on release no matter what the gesture did, so a long-press raised the
 * container's menu and then launched the app underneath it, and a completed reorder launched the icon it had just
 * dropped. Taps reach `onOpenInner` instead, which only fires for a gesture the machine actually resolved as a tap.
 *
 * **An empty container draws a "+", and it is a plain glyph — not a button.** Something has to be drawn — an empty
 * cell that cannot be removed reads as a rendering fault, which is `AppWidgetCell`'s argument for naming an unresolvable
 * widget. A tap on it reaches the add flow the same way a tap on a slot reaches its app: through the cell's one
 * gesture contract (`onOpen`), which resolves the empty container to its settings. It used to be a real `IconButton`,
 * and that reintroduced the exact overlap the rule above removes — a long-press raised the container's menu and the
 * button's `onClick` then opened settings on release, one press with two outcomes. Dragging an icon in still works
 * and is the faster route; the "+" is what makes an empty container usable without one.
 */
@Composable
internal fun IconContainerCell(
    icons: List<ContainerIcon>,
    arrangement: IconArrangement,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
    metrics: IconMetrics = LocalIconMetrics.current,
    iconScalePercent: Int = 100,
    spacingScalePercent: Int = 100,
    dropTarget: IconContainerDropTarget? = null,
    resolveIncoming: (IconItem) -> ContainerIcon? = { null },
) {
    // `positionInRoot() + size`, never `boundsInRoot()`: this cell lives inside home's pager, and that call
    // clips to every ancestor — so a container on a half-scrolled page would report a clipped rectangle and
    // silently refuse drops over the part that was trimmed.
    var boundsInRoot by remember { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(
        modifier = modifier
            .onGloballyPositioned { boundsInRoot = Rect(it.positionInRoot(), it.size.toSize()) }
            .containerPanel()
            .then(itemGestures),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // **The container asks what it should be drawing, not what it holds** — an icon being carried out of it
        // keeps its slot, one being carried *in* has already been given one, and a rearrangement in progress is
        // previewed as it will land. The geometry comes back with it because how many slots there are is part of
        // that answer.
        val preview = rememberIconContainerPreview(
            target = dropTarget,
            icons = icons,
            arrangement = arrangement,
            spacingScalePercent = spacingScalePercent,
            size = Size(widthPx, heightPx),
            bounds = boundsInRoot,
            resolve = resolveIncoming,
        )
        val slots = preview.slots

        // **Everything inside the tile is on the tile**, which is two things and they were split for a while. It is
        // themed against the panel — the panel carries the user's own blur and wash, so a dark tile can sit on a
        // bright home screen — and nothing in it may frost itself again, which is what an icon's plate was doing:
        // sampling the wallpaper a second time on a surface that had already blurred it. The empty state had this
        // and the icons did not, so the glyph read correctly and every plated icon in a container did not.
        //
        // An inner `Box` because [OnPanel]'s content is not a `BoxScope` and the slots below place themselves
        // absolutely. `containerPanel` stays on the *outer* modifier, above the provider, for the sheet's reason: a
        // panel told it was over frost would fill flat, which is the tile itself disappearing.
        OnPanel {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // **Empty is what is about to be shown, not what is stored.** The first app dropped into an empty
                // container is promised a slot before the store holds it; testing the stored list drew the "+" over
                // that promise until the write landed, so the container flicked between empty and filled.
                if (preview.shown.isEmpty()) {
                    ContainerAddGlyph(
                        contentDescription = "Add app",
                        modifier = Modifier.fillMaxSize(),
                    )
                    return@Box
                }
                // A swipe claimed on one of these icons pulls that icon, not the container. See `InnerCellPull`.
                val innerPull = LocalInnerCellPull.current
                val coordinator = LocalDragCoordinator.current
                val presented = LocalSurfacePresented.current
                preview.shown.forEachIndexed { index, icon ->
                    // The gap a newcomer is about to fill: a slot with nothing to draw in it yet.
                    if (icon == null) return@forEachIndexed
                    val slot = slots.getOrNull(index) ?: return@forEachIndexed
                    val iconSize = containerIconSize(slot, metrics, iconScalePercent, density)
                    // **Keyed by the icon, so a reflow moves it rather than redrawing a different icon in its place**,
                    // which is what lets `animatePlacement` glide it: an exchange being previewed, a gap opening for an
                    // arrival, a departure closing up. Without the key an icon was bound to its *index*, and every
                    // rearrangement jumped.
                    key(icon.asIconItem()) {
                        // **An icon dropped in glides from where it was let go**, rather than appearing in its slot as
                        // the proxy vanishes — the same landing a grid cell takes, for the same reason.
                        val gridItem = icon.asIconItem().asGridItem()
                        val landing = rememberLandingGlide()
                        // With this slot's icon size, so an icon carried in at a home cell's size shrinks into it.
                        landing.update(coordinator?.landing?.takeIf { presented && it.item == gridItem }, iconSize)
                        Box(
                            modifier = Modifier
                                // The one being carried keeps its slot but is not drawn: the floating proxy under the
                                // finger is standing in for it, and `LauncherDragCell` hides a lifted *cell* the same way.
                                .alpha(if (icon.asIconItem() == preview.lifted) 0f else 1f)
                                // Absolute placement inside the container, so the arrangement owns the layout
                                // completely — there is no row/column structure for a shape like the circle or the
                                // beehive to be forced into.
                                .align(Alignment.TopStart)
                                .offset { IntOffset(slot.x.roundToInt(), slot.y.roundToInt()) }
                                // After the offset, so a new slot reads as a move and the icon glides to it.
                                .animatePlacement()
                                // At draw, so the pull moves the icon without re-laying the arrangement out.
                                // Before the draw-time translations, so the glide measures the slot, not itself.
                                .onPlaced(landing::onPlaced)
                                .graphicsLayer {
                                    val pulled = innerPull?.translationOf(gridItem) ?: Offset.Zero
                                    val landed = landing.translation()
                                    translationX = pulled.x + landed.x
                                    translationY = pulled.y + landed.y
                                    scaleX = landing.scale()
                                    scaleY = landing.scale()
                                }
                                .size(
                                    width = with(density) { slot.width.toDp() },
                                    height = with(density) { slot.height.toDp() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (icon) {
                                is ContainerIcon.App -> AppIcon(
                                    component = icon.info.componentKey,
                                    contentDescription = icon.info.label,
                                    sizePx = with(density) { iconSize.roundToPx() },
                                    modifier = Modifier.size(iconSize),
                                )
                                // `backing = false` for the category cluster's reason: the container already has a
                                // fill, so a plate inside it is a box within a box — and dropping the plate drops its
                                // inset with it, which is only there to keep icons off the plate's own rounded edge.
                                is ContainerIcon.Folder -> IconPreviewPlate(
                                    apps = icon.apps,
                                    size = iconSize,
                                    backing = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The size an icon is drawn at in [slot] — **and the size of its touch target**, which is why it is one function:
 * the cell draws with it and home's hit test (`liftedInCell`) resolves presses against it, and a copy in either
 * would put the target a few dp off the picture, silently.
 *
 * **The slot says how much room there is; the metrics say how much of it an icon may take.**
 * Without this the icon *was* the slot, so a container holding two apps drew them at half the
 * tile — far larger than any icon on the grid around it, and growing further with every resize.
 * The ceiling is the user's own `maxIconDp`, the same guardrail every other surface resolves
 * through, which is why this is a metrics read and not a number invented here.
 *
 * **Unfloored, which is the container's one departure**: `minIconDp` keeps an icon on a *grid*
 * readable, and a container packs many into one cell, so small icons are what it is for. With the
 * floor applied the icons pinned at 24dp partway through a resize and stopped answering to the
 * drag — the container grew and its contents did not. Capped to the slot as well, so an
 * `iconPercent` above 1 cannot spend the gap its neighbour is using.
 *
 * **The container's own scaling multiplies that result, and is bounded by the slot rather than
 * by `maxIconDp`.** The plan said the global ceiling should still bind; implementing it showed
 * that it cannot — the resolve already returns `maxIconDp` for any slot larger than it, so
 * every value above 100% coerced straight back and the control was inert everywhere. A
 * guardrail exists for icons nobody sized on purpose, and this slider *is* sizing them on
 * purpose. The slot stays binding, because past it neighbours overlap; lowering the spacing is
 * how the slot is made bigger, which is why the two are offered together.
 */
internal fun containerIconSize(slot: ArrangementSlot, metrics: IconMetrics, iconScalePercent: Int, density: Density): Dp {
    val slotSize = with(density) { minOf(slot.width, slot.height).toDp() }
    return (metrics.resolveIconSizeUnfloored(slotSize, slotSize) * (iconScalePercent / PercentOf)).coerceAtMost(slotSize)
}

/** [containerIconSize]'s scale is a percentage. */
private const val PercentOf = 100f

/** The rectangle an icon of [iconPx] occupies in this slot, in the container's px — centred, as the cell draws it. */
internal fun ArrangementSlot.iconBounds(iconPx: Float): Rect {
    val left = x + (width - iconPx) / 2f
    val top = y + (height - iconPx) / 2f
    return Rect(left, top, left + iconPx, top + iconPx)
}
