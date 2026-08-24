package inkspire.morphic.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.cell.IconPreviewPlate
import inkspire.morphic.core.designsystem.container.slots
import inkspire.morphic.core.model.ComponentKey
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
 * **Two tap targets, on the card's model.** Each slot launches its app or opens its folder; the container's own
 * [itemGestures] go on the **whole cell**, because a container fills its cell the way a widget does — that is
 * `LauncherDragCell`'s stated exception to the icon+label rule, not a departure from it. The two compose without
 * arbitration: `clickable` consumes the down on the Main pass, but `launcherItemGestures` takes
 * `awaitFirstDown(requireUnconsumed = false)` and reads movement with `positionChangedIgnoreConsumed()`, so a
 * long-press *on a slot* still reaches the container's own menu and drag.
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
    onLaunch: (ComponentKey) -> Unit = {},
    onOpenFolder: (Long) -> Unit = {},
    onAddIcon: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .containerPanel()
            .then(itemGestures),
        contentAlignment = Alignment.Center,
    ) {
        if (icons.isEmpty()) {
            ContainerAddGlyph(
                contentDescription = "Add app",
                modifier = Modifier.fillMaxSize(),
                onAdd = onAddIcon,
            )
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Keyed on everything the maths reads, so a resize or a membership change re-lays out and a recomposition
        // for any other reason does not.
        val slots = remember(arrangement, icons.size, widthPx, heightPx) {
            arrangement.slots(icons.size, widthPx, heightPx)
        }

        icons.forEachIndexed { index, icon ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            val slotSize = with(density) { minOf(slot.width, slot.height).toDp() }
            Box(
                modifier = Modifier
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
                        sizePx = with(density) { slotSize.roundToPx() },
                        modifier = Modifier
                            .size(slotSize)
                            .clickable { onLaunch(icon.info.componentKey) },
                    )
                    // `backing = false` for the category cluster's reason: the container already has a fill, so a
                    // plate inside it is a box within a box — and dropping the plate drops its inset with it, which
                    // is only there to keep icons off the plate's own rounded edge.
                    is ContainerIcon.Folder -> IconPreviewPlate(
                        apps = icon.apps,
                        size = slotSize,
                        backing = false,
                        modifier = Modifier.clickable { onOpenFolder(icon.folder.id) },
                    )
                }
            }
        }
    }
}
