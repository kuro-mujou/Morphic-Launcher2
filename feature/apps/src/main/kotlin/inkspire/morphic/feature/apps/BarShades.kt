package inkspire.morphic.feature.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import inkspire.morphic.core.designsystem.surface.LocalSurfaceSettled

/** How dark a shade is at the screen's edge; it fades to nothing where the bar ends. */
private const val ShadeEdgeAlpha = 0.45f

/**
 * A translucent black shade under the status bar and another over the navigation bar, so content scrolling beneath the
 * bars stays readable. For the APPS layouts that scroll under them; the pager does not.
 *
 * **Each is exactly its bar's height, read from the real inset** — a dp guess would leave a strip on gesture
 * navigation, where the navigation bar is a few dp. The system draws no scrim of its own: `MainActivity` makes both
 * bars scrimless and turns navigation-bar contrast off.
 *
 * **Only while the surface is settled** ([LocalSurfaceSettled]). They are drawn inside the surface, so during a pan they
 * would travel with it — a dark band sliding up the screen, where the bars themselves stay put. They fade in once the
 * pan comes to rest and are gone the moment it moves again, since fading out would show that band for a few frames.
 *
 * Plain boxes with no pointer input, so a touch on the bar's area still reaches the content underneath.
 */
@Composable
internal fun BoxScope.BarShades() {
    val settled = LocalSurfaceSettled.current
    val shown = remember { Animatable(if (settled) 1f else 0f) }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val fadeSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(settled) { if (settled) shown.animateTo(1f, fadeSpec) else shown.snapTo(0f) }

    val edge = Color.Black.copy(alpha = ShadeEdgeAlpha)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer { alpha = shown.value }
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(edge, Color.Transparent))),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer { alpha = shown.value }
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(Brush.verticalGradient(listOf(Color.Transparent, edge))),
    )
}
