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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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
 * Every new recipe is baked twice: once **downscaled**, immediately, and then — once nothing newer has arrived for
 * [SettleMs] — once at full size. The bake runs in a `LaunchedEffect` keyed on the recipe, so a newer one cancels
 * the older outright: during a drag the draft keeps landing and every full-size pass is cancelled in its wait, and
 * when the finger stops the wait elapses and the sharp one replaces the draft.
 *
 * **This is what "settled" actually means, rather than a proxy for it.** The plan proposed threading a
 * gesture-in-flight signal down from the studio (`onUpdate` without `onCommit`); none is needed, because "no newer
 * recipe has arrived" *is* the condition and a keyed effect already knows it. One mechanism decides both what to
 * skip and what resolution to skip it at, so the two cannot disagree.
 *
 * **There is one timer, and it was not always needed.** The original had none: cancellation alone conflated a
 * drag's frames, because the full-size bake was slow enough that a newer recipe always killed it first. Once the
 * bake got fast — rows split across cores, a capped preview size — it began *finishing* between two frames of a
 * slider, so the preview alternated soft draft and sharp bake several times a second. That reads as flashing. The
 * wait restores the property the speed took away, and it is a different question from the cancellation around it:
 * that asks "has something newer arrived?", this asks "is the user still going?".
 *
 * **No queue, still.** A drag emits far more frames than any bake can service, and conflating them is the whole
 * requirement.
 *
 * **And cancellation only became real when [IconRenderer.render] learned to cooperate with it.** This design was
 * correct and inert: coroutine cancellation is cooperative, so a bake that never checks runs to the end however
 * dead its coroutine is. Every frame of a drag therefore queued a full draft *and* a full-size bake, all of which
 * completed in turn — which is why the preview arrived in a backlog seconds after the finger lifted rather than
 * following it, and why the studio's bakes starved every other icon sharing the dispatcher. The lesson is worth
 * keeping: a `LaunchedEffect` key gives you the *intent* to abandon work, never the fact of it.
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
        //
        // **Quantised, and that is what makes the canvas survive a pinch.** The node's size is a *gesture* here: a
        // two-finger zoom changes it every frame, and since the size is part of the request, every frame threw away
        // the bake in flight and started another — so a pinch produced a preview that was permanently mid-bake and
        // never settled on anything. Rounding up to [BakeQuantum] means a zoom crosses a bucket occasionally instead
        // of continuously, and the draw scales whatever is held to whatever the node currently is, which it did
        // anyway. It costs at most one bucket's worth of extra pixels and never any sharpness, since rounding is up.
        val sizePx = bakeSizeFor(constraints.maxWidth)

        val request = Request(icon, layerSet, sizePx, customImage, packImage)
        var baked by remember { mutableStateOf<ImageBitmap?>(null) }

        // **The request is the key, and the restart is the throttle.** `LaunchedEffect` cancels the running coroutine
        // when its key changes, which is exactly the conflation this needs: a newer recipe kills the bake in flight
        // and starts its own. `Request` is a data class, so a recomposition that changes nothing compares equal and
        // nothing restarts.
        //
        // **This was a `snapshotFlow` + `collectLatest` and did not work**, which is worth keeping written down: that
        // block read `layerSet` and `sizePx` as plain captured parameters, and `snapshotFlow` only re-runs its block
        // when *snapshot state* it read is invalidated. Having read none, it emitted once and never again — so the
        // preview baked whatever recipe was current when the icon first fell back and then sat there. The rule is
        // that a `snapshotFlow` over captured values is a one-shot; keying an effect needs no such care.
        LaunchedEffect(renderer, request) {
            if (request.sizePx !in 1..MaxBakePx) return@LaunchedEffect

            // **Two caps, not one**, because the settled bake and the draft are paid at different moments — see
            // [MaxPreviewPx] and [MaxDraftPx]. The settled one may be large enough to survive being zoomed into;
            // the draft stays small however large that is, so a drag costs the same whatever the canvas is doing.
            val fullPx = request.sizePx.coerceAtMost(MaxPreviewPx)
            val draftPx = (fullPx * DraftScale).roundToInt()
                .coerceAtLeast(MinDraftPx)
                .coerceAtMost(MaxDraftPx)
                .coerceAtMost(fullPx)
            // Only worth a first pass while it is meaningfully cheaper — on a thumbnail the draft *is* the full
            // size, and baking twice would be two bakes for one picture.
            if (draftPx < fullPx) {
                baked = renderer.bake(request, draftPx)
                // **The settle, and it has to be a wait rather than a `yield`.** A yield was enough while the
                // full-size bake was slow: a newer recipe always arrived first and cancelled it, so a drag showed
                // drafts and nothing else. Once the bake got fast enough to finish *between* two frames of a slider,
                // the preview began alternating soft draft and sharp full several times a second — which reads as
                // flashing, and is the resolution changing back and forth rather than anything being redrawn wrongly.
                //
                // Waiting first makes "settled" mean what it says: during a drag every full-size pass is cancelled
                // here and only drafts are shown, so the softness is *constant* and the picture is stable. The
                // suspension is also still where cancellation is observed, which is what the yield was for.
                delay(SettleMs.milliseconds)
            }
            baked = renderer.bake(request, fullPx)
        }

        Canvas(Modifier.fillMaxSize()) {
            baked?.let { drawPreview(it) }
        }
    }
}

/**
 * What a bake is *of* — everything that changes what comes out, as one value.
 *
 * **The two image lambdas are in here deliberately.** They are `remember`ed by the studio against the images it has
 * decoded, so their identity changes exactly when those arrive — and a picked image whose bytes land *after* the
 * recipe naming it would otherwise never be baked in, leaving a missing layer that nothing would ever redraw.
 *
 * A holder rather than several `snapshotFlow` reads, so the flow emits once per meaningful change rather than once
 * per input that moved. Equality is the point: [IconLayerSet] is a data class all the way down, so a recipe dragged
 * back to a value it already had produces no emission at all.
 */
private data class Request(
    val icon: ParsedIcon,
    val layerSet: IconLayerSet,
    val sizePx: Int,
    val customImage: (path: String) -> Drawable?,
    val packImage: (packPackage: String, drawableName: String?) -> Drawable?,
)

/** Composites [request] at [sizePx], off the main thread. */
private suspend fun IconRenderer.bake(request: Request, sizePx: Int): ImageBitmap =
    withContext(Dispatchers.Default) {
        render(
            icon = request.icon,
            layerSet = request.layerSet,
            sizePx = sizePx,
            packImage = request.packImage,
            customImage = request.customImage,
        ).asImageBitmap()
    }

/**
 * The size to bake at for a node [nodePx] wide: capped, then rounded **up** to [BakeQuantum].
 *
 * Rounding up rather than to the nearest, so the held bitmap is never smaller than the node it will be stretched
 * across — which would turn a bucket boundary into a visible softening.
 */
private fun bakeSizeFor(nodePx: Int): Int {
    if (nodePx !in 1..MaxBakePx) return nodePx
    val capped = nodePx.coerceAtMost(MaxPreviewPx)
    return ((capped + BakeQuantum - 1) / BakeQuantum * BakeQuantum).coerceAtMost(MaxPreviewPx)
}

/**
 * Draws the baked bitmap over the whole node, scaled to it.
 *
 * **Scaled to the node and not to itself**, which is what lets the bake size be quantised and capped independently
 * of what is on screen: what is held may be a draft, a bucket larger than the node, or a bitmap baked before the
 * last pinch frame, and all three are stretched to fit rather than drawn at whatever size they happen to be.
 *
 * A node with no size is skipped rather than drawn into: a zero destination is not a picture, and asking the
 * platform to scale into one during a frame where layout has not settled is how a stale bitmap ends up somewhere it
 * was never meant to be.
 *
 * [FilterQuality.Low] deliberately: a draft is going to be soft whatever is done to it, and bilinear is what makes
 * it read as *soft* rather than as blocky — which is the difference between a preview that looks unfinished and one
 * that looks broken.
 */
private fun DrawScope.drawPreview(bitmap: ImageBitmap) {
    val width = size.width.roundToInt()
    val height = size.height.roundToInt()
    if (width <= 0 || height <= 0) return

    drawImage(
        image = bitmap,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * How large a draft is, against the settled bake.
 *
 * A sixth of the side is a thirty-sixth of the pixels, and the effects this exists for are all O(n) in them — so a
 * draft lands in roughly a thirty-sixth of the time, which is what keeps a drag moving. **This is the one number to
 * tune on device**: too large and the drag stutters, too small and the picture being judged is not the picture.
 */
private const val DraftScale = 0.16f

/**
 * The largest a *preview* is baked at, however large the node showing it.
 *
 * **A cap on work, not on quality, and it is scoped to exactly the icons that need one.** This whole path runs only
 * for a recipe the live renderer cannot draw — which is to say one carrying a blur, a halo or a per-pixel effect —
 * and every one of those is low-frequency by nature: a grain field, a blurred halo and a dot grid all look the same
 * scaled up, because none of them *has* detail at the pixel. What they do have is a cost per pixel, and a studio
 * canvas on a modern phone is over four hundred thousand of them.
 *
 * **The number is a trade, not a threshold, and it is where zoom meets sharpness.** This path renders the whole
 * square and never just the part on screen, so a zoomed-in preview cannot be made sharp cheaply: matching a node
 * two canvases wide pixel for pixel is several million of them for a picture that is mostly off screen. It was
 * **512**, chosen while the node could be no larger than the canvas, and `requiredSize` — which let a zoomed bound
 * genuinely outgrow the canvas — quietly turned that into a fivefold upscale. Under the zoom ceiling it now sits
 * beneath, the worst upscale is about two.
 *
 * The surfaces are untouched by this: a home icon bakes at its own real size through `IconRenderManager`, which is
 * far below the cap anyway. The one thing given up is a preview of a **sharp** effect at canvas resolution, and
 * there is no such thing here — a sharp recipe draws live and never reaches this file.
 */
private const val MaxPreviewPx = 1024

/**
 * The largest a **draft** is, whatever [MaxPreviewPx] allows the settled bake.
 *
 * **Capped separately, because the two are paid at different moments.** The settled bake happens once, after the
 * finger has stopped, and can afford to be large; a draft happens on every frame of a drag and its whole job is to
 * be cheap. Deriving one from the other is what made raising the settled cap slow the *drag* down — which is the
 * thing the draft exists to protect.
 */
private const val MaxDraftPx = 128

/**
 * How coarsely the bake size follows the node's.
 *
 * The node's width is a gesture — a pinch moves it every frame — and the bake size is part of the request, so
 * following it exactly meant every frame of a zoom cancelled the bake in flight and started another. Sixty-four
 * puts a handful of buckets across the whole zoom range, which is few enough that a pinch settles inside one and
 * fine enough that the held bitmap is never much larger than what it is drawn into.
 */
private const val BakeQuantum = 64

/**
 * How long a recipe must go unchanged before the full-size bake is worth starting.
 *
 * **Long enough to outlast the gap between two frames of a slider drag**, which is the whole job: below that the
 * full-size pass lands between emissions and the preview alternates between a soft draft and a sharp bake several
 * times a second. Short enough that lifting a finger feels like the picture sharpening rather than like waiting.
 *
 * The one timer in this file, and it earns its place because it answers a different question from the cancellation
 * around it: that one asks *"has something newer arrived?"*, and this asks *"is the user still going?"* — which
 * nothing else here can see, now that the bake is fast enough to finish inside a gesture.
 */
private const val SettleMs = 140L

/** Below this a draft has no detail left to judge, so there is nothing to gain by going smaller. */
private const val MinDraftPx = 48

/**
 * A bound on what will be allocated, against an unbounded constraint.
 *
 * `BoxWithConstraints` reports `Constraints.Infinity` for a width nothing has bounded — inside a horizontal
 * scroller, say — and a bitmap of that side is an immediate out-of-memory rather than a slow preview. No studio
 * surface is unbounded today; this is the guard for the one that is not, since the failure is fatal and silent
 * about its cause.
 */
private const val MaxBakePx = 4096
