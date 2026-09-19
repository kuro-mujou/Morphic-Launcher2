package inkspire.morphic.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import inkspire.morphic.core.designsystem.backdrop.OnPanel
import inkspire.morphic.core.designsystem.drag.requireDragCoordinator
import inkspire.morphic.core.designsystem.surface.claimSurfaceGestureWhilePressed
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppWidgetInfo
import inkspire.morphic.core.model.WidgetContainerAxis
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

/**
 * One placed **widget container** — several widgets sharing a cell, **one shown at a time**, swiped between along
 * the container's [axis].
 *
 * **Paged, not stacked**, which is `WidgetContainer`'s own correction: dividing the cell between the contained
 * widgets would shrink each of them, and a user groups widgets to buy *cells* back, not to
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
 * removed reads as a rendering fault. It is a plain glyph, not a button — a tap on the empty cell reaches the add
 * flow this surface already holds (bind a widget, then the provider's configuration screen) through `onOpen`, the
 * same gesture contract every tap uses, rather than through a second `onClick` that would fire on release after a
 * long-press had already opened the menu.
 *
 * @param pages where this container's current page is recorded, for its drag proxy to capture and for a recreated
 *   cell to reopen on.
 */
@Composable
internal fun WidgetContainerCell(
    containerId: Long,
    widgets: List<AppWidgetInfo>,
    axis: WidgetContainerAxis,
    pages: WidgetContainerPages,
    modifier: Modifier = Modifier,
    itemGestures: Modifier = Modifier,
    autoRotate: Boolean = false,
    resetOnReturn: Boolean = false,
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
            // The panel's theme, not home's — see the icon container's own note.
            OnPanel {
                ContainerAddGlyph(
                    contentDescription = "Add widget",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return@Box
        }

        // Starts on the page it was last showing: a container moved to another page or zone is a new cell, and a
        // fresh pager would open it on page one.
        val pagerState = rememberPagerState(initialPage = pages.of(containerId).coerceIn(0, widgets.size - 1)) {
            widgets.size
        }
        LaunchedEffect(pagerState, containerId) {
            snapshotFlow { pagerState.currentPage }.collect { pages.record(containerId, it) }
        }
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
            AppWidgetCell(
                appWidgetId = widgets[index].appWidgetId,
                label = widgets[index].label.ifBlank { UnnamedAppWidget },
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

        PanelPageDots(count = widgets.size, current = pagerState.currentPage, axis = axis)
    }
}

/**
 * The container's page dots, placed on its tile. Shared by the cell and its drag proxy, so lifting a container does
 * not take its dots away and dropping it put them back.
 */
@Composable
private fun BoxScope.PanelPageDots(count: Int, current: Int, axis: WidgetContainerAxis) {
    // A single page is not paged, so it gets no dots — the same rule the folder's pager follows.
    if (count <= 1) return
    // On the trailing edge of whichever axis is *not* being swiped, so the dots never sit under the finger that is
    // changing them.
    val edge = if (axis == WidgetContainerAxis.VERTICAL) Alignment.CenterEnd else Alignment.BottomCenter
    // **Themed against the panel, like the "+" beside them**, since they are drawn on the tile rather than on home.
    // The scope is captured because [OnPanel]'s content is not a `BoxScope` and `align` needs one. The widgets
    // themselves need nothing: an embedded `View` draws its own colors.
    val tile = this
    OnPanel {
        PageDots(count = count, current = current, axis = axis, modifier = with(tile) { Modifier.align(edge) })
    }
}

/**
 * Which page each widget container is showing, by container id — kept outside the cell because two things outside it
 * need the answer.
 *
 * The **drag proxy** captures that page. It used to take the first page whose widget had a view, on the assumption
 * that the pager composes only the page on screen; it does not (a page just left stays composed), so a container
 * scrolled to page two lifted as a picture of page one. And a **recreated cell** reopens on it: a container dropped on
 * another page or into another zone is a new composition, which would otherwise start again at the first page.
 *
 * A plain map rather than state: nothing composes against a page being recorded, only reads it at those two moments.
 */
internal class WidgetContainerPages {
    private val current = HashMap<Long, Int>()

    fun of(containerId: Long): Int = current[containerId] ?: 0

    fun record(containerId: Long, page: Int) {
        current[containerId] = page
    }
}

/**
 * A widget container as the **floating drag proxy** draws it: the same panel, with a still picture of the page that
 * was on screen when the drag began, and the same dots marking that page ([page] of [pageCount]).
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
internal fun WidgetContainerProxy(
    snapshot: Bitmap?,
    page: Int,
    pageCount: Int,
    axis: WidgetContainerAxis,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.containerPanel(), contentAlignment = Alignment.Center) {
        if (snapshot != null) {
            Image(
                bitmap = snapshot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        PanelPageDots(count = pageCount, current = page, axis = axis)
    }
}

/**
 * The page indicator, laid out along the container's [axis].
 *
 * Colors come from the theme rather than being hardcoded white: a launcher whose whole chrome is driven by the
 * wallpaper-brightness signal cannot have one component opting out of it.
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
            modifier = modifier
                .fillMaxHeight()
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(count) { index -> Dot(active = index == current, orientation = orientation) }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
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
                horizontal = if (orientation == Orientation.Horizontal) 2.dp else 0.dp,
                vertical = if (orientation == Orientation.Vertical) 2.dp else 0.dp,
            )
            .size(if (active) 6.dp else 4.dp)
            .background(
                color = if (active) colors.content else colors.contentMuted,
                shape = CircleShape,
            ),
    )
}
