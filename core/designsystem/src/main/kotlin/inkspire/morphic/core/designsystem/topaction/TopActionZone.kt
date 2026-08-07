package inkspire.morphic.core.designsystem.topaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors

/** How deep the band reaches once it has opened up — L1's `EXPANDED_HEIGHT`, and its disengage threshold too. */
val TopActionExpandedHeight = 96.dp

/** The drawn glyph, sized to sit level with the `titleMedium` label beside it. */
private val GlyphSize = 24.dp

/** What the band offers for the item currently in flight. */
enum class TopActionMode {
    /**
     * A side surface is open and the drag can be handed to HOME. One target, and it fires on a **dwell** rather
     * than on release: the point of the band here is to get the drawer out of the way so the drag can carry on
     * over home, which has to happen while the finger is still down.
     */
    ADD_TO_HOME,

    /**
     * The drag is over HOME, where the band takes the item **off** the launcher instead. One or two targets (see
     * [TopActionZone]'s `showUninstall`), and unlike the mode above it fires on **release** — a destructive action
     * that triggered itself on a hold would be a trap, since the finger is often up here on its way somewhere else.
     */
    DELETE,
}

/** Which half of an expanded [TopActionMode.DELETE] band the finger is over. */
enum class TopActionTarget {
    /** Take the item off HOME. For an app that was never placed — one carried in from the drawer — this is a cancel. */
    REMOVE,

    /** Hand the package to the system uninstaller, which asks the user itself. Apps only. */
    UNINSTALL,
}

/**
 * **The band across the top of the screen that takes a dragged item somewhere other than a grid.** Two modes and,
 * crucially, **two states**: a thin collapsed strip that sits in the status bar waiting to be reached, and an
 * expanded 96dp panel naming what it will do.
 *
 * The two states are the whole design and the reason this is a full port of L1's `TopActionZone` rather than the
 * always-open banner it replaced. Collapsed, it is a hint that costs no screen and cannot be hit by accident on the
 * way to the top row of home. Expanded, it has committed to being a target and says so in words — which is the only
 * way to distinguish *remove* from *uninstall*, and the reason a shadow could never do this job (see
 * `DropIntent.REMOVE`). The transition between them is a dwell, owned by [rememberTopActionState].
 *
 * **Reduced from L1's version in one place only:** L1 drove the two modes from two entirely separate
 * implementations — the drawer's `SurfaceExtractEngagement` and home's `rememberTopActionState`, each with its own
 * thresholds and its own copy of the band. Here one band spans both, because the shell is above both surfaces; the
 * mode is a parameter and the timing difference between them lives in one state holder.
 *
 * **Colour.** [TopActionMode.ADD_TO_HOME] is `accent`, the palette's greyscale emphasis. [TopActionMode.DELETE] is
 * `error` — the one hue the palette reserves, and this is what it is reserved *for*: an action that destroys
 * something. L1 used red for delete and green for add; the green goes, the red stays, and it stays because it is
 * carrying meaning rather than decoration.
 *
 * @param mode what the band offers, or null when there is nothing to offer — no drag, or a drag of something the
 *   band cannot take. Null draws nothing at all, so a hidden band cannot swallow a touch.
 * @param expanded whether the band has committed to being a target. Collapsed it is exactly the status-bar inset
 *   deep; both the height and the colour animate, so neither state change is a jump cut mid-gesture.
 * @param showUninstall whether to split the expanded band into Remove | Uninstall. False for anything that is not an
 *   app (a folder has no package), and always false in [TopActionMode.ADD_TO_HOME].
 * @param hoveredTarget which half the finger is over, drawn lit. Null in [TopActionMode.ADD_TO_HOME], whose single
 *   target is always the one being aimed at.
 */
@Composable
fun TopActionZone(
    mode: TopActionMode?,
    expanded: Boolean,
    showUninstall: Boolean,
    hoveredTarget: TopActionTarget?,
    modifier: Modifier = Modifier,
) {
    if (mode == null) return
    val colors = LocalMorphicColors.current
    val density = LocalDensity.current
    val statusBar = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val base by animateColorAsState(
        targetValue = when (mode) {
            TopActionMode.ADD_TO_HOME -> colors.accent
            TopActionMode.DELETE -> colors.error
        },
        label = "topActionColor",
    )
    // The label colour has to follow the band's own, not `onAccent` for both: the DELETE band is `error`, and
    // greyscale-on-accent over red is the wrong contrast pair. Each token names the content its own surface takes.
    val onBase by animateColorAsState(
        targetValue = when (mode) {
            TopActionMode.ADD_TO_HOME -> colors.onAccent
            TopActionMode.DELETE -> colors.onError
        },
        label = "topActionContentColor",
    )
    // Springs rather than a tween so the band settles the way every other surface in the launcher does; no bounce,
    // because a target that overshoots its own bounds is a target that moves while you are aiming at it.
    val height by animateDpAsState(
        targetValue = if (expanded) TopActionExpandedHeight else statusBar,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "topActionHeight",
    )

    // L1's gradient: solid where the labels are, fading out below so the band has no hard bottom edge to be mistaken
    // for a real one. Collapsed it reads as a tint over the status bar and nothing more.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.85f), base.copy(alpha = 0f)))),
    ) {
        // Nothing legible fits in a status bar's worth of height, and drawing into it would put our own label under
        // the clock. The collapsed state is the tint.
        if (!expanded) return@Box
        Row(
            modifier = Modifier
                .fillMaxSize()
                // Padded *inside* the band: the wash covers the status bar, which is what makes the target reachable
                // by a thumb that has run out of screen, while the labels stay clear of it.
                .uiInsetsPadding(WindowInsetsSides.Top)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                TopActionMode.ADD_TO_HOME -> Target(
                    glyph = TopActionGlyph.PLUS,
                    label = "Drop to home",
                    // Its own target: there is nowhere else in this band to aim.
                    highlighted = true,
                    content = onBase,
                )
                TopActionMode.DELETE -> if (showUninstall) {
                    Target(
                        glyph = TopActionGlyph.CROSS,
                        label = "Remove",
                        highlighted = hoveredTarget == TopActionTarget.REMOVE,
                        content = onBase,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Target(
                        glyph = TopActionGlyph.BIN,
                        label = "Uninstall",
                        highlighted = hoveredTarget == TopActionTarget.UNINSTALL,
                        content = onBase,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                } else {
                    Target(TopActionGlyph.CROSS, "Remove", highlighted = true, content = onBase)
                }
            }
        }
    }
}

/** The three marks the band draws. Two strokes each, for [TopActionGlyph]'s reason. */
private enum class TopActionGlyph { PLUS, CROSS, BIN }

/**
 * One target: its glyph and label, lit when it is the one being aimed at.
 *
 * A dimmed target is still perfectly readable — the point of the two halves is that you can see *both* options and
 * choose, so the unhovered one must not disappear.
 *
 * @param content the band's own content colour, passed in rather than read from the palette here: which one is right
 *   depends on which surface colour the band is painted in, and only the caller knows the mode.
 */
@Composable
private fun Target(
    glyph: TopActionGlyph,
    label: String,
    highlighted: Boolean,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (highlighted) content else content.copy(alpha = 0.6f),
        label = "topActionTargetTint",
    )
    // The lit half also gets a wash behind it, so which one is armed reads from the corner of the eye rather than
    // from a contrast difference between two labels. Struck from the content colour rather than a literal white, so
    // it lightens a dark-on-light band and darkens a light-on-dark one instead of always doing the first.
    val wash by animateColorAsState(
        targetValue = if (highlighted) content.copy(alpha = 0.16f) else Color.Transparent,
        label = "topActionTargetWash",
    )
    Row(
        modifier = modifier.background(wash).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopActionGlyphMark(glyph, tint)
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * The glyphs, drawn rather than imported: `core:designsystem` carries no material-icons dependency, and pulling one
 * in for three marks of two strokes each is not a trade worth making. L1 used the icon set here.
 */
@Composable
private fun TopActionGlyphMark(glyph: TopActionGlyph, tint: Color, size: Dp = GlyphSize) {
    Canvas(Modifier.size(size)) {
        val mid = this.size.minDimension / 2f
        val arm = mid * 0.6f
        val width = this.size.minDimension * 0.1f
        fun line(a: Offset, b: Offset) = drawLine(tint, a, b, width, StrokeCap.Round)
        when (glyph) {
            TopActionGlyph.PLUS -> {
                line(Offset(mid - arm, mid), Offset(mid + arm, mid))
                line(Offset(mid, mid - arm), Offset(mid, mid + arm))
            }
            TopActionGlyph.CROSS -> {
                line(Offset(mid - arm, mid - arm), Offset(mid + arm, mid + arm))
                line(Offset(mid + arm, mid - arm), Offset(mid - arm, mid + arm))
            }
            // A bin reduced to what still reads at 24dp: a lid and a body, no hatching.
            TopActionGlyph.BIN -> {
                line(Offset(mid - arm, mid - arm * 0.6f), Offset(mid + arm, mid - arm * 0.6f))
                line(Offset(mid - arm * 0.7f, mid - arm * 0.2f), Offset(mid - arm * 0.5f, mid + arm))
                line(Offset(mid + arm * 0.7f, mid - arm * 0.2f), Offset(mid + arm * 0.5f, mid + arm))
                line(Offset(mid - arm * 0.5f, mid + arm), Offset(mid + arm * 0.5f, mid + arm))
            }
        }
    }
}
