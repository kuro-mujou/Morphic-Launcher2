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
 * **[colorMode] is the one knob every generator honors, and it does so without reading this field.** It is applied by
 * reducing the palette before the generator runs (`PaletteColorMode` in `core:graphics`), so a design is bichromatic
 * because it was handed two colors — not because it branched on the mode. That is why it defaults to the *restrained*
 * [WallpaperColorMode.BICHROMATIC] rather than the loudest: the default look is calm, and "use the whole palette" is a
 * deliberate opt-in. It is a new, defaulted field, so a recipe from before it existed still reads back.
 *
 * **[irregularity] is the organic-noise knob — the family Smart Launcher exposes on almost every design (as
 * *Irregularity / Distortion / Jitter / Randomness / Variation*) and the one ours were missing.** It is the single knob
 * that takes a rigid generator to an organic one: a facet field's point jitter, a mosaic's cell scatter, a wave's
 * jaggedness, a flow's turbulence. Unlike [colorMode] there is no one place to apply it — "organic" means something
 * geometrically different per design — so each generator reads it and maps it onto its own noise. Two rules keep that
 * honest: a generator with no organic axis (a plain gradient, concentric rings) ignores it exactly as a density-less
 * one ignores [density]; and `0` always means *rigid* — a clean lattice, a straight crest — with disturbance climbing
 * from there, so the knob reads the same direction everywhere. A design that shipped with fixed jitter is scaled so its
 * default `0.5` reproduces that shipped look; a design that shipped rigid takes `0.5` as a tasteful organic default.
 *
 * **[scale] is how much room the design's elements take, which is a different question from how many there are.** A
 * count and a size are independent everywhere they both apply — twenty small dots and twenty large ones are different
 * pictures — and squeezing both onto [density] is what forces a generator to guess. It is the *spacing / gaps* family
 * the reference studio exposes as *Spacing, Margin, Coverage, Size, Radius*, and the same rules as [irregularity]
 * apply: a design with no notion of size ignores it, and one that reads it maps `0.5` to its shipped look.
 *
 * @property density how much of the design there is, `0..1` — sparse to dense. A generator with no notion of density
 *   ignores it.
 * @property irregularity how much organic noise disturbs the design, `0..1` — `0` is rigid and geometric (a clean
 *   lattice, a straight crest, a perfect circle), `1` is chaotic. A generator with no organic axis ignores it, and one
 *   that reads it maps `0.5` to its shipped look. See the class note.
 * @property scale how much room the design's elements take, `0..1` — tight to sprawling. See the class note; a
 *   generator with no notion of size ignores it, exactly as a density-less one ignores [density].
 * @property variant which of a design's sub-looks is chosen, by index. `0` is the design's own default look; a
 *   generator with a single look ignores it, and one with several clamps an out-of-range index to what it has.
 * @property colorMode how much of the palette to paint with — see [WallpaperColorMode]. Applied to the palette, not
 *   read by the generator, so every design honors it.
 */
@Serializable
data class DesignParams(
    val density: Float = 0.5f,
    val irregularity: Float = 0.5f,
    val scale: Float = 0.5f,
    val variant: Int = 0,
    val colorMode: WallpaperColorMode = WallpaperColorMode.BICHROMATIC,
)
