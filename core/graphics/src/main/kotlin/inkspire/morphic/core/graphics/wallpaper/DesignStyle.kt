package inkspire.morphic.core.graphics.wallpaper

import inkspire.morphic.core.model.wallpaper.DesignParams
import kotlin.math.roundToInt

/**
 * Which of [DesignParams]' knobs a design answers to, and what that design calls them — the studio's *Style* panel,
 * described by the generator that reads them.
 *
 * **Declared by the generator rather than by the panel, because a knob the panel offers and the generator ignores
 * fails silently.** There is no error and no wrong picture: the finger drags, the wallpaper re-renders, and nothing
 * moves. Seven of the designs have no organic axis and read no [DesignParams.irregularity] at all; twenty read no
 * [DesignParams.variant]. A table of that living in the UI would be a second statement of a fact only the generator
 * knows, which is the divergence this codebase keeps rediscovering — so the fact is stated once, in the file that
 * reads the param.
 *
 * **The [AmountKnob.Count] range is the same reason, made structural.** A count slider has to offer the generator's
 * *own* range (`4..22` bands, `300..1200` strokes), and those bounds were `private const`s inside each generator.
 * Retyping them in the panel would put the two a rename apart from disagreeing — and a slider whose top half all
 * resolves to the same count is not visibly wrong either. So the knob **is** the mapping: the generator asks it for
 * the count, the panel asks it for the range, and there is one arithmetic.
 *
 * @property amount the design's "how many" knob, driven by [DesignParams.density] — null where the design has no
 *   notion of amount at all (a plain gradient).
 * @property scale the design's own word for its size knob (*Spread*, *Size*, *Margin*), driven by
 *   [DesignParams.scale] — null where the design's elements have no size to set. A label rather than a knob, for
 *   [irregularity]'s reason: the control is always the same `0..1` fraction and only the word changes.
 * @property irregularity the design's own word for its organic-noise knob (*Curl*, *Scatter*, *Refraction*), driven by
 *   [DesignParams.irregularity] — null where the design is rigid by nature and ignores it. **A label rather than a
 *   knob**, because unlike the amount this control is the same everywhere: a `0..1` fraction, rigid to chaotic. Only
 *   the word changes.
 * @property depth the design's own word for its out-of-plane knob (*Relief*, *Shadow*, *Refraction*), driven by
 *   [DesignParams.depth] — null for the flat designs, which is nearly all of them. A label, for [irregularity]'s
 *   reason.
 * @property depthScale the design's own word for the *size* of whatever [depth] counts (*Orb size*), driven by
 *   [DesignParams.depthScale] — null wherever [depth] is a continuous amount rather than a count of somethings, which
 *   is most designs that read [depth] at all. A label, for [irregularity]'s reason.
 * @property roundness the design's own word for its corner-softness knob (*Roundness*, *Corner radius*), driven by
 *   [DesignParams.roundness] — null wherever the design has no corners to soften, which is nearly everywhere. A label,
 *   for [irregularity]'s reason.
 * @property rotation the design's own word for its orientation knob (*Turn*, *Rotation*, *Direction*), driven by
 *   [DesignParams.rotation] — null wherever the design has no orientation to set, which includes a design whose shape
 *   is a circle. A label, for [irregularity]'s reason.
 * @property colorLayout the design's chooser for *where* its palette's stops go (*Colors*, *Distribution*), driven by
 *   [DesignParams.colorLayout] — null for the designs that spend their palette one way. Separate from [variant]
 *   because a design can want both at once, which is what brought the field into being; see [DesignParams].
 * @property finish the design's chooser for *how* its marks are painted (*Mode*, *Blend mode*), driven by
 *   [DesignParams.finish] — null for the designs that paint one way. Separate from [variant] because a design can
 *   want both at once, which is the argument [colorLayout] arrived on; see [DesignParams].
 * @property variant the design's sub-look chooser, driven by [DesignParams.variant] — null for the twenty designs
 *   that have a single look.
 */
data class DesignStyle(
    val amount: AmountKnob? = null,
    val scale: String? = null,
    val irregularity: String? = null,
    val depth: String? = null,
    val depthScale: String? = null,
    val roundness: String? = null,
    val rotation: String? = null,
    val variant: VariantKnob? = null,
    val finish: VariantKnob? = null,
    val colorLayout: VariantKnob? = null,
)

/**
 * A design's "how many" knob — the [DesignParams.density] field, in the units that design actually thinks in.
 *
 * Two kinds, because the designs genuinely have two: nearly every one resolves density to a **count** of something it
 * then draws, and one (the plasma's frequency) has no countable element at all and is simply a continuous scale.
 */
sealed interface AmountKnob {

    /** What the design calls this knob — the tab's name, and the word beside its number. */
    val label: String

    /**
     * Density read as a count of somethings, over the range the generator draws between.
     *
     * The panel offers exactly [range] and the generator resolves exactly [at], so the slider's steps and the
     * design's counts are the same set of numbers — no position on the track means the picture the position beside it
     * already meant.
     *
     * @property range the counts this design draws between, inclusive — the slider's bounds, and what `density` `0`
     *   and `1` resolve to.
     */
    data class Count(override val label: String, val range: IntRange) : AmountKnob {

        /**
         * How many to draw at [density] — clamped, so a stored value outside `0..1` reads as the nearer end rather
         * than running off the range.
         */
        fun at(density: Float): Int =
            range.first + (density.coerceIn(0f, 1f) * (range.last - range.first)).roundToInt()

        /**
         * The density that resolves back to [count] — what the slider writes when it is moved to a number.
         *
         * The inverse of [at] rather than an independently-written one, which is why they sit together: a panel that
         * derived density its own way would land the user on a count adjacent to the one they dragged to, on some
         * ranges only. A degenerate single-count range answers `0`, having nothing to interpolate.
         */
        fun densityFor(count: Int): Float {
            val span = range.last - range.first
            if (span <= 0) return 0f
            return (count.coerceIn(range) - range.first).toFloat() / span
        }
    }

    /**
     * Density read as a plain `0..1` scale, for a design with nothing to count — the plasma's frequency, which is a
     * continuous property of a field rather than a number of drawn things.
     */
    data class Fraction(override val label: String) : AmountKnob
}

/**
 * A design's sub-look chooser — the [DesignParams.variant] field, named and enumerated.
 *
 * **The options are positional: index `n` is `variant = n`**, which is what makes a stored recipe survive a label
 * being reworded. A generator clamps an index it does not have, so the list is what the panel offers rather than a
 * bound the model enforces.
 *
 * @property label what this design's sub-look axis *is* — *Look*, *Direction*, *Sides*, *Blend*. It differs per design
 *   far more than the amount does, which is the whole reason the panel cannot label these itself.
 * @property options one short name per variant, in index order.
 */
data class VariantKnob(val label: String, val options: List<String>)
