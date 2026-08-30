package inkspire.morphic.core.model.wallpaper

import kotlinx.serialization.Serializable

/**
 * The knobs a design exposes in the studio's *Style* panel — how much of it there is, and which of its looks.
 *
 * **The common subset the walkthrough actually surfaced, not a per-design union.** Every generator the studio offers
 * is tuned by at most a couple of controls, and two were near-universal: a *Density* slider (how many cells / dots /
 * strokes) and a *variant* selector (Flow Field's *Eclectic / Pearls* — a design's sub-look). Those are the two here.
 * A generator reads the ones it understands and ignores the rest — a gradient has no density, and that is fine.
 *
 * **Deliberately not a per-design sealed type yet.** When a generator needs a knob these two cannot express, the
 * honest move is to specialize — a sealed `DesignParams` variant per design, or a typed extension — shaped by that
 * generator's real needs. Inventing that structure now, with one trivial generator that uses neither field, would be
 * a model in a vacuum. This is the smallest thing that lets `Generator.render` take parameters at all; it grows when
 * a generator gives it a reason to.
 *
 * Persisted inside the recipe; `encodeDefaults = false` on the store means an untouched design writes nothing.
 *
 * @property density how much of the design there is, `0..1` — sparse to dense. A generator with no notion of density
 *   ignores it.
 * @property variant which of a design's sub-looks is chosen, by index. `0` is the design's own default look; a
 *   generator with a single look ignores it, and one with several clamps an out-of-range index to what it has.
 */
@Serializable
data class DesignParams(
    val density: Float = 0.5f,
    val variant: Int = 0,
)
