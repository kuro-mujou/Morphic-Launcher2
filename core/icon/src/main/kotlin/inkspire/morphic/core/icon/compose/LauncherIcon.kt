package inkspire.morphic.core.icon.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import inkspire.morphic.core.icon.layer.IconLayerSet
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.core.model.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The baker used to render icons. Provided at the app root once it is wired; `null` renders nothing. */
val LocalIconRenderManager = staticCompositionLocalOf<IconRenderManager?> { null }

/** The global default layer set every app icon uses until per-app overrides land (data:icons, B9). */
val LocalIconLayerSet = staticCompositionLocalOf { IconLayerSet.Base }

// TODO(B4 / core:designsystem AppCell): TEMPORARY fallback bake resolution — REMOVE once the layout
//  passes a real sizePx from IconMetrics (the layout owns icon size). This magic number is not
//  density-aware and only exists so LauncherIcon renders standalone / in @Preview before that wiring.
private const val DEFAULT_ICON_RENDER_PX = 192

/**
 * Draws one app icon — **icon only, no label and no fixed size**. It fills whatever [modifier] the caller
 * gives it (via [ContentScale.Fit]), so the surface's layout owns the display size (the min→max icon-rail
 * percentage) and the icon+text arrangement, exactly as in L1. This keeps that arrangement — which lives one
 * layer up in the design system's cell — free to wrap this composable unchanged.
 *
 * The baked bitmap is fetched from [IconRenderManager]: a synchronous [IconRenderManager.peek] gives an
 * instant cached icon with no flicker, and a miss bakes off the main thread. [sizePx] is the bake resolution
 * (a cache dimension), distinct from the on-screen size set by [modifier].
 *
 * Per-app icon overrides and the "skin" backdrop plate are deliberately not here yet (deferred).
 */
@Composable
fun LauncherIcon(
    component: ComponentKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = DEFAULT_ICON_RENDER_PX,
    layerSet: IconLayerSet = LocalIconLayerSet.current,
) {
    val manager = LocalIconRenderManager.current
    if (manager == null) {
        Box(modifier)
        return
    }

    var bitmap by remember(component, layerSet, sizePx) {
        mutableStateOf(manager.peek(component, layerSet, sizePx))
    }
    LaunchedEffect(component, layerSet, sizePx) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.Default) { manager.get(component, layerSet, sizePx) }
        }
    }

    val rendered = bitmap
    if (rendered != null) {
        Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        // Placeholder while baking (or when the icon can't be resolved). Sized by the caller's modifier so
        // layout does not jump when the bitmap arrives.
        Box(modifier)
    }
}
