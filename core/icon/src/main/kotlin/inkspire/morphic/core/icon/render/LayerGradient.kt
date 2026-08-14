package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.ShapeAnchor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a bloom's light sits and which way it runs, in pixels of a square box.
 *
 * Shared for [LayerTransform]'s and [LayerFilter]'s reason — two renderers, and an angle read in opposite
 * directions is a bug that looks like a design choice. It is small enough to be tempting to inline on each side,
 * which is exactly how the two would come to disagree about which way 90° runs.
 *
 * **The convention is clockwise from straight down**, so 0° is the top-to-bottom ramp a user expects by default and
 * 90° runs left-to-right.
 *
 * **Everything here is pure float arithmetic, deliberately.** [ShapeMask] had to split its decision from its matrix
 * assembly because `android.graphics.Matrix` stubs to a no-op in a JVM test; this file sidesteps that by never
 * reaching for one — a gradient is placed by handing its endpoints or its center to a platform constructor, so
 * there is nothing a matrix would buy and everything below can be tested.
 */
object LayerGradient {

    /**
     * The square a bloom is laid out in, and how it is turned — the answer [ShapeAnchor] decides between.
     *
     * A frame rather than a pair of anchors' worth of special cases: [ShapeAnchor.BOX] is the icon's own box
     * unturned, [ShapeAnchor.CONTENT] is the artwork's square carried by the layer's transform, and from there on
     * both take one code path. That is the same trick [ShapeMask] plays with its matrix, reached without one.
     *
     * @property sizePx the square's side. Its half is what a linear ramp spans and what a radius is a fraction of.
     * @property rotationDegrees how the frame is turned, clockwise — which a linear ramp adds to its own angle and a
     *   disc ignores, being round.
     */
    data class Frame(
        val centerX: Float,
        val centerY: Float,
        val sizePx: Float,
        val rotationDegrees: Float,
    ) {

        companion object {
            /** The icon's own box, unturned — what [ShapeAnchor.BOX] resolves to and what a composite has. */
            fun box(sizePx: Int): Frame =
                Frame(centerX = sizePx / 2f, centerY = sizePx / 2f, sizePx = sizePx.toFloat(), rotationDegrees = 0f)
        }
    }

    /**
     * Where [bloom]'s light is laid out: its own offset, applied inside whichever frame its anchor names.
     *
     * **A content-anchored bloom is placed in the artwork's frame and carried by the same transform the artwork
     * takes**, which is what makes them provably agree — the light cannot drift off the ink under zoom, rotation or
     * an offset, because it is not being positioned *alongside* the artwork. That is [ShapeMask]'s argument exactly,
     * and the two take the same [ShapeMask.InkFit] so a shape and a bloom on one layer land on the same square.
     *
     * **Unmeasured content degrades to the box** for [ShapeMask.inkFit]'s reason — only the app's own artwork is
     * measured, so a pack drawable, an imported image or a flat fill has no ink to sit on. The anchor still does its
     * other half there (following the transform), so the control is never inert.
     *
     * @param fit the square the layer's artwork occupies, from [ShapeMask.inkFit].
     * @param transform the layer's own transform, already resolved against [sizePx].
     */
    fun frameOf(bloom: LayerEffect.Bloom, fit: ShapeMask.InkFit, transform: LayerTransform, sizePx: Int): Frame {
        val base = when (bloom.anchor) {
            ShapeAnchor.BOX -> Frame.box(sizePx)

            ShapeAnchor.CONTENT -> {
                val half = sizePx / 2f
                // The ink's offset from the box center, scaled as the content is — then turned and displaced by the
                // rest of the transform, which is the same scale-rotate-translate order `LayerTransform.toMatrix`
                // applies, spelled out because there is no matrix here to apply it for us.
                val inkX = (fit.centerX - 0.5f) * sizePx * transform.zoom
                val inkY = (fit.centerY - 0.5f) * sizePx * transform.zoom
                val turned = turn(inkX, inkY, transform.rotationDegrees)
                Frame(
                    centerX = half + turned.first + transform.translateXPx,
                    centerY = half + turned.second + transform.translateYPx,
                    sizePx = fit.scale * sizePx * transform.zoom,
                    rotationDegrees = transform.rotationDegrees,
                )
            }
        }

        if (bloom.offsetX == 0f && bloom.offsetY == 0f) return base

        // In the frame's own units and turned with it, so "right" means the artwork's right rather than the
        // screen's — which is the whole point of anchoring to content, and would be lost by adding raw pixels here.
        val moved = turn(bloom.offsetX * base.sizePx, bloom.offsetY * base.sizePx, base.rotationDegrees)
        return base.copy(centerX = base.centerX + moved.first, centerY = base.centerY + moved.second)
    }

    /**
     * `[x0, y0, x1, y1]` for a ramp at [angleDegrees] across [frame].
     *
     * Both endpoints sit on the circle through the frame's edge midpoints, so the ramp covers the same distance at
     * every angle rather than being foreshortened as it turns.
     */
    fun endpoints(frame: Frame, angleDegrees: Float): FloatArray {
        val radians = (angleDegrees + frame.rotationDegrees) * Math.PI.toFloat() / 180f
        val half = frame.sizePx / 2f
        // Straight down at 0°: the direction vector is (sin, cos), which puts 90° along +x.
        val dx = sin(radians) * half
        val dy = cos(radians) * half
        return floatArrayOf(frame.centerX - dx, frame.centerY - dy, frame.centerX + dx, frame.centerY + dy)
    }

    /** Where a radial fill sits, in pixels — a center and how far it reaches. */
    data class Radial(val centerX: Float, val centerY: Float, val radiusPx: Float)

    /**
     * A disc at [frame]'s center reaching [radiusFraction] of the way to its **corners** — so 1 covers the frame
     * entirely and anything less leaves the corners at the outer stop.
     *
     * **Measured to the corners where [endpoints] measures to the edge midpoints**, and the difference is right
     * rather than an oversight: a ramp is asked to span the frame at *every* angle, so it takes the distance that is
     * the same in all directions, while a disc is asked how far out it goes, and "all the way" for a disc over a
     * square means the corners. Reading them the same way would leave four unlit corners at a radius of 1.
     *
     * The frame's rotation is ignored, a disc being round — the only place the two forms read [Frame] differently.
     *
     * Clamped above zero because `RadialGradient` rejects a non-positive radius outright, and a slider can always be
     * dragged to its floor. [LayerEffect.Bloom.isIdentity] already filters that case out before either renderer is
     * reached; this is the guard for a stored recipe that never went through it.
     */
    fun radial(frame: Frame, radiusFraction: Float): Radial {
        val reach = (frame.sizePx / 2f) * HalfDiagonal * radiusFraction
        return Radial(centerX = frame.centerX, centerY = frame.centerY, radiusPx = reach.coerceAtLeast(MinRadiusPx))
    }

    /**
     * The far end of a bloom: [argb]'s own color with no alpha left.
     *
     * **Not `Color.TRANSPARENT`**, and the difference is visible. Transparent black is `0x00000000`, so a ramp to it
     * drags the color toward black on the way out and a white bloom fades through gray — a dirty edge that looks
     * like a rendering fault. Holding the hue and dropping only the alpha fades the light out of the picture
     * instead. Shared rather than written twice because it is exactly the kind of detail one renderer would get
     * right and the other would not.
     */
    fun fadeOut(argb: Int): Int = argb and 0x00FFFFFF

    /** [x], [y] rotated clockwise by [degrees] about the origin — screen coordinates, so positive y is down. */
    private fun turn(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
        if (degrees == 0f) return x to y
        val radians = degrees * Math.PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        return (x * c - y * s) to (x * s + y * c)
    }

    /** How much further a square's corner is than its edge midpoint — `sqrt(2)`, as a multiple of the half-width. */
    private const val HalfDiagonal = 1.4142135f

    /** Small enough to be invisible, large enough that no platform constructor refuses it. */
    private const val MinRadiusPx = 0.01f
}
