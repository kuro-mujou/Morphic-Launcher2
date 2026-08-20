package inkspire.morphic.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.surface.claimSurfaceGestureWhilePressed
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.WidgetContainerAxis
import inkspire.morphic.core.model.WidgetInfo
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long each widget is shown for when the container rotates itself.
 *
 * A placeholder in the "don't invent a dimension nothing owns yet" sense — it is not a setting anyone owns, and the
 * screen offers the behavior as a switch rather than a duration. Five seconds is long enough to read a clock or a
 * forecast and short enough that the second widget is discovered rather than waited for.
 */
private const val AutoRotateIntervalMs = 5_000L

/** The dots' sizes and spacing — L1's, which are chosen against a widget rather than against a page of them. */
private val ActiveDotSize = 6.dp
private val InactiveDotSize = 4.dp
private val DotSpacing = 2.dp
private val DotsInset = 4.dp

/**
 * One placed **widget container** — several widgets sharing a cell, **one shown at a time**, swiped between along
 * the container's [axis].
 *
 * **Paged, not stacked**, which is `WidgetContainer`'s own correction and L1's actual behavior: dividing the cell
 * between the contained widgets would shrink each of them, and a user groups widgets to buy *cells* back, not to
 * make each widget smaller. So every page fills the container and the dots say how many there are.
 *
 * **`VerticalPager`/`HorizontalPager` from foundation, deliberately not `LauncherPager`.** That component is
 * horizontal only — it measures a page width out of `constraints.maxWidth` — and, more to the point, it carries the
 * launcher's edge-flip and drag machinery, none of which a container's pages want.
 *
 * **It claims the surface swipe while a finger is on it** ([claimSurfaceGestureWhilePressed]), because otherwise the
 * pan takes the gesture on the `Initial` pass before the inner pager sees a single move. That claim is what the
 * whole cell is registering, so a surface pan cannot *start* here — see the modifier's KDoc for why that trade is
 * right for this one item and wrong for a plain widget.
 *
 * **An empty container gets a "+"**, as an icon container does: something has to be drawn, or a cell that cannot be
 * removed reads as a rendering fault. It runs the add flow this surface already holds — bind a widget, then the
 * provider's configuration screen — aimed at this container rather than at the grid.
 */
@Composable
internal fun WidgetContainerCell(
    widgets: List<WidgetInfo>,
    axis: WidgetContainerAxis,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
    autoRotate: Boolean = false,
    resetOnReturn: Boolean = false,
    onAddWidget: () -> Unit = {},
) {
    val colors = LocalMorphicColors.current
    val coordinator = requireDragCoordinator()

    Box(
        modifier = modifier
            .containerPanel()
            .claimSurfaceGestureWhilePressed()
            .then(itemGestures),
        contentAlignment = Alignment.Center,
    ) {
        if (widgets.isEmpty()) {
            IconButton(onClick = onAddWidget) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add widget",
                    tint = colors.contentMuted,
                )
            }
            return@Box
        }

        val pagerState = rememberPagerState(pageCount = { widgets.size })
        // Gated off mid-drag for the reason every other dragging surface gates its own scroller: two gestures
        // otherwise fight over one finger while an item is being carried across the screen.
        val userScrollEnabled = !coordinator.isDragging

        // **Both behaviors are scoped to the launcher being resumed**, which is not tidiness in either case.
        // Auto-rotate would otherwise animate a container nobody is looking at, for as long as the process lives;
        // and "on return" *is* a resume, so `repeatOnLifecycle` is not a wrapper round the reset but the whole of
        // it — the block runs again each time home comes back, which is exactly the event being described.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        if (autoRotate && widgets.size > 1) {
            LaunchedEffect(pagerState, widgets.size, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        delay(AutoRotateIntervalMs.milliseconds)
                        // Skipped rather than canceled while a drag is in flight: the pager is already refusing
                        // the finger then, and a container that shuffled under a dragged icon would move the drop
                        // target out from under it.
                        if (!coordinator.isDragging) {
                            pagerState.animateScrollToPage((pagerState.currentPage + 1) % widgets.size)
                        }
                    }
                }
            }
        }
        if (resetOnReturn) {
            LaunchedEffect(pagerState, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    // `scrollTo`, not `animateScrollTo`: the user has just come back to home and the container
                    // should already be where they will find it, rather than visibly rewinding in front of them.
                    pagerState.scrollToPage(0)
                }
            }
        }
        val page: @Composable (Int) -> Unit = { index ->
            WidgetCell(
                appWidgetId = widgets[index].appWidgetId,
                label = widgets[index].label.ifBlank { UnnamedWidget },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (axis) {
            WidgetContainerAxis.HORIZONTAL -> HorizontalPager(
                state = pagerState,
                userScrollEnabled = userScrollEnabled,
                modifier = Modifier.fillMaxSize(),
            ) { page(it) }
            WidgetContainerAxis.VERTICAL -> VerticalPager(
                state = pagerState,
                userScrollEnabled = userScrollEnabled,
                modifier = Modifier.fillMaxSize(),
            ) { page(it) }
        }

        // A single page is not paged, so it gets no dots — the same rule the folder's pager follows.
        if (widgets.size > 1) {
            PageDots(
                count = widgets.size,
                current = pagerState.currentPage,
                axis = axis,
                modifier = Modifier.align(
                    // On the trailing edge of whichever axis is *not* being swiped, so the dots never sit under the
                    // finger that is changing them. L1's alignment pair exactly.
                    if (axis == WidgetContainerAxis.VERTICAL) Alignment.CenterEnd else Alignment.BottomCenter,
                ),
            )
        }
    }
}

/**
 * A widget container as the **floating drag proxy** draws it: the same panel, with a still picture of the page that
 * was on screen when the drag began.
 *
 * **It cannot be the real cell**, for `AppWidgetHostController.snapshot`'s reason one level up. A container's pages
 * are `AppWidgetHostView`s, so re-composing it under the finger would build a *second* live instance of every
 * widget in it — the exact thing a dragged widget's snapshot exists to avoid. What the user is carrying is the
 * thing they were looking at, which is what a snapshot is.
 *
 * [snapshot] is null for an **empty** container, and for one whose page could not be captured. Then the panel is
 * drawn on its own, which is right rather than a fallback: an empty container genuinely is an empty panel, and its
 * "+" is deliberately left out — a button is not something to draw on a proxy that cannot be pressed.
 */
@Composable
internal fun WidgetContainerProxy(snapshot: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.containerPanel(), contentAlignment = Alignment.Center) {
        if (snapshot != null) {
            Image(
                bitmap = snapshot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The page indicator, laid out along the container's [axis].
 *
 * Colors come from the theme rather than being hardcoded white as L1's are — a launcher whose whole chrome is
 * driven by the wallpaper-brightness signal cannot have one component opting out of it.
 */
@Composable
private fun PageDots(
    count: Int,
    current: Int,
    axis: WidgetContainerAxis,
    modifier: Modifier = Modifier,
) {
    val orientation =
        if (axis == WidgetContainerAxis.VERTICAL) Orientation.Vertical else Orientation.Horizontal
    if (orientation == Orientation.Vertical) {
        Column(
            modifier = modifier.fillMaxHeight().padding(end = DotsInset),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(count) { index -> Dot(active = index == current, orientation = orientation) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().padding(bottom = DotsInset),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { index -> Dot(active = index == current, orientation = orientation) }
        }
    }
}

/** One dot: larger and fully opaque for the page being shown, smaller and dimmed for the rest. */
@Composable
private fun Dot(active: Boolean, orientation: Orientation) {
    val colors = LocalMorphicColors.current
    Box(
        modifier = Modifier
            .padding(
                horizontal = if (orientation == Orientation.Horizontal) DotSpacing else 0.dp,
                vertical = if (orientation == Orientation.Vertical) DotSpacing else 0.dp,
            )
            .size(if (active) ActiveDotSize else InactiveDotSize)
            .background(
                color = if (active) colors.content else colors.contentMuted,
                shape = CircleShape,
            ),
    )
}
