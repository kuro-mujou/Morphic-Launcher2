package inkspire.morphic.feature.settings.iconstudio

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.model.icon.PreviewBackground

/** The swatch's own corner. Small enough to read as a tile rather than as a pill at this size. */
private val SwatchCorner = 8.dp

/** The button's side. A press target on its own, so it does not need a surrounding pill. */
private val SwatchSide = 34.dp

/** One square of the swatch's checkerboard — a fraction of the canvas's, so the pattern still reads at 34dp. */
private val SwatchCheckerSquare = 4.dp

/**
 * The swatch's outline, and **the reason it is gray rather than [StudioContentColor]**: it is drawn over a swatch the
 * user can cycle to white, and white-on-white is no border at all. The checkerboard's own dark gray is reused for
 * exactly the property its declaration claims — mid-toned enough to read against both a black and a white surround —
 * so the one color in the studio that has to survive both extremes is not chosen twice.
 */
private val SwatchBorder = CheckerDark

/**
 * The canvas-background control: a bordered tile showing **the background it will switch to**, not the one in use.
 *
 * A cycle button has to answer "what happens if I press this?", and the backdrop it would give is a better answer than
 * a name or a counter — the five values are *pictures*, and four of them are hard to name at all ("black with checker"
 * is a description of a drawing, not a label). So the tile draws [PreviewBackground.next] and the label is gone with
 * the pills it stood among.
 *
 * @param background the background **currently** in use; the swatch shows its successor.
 */
@Composable
fun BackgroundCycleButton(
    background: PreviewBackground,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackgroundSwatch(
        background = background.next(),
        modifier = modifier
            .size(SwatchSide)
            .clickable(onClick = onClick),
    )
}

/**
 * A tile painted as [background] would paint the canvas.
 *
 * **Made of the canvas's own parts**, which is the whole of its correctness: the flat color comes from the same
 * `WHITE*`-or-black rule `drawBackdrop` applies, and the checkerboard is that function's own
 * [drawCheckerboard] at a smaller square. A swatch that drew its own approximation of a backdrop would be the one
 * thing a swatch must not be — a picture of something else.
 *
 * **The two mixed modes are drawn as two triangles rather than as a square within a square.** On the canvas the
 * checkerboard fills the icon's *bound*, a square centered in a much larger area; shrinking that arrangement into 34dp
 * would leave a checkered speck inside a border, indistinguishable between the two mixes. A diagonal split gives each
 * half the same weight, so what the tile shows is the *pairing* — flat surround plus transparency — which is what the
 * mode actually is.
 */
@Composable
fun BackgroundSwatch(
    background: PreviewBackground,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SwatchCorner),
    borderWidth: Dp = 1.dp,
) {
    val checkerPx = with(LocalDensity.current) { SwatchCheckerSquare.toPx() }

    // No content: the tile *is* the drawing, and its size is the caller's — so one swatch serves this button and
    // whatever a future presets row wants.
    Box(
        modifier = modifier
            .clip(shape)
            // Inside the clip, so the corners cut the paint; the border is applied after and traces the same shape.
            .drawBehind {
                if (background.checkersOutsideBound) {
                    drawCheckerboard(Offset.Zero, size, checkerPx)
                    return@drawBehind
                }

                // The same expression `drawBackdrop` uses — the flat half of a mixed mode is its *surround*, which is
                // the color the mode is named for.
                val base = when (background) {
                    PreviewBackground.WHITE, PreviewBackground.WHITE_WITH_CHECKER -> Color.White
                    else -> Color.Black
                }
                drawRect(base)
                if (!background.checkersInsideBound) return@drawBehind

                // Split top-left → bottom-right; the checkerboard takes the lower-left half and the flat color keeps
                // the upper-right. Which half is which is arbitrary, but it is fixed here so every swatch agrees.
                val lowerLeft = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, size.height)
                    lineTo(size.width, size.height)
                    close()
                }
                clipPath(lowerLeft) { drawCheckerboard(Offset.Zero, size, checkerPx) }
            }
            .border(borderWidth, SwatchBorder, shape),
    )
}
