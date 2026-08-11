package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.math.ceil
import kotlin.math.min

/** How much of the canvas's shorter side the icon's bound takes. Large enough to work in, short of edge to edge. */
private const val IconBoundFraction = 0.62f

/** One square of the transparency checkerboard. */
private val CheckerSquare = 12.dp

/** The checkerboard's two greys — mid-toned, so they read against both a black and a white surround. */
private val CheckerLight = Color(0xFFBDBDBD)
private val CheckerDark = Color(0xFF8A8A8A)

/**
 * The studio's canvas: a backdrop, and a **square bound** in the middle of it that the icon is drawn in.
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
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawBackdrop(background, checkerPx) },
        contentAlignment = Alignment.Center,
    ) {
        // A fraction of **this node**, not of the screen. The two matter differently here: the canvas is what the
        // icon is centred in, and it is not always the window — the rails inset it, and a tablet gives it a
        // different share again. It is also what lets `drawBackdrop` derive the same square from its own draw-time
        // size and be certain the two agree, rather than sharing a number and hoping.
        val side = min(maxWidth.value, maxHeight.value) * IconBoundFraction
        Box(
            modifier = Modifier
                .size(side.dp)
                // The bound's whole job beyond holding the icon: overflow vanishes here as it would in the bake.
                .clipToBounds(),
        ) {
            content()
        }
    }
}

/**
 * Paints the backdrop: a flat colour, a checkerboard, or a flat colour with the checkerboard confined to the icon's
 * bound.
 *
 * The mixed modes are the useful ones and the reason this is not just two colours — they show an icon's own
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
    // same node.
    val side = min(size.width, size.height) * IconBoundFraction
    val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
    drawCheckerboard(topLeft, Size(side, side), checkerPx)
}

/** The transparency checkerboard, drawn over [area] starting at [topLeft]. */
private fun DrawScope.drawCheckerboard(topLeft: Offset, area: Size, squarePx: Float) {
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
