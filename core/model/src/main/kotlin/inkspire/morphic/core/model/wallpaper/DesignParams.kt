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
 * **[depth] is the *depth* family — how much the design pretends to a third dimension.** It is the last of the seven
 * families the reference studio exposes (*Shadow, Blend mode, Refraction, Tridimensionality*) and the one nothing here
 * had a home for; a facet field's lighting is its first consumer. It is separate from [irregularity] because the two
 * disturb different axes — irregularity moves an element *within* the picture plane, depth moves it *out of* one — and
 * a design routinely wants a lot of one and none of the other, which the reference's own defaults do (its facets open
 * at half distortion and a sixth of the relief). The same rules apply: a flat design ignores it, `0` always means
 * *flat*, and a design that reads it maps `0.5` to a restrained default rather than to its maximum.
 *
 * **[roundness] is the *shape* family — how soft the design's corners are.** It is the last of the reference
 * studio's seven families to get a field of its own (*Roundness, Corner radius, Thickness, Curves, Wideness*), and the
 * one that had been riding whatever field was spare: a mosaic's rounding was folded into [variant] as a *look*, a
 * facet's leading onto [scale], a pane's bowing onto [irregularity]. Its first consumer is the Modern Mosaic, where it
 * is not a refinement but the identity of the design — the same tiling with square corners *is* a Mondrian, and with
 * the corners fully round the narrow tiles are pills. It is separate from [irregularity] for that field's own reason:
 * irregularity is disorder and this is not — a fully rounded tile is exactly as regular as a square one. The usual
 * rules hold: a design with no corners to soften ignores it, `0` always means *sharp*, and a design that reads it maps
 * `0.5` to a restrained default.
 *
 * **[depthScale] is to [depth] what [scale] is to [density] — a size beside a count, because the two are
 * independent.** The reference splits its own depth family exactly here: Flow Field's orbs are an *Orbs* count and an
 * *Orb size*, and four large orbs and four small ones are different pictures. Squeezing both onto [depth] is what
 * forces a generator to guess a size from a count, which is the same mistake [scale] exists to undo one field up. It
 * is a separate field rather than a reuse of [scale] because a design routinely reads both: Flow Field's [scale] is
 * the thickness of its *marks*, which has nothing to do with how big its orbs are. `0` collapses the element to
 * nothing, so it reads as [depth] `0` — the same "`0` means absent" rule the rest of the family keeps.
 *
 * **[rotation] is the *orientation* family — which way the design points, or how far it turns.** It is the last of the
 * seven families the reference studio exposes to get a field, and it was held back on purpose: six of their designs
 * expose it and four of ours were spending [variant] on a discrete stand-in (five sampled angles on the diagonal
 * bands, three on the louvers), so a field shaped by one consumer risked being the wrong shape for the four that
 * would move onto it later. What settled it is that the *Polygon Cascade*'s turn is **continuous and signed** where
 * every stand-in is a handful of sampled angles — a discrete chooser cannot express it at all, so the field has to be
 * a fraction and this is the consumer that proves it. **The four that already spend [variant] on a direction stay
 * where they are**, for [colorLayout]'s reason: a stored recipe's `variant` would keep its old number and be read as
 * a look it never meant.
 *
 * **It is a plain `0..1` fraction with no unit, like [irregularity] and [scale]** — a design maps it onto its own span,
 * because "a full turn" means different things to a design that *points* somewhere and one that turns each of its
 * copies a little further. `0` is always *untouched*: square-on, or every copy aligned.
 *
 * **[finish] is *how* a design's marks are painted, which is a third question again — not what shape they are
 * ([variant]) and not where their colors go ([colorLayout]).** The reference exposes it as a segmented control beside
 * both: the Polygon Cascade's *Mode* is **Stroke / Fill**, and Soft Overlaps and Rounded Tiles each carry a *Blend
 * mode*. Its first consumer is that cascade, where the two are genuinely different pictures of the same geometry —
 * an outline shows the ground and every copy behind it, a fill hides both and turns the cascade into overlapping
 * paper.
 *
 * **It is a field of its own rather than more values on [variant] because a design wants both at once.** That is the
 * argument [colorLayout] was added on, and here it is not close: the cascade has six shapes and two modes, and one
 * chooser would have to offer their twelve combinations — a segmented control divides its width evenly among its
 * options, so twelve of them is a strip of eighty-pixel segments nobody can read or hit. Two controls of six and two
 * is the same information at a usable size, and it is how the reference presents it.
 *
 * **[colorLayout] is *where* each palette stop goes, which is a different question from [colorMode]'s *how many*.**
 * The reference exposes both on one design and the pair is genuinely independent: its Topography chooses a *Style*
 * (contour lines or an embossed relief) and, beside it, a *Color mode* that is not the global one at all but a
 * layout — by hill, by altitude, or at random. That is the **color-distribution** family the teardown named, and it
 * had been riding [variant] for want of a field of its own: the mesh gradient's *Vertical / Corners / Scattered*, the
 * facets' *Field / Speckled / Scattered* and Vitrall's are all this knob wearing the sub-look's field, which was fine
 * only while no design wanted both at once. **The three that already spent [variant] on it stay where they are** — a
 * stored recipe's `variant` would keep its old number and be read as a look it never meant, and a key whose meaning
 * changes has to change its name.
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
 * @property depth how far the design steps out of the picture plane, `0..1` — `0` is flat, `1` fully dimensional
 *   (a lit relief, a hard shadow). A flat design ignores it. See the class note.
 * @property depthScale how large the things [depth] counts are drawn, `0..1` — `0` collapses them to nothing, so a
 *   design reads exactly as it does at `depth` `0`. A design whose depth knob is a continuous amount rather than a
 *   count of somethings ignores it. See the class note.
 * @property roundness how soft the design's corners are, `0..1` — `0` is sharp, `1` as round as the shape allows (a
 *   narrow tile becomes a pill). A design with no corners ignores it. See the class note.
 * @property rotation which way the design points or how far it turns, `0..1` — `0` is untouched (square-on, every
 *   copy aligned) and `1` is as far as this design turns. A design with no orientation ignores it. See the class note.
 * @property finish which of a design's *finishes* is chosen, by index — how its marks are painted (outlined or
 *   filled, and how they blend), as against [variant]'s what-shape and [colorLayout]'s where-the-colors-go. `0` is
 *   the design's own default finish; a design that paints one way ignores it. See the class note.
 * @property colorLayout which of a design's color *layouts* is chosen, by index — where the stops go, as against
 *   [colorMode]'s how many of them there are. `0` is the design's own default layout; a design that spends its palette
 *   one way ignores it. See the class note.
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
    val depth: Float = 0.5f,
    val depthScale: Float = 0.5f,
    val roundness: Float = 0.5f,
    val rotation: Float = 0.5f,
    val variant: Int = 0,
    val finish: Int = 0,
    val colorLayout: Int = 0,
    val colorMode: WallpaperColorMode = WallpaperColorMode.BICHROMATIC,
)
