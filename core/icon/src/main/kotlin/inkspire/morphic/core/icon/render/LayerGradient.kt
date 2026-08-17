package inkspire.morphic.core.icon.render

import inkspire.morphic.core.model.icon.LayerEffect
import inkspire.morphic.core.model.icon.ContentAnchor
import kotlin.math.absoluteValue
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
     * The square a bloom is laid out in, and how it is turned — the answer [ContentAnchor] decides between.
     *
     * A frame rather than a pair of anchors' worth of special cases: [ContentAnchor.BOX] is the icon's own box
     * unturned, [ContentAnchor.CONTENT] is the artwork's square carried by the layer's transform, and from there on
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

        /**
         * This frame with its center displaced by [x], [y] — fractions of the frame, **turned with it**, so "right"
         * means the artwork's right rather than the screen's under [ContentAnchor.CONTENT].
         *
         * Separate from [frameOf] because not every effect placed against a frame has a position of its own: a bloom
         * does, a gloss is placed by its angle. Folding it in would have meant the second one passing zeros.
         */
        fun movedBy(x: Float, y: Float): Frame {
            if (x == 0f && y == 0f) return this
            val moved = turn(x * sizePx, y * sizePx, rotationDegrees)
            return copy(centerX = centerX + moved.first, centerY = centerY + moved.second)
        }

        companion object {
            /** The icon's own box, unturned — what [ContentAnchor.BOX] resolves to and what a composite has. */
            fun box(sizePx: Int): Frame =
                Frame(centerX = sizePx / 2f, centerY = sizePx / 2f, sizePx = sizePx.toFloat(), rotationDegrees = 0f)
        }
    }

    /**
     * The frame [anchor] names: the icon's box, or the layer's artwork carried by its own transform.
     *
     * **Content-anchored light is placed in the artwork's frame and carried by the same transform the artwork
     * takes**, which is what makes them provably agree — it cannot drift off the ink under zoom, rotation or an
     * offset, because it is not being positioned *alongside* the artwork. That is [ShapeMask]'s argument exactly,
     * and the two take the same [ShapeMask.InkFit] so a shape and a bloom on one layer land on the same square.
     *
     * **Unmeasured content degrades to the box** for [ShapeMask.inkFit]'s reason — only the app's own artwork is
     * measured, so a pack drawable, an imported image or a flat fill has no ink to sit on. The anchor still does its
     * other half there (following the transform), so the control is never inert.
     *
     * @param fit the square the layer's artwork occupies, from [ShapeMask.inkFit].
     * @param transform the layer's own transform, already resolved against [sizePx].
     */
    fun frameOf(anchor: ContentAnchor, fit: ShapeMask.InkFit, transform: LayerTransform, sizePx: Int): Frame =
        when (anchor) {
            ContentAnchor.BOX -> Frame.box(sizePx)

            ContentAnchor.CONTENT -> {
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

    /** Where [bloom]'s light is laid out: its own offset, applied inside whichever frame its anchor names. */
    // **The resolved placement, not the raw offsets** — a ramp and a disc are placed by different fields now, and
    // `LayerEffect.Bloom.placementX` is what answers for both. It has to be the model's arithmetic rather than a
    // branch here: the panel used to do the ramp's projection itself and store the result in the disc's own point,
    // which is exactly how the two falloffs came to overwrite each other.
    fun frameOf(bloom: LayerEffect.Bloom, fit: ShapeMask.InkFit, transform: LayerTransform, sizePx: Int): Frame =
        frameOf(bloom.anchor, fit, transform, sizePx).movedBy(bloom.placementX, bloom.placementY)

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
     * Where a gloss's sheen sits: a disc whose **edge** is the light's boundary, and which side of it is lit.
     *
     * **A sheen is an arc, and an arc is a circle seen close up.** The whole of `LayerEffect.Gloss.curve` is how big
     * that circle is relative to the frame: a huge one crosses the frame as very nearly a straight edge, a small one
     * as a pronounced bow. So there is no second mechanism here — a gloss is the radial fill a bloom already uses,
     * with its center pushed outside the frame so only the *rim* of it lands on the artwork.
     *
     * @property stops the four positions the [colorsOf] colours sit at, ascending. Four rather than two because the
     *   transition has to stay the same visual width whatever the circle's size — with two stops spanning the whole
     *   radius, a large circle would leave the frame in an almost flat part of the ramp and the sheen would fade out
     *   as the curve was flattened, which is a control undoing itself.
     * @property litInside whether the light is inside the arc or outside it, which is what the sign of the curve
     *   flips. Both keep the light on the side the angle names; what changes is which way the boundary bows.
     */
    data class Sweep(
        val centerX: Float,
        val centerY: Float,
        val radiusPx: Float,
        val stops: List<Float>,
        val litInside: Boolean,
    ) {

        /**
         * The four colours, in [stops] order — [argb] on the lit side, its own fade on the other.
         *
         * A member rather than something each renderer assembles, because the order is the whole of what [litInside]
         * means: getting it backwards draws a perfectly plausible sheen with the light on the wrong side, on the one
         * axis neither renderer can check against the other.
         */
        fun colorsOf(argb: Int): IntArray {
            val fade = fadeOut(argb)
            return if (litInside) {
                intArrayOf(argb, argb, fade, fade)
            } else {
                intArrayOf(fade, fade, argb, argb)
            }
        }
    }

    /**
     * A sheen across [frame], struck from [angleDegrees] with its boundary bowed by [curve].
     *
     * [curve] runs −1..1 and does two things at once, which is what makes it one control rather than two: its
     * **magnitude** is how tightly the boundary is curved (0 is very nearly a straight edge), and its **sign** is
     * which way it bows — the lit region bulging outward, or the arc cutting into it. The light stays on the side
     * [angleDegrees] names either way, so the sign is never mistakable for a 180° turn.
     *
     * The angle follows the frame's rotation for [endpoints]' reason: a sheen anchored to the artwork has to turn
     * with it, or a rotated layer would be lit from a direction the user never chose.
     */
    fun sweep(frame: Frame, angleDegrees: Float, curve: Float): Sweep {
        val half = frame.sizePx / 2f
        // Tight at full curve, and `FlatSweepReach` times the frame at none — far enough out that the rim reads as a
        // straight edge without the arithmetic ever reaching for an infinite radius.
        val radius = half * (1f + FlatSweepReach * (1f - curve.absoluteValue.coerceAtMost(1f)))

        // `endpoints`' own direction vector, which points *away* from where its first colour sits — so the lit side
        // is the negative one, and 0° means light from the top in both.
        val radians = (angleDegrees + frame.rotationDegrees) * Math.PI.toFloat() / 180f
        val litInside = curve >= 0f
        // Toward the light when the lit side is inside the disc, away from it when the arc bows the other way.
        val push = if (litInside) -radius else radius
        val centerX = frame.centerX + sin(radians) * push
        val centerY = frame.centerY + cos(radians) * push

        // The gradient reaches one half-frame past the circle's rim, which puts the frame's far edge at 1 and the
        // boundary itself at `radius / span` — so the soft band below is a constant share of the frame at every
        // curve rather than shrinking as the circle grows.
        val span = radius + half
        val boundary = radius / span
        val soft = (half / span) * SweepSoftness

        return Sweep(
            centerX = centerX,
            centerY = centerY,
            radiusPx = span.coerceAtLeast(MinRadiusPx),
            stops = listOf(
                0f,
                (boundary - soft).coerceIn(0f, 1f),
                (boundary + soft).coerceIn(0f, 1f),
                1f,
            ),
            litInside = litInside,
        )
    }

    /**
     * Where a two-stop ramp's stops sit, ascending, as fractions of its own extent: the end of the untouched region,
     * and the point at which the effect reaches full strength.
     *
     * Below the first the gradient clamps to nothing and above the second to everything, which is why two stops are
     * enough for what reads as three regions.
     *
     * **Here rather than on [LayerProgressiveBlur], which is where it was written, because a vignette asks the
     * identical question** — how much of the frame stays clear, and how far past that the colour takes to arrive.
     * Extract-on-the-second-consumer, and this file is already the one that answers "where does a ramp sit"; the
     * blur's own name would otherwise be imported by an effect that is not one.
     *
     * **Separated by at least a hair**, because a gradient with two coincident stops is undefined — and a softness
     * of zero is a legitimate request for a hard edge, not an invalid one.
     *
     * **[clear] is capped short of the end to leave room for that hair**, which is not fussiness: without it a clear
     * area of 1 asks for a band from 1.001 to 1, and `coerceIn` throws outright on an inverted range. A slider
     * dragged to its own top would have taken the bake down rather than drawing an unaffected icon.
     *
     * @param clear how much of the ramp's extent is left alone, 0..1. A progressive blur's sharp area; a vignette's
     *   clear middle, which is its reach read from the other end.
     * @param softness how far past [clear] the ramp takes to reach full, as a fraction of the same extent.
     */
    fun rampStops(clear: Float, softness: Float): FloatArray {
        val start = clear.coerceIn(0f, 1f - MinBand)
        val end = (start + softness.coerceAtLeast(0f)).coerceIn(start + MinBand, 1f)
        return floatArrayOf(start, end)
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

    /**
     * How far a *flat* sweep's circle sits from the frame, in half-frames — the "straight edge" end of the curve.
     *
     * At seven the boundary's sag across the whole frame is about a sixteenth of a half-frame, which reads as a
     * straight line while keeping the arithmetic finite. Larger buys nothing visible and starts losing float
     * precision in the stop positions.
     */
    private const val FlatSweepReach = 7f

    /** How wide a sweep's transition is, as a share of the half-frame. Soft enough to read as light, not as a cut. */
    private const val SweepSoftness = 0.6f

    /** Small enough to be invisible, large enough that no platform constructor refuses it. */
    private const val MinRadiusPx = 0.01f

    /** The narrowest a [rampStops] transition may be — a hard edge, without the two stops actually coinciding. */
    private const val MinBand = 0.001f
}
