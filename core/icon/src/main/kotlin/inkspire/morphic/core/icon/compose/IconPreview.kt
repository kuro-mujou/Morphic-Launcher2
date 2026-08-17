package inkspire.morphic.core.icon.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * Every recipe is baked twice: once **downscaled**, immediately, and then — once nothing newer has arrived for
 * [SettleMs] — once at full size. A loop takes the newest recipe, drafts it, waits, and sharpens it.
 *
 * **The draft is never abandoned; only the full-size pass is.** That asymmetry is the correction this file's own
 * design needed, and it is worth stating plainly because the old shape looked obviously right: the bake lived in a
 * `LaunchedEffect` *keyed on the recipe*, so anything newer cancelled whatever was running. Conflating by
 * cancellation works only while the work is shorter than the gap between two emissions — and nothing was checking
 * that. A slider thumb emits per pointer event, about seven milliseconds apart on a 144Hz phone, so for any effect
 * whose draft costs more than that, **every draft was killed before it finished and the preview did not move at all
 * until the finger lifted.** It was reported as the preview freezing on a drag while the +/- buttons worked, which
 * sounds like two bugs and is one: a discrete step leaves a gap long enough for a draft to land.
 *
 * Letting the draft finish and *then* taking whatever the newest recipe is gives the property actually wanted —
 * the preview updates as fast as the machine can draft, never slower and never not at all — while still coalescing,
 * since everything emitted mid-draft collapses into one value. The full-size pass keeps the old behaviour, because
 * there the old argument holds: it is slow, it is superseded the moment the recipe moves, and a stale sharp icon is
 * worth nothing.
 *
 * **The settle is a second, different question.** The loop asks "has something newer arrived?"; [SettleMs] asks "is
 * the user still going?" — which nothing else can see, and without which a full-size pass fast enough to finish
 * between two slider frames makes the preview alternate soft and sharp several times a second. That reads as
 * flashing.
 *
 * **No queue.** A drag emits far more frames than any bake can service, and conflating them is the whole requirement.
 *
 * **And abandoning only became real when [IconRenderer.render] learned to cooperate with it.** Cancellation is
 * cooperative, so a bake that never checks runs to the end however dead its coroutine is. Every frame of a drag
 * therefore queued a full draft *and* a full-size bake, all of which completed in turn — which is why the preview
 * once arrived in a backlog seconds after the finger lifted, and why the studio's bakes starved every other icon
 * sharing the dispatcher. The lesson is worth keeping: cancelling a coroutine gives you the *intent* to abandon
 * work, never the fact of it.
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

        // **What the pipeline is fed, rather than what it is keyed on** — see the loop below for why that distinction
        // is the whole of this. A `StateFlow` conflates by equality, so a recomposition that changes nothing emits
        // nothing, which is what keying on the request used to buy.
        val latest = remember { MutableStateFlow(request) }
        SideEffect { latest.value = request }

        // **The draft runs to completion; only the full-size pass is abandoned.** This used to be `LaunchedEffect`
        // keyed on the request, so *every* bake was cancelled the moment a newer recipe arrived — and that starves
        // outright as soon as one draft costs more than the gap between two emissions. A slider thumb emits per
        // pointer event, which on a 144Hz phone is about seven milliseconds apart, so on a heavy effect **no draft
        // ever finished and the preview did not move at all until the finger lifted**. Pressing the +/- buttons
        // worked, and looked like a different bug: a step leaves a gap long enough for a draft to land.
        //
        // Conflation by cancellation only works while the work is shorter than the interval, and nothing was checking
        // that. Finishing the draft and *then* taking whatever the newest recipe is gives the property actually
        // wanted — the preview updates as fast as the machine can draft, never slower and never not at all — and it
        // still coalesces, because everything emitted while a draft is in flight collapses into one value.
        //
        // The full-size pass keeps the old behaviour, because there the old reasoning holds: it is slow, it is
        // superseded the instant the recipe moves, and a stale sharp icon is worth nothing.
        //
        // **This was a `snapshotFlow` + `collectLatest` before either**, which is worth keeping written down: that
        // block read `layerSet` and `sizePx` as plain captured parameters, and `snapshotFlow` only re-runs its block
        // when *snapshot state* it read is invalidated. Having read none, it emitted once and never again.
        LaunchedEffect(renderer) {
            var rendered: Request? = null
            while (true) {
                val next = latest.first { it != rendered }
                rendered = next
                if (next.sizePx !in 1..MaxBakePx) continue

                // **The draft has a size of its own rather than a fraction of the settled one** — see [DraftPx].
                // The settled bake may be large enough to survive being zoomed into; the draft stays fixed however
                // large that is, so a drag costs the same whatever the canvas is doing.
                val fullPx = next.sizePx.coerceAtMost(MaxPreviewPx)
                val draftPx = DraftPx.coerceAtMost(fullPx)

                // Only worth a first pass while it is meaningfully cheaper — on a thumbnail the draft *is* the full
                // size, and baking twice would be two bakes for one picture.
                if (draftPx < fullPx) {
                    baked = renderer.bake(next, draftPx)
                    // **The settle: is the user still going?** A different question from "has something newer
                    // arrived", which is what the loop above answers. Without the wait, a full-size pass that is fast
                    // enough to finish between two slider frames makes the preview alternate soft and sharp several
                    // times a second, which reads as flashing.
                    if (withTimeoutOrNull(SettleMs) { latest.first { it != rendered } } != null) continue
                }

                // Abandoned outright if the recipe moves under it — the one place cancellation is still right.
                coroutineScope {
                    val full = launch { baked = renderer.bake(next, fullPx) }
                    val superseded = launch {
                        latest.first { it != rendered }
                        full.cancel()
                    }
                    full.join()
                    superseded.cancel()
                }
            }
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
 * **The** size a draft is baked at — the one number the cheap pass is tuned by.
 *
 * It was three interacting numbers (a fraction of the settled bake, a floor, and a cap) and they **contradicted each
 * other**: the floor asked for 144 and the cap immediately pulled it back to 128, so the floor was dead code and the
 * paragraph explaining it was false. One value cannot disagree with itself.
 *
 * **Fixed rather than a fraction of the settled bake**, because the two are paid at different moments: the settled
 * pass happens once, after the finger has stopped, and can afford to be large, while a draft happens on every step of
 * a drag and its whole job is to be cheap. Deriving one from the other is what made raising the settled cap slow the
 * *drag* down — the thing the draft exists to protect.
 *
 * **And 144 rather than as small as possible, because a draft can be too small to be true.** It is roughly the
 * smallest bitmap any surface bakes an icon into (a home cell's icon, 48dp at 3× density), which buys one property
 * worth the pixels: *the draft can represent anything a surface can*. Below that an effect can cease to exist rather
 * than merely soften — `LayerGrain`'s lattice has a four-pixel floor, so at a small enough draft a whole stretch of
 * its size slider clamps to one cell and every draft in that range comes back **identical**, which on a device reads
 * as the preview having frozen. It was reported exactly that way.
 *
 * Measured on a Snapdragon-class phone, one grain bake: **~15ms at 128px, ~19ms at 144, 332ms at 768**. So the
 * invariant costs about four milliseconds a draft, and the settled pass is what a large recipe really costs.
 *
 * A node whose settled bake is already smaller — a layer tile — is left alone by the `coerceAtMost(fullPx)` at the
 * call site: it bakes once, at its real size, rather than drafting larger than the thing it is drafting for.
 *
 * **`internal` so a test can hold an effect to it**, which is the shape the invariant above needed. It was stated here
 * as prose and nowhere else, and prose cannot fail: `LayerGrain`'s ramp was later pushed finer than this size can
 * draw, which made the bottom of its slider inert *under the finger* — the very defect this floor had just been raised
 * to fix. `LayerGrainTest` reads this value rather than repeating 144, so moving one without the other now fails a
 * test instead of a device.
 */
internal const val DraftPx = 144

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


/**
 * A bound on what will be allocated, against an unbounded constraint.
 *
 * `BoxWithConstraints` reports `Constraints.Infinity` for a width nothing has bounded — inside a horizontal
 * scroller, say — and a bitmap of that side is an immediate out-of-memory rather than a slow preview. No studio
 * surface is unbounded today; this is the guard for the one that is not, since the failure is fatal and silent
 * about its cause.
 */
private const val MaxBakePx = 4096
