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
import inkspire.morphic.core.icon.render.IconRenderManager
import inkspire.morphic.core.model.ComponentKey
import inkspire.morphic.core.model.icon.IconAppearance
import inkspire.morphic.core.model.icon.IconLayerSet

/** The baker used to render icons. Provided at the app root once it is wired; `null` renders nothing. */
val LocalIconRenderManager = staticCompositionLocalOf<IconRenderManager?> { null }

/** The global default appearance an app icon uses when it has no override of its own. */
val LocalIconAppearance = staticCompositionLocalOf { IconAppearance.Base }

/**
 * The apps that have been **detached** from [LocalIconAppearance] and render from a recipe of their own, provided by
 * `app` from `data:icons`. Empty until something is customized, which is what makes the plain case free.
 *
 * **A map rather than a per-icon lookup**, because every icon on screen asks this question and a `Flow` per cell
 * would be hundreds of collectors. `static` for the same reason [LocalIconAppearance] is: reads are the hot path here
 * and there are hundreds of them. The bill is that re-providing it recomposes the whole subtree rather than only its
 * readers — paid once per icon edit, and cheap even then, because a recomposition with unchanged inputs re-`remember`s
 * nothing and re-bakes nothing (see below).
 */
val LocalIconOverrides = staticCompositionLocalOf<Map<ComponentKey, IconAppearance>> { emptyMap() }

/**
 * What [component] looks like: its own appearance if it has been detached, otherwise the global default.
 *
 * **One expression, because two consumers resolve it and they must agree.** This one takes the layer set from it to
 * bake; the cell that draws the icon takes the plate and the zoom, which are live and so cannot be baked at all
 * (see `IconAppearance`). Resolved twice, an app could end up baked from its own recipe and plated from the default.
 */
@Composable
fun localAppearanceOf(component: ComponentKey): IconAppearance =
    LocalIconOverrides.current[component] ?: LocalIconAppearance.current

// TODO(B4): fallback bake resolution. The grid `AppCell` now passes a real sizePx (from IconMetrics), so this
//  is only for standalone / @Preview callers and any layout cell that doesn't yet pass a size (e.g. the list
//  row / folder cell). Remove once every cell passes sizePx. Not density-aware.
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
 * **The miss path deliberately names no dispatcher.** It used to `withContext(Dispatchers.Default)`, which put every
 * composed cell's bake on the pool sized to the core count — hundreds of coroutines taking every core and starving
 * the main thread. Where baking runs is one decision and it belongs to the thing that bakes: [IconRenderManager.get]
 * is a `suspend` function that moves onto its own bounded dispatcher and coalesces duplicate requests. A cell just
 * asks.
 *
 * **Which recipe an icon renders from is resolved in [layerSet]'s default**, and that placement is doing real work:
 * an *explicit* argument bypasses both the per-app override and the global default, which is exactly what the icon
 * studio's live preview needs — it draws a set the user is still editing and has not committed anywhere. Every
 * ordinary caller passes nothing and gets the resolution.
 *
 * **No cache invalidation is needed when a recipe changes, and calling it would be wrong.** [IconId] carries the
 * layer set, so an edited icon simply *is* a different key — it misses, bakes, and the stale bitmap ages out of the
 * LRU on its own. [IconRenderManager.invalidate] exists for the one input the key cannot see (an app replacing its
 * own artwork) and bumps [IconRenderManager.generation], which recomposes every icon on screen; spending that on a
 * change the key already handles would be work for nothing.
 *
 * **The plate is not drawn here**, and cannot be: it samples the wallpaper by screen position, where everything
 * this function does is keyed on a bitmap that has no position in it. It is the cell's, one layer out — see
 * `IconAppearance` for the whole of that boundary.
 */
@Composable
fun LauncherIcon(
    component: ComponentKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = DEFAULT_ICON_RENDER_PX,
    layerSet: IconLayerSet = localAppearanceOf(component).layerSet,
) {
    val manager = LocalIconRenderManager.current
    if (manager == null) {
        Box(modifier)
        return
    }

    // **The generation is part of the bake key**, which is what makes an app update actually change the icon on
    // screen. Evicting the cache alone would not: the three keys below are unchanged by an update, so nothing here
    // would re-run and the stale bitmap would stay until something else happened to recompose this cell. See
    // [IconRenderManager.generation] for why the key cannot capture this on its own.
    val generation = manager.generation
    var bitmap by remember(component, layerSet, sizePx, generation) {
        mutableStateOf(manager.peek(component, layerSet, sizePx))
    }
    LaunchedEffect(component, layerSet, sizePx, generation) {
        if (bitmap == null) bitmap = manager.get(component, layerSet, sizePx)
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
