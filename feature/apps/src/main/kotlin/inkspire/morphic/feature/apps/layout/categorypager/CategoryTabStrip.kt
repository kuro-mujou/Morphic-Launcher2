package inkspire.morphic.feature.apps.layout.categorypager

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import inkspire.morphic.core.designsystem.cell.AppIcon
import inkspire.morphic.core.designsystem.drag.ItemGestureConfig
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.feature.apps.AppsCategory
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which tab a point [x] px from the strip's **left edge** falls in, clamped to a real tab.
 *
 * The strip's tabs are equal width, and this is that division — shared because three things ask it of the same
 * strip: the scrub gesture and the tab drag inside [CategoryTabStrip], and the drop planner in [AppsCategoryPager]
 * that decides which category a dragged app would be filed under. Restated in any of them, the tab the finger lights
 * and the tab it files into could disagree by one, which is a mis-filing the user only discovers later.
 *
 * Answers 0 for a strip with no width yet, rather than failing: it is called from pointer callbacks that can run
 * before layout has reported, and the caller has already decided there is a tab to hit.
 */
internal fun tabIndexAt(x: Float, stripWidth: Float, tabs: Int): Int =
    if (stripWidth <= 0f || tabs <= 0) 0 else (x / stripWidth * tabs).toInt().coerceIn(0, tabs - 1)

/**
 * Where tab [index] sits, given the strip's own rectangle — [tabIndexAt] read backwards, and here for that reason:
 * a menu anchored by one division while the finger is resolved by another would open beside the tab it names.
 *
 * Root coordinates in, root coordinates out, since the only caller is a menu anchor.
 */
internal fun tabRect(strip: Rect, index: Int, tabs: Int): Rect {
    if (tabs <= 0) return strip
    val width = strip.width / tabs
    return Rect(strip.left + width * index, strip.top, strip.left + width * (index + 1), strip.bottom)
}

/**
 * What the strip's gestures commit, gathered rather than passed one lambda at a time.
 *
 * Four verbs and no state: the strip owns how a gesture *looks* while it happens (which tab is marked, which one is
 * being carried) and this is everything it hands outward when one lands.
 *
 * @property goToPage a tap on a tab, or a slide crossing into it.
 * @property showMenu a long press survived on the tab holding [AppsCategory], anchored at its rectangle.
 * @property dismissMenu the press that opened that menu has started moving, so it is now a drag and the menu is in
 *   the way. Separate from [showMenu] because the strip cannot take down a menu it does not own.
 * @property reorder a carried tab was dropped: the category ids in the order the strip now shows them.
 */
internal class CategoryTabActions(
    val goToPage: (Int) -> Unit,
    val showMenu: (category: AppsCategory, anchor: Rect) -> Unit,
    val dismissMenu: () -> Unit,
    val reorder: (orderedIds: List<String>) -> Unit,
)

/** A tab being carried along the strip: where it came from, and where the finger is now (strip-local px). */
private data class TabDrag(val from: Int, val fingerX: Float)

/**
 * The category pager's **tab strip**: one tab per category, spanning the width above or below the pages.
 *
 * Four gestures, and the three beyond the tap are why the strip is worth its height — a pager alone can only be
 * walked one page at a time, and its categories can only be reordered by editing something else:
 * - **Tap** a tab to go to its page.
 * - **Slide** across the strip to page through them live, a tab at a time under the finger. This is what keeps the
 *   strip usable at eight or twelve categories, where a tab is too narrow to aim at.
 * - **Hold** a tab for its menu (rename), and **keep moving** to carry that tab along the strip and reorder the
 *   categories. One press, two outcomes, decided by whether the finger moves — L1's own chain, and the reason the
 *   menu has to be dismissible from here: it is in the way the moment the press becomes a drag.
 * - **Drag an app onto a tab** to file it under that category without carrying it across pages. That drop is not
 *   this composable's: the strip publishes its bounds and paints [hovered], while the zone answering for it belongs
 *   to [AppsCategoryPager], which owns the commit — behavior travels with the destination zone.
 *
 * **Tabs are equal width, which is the premise of both the scrub and the carry rather than a look.** Both read a
 * tab straight out of the finger's x ([tabIndexAt]); variable-width tabs would need a hit list, and would make one
 * slide cover different numbers of categories depending on where it started.
 *
 * **Icons, not names.** A tab is `width / categories.size` across — some 30dp on a phone with a dozen categories —
 * so a label would be an ellipsis. The name of the page being looked at is that page's own header, which is also
 * what lets it slide away with the page while the strip stays put.
 *
 * @param selected the page showing now — marked under its tab, unless a scrub or a carry is choosing another.
 * @param hovered the tab a dragged app is over, or null. The tab lights up; there is no footprint to paint here,
 *   since a tab is a whole category rather than a slot in one.
 * @param enabled false while an item drag is in flight: the strip is a *drop target* then, and a second finger on it
 *   must not also be paging or rearranging.
 * @param onBounds the strip's rectangle in root coordinates, for the drop zone its caller registers over it.
 */
@Composable
internal fun CategoryTabStrip(
    categories: List<AppsCategory>,
    selected: Int,
    hovered: Int?,
    gestures: ItemGestureConfig,
    enabled: Boolean,
    actions: CategoryTabActions,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = categories.size
    // The tab a scrub is over, and the tab a carry is holding. Both local because they are nobody else's business:
    // each exists only between the finger passing a boundary and the gesture ending.
    var scrubbed by remember { mutableStateOf<Int?>(null) }
    var carried by remember { mutableStateOf<TabDrag?>(null) }
    // The strip's own rectangle, kept here as well as published: a menu is anchored on one tab, and that rectangle
    // is this one divided by the tab count ([tabRect]).
    var stripInRoot by remember { mutableStateOf<Rect?>(null) }

    BoxWithConstraints(modifier) {
        // Computed, so it earns a name: 26dp is the size a tab icon wants, and what it gets is whatever a tab has
        // room for once the gaps either side are taken out — which at a dozen categories is less.
        val iconSize = minOf(26.dp, (maxWidth / tabs.coerceAtLeast(1) - 8.dp).coerceAtLeast(0.dp))
        val tabWidthPx = if (tabs > 0) constraints.maxWidth.toFloat() / tabs else 0f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    val bounds = it.boundsInRoot()
                    stripInRoot = bounds
                    onBounds(bounds)
                }
                .tabStripGestures(
                    tabs = tabs,
                    gestures = gestures,
                    enabled = enabled,
                    handlers = TabPressHandlers(
                        page = actions.goToPage,
                        scrub = { scrubbed = it },
                        menu = { index ->
                            val strip = stripInRoot
                            val category = categories.getOrNull(index)
                            if (strip != null && category != null) {
                                actions.showMenu(category, tabRect(strip, index, tabs))
                            }
                        },
                        dismissMenu = actions.dismissMenu,
                        carry = { carried = it },
                        drop = { from, to -> actions.reorder(categories.reordered(from, to)) },
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The mark follows the **finger**, not the animation: a slide across the strip outruns the spring, and a
            // mark trailing the page it is choosing reads as a gesture that was dropped.
            val marked = scrubbed ?: selected
            val drag = carried
            // Where the carried tab would land — the same division the carry gesture itself reads.
            val target = drag?.let { tabIndexAt(it.fingerX, tabWidthPx * tabs, tabs) }
            categories.forEachIndexed { index, category ->
                key(category.category.id) {
                    CategoryTab(
                        category = category,
                        iconSize = iconSize,
                        selected = index == marked,
                        hovered = index == hovered,
                        lifted = drag?.from == index,
                        // The carried tab is placed under the finger; every tab between its origin and the target
                        // steps aside by one tab's width, which is what makes the gap it will land in visible.
                        offsetPx = if (drag?.from == index) {
                            (drag.fingerX - (index + 0.5f) * tabWidthPx).roundToInt()
                        } else {
                            stepAsidePx(drag, target, index, tabWidthPx)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * These ids with the tab at [from] moved to [to] — what a dropped carry reports.
 *
 * The insertion is clamped against the list *after* the removal, which is the off-by-one this hides: moving the
 * first tab to the last position asks for an index one past the end of the shortened list.
 */
private fun List<AppsCategory>.reordered(from: Int, to: Int): List<String> =
    map { it.category.id }.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) }

/**
 * How far tab [index] steps aside while another is carried over it, in px — negative (leftward) when the carried tab
 * came from its left, positive when from its right, and zero when it is not between the origin and the target.
 */
private fun stepAsidePx(drag: TabDrag?, target: Int?, index: Int, tabWidthPx: Float): Int {
    if (drag == null || target == null) return 0
    return when {
        drag.from < target && index in (drag.from + 1)..target -> -tabWidthPx.roundToInt()
        drag.from > target && index in target until drag.from -> tabWidthPx.roundToInt()
        else -> 0
    }
}

/**
 * Everything one press on the strip can set in motion — the outward verbs plus the two pieces of local state a
 * gesture drives while it runs, gathered so the pointer code below takes one parameter instead of six.
 *
 * @property scrub the tab a slide is over, and null when it ends.
 * @property carry the tab being carried, and null when the carry ends.
 */
private class TabPressHandlers(
    val page: (Int) -> Unit,
    val scrub: (Int?) -> Unit,
    val menu: (Int) -> Unit,
    val dismissMenu: () -> Unit,
    val carry: (TabDrag?) -> Unit,
    val drop: (from: Int, to: Int) -> Unit,
)

/** What a press turned out to be, once it has been watched for the length of a long press. */
private enum class TabPress { RELEASED, SLID, LOST }

/**
 * The strip's own gestures: **tap** a tab to open its page, **slide** across to page through them live, **hold** for
 * the tab's menu and keep moving to carry it.
 */
@Composable
private fun Modifier.tabStripGestures(
    tabs: Int,
    gestures: ItemGestureConfig,
    enabled: Boolean,
    handlers: TabPressHandlers,
): Modifier {
    // Held live, because `pointerInput` is keyed on the tab count alone: whatever this captures on the composition
    // that installs it, it keeps — and [enabled] flips on every drag.
    val current by rememberUpdatedState(handlers)
    val pagingEnabled by rememberUpdatedState(enabled)
    return pointerInput(tabs) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!pagingEnabled || tabs == 0) return@awaitEachGesture
            trackTabPress(
                down = down,
                stripWidth = size.width.toFloat(),
                tabs = tabs,
                gestures = gestures,
                handlers = current,
            )
        }
    }
}

/**
 * Follows one press on the strip to its end.
 *
 * **The first thing decided is which gesture this is**, by watching the press for exactly one long-press timeout:
 * released inside it is a tap, moved sideways inside it is a slide, and surviving it is a hold. Deciding once, up
 * front, is what keeps the three from having to guard against each other — and it is why a tap is *not* handled by
 * timing a release, which is what a single flat loop had to do.
 *
 * Nothing is consumed until the press has become one of the three, which is what leaves the **surface's** own
 * long-press (`surfaceMenuGestures`, which opens the APPS menu) reachable through a press that turns out to be
 * neither: this waits the same timeout, so a hold here fires first and the surface stands down when the tab's menu
 * takes the gesture lock.
 */
private suspend fun AwaitPointerEventScope.trackTabPress(
    down: PointerInputChange,
    stripWidth: Float,
    tabs: Int,
    gestures: ItemGestureConfig,
    handlers: TabPressHandlers,
) {
    val from = tabIndexAt(down.position.x, stripWidth, tabs)
    when (withTimeoutOrNull(gestures.longPressTimeoutMillis) { awaitTabPress(down, gestures.touchSlopPx) }) {
        // A tap goes to the tab it *started* on, not the one it drifted onto: inside the slop it is one press, and
        // the tab under the finger at release is a worse answer than the one the finger was aimed at.
        TabPress.RELEASED -> handlers.page(from)
        TabPress.SLID -> trackScrub(down, stripWidth, tabs, from, handlers)
        TabPress.LOST -> Unit
        // Null is the press *surviving* the wait, which is the hold: the tab's menu opens, and from here a move
        // turns the same press into a carry.
        null -> {
            handlers.menu(from)
            trackCarry(down, stripWidth, tabs, from, gestures.touchSlopPx, handlers)
        }
    }
}

/** Watches a fresh press until it releases, slides sideways, or is lost — whichever comes first. */
private suspend fun AwaitPointerEventScope.awaitTabPress(down: PointerInputChange, slopPx: Float): TabPress {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return TabPress.LOST
        if (!change.pressed) {
            change.consume()
            return TabPress.RELEASED
        }
        if (startsScrub(down, change, slopPx)) {
            change.consume()
            return TabPress.SLID
        }
    }
}

/**
 * Drives one pointer to the end of its life: [onMove] for every event while it is down, [onRelease] once when it
 * lifts, and nothing at all if it is lost (cancelled, or claimed by something that took the gesture).
 *
 * The shape both phases below share, and worth its own name for the reason the phases have theirs: the loop
 * bookkeeping is identical between them and the interesting part is the two lambdas.
 */
private suspend fun AwaitPointerEventScope.followPointer(
    id: PointerId,
    onMove: (PointerInputChange) -> Unit,
    onRelease: (PointerInputChange) -> Unit,
) {
    var following = true
    while (following) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id }
        when {
            change == null -> following = false
            change.pressed -> onMove(change)
            else -> {
                change.consume()
                onRelease(change)
                following = false
            }
        }
    }
}

/** Pages through the tabs under a sliding finger until it lifts. */
private suspend fun AwaitPointerEventScope.trackScrub(
    down: PointerInputChange,
    stripWidth: Float,
    tabs: Int,
    from: Int,
    handlers: TabPressHandlers,
) {
    var last = from
    followPointer(
        id = down.id,
        onMove = { last = scrubTo(it, stripWidth, tabs, last, handlers.scrub, handlers.page) },
        onRelease = {},
    )
    handlers.scrub(null)
}

/**
 * Carries the tab held at [from] along the strip, once the finger holding its menu open starts moving.
 *
 * A release before any movement is the ordinary case — the user wanted the menu — and commits nothing. The menu is
 * dismissed at the moment the carry starts rather than when it commits, because from then on it covers the strip the
 * tab is being dragged along.
 */
private suspend fun AwaitPointerEventScope.trackCarry(
    down: PointerInputChange,
    stripWidth: Float,
    tabs: Int,
    from: Int,
    slopPx: Float,
    handlers: TabPressHandlers,
) {
    var carrying = false
    followPointer(
        id = down.id,
        onMove = { change ->
            if (!carrying && (change.position - down.position).getDistance() > slopPx) {
                carrying = true
                handlers.dismissMenu()
            }
            if (carrying) {
                handlers.carry(TabDrag(from, change.position.x))
                change.consume()
            }
        },
        onRelease = { change ->
            // A tab dropped where it started commits nothing: an identical order would be a write that re-emits
            // the whole surface to say the same thing.
            val to = tabIndexAt(change.position.x, stripWidth, tabs)
            if (carrying && to != from) handlers.drop(from, to)
        },
    )
    handlers.carry(null)
}

/**
 * One step of a scrub: pages to the tab under [change] when that is a new one, and answers with the tab the finger
 * is over now — which the caller carries as [last] so a tab is paged to once rather than on every event inside it.
 *
 * The event is consumed either way, so the surface pan and the surface menu both stand down: this finger is driving
 * the pager now.
 */
private fun scrubTo(
    change: PointerInputChange,
    stripWidth: Float,
    tabs: Int,
    last: Int,
    onScrub: (Int?) -> Unit,
    onGoToPage: (Int) -> Unit,
): Int {
    val page = tabIndexAt(change.position.x, stripWidth, tabs)
    if (page != last) {
        onScrub(page)
        onGoToPage(page)
    }
    change.consume()
    return page
}

/**
 * Whether this press has become a scrub: horizontal travel past the launcher's shared slop, and more of it than
 * vertical — a finger sliding down off the strip is leaving it, not scrubbing it.
 */
private fun startsScrub(down: PointerInputChange, change: PointerInputChange, slopPx: Float): Boolean {
    val dx = change.position.x - down.position.x
    val dy = change.position.y - down.position.y
    return abs(dx) > slopPx && abs(dx) > abs(dy)
}

/**
 * One tab: the category's leading app icon, with the mark under it that says which page is showing.
 *
 * **The icon is the category's first app**, because a category has no icon of its own in the model and deriving one
 * from its name would be a second thing to keep true. The first app is also the icon at the top-left of that page,
 * so a tab and the page it opens show the same thing. An empty category still gets a tab — its page exists
 * precisely to be dragged into — and draws a plain disc, there being no app to borrow from.
 *
 * @param lifted this tab is the one being carried: it sits above its neighbors, grows slightly, and takes
 *   [offsetPx] **unanimated**, since anything but the raw finger position reads as lag under the thumb.
 * @param offsetPx how far left or right this tab is drawn from where it was laid out.
 */
@Composable
private fun CategoryTab(
    category: AppsCategory,
    iconSize: Dp,
    selected: Boolean,
    hovered: Boolean,
    lifted: Boolean,
    offsetPx: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val stepAside by animateIntOffsetAsState(IntOffset(offsetPx, 0), label = "categoryTabStepAside")
    val lift by animateFloatAsState(if (lifted) 1.18f else 1f, label = "categoryTabLift")
    Column(
        modifier = modifier
            .zIndex(if (lifted) 1f else 0f)
            .offset { if (lifted) IntOffset(offsetPx, 0) else stepAside }
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .clip(RoundedCornerShape(12.dp))
            // Lit for two different reasons that want the same thing said: an app is hovering over this tab and
            // would be filed here, or this tab is the one in hand. Both are "this tab is what the finger is about
            // to act on", and everywhere else on this surface that is a footprint in the grid — a tab is a whole
            // category rather than a slot in one, so what lights up is the tab.
            .background(if (hovered || lifted) colors.accentMuted else Color.Transparent)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val leading = category.apps.firstOrNull()
        if (leading != null) {
            AppIcon(
                component = leading.componentKey,
                contentDescription = category.category.name,
                sizePx = with(LocalDensity.current) { iconSize.roundToPx() },
                modifier = Modifier.size(iconSize),
            )
        } else {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(colors.contentDisabled),
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                // Never wider than the icon above it, which is what keeps the marks apart on a crowded strip.
                .size(width = minOf(14.dp, iconSize), height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) colors.content else Color.Transparent),
        )
    }
}
