package inkspire.morphic.core.designsystem.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.filmBackdrop
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** The tallest a menu may be, as a fraction of the usable height — past this it scrolls. L1's 0.6. */
private const val MenuMaxHeightFraction = 0.6f

/**
 * A context menu, anchored to the item it was opened on.
 *
 * **It renders inline, in the ordinary composition tree, and never in a `Popup`** — which is the single most
 * important thing about it. The launcher's item gesture opens this menu **while the finger is still down**
 * (`ItemGesturePhase.MenuOpen`), and moving from there begins a drag on the *same* pointer stream. A `Popup` is a
 * separate platform window: raising one mid-gesture takes focus and can cancel the stream the drag depends on. So
 * this is a full-screen `Box` in the shell's own tree — the reason an inline menu is the only kind that works here
 * at all.
 *
 * Everything about *where* it goes is [menuPlacementFor] / [menuOffsetFor] — pure, unit-tested, and given the
 * usable area rather than the raw window so a notch cannot push the menu under itself.
 *
 * **It is frosted with the film's own material**, not with a panel's — see [MenuSurface], which owns why a menu gave
 * up the user's blur strength and the liquid-glass rim to look like the sheet it appears a second away from.
 *
 * @param anchor what the menu belongs to — an item's bounds, or the point an empty-space long-press landed on.
 *   [MenuAnchor] owns why those two are placed and revealed differently.
 * @param onDismiss called once the closing animation has finished, so the caller drops its state after the menu
 *   has actually left rather than while it is still fading.
 * @param header an optional block above the actions (a title, a stage toggle). Inside the same surface, so it
 *   scrolls with nothing and stays pinned while the actions below it scroll.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContextMenu(
    anchor: MenuAnchor,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val insets = uiInsets
    val window = LocalWindowInfo.current.containerSize

    // The area the menu may occupy: the window less the bars and the cutout. `LayoutDirection.Ltr` on both
    // horizontal reads because this is an absolute pixel rectangle — left is left — not a start/end pair.
    val frame = remember(insets, window, density) {
        IntRect(
            left = insets.getLeft(density, LayoutDirection.Ltr),
            top = insets.getTop(density),
            right = window.width - insets.getRight(density, LayoutDirection.Ltr),
            bottom = window.height - insets.getBottom(density),
        )
    }
    val gapPx = with(density) { 8.dp.roundToPx() }

    // The anchor resolved to whole pixels and to the side this menu opens on — computed once, read by both the
    // reveal (in composition) and the placement (in the measure block below), so the two cannot disagree about
    // which way the menu is coming from.
    val resolved = remember(anchor, frame) {
        when (anchor) {
            is MenuAnchor.Item -> {
                val rect = with(anchor.bounds) {
                    IntRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                }
                ResolvedAnchor.Beside(rect, menuPlacementFor(rect, frame))
            }

            is MenuAnchor.Press -> {
                val point = IntOffset(anchor.position.x.toInt(), anchor.position.y.toInt())
                ResolvedAnchor.Docked(point, menuDockFor(point, frame))
            }
        }
    }

    // The menu owns its own exit: a dismiss sets the target and [onDismiss] fires only once the transition has
    // settled, so the caller's state outlives the animation. Without this the surface would drop the request on
    // the first frame of the fade and the menu would vanish instead of closing.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.currentState && !visible.targetState) onDismiss()
    }
    val dismiss: () -> Unit = { visible.targetState = false }

    Box(modifier.fillMaxSize()) {
        // The tap-catcher: anywhere outside the menu closes it. Beneath the menu in the stack, so a tap on a row
        // reaches the row. `detectTapGestures` rather than `clickable` so it stays silent — no ripple, no
        // semantics click on a full-screen rectangle whose only job is to catch a miss.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )

        Layout(
            content = {
                // **Two reveals, because the two anchors mean different things.** A menu beside an item *grows out
                // of* it, so it scales from the edge nearest the icon; a docked menu *arrives from* its screen edge,
                // so it slides in along it. The anchor decides, rather than the caller picking a composable.
                // Expressive motion from the theme's own scheme either way: spatial for the movement, effects for
                // the fade.
                val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                val enter = fadeIn(fade) + when (resolved) {
                    is ResolvedAnchor.Beside -> scaleIn(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialScale = 0.85f,
                        transformOrigin = resolved.placement.transformOrigin(),
                    )

                    is ResolvedAnchor.Docked -> slideInHorizontally(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ) { width -> if (resolved.dock == MenuDock.LEFT) -width else width }
                }
                val exit = fadeOut(fade) + when (resolved) {
                    is ResolvedAnchor.Beside -> scaleOut(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetScale = 0.9f,
                        transformOrigin = resolved.placement.transformOrigin(),
                    )

                    is ResolvedAnchor.Docked -> slideOutHorizontally(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ) { width -> if (resolved.dock == MenuDock.LEFT) -width else width }
                }
                AnimatedVisibility(visibleState = visible, enter = enter, exit = exit) {
                    MenuSurface(
                        actions = actions,
                        maxHeight = with(density) { (frame.height * MenuMaxHeightFraction).toDp() },
                        onAction = { it.onClick(); dismiss() },
                        header = header,
                    )
                }
            },
        ) { measurables, constraints ->
            // Empty once the exit has finished and `AnimatedVisibility` has released its content — the frame
            // between that and [onDismiss] taking the menu out of the tree.
            val measurable = measurables.firstOrNull()
                ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
            val placeable = measurable.measure(
                Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight),
            )
            val menuSize = IntSize(placeable.width, placeable.height)
            val offset = when (resolved) {
                is ResolvedAnchor.Beside ->
                    menuOffsetFor(resolved.bounds, menuSize, frame, resolved.placement, gapPx)

                is ResolvedAnchor.Docked ->
                    dockedMenuOffsetFor(resolved.at, menuSize, frame, resolved.dock, gapPx)
            }
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(offset) }
        }
    }
}

/**
 * A [MenuAnchor] resolved against the usable frame: whole pixels, plus the side this menu opens on.
 *
 * It exists so that side is decided **once**. The reveal needs it during composition and the placement needs it
 * during measurement, and a menu that scaled out of its own top edge while being placed *above* its item would be
 * exactly the kind of mismatch nobody notices in review.
 */
private sealed interface ResolvedAnchor {
    data class Beside(val bounds: IntRect, val placement: MenuPlacement) : ResolvedAnchor
    data class Docked(val at: IntOffset, val dock: MenuDock) : ResolvedAnchor
}

/**
 * The menu's own panel: the frost, the shape, and the rows inside them.
 *
 * **Frosted over HOME, flat over the film** — [LocalOverFrost], and the switch is `wallpaperBackdrop`'s rather than
 * this composable's: a menu raised on the APPS surface or in an open collection is already sitting on a blurred sheet
 * of the wallpaper, and sampling it a second time cuts a *sharper* hole through that sheet rather than laying glass
 * on it. The flat panel is the same color the frost falls back to with no wallpaper to sample, so what a menu looks
 * like on a fresh install is what it looks like there.
 *
 * **And the frost it does draw is the film's, not a panel's** ([filmBackdrop]) — the same material the APPS surface is
 * read against, clipped to a rounded rect. A launcher with one frost that appears at two strengths reads as two
 * materials: a menu on HOME sat at the user's blur while the sheet a swipe away sat at the fixed one, and the two are
 * on screen a second apart. So the menu follows the film and gives up the two things a *panel* has — the user's blur
 * strength and, on API 33+, the liquid-glass rim. The rim is the deliberate half: an edge that bends light is exactly
 * what would make this a different glass from the sheet it has to match. A container tile on the grid keeps both, and
 * `filmBackdrop` owns the test that separates the two.
 */
@Composable
private fun MenuSurface(
    actions: List<MenuAction>,
    maxHeight: Dp,
    onAction: (MenuAction) -> Unit,
    header: (@Composable () -> Unit)?,
) {
    val colors = LocalMorphicColors.current
    Column(
        modifier = Modifier
            .width(248.dp)
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(16.dp))
            // The scrim is the theme's elevated surface, which is what a menu has to be readable against with no
            // wallpaper to sample — and it is also what this fills with over the film, since `wallpaperBackdrop`
            // makes that decision itself. One color for both, so a menu looks the same on a device with no wallpaper
            // as it does on a surface already carrying the frost.
            .filmBackdrop(scrimColor = colors.surfaceElevated, shape = RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp),
    ) {
        header?.invoke()
        Column(
            // `fill = false` so a short menu wraps its content and only a long one takes the height cap.
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            actions.forEach { action ->
                MenuRow(action) { onAction(action) }
            }
        }
    }
}

/**
 * One tappable row.
 *
 * **Colors come from the theme.** The launcher's light/dark input is
 * the *wallpaper's* brightness, so a menu over a bright wallpaper is drawn on a light panel — and white-on-white
 * would be the one place in the launcher that ignored the signal the whole theme is built on.
 */
@Composable
private fun MenuRow(action: MenuAction, onClick: () -> Unit) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = action.enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Drawn as it was published, never tinted: an app's shortcut icon is its own artwork, and flattening it
        // to the menu's content color would make every app's shortcuts look alike.
        action.icon?.let { icon ->
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.enabled) colors.content else colors.contentDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Which way the header's stage button takes you.
 *
 * A chevron either way, rather than a gear-then-back pair: the two stages are a sequence — what the *app* offers,
 * then what the *launcher* offers — and a gear meaning "our actions" is a stretch. One mark, two
 * directions, and the direction alone says whether you are going deeper or coming back.
 */
internal enum class MenuStage {
    /** On the app's shortcuts; the button goes on to the launcher's actions. */
    FORWARD,

    /** On the launcher's actions; the button goes back to the shortcuts. */
    BACK,
}

/**
 * The header block shared by every item menu: the item's name, and — when there is a second stage to reach — a
 * button that swaps to it.
 *
 * @param stage null when the menu has only one stage, which **hides** the button rather than disabling it. A
 *   disabled control that could never become enabled is the thing this codebase keeps refusing to draw.
 */
@Composable
internal fun MenuHeader(
    title: String,
    stage: MenuStage?,
    onToggle: () -> Unit,
) {
    val colors = LocalMorphicColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 10.dp),
        )
        if (stage != null) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .padding(6.dp),
            ) {
                ChevronMark(stage, colors.contentMuted)
            }
        }
    }
    HorizontalDivider(color = colors.divider)
}

/**
 * The stage button's mark: two strokes, drawn here rather than imported, because `core:designsystem` carries no
 * material-icons dependency — the same trade [inkspire.morphic.core.designsystem.topaction.TopActionZone] makes
 * for its three glyphs.
 */
@Composable
private fun ChevronMark(stage: MenuStage, tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val mid = this.size.minDimension / 2f
        val arm = mid * 0.45f
        val stroke = this.size.minDimension * 0.11f
        // FORWARD points at the stage being opened (to the right in reading order); BACK reverses it.
        val tipX = if (stage == MenuStage.FORWARD) mid + arm else mid - arm
        val tailX = if (stage == MenuStage.FORWARD) mid - arm else mid + arm
        drawLine(tint, Offset(tailX, mid - arm), Offset(tipX, mid), stroke, StrokeCap.Round)
        drawLine(tint, Offset(tailX, mid + arm), Offset(tipX, mid), stroke, StrokeCap.Round)
    }
}
