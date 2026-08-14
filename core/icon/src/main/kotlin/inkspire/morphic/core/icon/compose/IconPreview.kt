package inkspire.morphic.core.icon.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import inkspire.morphic.core.icon.parse.ParsedIcon
import inkspire.morphic.core.icon.render.IconRenderer
import inkspire.morphic.core.model.icon.IconLayerSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

/**
 * One icon as the editor should see it: drawn live where that is possible, and **from the bake where it is not**.
 *
 * ## Why there are two
 *
 * [IconLayerStack] is the live path and is what makes a slider respond per frame — but it is Compose, and Compose's
 * only blur is `RenderEffect` (API 31+) and its only per-pixel route is AGSL (API 33+), against a `minSdk` of 26.
 * [IconRenderer] has neither limit: it owns a software bitmap, so a blur is a `BlurMaskFilter` and a displacement is
 * arithmetic over an `IntArray` at every API level.
 *
 * **Gating the effects that need those to API 31/33 was considered and rejected.** It would deny glow and drop
 * shadow to every device below Android 12 to solve a problem only the *editor* has — the home screen, which draws
 * from the bake already, could have shown them all along. So the studio previews from the bake instead, and
 * `LayerEffect.drawsLive` is what each effect answers to say which side it falls on.
 *
 * ## What falls back
 *
 * **The whole icon, never one layer** — see [IconLayerSet.drawsLive] for why a hybrid stack is the worst version of
 * the two-renderer problem rather than the cheapest version of this one.
 *
 * @param modifier must resolve to a **square**, for [IconLayerStack]'s reason: the layer geometry is defined in a
 *   square box, and a non-square node would distort every transform while still looking plausible.
 */
@Composable
fun IconPreview(
    icon: ParsedIcon,
    layerSet: IconLayerSet,
    modifier: Modifier = Modifier,
    customImage: (path: String) -> Drawable? = { null },
    packImage: (packPackage: String, drawableName: String?) -> Drawable? = { _, _ -> null },
) {
    if (layerSet.drawsLive) {
        IconLayerStack(
            icon = icon,
            layerSet = layerSet,
            modifier = modifier,
            customImage = customImage,
            packImage = packImage,
        )
    } else {
        BakedIconPreview(
            icon = icon,
            layerSet = layerSet,
            modifier = modifier,
            customImage = customImage,
            packImage = packImage,
        )
    }
}

/**
 * The baked half: composite off the main thread, hold the result, draw it.
 *
 * ## Draft first, then full — which is the throttle *and* the resolution split, in one mechanism
 *
 * Every new recipe is baked twice: once **downscaled**, immediately, and then once at full size. [collectLatest]
 * cancels an in-flight collector the moment a newer recipe arrives, so during a drag the draft keeps landing and the
 * full-size bake is cancelled before it starts — and when the finger stops, nothing newer arrives and the full-size
 * one completes and replaces it.
 *
 * **This is what "settled" actually means, rather than a proxy for it.** The plan proposed threading a
 * gesture-in-flight signal down from the studio (`onUpdate` without `onCommit`); building it showed none is needed,
 * because "no newer recipe has arrived" *is* the condition, and [collectLatest] already knows it. One mechanism
 * decides both what to skip and what resolution to skip it at, so the two cannot disagree.
 *
 * **No timer and no queue.** A drag emits far more frames than any bake can service; conflating them is the whole
 * requirement, and cancellation gives it without an interval anyone has to pick.
 *
 * **Deliberately not [inkspire.morphic.core.icon.render.IconRenderManager].** That cache is keyed on the resolved
 * layer set, which is exactly what changes on every frame of a drag — so a preview going through it would evict
 * every real icon on the device within seconds of a slider moving. The editor wants one slot, and it has one.
 *
 * The one thing not built is a *"working"* hint for a bake that runs genuinely long. It wants measuring against the
 * heaviest effect on the slowest device to hand, which is a number nobody can pick from a desk — and the draft is
 * what makes its absence survivable, since something always lands quickly.
 */
@Composable
private fun BakedIconPreview(
    icon: ParsedIcon,
    layerSet: IconLayerSet,
    modifier: Modifier,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
) {
    val context = LocalContext.current
    val renderer = remember(context) { IconRenderer(context) }

    BoxWithConstraints(modifier) {
        // Square, from the width — the same quantity every other derivation in the render package reads the box by.
        val sizePx = constraints.maxWidth

        var baked by remember { mutableStateOf<ImageBitmap?>(null) }

        // Keyed on nothing that changes per recipe: the flow below is what carries those, so the collector is not
        // restarted — restarting it would cancel the in-flight bake *and* lose the draft-then-full sequencing.
        LaunchedEffect(renderer, customImage, packImage) {
            snapshotFlow { Request(icon, layerSet, sizePx) }.collectLatest { request ->
                if (request.sizePx <= 0) return@collectLatest

                val draftPx = (request.sizePx * DraftScale).roundToInt().coerceAtLeast(MinDraftPx)
                // Only worth a first pass while it is meaningfully cheaper — on a thumbnail the draft *is* the
                // full size, and baking twice would be two bakes for one picture.
                if (draftPx < request.sizePx) {
                    baked = renderer.bake(request, draftPx, customImage, packImage)
                    // The suspension point cancellation needs to be observed at: without it a newly arrived recipe
                    // would not stop the full-size bake below from starting.
                    yield()
                }
                baked = renderer.bake(request, request.sizePx, customImage, packImage)
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            baked?.let { drawPreview(it) }
        }
    }
}

/**
 * What a bake is *of* — the three inputs that change what comes out, as one value.
 *
 * A holder rather than three `snapshotFlow` reads, so the flow emits once per meaningful change instead of three
 * times per frame in which any of them moved. Equality is the point: [IconLayerSet] is a data class all the way
 * down, so a recipe that came back to a value it already had produces no new emission at all.
 */
private data class Request(val icon: ParsedIcon, val layerSet: IconLayerSet, val sizePx: Int)

/** Composites [request] at [sizePx], off the main thread. */
private suspend fun IconRenderer.bake(
    request: Request,
    sizePx: Int,
    customImage: (path: String) -> Drawable?,
    packImage: (packPackage: String, drawableName: String?) -> Drawable?,
): ImageBitmap = withContext(Dispatchers.Default) {
    render(request.icon, request.layerSet, sizePx, packImage, customImage).asImageBitmap()
}

/**
 * Draws the baked bitmap over the whole node, scaled if it is a draft.
 *
 * [FilterQuality.Low] deliberately: a draft is going to be soft whatever is done to it, and bilinear is what makes
 * it read as *soft* rather than as blocky — which is the difference between a preview that looks unfinished and one
 * that looks broken.
 */
private fun DrawScope.drawPreview(bitmap: ImageBitmap) {
    drawImage(
        image = bitmap,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * How large a draft is, against the real thing.
 *
 * A quarter of the side is a sixteenth of the pixels, and the effects this exists for are all O(n) in them — so a
 * draft lands in roughly a sixteenth of the time, which is what keeps a drag moving. **This is the one number to
 * tune on device**: too large and the drag stutters, too small and the picture being judged is not the picture.
 */
private const val DraftScale = 0.25f

/** Below this a draft has no detail left to judge, so there is nothing to gain by going smaller. */
private const val MinDraftPx = 48
