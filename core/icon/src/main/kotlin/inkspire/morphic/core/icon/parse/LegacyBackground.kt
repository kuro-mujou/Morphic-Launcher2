package inkspire.morphic.core.icon.parse

/**
 * Decides whether a legacy icon's edge pixels are *one flat opaque color*, and which.
 *
 * A legacy (non-adaptive) icon is a single flat bitmap with no separate background, so the background layer of its
 * recipe has nothing to resolve to and renders as nothing. That is fine until the user shrinks or moves the
 * foreground in the studio, at which point the revealed area is transparent — when for many older icons ("a logo on
 * a solid colored plate") the *right* color was sitting in the artwork all along.
 *
 * Pure on purpose: rasterizing the drawable and reading its border is Android's job and lives in [DrawableParser],
 * while everything interesting here — the thresholds, and therefore the behavior — is arithmetic over an
 * `IntArray` and can be unit-tested without an emulator. Same split as `SettingsSlice` (rules without DataStore)
 * and `IconLayerResolver` (resolution without file I/O).
 *
 * **No matting, ever.** There is no reliable way to cut a glyph out of a rasterized icon — alpha matting breaks on
 * gradients, shadows and anti-aliasing — so the foreground keeps the whole bitmap, color included, and this only
 * decides what sits behind it. L1's plan reached the same conclusion and rejected the alternative explicitly.
 */
object LegacyBackground {

    /** Below this alpha a pixel is not "solid color" — it is the artwork fading out, or nothing at all. */
    private const val AlphaFloor = 250

    /**
     * How much of the sampled ring must be solid for a fill to be offered at all — deliberately near-total.
     *
     * **This is the threshold that keeps the fill invisible until it is wanted**, and it is the whole reason the
     * detection is safe to apply by default. A fill is only correct when the foreground already covers it: if the
     * icon's edge is fully opaque, painting its own edge color behind it changes not one pixel until the user
     * moves the foreground. Rounded corners, a drop shadow, or a transparent margin all mean the fill *would*
     * show — squaring off a rounded icon, or filling in the gap a shadow leaves — so those are declined rather
     * than guessed at. The user can still choose a solid color by hand; what this must never do is change an icon
     * nobody asked it to change.
     */
    private const val MinSolidFraction = 0.95f

    /** How far a channel may sit from the mean and still count as "the same color". */
    private const val ConsensusTolerance = 16

    /** How much of the ring must agree with the mean. Below this the edge is a gradient or picture, not a plate. */
    private const val MinConsensusFraction = 0.9f

    /**
     * The packed ARGB fill for an edge [ring], or `null` when the edge is not one flat opaque color.
     *
     * @param ring the icon's border pixels in any order — the sampling geometry is the caller's business.
     */
    fun detectFill(ring: IntArray): Int? {
        if (ring.isEmpty()) return null

        val solid = ring.filter { (it ushr 24 and 0xFF) >= AlphaFloor }
        if (solid.size < ring.size * MinSolidFraction) return null

        val meanR = solid.sumOf { it shr 16 and 0xFF } / solid.size
        val meanG = solid.sumOf { it shr 8 and 0xFF } / solid.size
        val meanB = solid.sumOf { it and 0xFF } / solid.size

        // A consensus test rather than a variance one: a handful of stray pixels (an antialiased logo edge that
        // reaches the border, a single-pixel highlight) should not veto an obviously flat plate, where a standard
        // deviation would let them.
        val agreeing = solid.count { pixel ->
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            kotlin.math.abs(r - meanR) <= ConsensusTolerance &&
                kotlin.math.abs(g - meanG) <= ConsensusTolerance &&
                kotlin.math.abs(b - meanB) <= ConsensusTolerance
        }
        if (agreeing < solid.size * MinConsensusFraction) return null

        return (0xFF shl 24) or (meanR shl 16) or (meanG shl 8) or meanB
    }
}
