package inkspire.morphic.core.designsystem.backdrop

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.unit.toRect
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.designsystem.theme.MorphicColors
import inkspire.morphic.core.designsystem.theme.lerp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * **A picture that text sits straight on**, read spot by spot: its luminance map, and the wash painted over it.
 *
 * One exists: the **wallpaper** on HOME, with no wash. The film, a frosted panel and a flat scrim provide none — the
 * blur or the flat tone evens out what is under the text, and the enclosing theme already answers for it.
 *
 * @param wash the color painted over the picture, alpha included; `Color.Transparent` for none.
 */
class InkSurface(val brightness: BackdropBrightness, wash: Color = Color.Transparent) {
    private val washLuminance = wash.luminance()
    private val washAlpha = wash.alpha

    /** The luminance the eye sees at a cell — the picture composited under the wash, by [washedLuminance]'s formula. */
    fun luminanceAt(column: Int, row: Int): Float = washedLuminance(brightness.map[column, row], washLuminance, washAlpha)
}

/**
 * The [InkSurface] behind content composed here, or null where the background is one flat tone (a frosted panel, a
 * scrim, settings) and the enclosing theme is the answer.
 */
val LocalInkSurface = staticCompositionLocalOf<InkSurface?> { null }

/**
 * Themes [content] against the patch of [LocalInkSurface] it actually occupies — the one place text on a picture takes
 * its color from.
 *
 * **The spot is read from where the content is on screen, as it moves.** `onLayoutRectChanged` rather than
 * `onGloballyPositioned`, because the second does not reliably re-fire when a scroller moves a node — and the lists
 * scroll, while a pager swipe carries every label across the picture. The ink is snapshot state read in composition,
 * so content recomposes only while its ink *flips*.
 *
 * **A flip is a cross-fade of the whole palette, not a switch** — ink and glow color together, over the motion
 * scheme's effects spec. Switched on a boolean, a label swiped across a boundary blinked. The first reading snaps
 * instead, since fading in from the surface's verdict would animate every label into place each time one is composed.
 *
 * **With no surface it does nothing and themes nothing**, which is every flat background and every settings preview.
 */
@Composable
fun SpotTheme(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val surface = LocalInkSurface.current
    if (surface == null) {
        Box(modifier = modifier, propagateMinConstraints = true) { content() }
        return
    }
    val spot = remember { InkSpot() }
    // Until the first layout there is no spot to read, and the enclosing theme is the surface's own verdict — one frame.
    val verdict = LocalMorphicColors.current == MorphicColors.Dark
    // 1 is light ink (the dark palette), 0 is dark ink.
    val lightness = remember { Animatable(if (verdict) 1f else 0f) }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val flipSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val light = spot.light
    LaunchedEffect(light) {
        if (light == null) return@LaunchedEffect
        val target = if (light) 1f else 0f
        if (spot.shown) lightness.animateTo(target, flipSpec) else lightness.snapTo(target)
        spot.shown = true
    }
    val colors = lerp(MorphicColors.Light, MorphicColors.Dark, lightness.value)
    CompositionLocalProvider(LocalMorphicColors provides colors) {
        Box(
            modifier = modifier
                .onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) { bounds ->
                    spot.read(surface, bounds.boundsInScreen.toRect())
                },
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** One [SpotTheme]'s last reading. */
@Stable
private class InkSpot {
    var light by mutableStateOf<Boolean?>(null)
        private set

    /** Whether an ink has been applied, so the first one snaps rather than fading in. Plain: nothing redraws on it. */
    var shown = false
    private val reader = InkReader()

    fun read(surface: InkSurface, screen: Rect) {
        light = reader.read(surface, screen, current = light)
    }
}

/**
 * Reads which ink a screen rectangle of an [InkSurface] wants — [inkOver]'s answer, true for light.
 *
 * **One buffer, reused**, so a label being swiped across the picture allocates nothing per frame. A rectangle partly off
 * the picture — a page mid-swipe — is read from the cells along the edge it hangs over, which is the picture it is about
 * to be over.
 */
internal class InkReader {
    private var scratch = FloatArray(64)

    /** @param current the ink the reader already shows — see [inkOver]. */
    fun read(surface: InkSurface, screen: Rect, current: Boolean? = null): Boolean {
        val map = surface.brightness.map
        val cells = surface.brightness.screenToMap(screen)
        val firstColumn = floor(cells.left).toInt().coerceIn(0, map.columns - 1)
        val lastColumn = (ceil(cells.right).toInt() - 1).coerceIn(firstColumn, map.columns - 1)
        val firstRow = floor(cells.top).toInt().coerceIn(0, map.rows - 1)
        val lastRow = (ceil(cells.bottom).toInt() - 1).coerceIn(firstRow, map.rows - 1)
        val count = (lastColumn - firstColumn + 1) * (lastRow - firstRow + 1)
        if (scratch.size < count) scratch = FloatArray(count)
        var i = 0
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) scratch[i++] = surface.luminanceAt(column, row)
        }
        return inkOver(scratch, count, current = current)
    }
}
