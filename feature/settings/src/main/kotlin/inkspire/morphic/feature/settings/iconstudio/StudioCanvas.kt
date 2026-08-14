package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.icon.PreviewBackground
import kotlin.math.ceil
import kotlin.math.min

/** How much of the canvas's shorter side the icon's bound takes. Large enough to work in, short of edge to edge. */
private const val IconBoundFraction = 0.62f

/**
 * How far above center the bound sits, as a fraction of the canvas's height.
 *
 * **The chrome is not symmetrical, so the middle of the canvas is not the middle of what can be seen.** The top holds
 * one row of pill buttons; the bottom holds the tool rail, the cycle row, and — whenever a section is open — a panel of
 * up to 300dp. Centered, the icon sits under that panel exactly when the user is adjusting the layer it is drawing.
 *
 * **A fixed lift rather than centering in the free space**, which was the alternative and is worse: the panel's height
 * varies by section, so the icon would jump every time the rail was tapped, and the jump would be largest for the
 * sections with the most controls — the ones being watched most closely. A constant offset is a compromise and is meant
 * to be: at 12% it clears the rail and the cycle row outright, and leaves an open panel overlapping only the bottom of
 * the bound rather than most of it.
 *
 * Read by the layout **and** by [drawBackdrop], because the checkerboard in the two mixed modes fills this same square
 * — the KDoc below promises the two agree, and they only do while both apply this.
 */
private const val IconBoundLift = 0.12f

/**
 * How far toward the start the bound sits, as a fraction of the canvas's width — the horizontal twin of
 * [IconBoundLift], and it exists for the same reason applied to the other axis.
 *
 * **The layer rail runs down the end edge**, so the canvas is no longer symmetrical left to right any more than it
 * was top to bottom. Centred, a phone's bound reaches within a few dp of the rail and a narrower one goes under it —
 * the icon being obscured by the very control used to pick which layer is being edited.
 *
 * A fraction rather than the rail's own width in dp, for the reason the lift gives: a dp shift would move the bound
 * further on a phone than on a tablet relative to what is around it, and — the load-bearing half — [drawBackdrop]
 * reproduces this square from its own draw-time size, so anything it cannot derive there would have to be threaded
 * in and kept in step.
 */
private const val IconBoundShift = 0.08f

/** One square of the transparency checkerboard, at canvas scale. */
private val CheckerSquare = 12.dp

/** The checkerboard's two grays — mid-toned, so they read against both a black and a white surround. */
internal val CheckerLight = Color(0xFFBDBDBD)
internal val CheckerDark = Color(0xFF8A8A8A)

/**
 * The studio's canvas: a backdrop, and a **square bound** the icon is drawn in — centered horizontally, and a little
 * above center vertically so the chrome below does not cover it ([IconBoundLift]).
 *
 * The bound is square and it **clips**, both because the real renderer works that way — an icon is composited into a
 * square bitmap — so a layer pushed past the edge here disappears exactly as it would on the home screen. An editor
 * that let artwork spill past the bound would be showing the user something the launcher can never draw.
 *
 * The whole canvas is the Haze source, so a floating surface blurs the backdrop *and* the icon it overlaps, which is
 * what makes the panels read as glass over the work rather than as opaque cards parked on it.
 *
 * @param content the icon, drawn to fill the square bound.
 */
@Composable
fun StudioCanvas(
    background: PreviewBackground,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val checkerPx = with(density) { CheckerSquare.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .drawBehind { drawBackdrop(background, checkerPx) },
        contentAlignment = Alignment.Center,
    ) {
        // A fraction of **this node**, not of the screen. The two matter differently here: the canvas is what the
        // icon is centered in, and it is not always the window — the rails inset it, and a tablet gives it a
        // different share again. It is also what lets `drawBackdrop` derive the same square from its own draw-time
        // size and be certain the two agree, rather than sharing a number and hoping.
        val side = min(maxWidth.value, maxHeight.value) * IconBoundFraction
        Box(
            modifier = Modifier
                .size(side.dp)
                // Centered, then lifted clear of the chrome below and shifted clear of the layer rail at the end
                // edge — see [IconBoundLift] and [IconBoundShift]. Offsets rather than an alignment bias because a
                // bias scales with the *leftover* space, so the same value would move the bound further on a tablet
                // than on a phone; these are fractions of the canvas either way, which is what lets `drawBackdrop`
                // reproduce the square from its own size.
                .offset(x = -maxWidth * IconBoundShift, y = -maxHeight * IconBoundLift)
                // The bound's whole job beyond holding the icon: overflow vanishes here as it would in the bake.
                .clipToBounds(),
        ) {
            content()
        }
    }
}

/**
 * Paints the backdrop: a flat color, a checkerboard, or a flat color with the checkerboard confined to the icon's
 * bound.
 *
 * The mixed modes are the useful ones and the reason this is not just two colors — they show an icon's own
 * transparency *and* how its silhouette reads against a dark or light surround at the same time, which is the pair
 * of questions actually being asked while shaping a layer.
 */
private fun DrawScope.drawBackdrop(background: PreviewBackground, checkerPx: Float) {
    val base = when (background) {
        PreviewBackground.WHITE, PreviewBackground.WHITE_WITH_CHECKER -> Color.White
        else -> Color.Black
    }
    if (!background.checkersOutsideBound) drawRect(base)

    if (background.checkersOutsideBound) {
        drawCheckerboard(Offset.Zero, size, checkerPx)
        return
    }
    if (!background.checkersInsideBound) return

    // The same square the icon is drawn in. Derived here from this draw scope's own size rather than passed in,
    // which is what makes the two certain to agree: both are `IconBoundFraction` of the shorter side of the very
    // same node, centered and then moved by `IconBoundShift` of its width and `IconBoundLift` of its height.
    val side = min(size.width, size.height) * IconBoundFraction
    val topLeft = Offset(
        x = (size.width - side) / 2f - size.width * IconBoundShift,
        y = (size.height - side) / 2f - size.height * IconBoundLift,
    )
    drawCheckerboard(topLeft, Size(side, side), checkerPx)
}

/**
 * The transparency checkerboard, drawn over [area] starting at [topLeft].
 *
 * Internal rather than private so the background swatch on the cycle button draws the *same* checkerboard, at its own
 * [squarePx] — a swatch claiming to show what a backdrop looks like has to be made of the backdrop's own parts.
 */
internal fun DrawScope.drawCheckerboard(topLeft: Offset, area: Size, squarePx: Float) {
    drawRect(CheckerLight, topLeft = topLeft, size = area)

    val columns = ceil(area.width / squarePx).toInt()
    val rows = ceil(area.height / squarePx).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 0) continue
            val x = topLeft.x + column * squarePx
            val y = topLeft.y + row * squarePx
            // Clipped to the area so a partial square at the edge does not bleed past the bound.
            val w = min(squarePx, topLeft.x + area.width - x)
            val h = min(squarePx, topLeft.y + area.height - y)
            drawRect(CheckerDark, topLeft = Offset(x, y), size = Size(w, h))
        }
    }
}
