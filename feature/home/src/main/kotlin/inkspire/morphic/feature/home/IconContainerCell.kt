package inkspire.morphic.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import inkspire.morphic.core.designsystem.backdrop.OnPanel
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.cell.resolveIconSizeUnfloored
import inkspire.morphic.core.model.IconArrangement
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
 * **An empty container draws a "+", and it is a real button.** Something has to be drawn — an empty cell that
 * cannot be removed reads as a rendering fault, which is `WidgetCell`'s argument for naming an unresolvable widget.
 * It opens `core:designsystem`'s `AppPicker`, which is **this component's first consumer**: it went into the design
 * system ahead of any caller precisely because three of them were named and blocked, and the container's "+" turned
 * out to be the fourth and the first to arrive. Dragging an icon in still works and is the faster route; the button
 * is what makes an empty container usable without one.
 */
@Composable
internal fun IconContainerCell(
    icons: List<ContainerIcon>,
    arrangement: IconArrangement,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
    onAddIcon: () -> Unit = {},
    metrics: IconMetrics = LocalIconMetrics.current,
    iconScalePercent: Int = 100,
    spacingScalePercent: Int = 100,
    dropTarget: IconContainerDropTarget? = null,
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
                if (icons.isEmpty()) {
                    ContainerAddGlyph(
                        contentDescription = "Add app",
                        modifier = Modifier.fillMaxSize(),
                        onAdd = onAddIcon,
                    )
                    return@Box
                }
                preview.shown.forEachIndexed { index, icon ->
                    // The gap a newcomer is about to fill: a slot with nothing to draw in it yet.
                    if (icon == null) return@forEachIndexed
                    val slot = slots.getOrNull(index) ?: return@forEachIndexed
                    val slotSize = with(density) { minOf(slot.width, slot.height).toDp() }
                    // **The slot says how much room there is; the metrics say how much of it an icon may take.**
                    // Without this the icon *was* the slot, so a container holding two apps drew them at half the
                    // tile — far larger than any icon on the grid around it, and growing further with every resize.
                    // The ceiling is the user's own `maxIconDp`, the same guardrail every other surface resolves
                    // through, which is why this is a metrics read and not a number invented here.
                    //
                    // **Unfloored, which is the container's one departure**: `minIconDp` keeps an icon on a *grid*
                    // readable, and a container packs many into one cell, so small icons are what it is for. With the
                    // floor applied the icons pinned at 24dp partway through a resize and stopped answering to the
                    // drag — the container grew and its contents did not. Capped to the slot as well, so an
                    // `iconPercent` above 1 cannot spend the gap its neighbour is using.
                    //
                    // **The container's own scaling multiplies that result, and is bounded by the slot rather than
                    // by `maxIconDp`.** The plan said the global ceiling should still bind; implementing it showed
                    // that it cannot — the resolve already returns `maxIconDp` for any slot larger than it, so
                    // every value above 100% coerced straight back and the control was inert everywhere. A
                    // guardrail exists for icons nobody sized on purpose, and this slider *is* sizing them on
                    // purpose. The slot stays binding, because past it neighbours overlap; lowering the spacing is
                    // how the slot is made bigger, which is why the two are offered together.
                    val iconSize = (metrics.resolveIconSizeUnfloored(slotSize, slotSize) * iconScalePercent / 100f)
                        .coerceAtMost(slotSize)
                    Box(
                        modifier = Modifier
                            // The one being carried keeps its slot but is not drawn: the floating proxy under the
                            // finger is standing in for it, and `LauncherDragCell` hides a lifted *cell* the same way.
                            .alpha(if (icon.asIconItem() == preview.lifted) 0f else 1f)
                            // Absolute placement inside the container, so the arrangement owns the layout completely — there
                            // is no row/column structure for a shape like the circle or the beehive to be forced into.
                            .align(Alignment.TopStart)
                            .offset { IntOffset(slot.x.roundToInt(), slot.y.roundToInt()) }
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
                            // `backing = false` for the category cluster's reason: the container already has a fill, so a
                            // plate inside it is a box within a box — and dropping the plate drops its inset with it, which
                            // is only there to keep icons off the plate's own rounded edge.
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
