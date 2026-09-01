# Smart Launcher Wallpaper Studio — live teardown & our gap analysis

**Captured 2026-08-31** by driving the installed `net.smartlauncher.wallpaperstudio` on the emulator over adb — every
design opened, every design's Style panel scrolled through tab by tab (screenshots in the session scratchpad, not
committed: it is their copyrighted UI). This is what [WALLPAPER_STUDIO_PLAN.md](WALLPAPER_STUDIO_PLAN.md) was missing.
W5 built sixteen generators and they render correctly, but two things are wrong and neither is the maths:

1. **Every design has ~6 tunable parameters; ours have one** (`density`). This is not a small gap — it is the whole
   product. See the full inventory below.
2. **The styling defaults are loud where theirs are restrained.** Aesthetic principles in the second half.

Companion to the plan (the *what/when*) and [GART_HARVEST.md](GART_HARVEST.md) (engine source). Read before the next
wallpaper slice — **and read `../gart` before writing a line of the fix.** That rule has a section of its own below
("Read gart first"); it is the one that has cost the most rework, so it is flagged here too.

---

## The parameter model — the big miss

Their Style panel is a **horizontally-scrolling row of tabs**, each tab a named parameter with either a numeric ruler
slider or a segmented variant control below it. **Almost every design exposes six** — and *at least* six: the row
scrolls, and Dot Grid turned out to have **eight** once it was driven to the end (W11e). Treat every count below as a
floor until the design has been opened and its tab row scrolled to the stop. Our `DesignParams` is `density: Float` +
an unused `variant: Int`. That is the gap in one sentence.

### Full per-design parameter inventory (all 22, observed directly)

| # | Design | Parameters (tabs, in order) |
|---|---|---|
| 1 | Diagonal Bands | Count · **Rotation** · **Coverage** · Spacing · **Offset** · Variation — **six** (W11l). **The bands do not fill the frame**: *Coverage* **10..100** (default 50) is the extent of the whole band *slab* across the band axis, centred, and the rest is ground — measured at 50, the slab spans `0.49` of the frame's own extent along that axis. *Count* **2..30** (default **5**) is bands in the slab. *Rotation* is **continuous, −180..180°**, opening on a shallow **20°**. *Spacing* **0..100** (default **0**) is a gap of ground within each band's pitch — a hairline at `7`, no bands at all at `100`. *Offset* is the four-arrow **nudge** Dot Grid has, walking the slab off centre. *Variation* is the band-width jitter; `0` is exactly equal. **The ground is one end stop and the bands cycle the rest** — scanned: five bands over a ground that is a *sixth* colour none of them takes |
| 2 | Modern Mosaic | Count · Spacing · Frame · **Ratio** · Roundness · Irregularity — **six** (W11k). Not a Voronoi (W11j) and **not a packing either**: it is a recursive **subdivision** — at *Count* `1` the whole frame is one tile — so our nearest design is the **Mondrian**. *Count* **1..100** (default 16), *Spacing* **0..100** (default 40) is the grout as a *fraction of the cell*, not pixels; *Frame* **0..100** (default 0) is the margin around the whole block, and at 100 the block is gone. **Ratio is a segmented `1/2 · Golden Ratio · 1/3 · 1/4 · 1/5`, defaulting to Golden — the earlier note reading it as "a segmented `1/4`" had simply caught the option at the right edge of the strip.** It is the **least share a cut may leave** (the fraction falls in `r .. 1-r`), which is why `1/2` halves exactly and why the app lists them in that order. *Roundness* **0..100** (default 60) runs to half the tile's short side, so narrow tiles become pills. *Irregularity* **0..100** (default 40) pushes the **shared** corners off square — the grout stays a uniform band, so it is one lattice moving, not each tile distorting |
| 3 | Gradient Columns | Rotation · Columns · Irregularity · Start column · Shadow · Progression smoothness |
| 4 | Flowing Blobs | **Color mode** · Shades · Complexity · Contrast · Shadow — **five** (W11i), and *Color mode* is the global one (Monochromatic · Bichromatic · **Colorful**, its default here · **Stroke**, a fourth we do not have). *Shades* **1..10** (default 8) is the band count, and the bands are **rungs on the interpolated ramp**, not the palette's own stops. **There is no count of blobs**: *Complexity* **4..40** (default **40**, the top) leaves the same two or three systems in the same corners and only makes their contours more convoluted — it is a domain warp's frequency, not a population. *Contrast* **0..100** (default 20) is how far down the field the bands are spread: `0` broad soft layers, `100` thin filaments on bare ground. *Shadow* **0..100** (default **0**) is a **paper-cut** shadow — each band darkens to `0.57` of itself at its boundary with the band *above*, easing out as `f^2.5`, on **every** side of a blob rather than in a light's direction. The ground is stop 0 |
| 5 | Triangular Facets | Resolution · Distortion · **Thickness** · **Distribution** · Randomness · Tridimensionality — **six** (W11h). *Resolution* counts cells along the **long** axis, **3..20** (default 10), cells square. *Distortion* **0..100** (default 50) jitters the lattice; at `0` every cell takes the *same* diagonal. *Thickness* **0..100** (default **0**) is not a stroke — it **insets every facet**, so the ground shows between them as leading; at 100 the facets are specks. *Distribution* is a two-option segmented control, **Random** / **Area** (default), and it is a **color** distribution, not a point one: *Area* paints a smooth two-dimensional field, *Random* gives each facet a flat random stop. *Randomness* **0..100** (default 25) is how far a facet departs from that field — **and the tab disappears entirely under *Random***, which is the app saying the two are one axis. *Tridimensionality* **0..30** (default 5) is a per-facet brightness, the relief. The ground is **stop 0**, which the field never paints with |
| 6 | Bauhaus Blocks | Resolution · Plain tiles · Tile background — *Resolution* counts cells along the **long** axis, **4..20** (cells are square, so the columns fall out and the grid bleeds sideways); *Plain tiles* is a **fraction** left undecorated, not a toggle and not a count; *Tile background* is Off/On. The tile vocabulary is **a quarter disc or nothing** — halves and circles are emergent |
| 7 | Confetti Dots | Resolution · Offset distortion · Max radius · Radius variation · **Color distribution** · Focus distance · **Focus range** — **seven** (W11f). The lattice is a square grid *turned ~12°* off the frame, pitch = long side / Resolution (**5..24**, default 15). *Color distribution* is a **segmented ratio preset** — `100/100/…`, `100/66/33`, **`100/50/25`** (default), `100/33/11` — i.e. how fast the pick weight falls off down the stops, not a hue choice. *Focus distance* + *Focus range* are a real **depth of field**: a disc's size is its distance, and everything outside the focal band is blurred |
| 8 | Mesh Gradient | **Rows** · Columns · Jitter · **Color distribution** · Softness — **five** (W11g). Rows and Columns are both **2..10**, default 4×4. *Color distribution* here is a **layout**, not a weighting: `Random` · `Corner interpolation` · **`Linear bottom`** (default) — and the default is why theirs reads as a soft progression down the frame where ours read as a quilt. At Jitter 0 the render is a **mathematically exact** gradient, which is what proves the blend is a bilinear mesh rather than distance-weighted points |
| 9 | Dot Grid | Rows · Columns · Irregularity · Corner radius · Square size · Aspect ratio · **Spacing · Offset** — **eight**, not six (W11e; the last two are past the fold in the tab row and were missed on the first pass). *Spacing* is the margin around the whole block, not the gap between tiles; *Offset* is a four-arrow **nudge**, hold-to-repeat, that walks the block off center; *Aspect ratio* is a **segmented** 1/1 · Golden · 2/1 · 4/1 |
| 10 | Layered Waves | Count · Spacing · Distortion · Palette gradients |
| 11 | Neon Ribbons | Count · Variation · Start area · End area — one bundle of curves sharing a spine; *Start/End area* are **percentages** (1.3% against 5% by default) and that asymmetry is the fan; *Variation* splays the bundle rather than reshaping the gesture |
| 12 | Wave Dividers | Rotation · Count · Irregularity · Wideness · Waves · Offset |
| 13 | Vitrall | Density · Spacing · Curves · Slices · **Color distribution** · Randomness · **Color mode** — **seven** (W11j; the last was past the fold). **Not a Voronoi either**: the frame is cut by **edge-to-edge chords**, so the panes are long shards and slender wedges rather than compact cells. *Density* **1..140** (default 68) is the number of cuts — at `1` a single chord crosses the frame. *Curves* **0..100** (default 35) bows them into arcs; at `0` every cut is straight. *Spacing* **0..100** (default 40) is the leading, and `0` removes it entirely. Every pane is filled with a **gradient**, always, which is most of why it reads as glass. *Color distribution* is a layout — **`Linear bottom to top`** (default) / `Random` |
| 14 | Flow Field | **Style** · Density · Irregularity · Thickness · Orbs · Orb size |
| 15 | Topography | **Style** · **Color mode** · Coverage · Levels · Zoom · Variation |
| 16 | Ribbed Glass | Real glass · Count · Complexity · Refraction · Vibrancy |
| 17 | Polygon Cascade | Shape · Mode · Thickness · Iterations · Rotate delta · Size |
| 18 | Soft Overlaps | Count · **Blend mode** · Mode · Position jitter · Radius · Size variation |
| 19 | Rounded Tiles | Count · Margin · Spacing · **Blend mode** · Rotation · Direction |
| 20 | Ribbon Flow | Count · Distortion · Rotation · Thickness · Detail · Gradient offset |
| 21 | Flow Lines | Iterations · Complexity · Thickness · Delta rotation · Start · End |
| 22 | Shape Trail | Path style · Length · Roundness · Variation · Thickness · Depth |

### The parameters aren't ad-hoc — they fall into shared families

Read down the table and the same handful of concepts recur under different names. **This is the taxonomy our
`DesignParams` should grow into** — a set of optional, defaulted fields, each generator reading the ones it means:

- **Amount / coverage** — *Count, Resolution, Columns, Rows, Iterations, Density, Slices*. The "how many" knob (our
  `density`, but named per design and often exposed as a raw integer). **Built (W6–W10).**
- **Spacing / gaps** — *Spacing, Margin, Offset, Coverage, Size, Radius*. How much air between elements, and how much
  room each takes. **Built as `scale` (W11c)**, on Ribbons' spread; it is now the field the *Coverage* knob lands on
  wherever one appears (Bauhaus in W11d, Diagonal Bands in W11l), which is also where the family's one real squeeze
  shows: a design exposing **both** a Coverage and a Spacing has two members of one family and one field, and the
  coverage wins; the designs whose element size is still fixed
  (Confetti's radius, Soft Overlaps' radius, Rounded Tiles' margin) are where it pays off next. Dot Grid spends it on
  the **margin** instead (W11e) — its own tile size rides on the `variant` look, because a contained block and a
  full-bleed field is the bigger of the two questions by a distance.
- **Orientation** — *Rotation, Direction, Delta rotation, Rotate delta*. Which way it points / turns. **Still no field
  of its own, and it is now the last family without one** (shape got `roundness` in W11k). Six of their designs expose
  it and three of ours spend `variant` on a discrete direction as a stand-in, which is what Diagonal Bands does with
  five sampled angles (W11l). Deliberately **not** added for one design: a field shaped by one consumer is the thing
  `roundness` only just got away with, and here there are three to move at once. That is a slice of its own, and the
  clearest model work left.
- **Organic noise** — *Irregularity, Distortion, Jitter, Randomness, Variation, Offset distortion*. The single most
  common family — the knob that takes a rigid generator to an organic one. **Ours have none of this**, which is part of
  why ours read as mechanical.
- **Color** — *Color mode, Color distribution, Shades, Palette gradients*. How the palette is applied (see below).
  **Watch the name: *Color distribution* means two unrelated things.** On Confetti it is a *weighting* (a ratio
  preset deciding how often each stop is picked); on Mesh it is a *layout* (Random / Corner interpolation /
  Linear bottom, deciding where each stop goes). Reading one for the other loses the design.
- **Stroke / shape** — *Thickness, Roundness, Corner radius, Curves, Wideness*. Line weight and corner softness.
  **Built as `roundness` (W11k)**, on the Modern Mosaic's corners — the last family to get a field of its own, and it
  took the design where the knob is not a refinement but the identity: the same tiling with square corners *is* a
  Mondrian. The family had been riding whatever field was spare (Facets' *Thickness* on `scale`, Vitrall's *Curves* on
  `irregularity`, Dot Grid's corner radius folded into `variant`), which is exactly how `depth` lived before W11h.
- **Rendering / depth** — *Blend mode, Contrast, Shadow, Refraction, Vibrancy, Tridimensionality, Real glass*, and
  Confetti's *Focus distance / Focus range*. The lighting/translucency that gives their output *depth*. Ours were all
  flat; built so far are Ribbed Glass's lens, Gradient Columns' seam shadow, Ribbons' ground glow (W11c) and
  **Confetti's depth of field (W11f)** — the first that is depth rather than lighting. **Built as `depth` (W11h)**, on
  Facets' relief: the last family to get a field of its own, and the one that decided it was Facets, where a flat
  render of the right geometry and the right colors is still not the design. Confetti's focus is the obvious next
  consumer — it rides `variant` today, which is why that design has no sub-look left for anything else.
- **Design-specific** — *Orbs / Orb size* (Flow Field's moons), *Frame / Ratio* (Mosaic), *Path style / Depth* (Shape
  Trail), *Start area / End area* (Neon Ribbons), *Start column / Progression smoothness* (Gradient Columns). A couple of
  bespoke knobs per design on top of the shared families.

### The color system, concretely

Two kinds of color tab, and this is the harmony engine:

- **Color mode** (Flowing Blobs, Topography, …) — a segmented variant: **Monochromatic · Bichromatic · Colorful ·
  Stroke** (observed on Flowing Blobs). One / two / many colors, or outline-only. **This is exactly the
  `WallpaperColorMode { MONO, DUOTONE, PALETTE, STROKE }` we should add.** Their popular designs default to Mono or
  Bichromatic — *not* Colorful — which is why they look composed and ours (always "Colorful") look like swatches.
- **Color distribution** (Confetti, Mesh, Vitrall) — how stops are *assigned* across elements (random vs ordered vs by
  position). A second, orthogonal color knob.

Recommended model (decide now, build in W7):

```
enum class WallpaperColorMode { MONOCHROMATIC, BICHROMATIC, COLORFUL, STROKE }
data class DesignParams(
    val amount: Float = 0.5f,          // Count/Resolution/… normalized
    val spacing: Float = …,
    val irregularity: Float = …,       // the organic-noise family — NEW, high impact
    val rotation: Float = 0f,
    val colorMode: WallpaperColorMode = BICHROMATIC,   // NEW; default NOT Colorful
    val variant: Int = 0,              // "Style" — a design's sub-look, finally used
    // plus a small typed extension per design for its bespoke knobs
)
```

### Corrections to earlier assumptions this teardown forces

- **Confetti is a *distorted grid*, not Poisson-disk — and the grid is *turned*.** Their tabs are Resolution + Offset
  distortion: a lattice pushed around, not dart-throwing. The turn (~12°, measured) is the part that is easy to miss and
  does the most work — an axis-aligned lattice announces itself through any amount of jitter because the eye finds the
  horizontals, and turned a little the same lattice reads as an even sprinkle. Ours built the (harder) Poisson sampler,
  which also left its knob with no rigid end, since uniformly-random points cannot be made *more* even. Fixed in W11f.
- **Mesh is a grid + jitter** (Rows + Columns + Jitter), not our random control points. Same look, simpler
  control. And the *blend* is a **bilinear mesh**, not inverse-distance weighting: at Jitter 0 theirs is an exact
  gradient, where any distance weighting leaves each node a core and beads into stripes. See W11g.
- **Every design has an organic-noise knob.** Ours are deterministic-and-rigid with no way to loosen them.

---

## The five aesthetic principles behind their look

Styling defaults, not algorithms — this is why "some of ours are bad."

1. **Restraint & negative space.** Dark/white grounds, motif as accent, air around it (Confetti, Dot Grid, Neon
   Ribbons, Flow Lines, Topography-contour). Ours fill every pixel with saturated color.
2. **Thin-line rendering is a first-class family.** Topography (Contour-lines variant), Ribbon Flow, Flow Lines, Neon
   Ribbons, Polygon Cascade — fine strokes on a ground. Built out over W8 and W11b; the family is now ours too.
3. **Soft edges & smooth gradients.** Flowing Blobs, Mesh, Soft Overlaps, Layered Waves are liquid. Ours favor hard
   flat bands everywhere (Metaballs onion-rings, flat cells).
4. **Color mode, not "cycle everything."** Their harmony comes from defaulting to Mono/Bichromatic. Ours always
   Colorful.
5. **Tuned, sparse defaults + depth.** Every design opens tasteful, and most carry a Shadow/Blend/Refraction depth
   knob. Ours open at `density = 0.5` and were flat; the color mode fixed the palette half (W6) and the depth half is
   being built per design as the quality pass reaches it — Ribbed Glass's lens, Gradient Columns' seam shadow, and
   Ribbons' ground glow (W11c) are the ones that exist.

Plus: **Bauhaus uses real shapes** (quarter-circles, triangles — "Plain tiles" toggles the decoration); ours draws only
rectangles.

---

## Design-by-design: their 22, our 16, the verdict

### W11 checklist — which designs have actually been driven

**"Driven" means the reference design was opened on the emulator and *every one of its knobs pushed to both ends*,**
against a render of ours. That is a much stronger claim than the verdict table below, which was written from one pass
through their studio and has been wrong about a design's *identity* four times out of six. A design is only ticked
when its slice landed and was device-verified; a design built from the verdict table alone (most of W8 and all of W9)
counts as **not driven**, because what built it was a one-line note rather than the reference in front of it.

| # | Theirs | Ours | Driven | Slice / note |
|---|---|---|---|---|
| 1 | Diagonal Bands | `DIAGONAL_BANDS` | ✅ | **W11l** — a slab on a ground, not a full-bleed stripe pattern; *Coverage* is the finding |
| 2 | Modern Mosaic | `MODERN_MOSAIC` | ✅ | **W11k** — a subdivision, not a packing; *Ratio* is the least share a cut may leave. Catalog **28** |
| 3 | Gradient Columns | `GRADIENT_COLUMNS` | ☐ | built in W9 from the note |
| 4 | Flowing Blobs | `METABALLS` | ✅ | **W11i** — Complexity is a warp, not a count; the paper-cut shadow |
| 5 | Triangular Facets | `TRIANGULAR_FACETS` | ✅ | **W11h** — color is a 2-D field of areas; `depth` added |
| 6 | Bauhaus Blocks | `BAUHAUS` | ✅ | **W11a** rebuild (ours was a Mondrian), **W11d** second pass |
| 7 | Confetti Dots | `CONFETTI` | ✅ | **W11f** — turned lattice, ground, falloff, depth of field |
| 8 | Mesh Gradient | `MESH_GRADIENT` | ✅ | **W11g** — color *layout*, and a bilinear mesh not IDW |
| 9 | Dot Grid | `DOT_GRID` | ✅ | **W11e** — theirs is contained + color-stepped; ours was a halftone |
| 10 | Layered Waves | `WAVES` | ☐ | verdict: "closest we have" — untested |
| 11 | Neon Ribbons | `RIBBONS` | ✅ | **W11b** rebuild, **W11c** second pass (knobs + ground glow) |
| 12 | Wave Dividers | `WAVE_DIVIDERS` | ☐ | built in W9 from the note |
| 13 | Vitrall | `VITRALL` | ✅ | **W11j** — chord subdivision, not a Voronoi; circle-clipped curves, translucent came rim. Catalog **27** |
| 14 | Flow Field | `FLOW_FIELD` | ☐ | verdict wants Orbs + a Style variant |
| 15 | Topography | `CONTOUR` | ☐ | W8b added the lines look from the note; theirs never driven |
| 16 | Ribbed Glass | `RIBBED_GLASS` | ☐ | built in W9 from the note |
| 17 | Polygon Cascade | `POLYGON_CASCADE` | ☐ | built in W8d from the note |
| 18 | Soft Overlaps | `SOFT_OVERLAPS` | ☐ | built in W9 from the note |
| 19 | Rounded Tiles | `TRUCHET` | ☐ | verdict calls ours "a reasonable analog", which is not the same design |
| 20 | Ribbon Flow | `RIBBON_FLOW` | ☐ | built in W8c from the note |
| 21 | Flow Lines | `FLOW_LINES` | ☐ | built in W8a from the note |
| 22 | Shape Trail | — | ☐ | **missing** — a 3-D tube/knot; the only one with nothing of ours at all |

**Ten of twenty-two driven.** Ours-only designs have no reference to drive and are not in the count:
`LINEAR_GRADIENT`, `PLASMA`, `RINGS`, `RAYS`, `MONDRIAN` (split out of Bauhaus by W11a, and *still* ours-only — W11k
built theirs beside it rather than reworking it), `HALFTONE` (split out of Dot Grid by W11e), and `VORONOI`, which
turns out to be neither of the two designs it was named for (W11j). Catalog is **28**.

**What the six taught, in one line each, because it is what predicts the next find:** check whether ours *is the same
design* before judging its quality (4 of 6 were not); drive *every* knob to *both* ends before concluding; measure
pixels rather than trusting the eye; and the rigid end of a knob is the strongest evidence about the machinery
underneath.

| Theirs | Ours | Verdict & fix |
|---|---|---|
| Topography | **Contour** | Biggest gap. Add **Contour-lines variant** (thin lines, their default + community favorite) alongside our filled "Embossed". |
| Flowing Blobs | **Metaballs** | ✅ **W11i.** "Ours hard onion rings" was right about the symptom and wrong about the cause: their *Complexity* is a **domain warp's frequency**, not a charge count, which is why no setting of ours could reach it. Rebuilt as three fixed charges read through a warp, with the band count off the palette's length (it was killing the design in the default color mode) and their **paper-cut shadow**, measured. |
| Confetti Dots | **Confetti** | ✅ **W11f.** Re-based on their turned, jittered lattice; the ground moved to the palette's **light** end (theirs is stop 0, whatever that is), the palette is now spent with a geometric falloff so the last stops are rare accents, and it gained a **depth of field**. The Poisson sampler is gone. |
| Neon Ribbons | **Ribbons** | ✅ **W11b.** Rebuilt as one fanning bundle of fine curves. The W8 decision to skip this was wrong: Flow Lines combs the whole frame, theirs draws *one* gesture — not the same look. |
| Bauhaus Blocks | **Bauhaus** | ✅ **W11a**, refined in **W11d**. Rebuilt as their arc lattice (what ours had been — recursive rects, ruled — was a *Mondrian*, and is now a design of that name), then narrowed to their real vocabulary: one quarter disc, everything else emergent, with coverage on its own knob. |
| Dot Grid | **DotGrid** | ✅ **W11e.** Same identity finding as Bauhaus: ours was a *halftone* (a field driving each dot's **size**, full-bleed) under their name, and theirs is a **contained** lattice of uniform tiles where only the **color** moves. The halftone split off as its own design; DotGrid was rebuilt as theirs. |
| — | **Halftone** | Ours only, split out of DotGrid by W11e — the noise-sized dot screen, kept unchanged. |
| Triangular Facets | **Facets** | ✅ **W11h.** The identity finding again: ours read the palette at a facet's *height*, theirs paints a **two-dimensional field of areas** — proved by measuring a path between two regions and finding a straight RGB line that skips the stops in between. Rebuilt on a coarse color lattice (`ColorLattice`, shared with the mesh gradient), plus the **relief** (which is what `DesignParams.depth` arrived for), the **leading**, and a shorter-diagonal split that kills the slivers. |
| Mesh Gradient | **Mesh** | ✅ **W11g.** Grid + jitter landed in W7; W11g added the **Colors** layout (Vertical / Corners / Scattered) and **Softness**, and replaced inverse-distance weighting with a **warped bilinear mesh** — the only blend that is an exact gradient at the rigid end, as theirs is. |
| Layered Waves | **Waves** | Closest we have. Add Distortion + Palette-gradients toggle. |
| Vitrall | **Vitrall** | ✅ **W11j.** Built as its own design: the frame cut by **edge-to-edge chords** into leaded panes, each filled with a gradient. The old plan — a *variant* of our Voronoi — was based on a mapping that turned out to be wrong twice over. |
| Modern Mosaic | **Mosaic** | ✅ **W11k.** Built as its own design. Not a packing — a recursive subdivision, which its *Count* `1` gives away by drawing one tile over the whole frame — so it is the **Mondrian**'s construction with the opposite finish: every tile pulled back from its own edges onto a light ground, corners rounded, shared corners skewed. *Ratio* turned out to be the **least share a cut may leave**, not a split position, and that is what makes the tile sizes a harmonious set. |
| — | **Voronoi** | Ours only, and honestly named at last (W11j). Cells built *around points* are neither their mosaic's rectangles nor their vitrall's shards. |
| — | **Mondrian** | Ours only. Their Modern Mosaic is the same subdivision, so W11k measured the two against each other and left this one alone: it *rules an ink line between blocks that touch*, where theirs floats tiles on a ground. Same skeleton, opposite finish, two designs. |
| Flow Field | **Flow** | Add **Orbs** (the moons), Style variant, lower density default. |
| Rounded Tiles | **Truchet** | Reasonable analog; theirs is diagonal rounded bars w/ Blend mode. |
| — | **Plasma, Rings, Rays** | Ours only (SL lacks). Keep, but give MONO/BICHROMATIC defaults — they are our loudest. |
| Diagonal Bands | **DiagonalBands** | ✅ **W11l.** Ours filled the frame with saturated stripes; theirs lays a *slab* of bands across a large calm ground, and its *Coverage* is how much of the frame that slab takes. Also: the ground is stop 0 and the bands cycle the tones **above** it, where ours cycled the whole palette and so had no ground to show. |
| Gradient Columns / Wave Dividers / Ribbed Glass | — | Built in W9 from one-line notes, never compared. Calm staples. |
| Polygon Cascade / Ribbon Flow / Flow Lines | — | Missing, all **thin-line** family. High visual value. |
| Soft Overlaps | — | Missing. Translucent overlapping shapes (Blend mode + palette alpha — our `Palette` already keeps alpha). |
| Shape Trail | — | Missing. 3D tube/knot (Path style + Depth). Real dimensional rendering — lower priority. |

---

## Revised plan (supersedes the W5 "done" framing — W5 was the *engine*, not the *studio*)

- **W6 — the color-mode system. ✅ (2026-08-31)** Added **`WallpaperColorMode`** (Monochromatic / Bichromatic /
  Colorful) + a `colorMode` field on `DesignParams`, defaulting to the restrained **Bichromatic**. It is applied by
  `PaletteColorMode.resolve` reducing the palette *before* the generator runs, so **all sixteen honor it with zero
  per-design code** — a mosaic drawn from a two-color palette is bichromatic by construction. Studio gained Mono/Duo/Full
  chips; device-verified (the studio now opens on cream-on-navy Bichromatic Flow, and Full/Mono recolor live). The rest
  of the family model (amount/spacing/**irregularity**/rotation as first-class fields) folds into W7 — the color mode was
  the single biggest restraint lever and shipped on its own.
- **W7 — the styling pass. ✅ (2026-08-31)** Added the **organic-noise knob** — an `irregularity: Float` on
  `DesignParams`, defaulting to a restrained `0.5`. Nine generators read it, each mapping it onto its own noise and
  scaled so **`0.5` reproduces the shipped look** (the renders already verified on device) while `0` is rigid and `1`
  chaotic: **Voronoi** (lattice → shards, i.e. Modern-Mosaic → Vitrall), **Mesh** (now grid+jitter, the teardown's
  correction), **Facets** (point jitter), **Confetti** (offset distortion), **Dot Grid** (loosened lattice), **Waves**
  (crest steepness), **Flow** + **Ribbons** (field curl), **Contour** (detail-octave variation). The lattice placement
  Voronoi and Mesh share is one helper (`PointScatter.gridJitter`). The seven with no organic axis (gradient, Plasma,
  Rings, Rays, Metaballs, Truchet, Bauhaus) ignore it, as density-less designs ignore density. Separately, the loud
  field designs were **softened** — Plasma/Rings/Rays open on broader swells at the default density. Device-verified via
  the render harness (which gained an `irr ∈ {0,1}` sweep); the knob is engine-only until **W10** surfaces it, exactly
  as `variant` is. The remaining W7 wish — a *per-design* default (each design opening on its own tasteful params) —
  needs the studio to carry per-design params and folds into W10.
- **W8 — thin-line family. ✅ (2026-08-31)** Biggest visual jump; four looks but **three** designs (Contour-lines is a `variant`, not an enum value), catalog now 19. Progress:
  - **W8a ✅** — the shared stroke renderer (`Streamlines.pathOf`, extracted from the verbatim copies in Flow and
    Ribbons) + **Flow Lines** (new design; gart's flowforce/perl, cyanowaves): dense fine hairlines seeded on an even
    lattice and combed through the flow field — Flow woven, Flow Lines combed. density → line count, irregularity →
    field curl.
  - **W8b ✅** — **Contour-lines**: a `variant` on Contour (its first real use), and — by the user's call — the lines
    look is now Contour's **default** (variant 0, thin ink on paper, the community favorite); the old filled relief is
    variant 1. Both fall out of the same banded field; the variant is reachable in code and by the harness now, in the
    Style panel at W10.
  - **W8c ✅** — **Ribbon Flow** (new design; gart flowforce/Eclectic): broad rounded ribbons flowing through the field,
    each sweeping the palette *along its length* (a gradient down the ribbon, seeded offset per ribbon) and drawn with no
    outline, so the bands melt past each other — the flat, outlined Neon Ribbons is the sibling it contrasts. Built on the
    shared `trace` + `colorLooping`, drawn per-segment.
  - **W8d ✅** — **Polygon Cascade** (new design; gart spirograph/harmongraph): a rosette of rotating, shrinking polygon
    outlines weaving a moiré into the frame's center — the first design that is *not* a per-pixel field but one shape
    drawn many times. density → iteration count, `variant` → the polygon's sides (0 = triangle), irregularity → vertex
    wobble (crisp rosette → hand-drawn). A centered mandala, so it carries a lot of negative space by design.
  - **Re-do Ribbons — deliberately skipped (user's call, 2026-08-31).** The teardown asked to rework Neon Ribbons into
    "fine bundled lines + glow", but that was written before Flow Lines existed; Flow Lines now *is* the fine bundled-line
    look, so the current thick-outlined Ribbons is kept for the bold, poster-style variety the family would otherwise
    lack. Not a gap — a decision.
- **W9 — the calm staples. ✅ (2026-08-31)** All five, catalog now **24**: **Diagonal Bands** (angled stripes, cycling
  palette, `variant` = direction), **Gradient Columns** (palette stepped once across columns, soft seam shadow), **Soft
  Overlaps** (translucent radial-gradient discs blending, `variant` = blend mode), **Wave Dividers** (banded stripes with
  wavy seams undulating in unison), **Ribbed Glass** (a diagonal gradient refracted through fluted-glass ribs with a
  specular lens — a standout). Two shared derivations fell out: `Bands` (variable-width banding, used by Diagonal Bands +
  Wave Dividers) and `Shades` (channel darken, used by Gradient Columns + Ribbed Glass), each extracted on its second
  consumer. `irregularity` maps per design (band-width variation / wave depth / position jitter / refraction).
- **W10 — the Style panel UI. ✅ (2026-08-31)** The knobs finally reachable. A ruler toggle opens a panel above the
  bottom bar: a **horizontal tab row** naming whatever knobs the current design declares, over a **ruler slider** or a
  **segmented control** for the selected one — theirs, mirrored. Three decisions:
  - **The generator declares its own knobs**, not a table in the UI (`DesignStyle` on the `Generator` interface,
    `core:graphics`). Open question 1 above leaned to "a flat bag with per-design labels"; this is that bag, declared
    beside the code that reads it. The reason is the silent failure: a knob the panel offers and the generator ignores
    drags, re-renders, and moves nothing — seven designs read no `irregularity`, twenty have no `variant`. It also puts
    the `Min`/`Max` bounds (which were `private const`) where the panel can ask for them.
  - **Ruler sliders expose the real counts** — open question 2, settled their way. 750 strokes, 12 bands, 24 ribs, over
    the generator's own range. The twenty-two copies of `Min + (d * (Max - Min)).roundToInt()` became
    `AmountKnob.Count.at`, with `densityFor` — its inverse — beside it, so the count the slider writes is the count the
    generator draws. Plasma is the one `AmountKnob.Fraction`: a frequency, nothing to count.
  - **Color mode moved into the panel as a tab**, so Style is every knob in `DesignParams` and the color chooser is the
    palette bank alone.

  Left for a later pass, in rough order of value: a **draft-quality render during the drag** (the panel commits on
  release, since a full-screen generate has no cheap intermediate — the plan's open question 1, now with a consumer);
  the **frosted `studioSurface` material** under the whole bottom bar (the panel carries a flat scrim, set by the
  lightest wallpaper a design produces); and the **per-design default params** W7 wanted — every design still opens on
  the same `0.5`, where each should open on its own tasteful value now that there is a panel to show it.
- **W11 — the design-by-design quality pass. In progress.** With the engine and the panel both built, the remaining
  gap is *per design*, and the only way to find it is one at a time: open theirs on the emulator, render ours through
  the harness, and put the two side by side. The verdict table above is the running record; each entry is its own
  slice, and the ones that turn out to be a different design rather than a worse one are the valuable finds.
  - **W11l — Diagonal Bands. ✅ (2026-09-01)** The first of the four W9 staples to be driven, and the identity
    finding lands on the *composition* rather than the geometry: the bands were right and there were simply far too
    many of them, everywhere.
    - **Their bands do not fill the frame, and that is the design.** *Coverage* is the extent of the whole band
      **slab** across the band axis — wound down, a slender ribbon of colour on bare ground; only at the top of its
      travel is it the full-bleed stripe pattern ours was permanently stuck at. Measured rather than guessed: at
      Coverage 50 the slab spans `0.49` of the frame's own extent along that axis, so the knob is a fraction of the
      *projected* extent and nothing else. Ours takes it on `scale`, the field Bauhaus's *Coverage* already uses.
      This is the teardown's own first aesthetic principle — restraint and negative space — as a single knob, and
      ours had no way to reach it.
    - **The ground is one end stop and the bands cycle the rest.** Scanning theirs down the middle gives five bands
      over a ground that is a *sixth* colour, which none of the bands ever takes. Ours cycled the whole palette
      including stop 0, so there was no ground to be had even where the coverage had allowed one. It is
      [RampTones]'s **third** consumer, one slice after it was extracted, and in bichromatic its floor is again what
      keeps the design alive: three real tones on the ground instead of one.
    - **Their *Rotation* is continuous over `−180..180°` and opens on a shallow `20°`** — ours had four fixed
      directions and none of them was shallow, so the design's own default look was unreachable. Ours now offers
      five sampled angles (`20 · 45 · 90 · 135 · 160`), ascending so the segmented control reads as one axis, and
      starting at the shallow one so that index `0` is also the design's default — the two constraints agree only at
      that ordering, which is why a flat `0°` is not on the list. **The continuous version is deliberately deferred**
      to a slice that gives the *orientation* family a field with its three consumers together; see the family note
      above.
    - **Projected in pixels, not in the unit square** — a `20°` band has to draw at `20°` on the screen, and in the
      unit square the frame's aspect turns it into something near-flat on a phone. Verified by measuring the render:
      `20.01°`. Same lesson as Vitrall's grain and the Mosaic's cuts, third time.
    - **Not ported: their *Spacing* and their *Offset*.** Spacing (a gap of ground inside each band's pitch — a
      hairline at `7`, no bands at `100`) is a real look with nowhere to sit while `scale` carries the coverage, which
      is worth more; it is fixed at their own default of `0`. Offset is Dot Grid's four-arrow nudge again, wanting a
      two-axis control neither the model nor the panel has — **second design to want one**, and now a named gap.
    - **The count range is theirs exactly (`2..30`), and the default is left wrong on purpose.** Theirs opens at 5
      and our panel's uniform `0.5` puts us at 16. Modern Mosaic bent its range to compensate one slice ago; that was
      justified there because 51 tiles is a different *character*, and it is not justified here because 16 shallow
      bands on a ground is the same picture as 5, finer. Bending a second range would make the workaround the pattern.
      **Third consumer for per-design defaults**, after Facets' leading and the Mosaic's count.
    - **gart has nothing for this design, and that is worth recording.** `arts/stripes`, `arts/lines/stripes1`,
      `stripes2` are all vertical or wavy sine lines; none is a flat rotated band set. So the reference was the only
      source here — which is the "read gart first" rule working as intended rather than failing: it is cheap to check
      and the answer is sometimes no.
  - **W11k — Modern Mosaic. ✅ (2026-09-01)** The design W11j handed off, and the first slice run under the
    "Read gart first" rule above — gart's `arts/rects/mondrian` and `rects/divide` carry the subdivision, and reading
    them first is what kept this from being re-derived.
    - **It is a subdivision, not a packing, and *Count* `1` is the whole proof** — the frame becomes a single rounded
      rectangle. Everything else follows: the boundaries are cuts that run the full width of whatever they cut, so
      this is [MondrianGenerator]'s construction and the Mondrian is the design of ours to measure it against, exactly
      as the checklist was corrected to say.
    - **Their *Ratio* is the least share a cut may leave, not the split position — and that is the design's harmony
      in one knob.** The cut falls anywhere in `r .. 1 - r`, so `1/2` is a *point* and every tile is an exact half of
      its parent, where `1/5` admits a tile four times its sibling. Three things settle it: the exact-halves render at
      `1/2`; the app listing the options `1/2 · Golden · 1/3 · 1/4 · 1/5`, which is monotone in `r` and so a single
      axis from rigid to lopsided; and a measurement — three strips at `0.382 / 0.236 / 0.382` of the frame, which is
      one golden cut and then a golden cut of the larger part, to the pixel. The default is the **golden minor**, and
      a fixed band of fractions is what makes the tile sizes a small set of related numbers instead of a spread.
      Ours takes it on `variant` as **Ratio: Even · Golden · Third · Quarter · Fifth**.
    - **Their *Irregularity* moves one shared lattice, not each tile.** The grout's narrowest crossing stays at
      36–43px from `0` to `100` — if tiles distorted independently some pair would drift together and close their gap.
      So ours maps every corner through **one smooth displacement field**, which is the *same technique W11j rejected
      for the window*, and the reason it is right here is worth keeping: a T-junction corner lands slightly off its
      neighbour's straightened edge, and in the window that residue is a hairline of ground and a bug, while here
      **there is already a wide band of ground between every pair of tiles**, so anything under the grout is invisible
      by construction. The deciding property is whether the design puts ground between its pieces. Confirmed on the
      render at full skew: no collisions, no gaps, and the amplitude can exceed the grout because what shows is the
      field's *curvature over one edge*, not its size.
    - **Their *Roundness* is the design's identity, so it arrived with a field of its own** — `DesignParams.roundness`,
      the *shape* family's first home, added exactly as `scale` arrived in W11c and `depth` in W11h. At `0` this is a
      Mondrian in a light grout and at `1` every narrow tile is a pill; folding that onto `variant` beside Ratio would
      have been fifteen combinations of two independent things, and onto `depth` a lie (`depth`'s contract is that `0`
      means *flat*). Ours rounds with **one quadratic through each corner** rather than a circular arc — an arc's
      tangent length `r / tan(θ/2)` blows up on the near-straight corners a skewed tile produces.
    - **Their *Frame* is the margin around the whole block** (at `100` the block is gone), and it is **not ported** —
      the one knob left on the table here, as Dot Grid's *Offset* was. Five fields, six knobs of theirs, and the margin
      is worth the least on a wallpaper whose block is the whole picture.
    - **Their *Spacing* is a fraction of the cell, not pixels** — measured at 84px for 6 tiles and 44px for 16, same
      setting. Ours reads it off `sqrt(area / count)`, so the grout means the same thing at any count and any frame.
    - **The count range is a departure, and it is the panel's fault.** Theirs is `1..100` opening at **16**; every knob
      in our panel opens at `0.5`, which on that range is 51 tiles — a texture, where the design's appeal is a composed
      handful. Ours is `2..40` so the midpoint lands near theirs. That is the third design to want **per-design
      defaults**, after Facets' leading and Dot Grid's margin, and it is now the clearest thing left in the studio.
    - **Two extractions fell out, both on their second consumer.** `GlassCut.inset` is gart's own `inset` — its
      `glasscut.kt` header lists "grout insets" beside the clipping this file was ported for, so the consumer simply
      arrived; note its everted-sliver guard has a **hole gart shares**, since a shape inset past half its width flips
      across *both* axes and the two winding reversals cancel, which no area test can see (ours adds the size
      precondition). And `RampTones` is the "tones on the ramp above stop 0, never fewer than three" arithmetic that
      Dot Grid and Flowing Blobs each arrived at after the default color mode drew them flat — this design would have
      been the third, so it now lives in one place.
  - **W11j — Vitrall. ✅ (2026-09-01)** Two designs were driven here, because the verdict table mapped *both* of
    them onto our Voronoi, and it was wrong about both.
    - **Their *Modern Mosaic* is a packing of rounded rectangles**, with a wide grout and corners that
      *Irregularity* pushes off square — nothing like a Voronoi, and much nearer our **Mondrian**. Left for a slice
      of its own; the checklist now points it at the right design of ours.
    - **Their *Vitrall* cuts the frame with edge-to-edge chords**, so its panes are long shards and slender wedges
      beside squat quadrilaterals. That is the difference from a Voronoi in one sentence: a Voronoi builds cells
      *around points* and every cell comes out a compact blob of roughly its neighbours' size. Drive their *Density*
      to `1` and a **single chord crosses the whole frame**, which no point-based diagram can do.
    - So it is a design of its own — **`VITRALL`, catalog 27** — and our Voronoi keeps its own name, which it now
      actually deserves. It is the third of these splits (Bauhaus→Mondrian, DotGrid→Halftone), and the rule holds:
      when ours turns out to be a *different* design, keep it and build theirs beside it.
    - **The subdivision is recursive and area-weighted**, one pane cut at a time with probability rising as a power
      of its area. Splitting the largest every time gives an even honeycomb; splitting a uniformly-chosen one leaves
      one huge pane untouched. A cut is sometimes taken **parallel to the pane's longest edge**, which is where their
      runs of parallel strips come from.
    - **Curves are a cut against a circle, and the hairline is answered by symmetry.** *(Corrected 2026-09-01 —
      this bullet previously claimed a plane warp, which was invented rather than observed and was never written;
      what shipped under the name "Curves" was angle jitter. See "Read gart first" above.)* `GlassCut.bowAbout`
      clips the pane against the circle twice, once keeping each side; both calls sample the arc from the *same*
      crossings over the *same* sweep magnitude in opposite directions, so the two panes carry the same arc vertices
      reversed and weld along it. Two consequences only the render and the tests showed: a bowed pane is **not
      convex**, so a later straight cut can cross it four times, and a half-plane clip then welds two of three pieces
      into a self-intersecting loop whose area is *plausible and wrong* — the subdivision stops terminating on area
      and returns thousands of panes of thousands of vertices. Both cut operations now **refuse** a cut that would
      not leave exactly two pieces, and the caller retries. And the arc's sampling needs a **cap** (48 segments),
      because every bow's samples stay on a pane that may be cut again and a repeatedly-shaved pane accumulates them
      until the heap goes.
    - **Every pane is filled with a gradient, at every setting of every knob of theirs**, and that is most of why the
      glass reads as material rather than as flat cells. Ours puts the strength on `depth` as *Glass*; the gradient
      spans the **pane's own** extent, so a small pane gets the whole sweep instead of looking flat beside a large
      one, and its angle and strength are the pane's own — gart draws each piece catching the light its own way.
    - **The came rim is a translucent black wash, and getting that wrong is what made *Glass* read as *Blur*.**
      gart's is black at alpha **74/255**, a blurred stroke `2.6 × lead` wide clipped to the pane, so the glass
      thickens toward the lead. Ours had it at the palette's darkest stop, **fully opaque**: a 34px opaque blurred
      band inside every pane on a 1080-wide frame, which on an average pane eats about 40% of it and reads as a dark
      smudge over the whole window. It was also binary on `glass > 0`, so the bottom of the knob already got the full
      rim. Now black and alpha-scaled by the knob — black is also what makes it darken a *bright* pane, where a wash
      of the darkest stop lightens one.
    - **A tone that runs off the ramp is reflected, not clamped** (gart's `uAt`, at `0.7` of the overshoot). Clamping
      piles every overshooting pane onto the *same* end stop, which shows up as large flat runs of one color exactly
      where the field is most interesting.
    - Knobs: `density` → **Panes** (12..160), `scale` → **Leading**, `irregularity` → **Curves**, `depth` → **Glass**,
      `variant` → **Colors** (Vertical / Scattered, their *Color distribution*). Their *Slices* and *Randomness* fold
      into the subdivision as fixed properties — driven to both ends, *Slices* moved the picture barely at all.
      Their **Color mode** tab is our global one, and its count in the inventory above was one short.
  - **W11i — Flowing Blobs. ✅ (2026-09-01)** The verdict table's own line — *"ours hard onion rings, theirs
    smooth"* — was right about the symptom and wrong about everything behind it, which is the argument for driving a
    design rather than reading a note about it.
    - **Their *Complexity* is a domain warp's frequency, not a count of blobs**, and the render is what proves it:
      drive it from `4` to `40` and the *same two or three systems stay in the same corners of the frame*, growing
      from smooth concentric rings into convoluted sinuous ridges. Nothing is added; the shapes are distorted. Ours
      had read it as the charge count — a count that topped out at **nine** — so no setting of ours could reach their
      default, which sits at the **top** of their range. Rebuilt as three fixed charges read through a warp; the
      frequency is `density` and the amplitude is `irregularity`, which is a split theirs does not have and which
      buys a genuine rigid end (at `0` the design is smooth nested ovals, exactly what the charges say).
    - **Their *Shades* is a band count, and ours was the palette's length.** One band per stop means the *default*
      color mode — which reduces the palette to two — draws two bands: a ground with lumps on it. That is the design
      dead at its own default, the same failure Dot Grid had in W11e, and the same fix: the count is its own knob and
      the bands are rungs on the **interpolated ramp**.
    - **Their *Shadow* is a paper-cut, and it is not directional.** Each band darkens along its boundary with the band
      *above* it and recovers across itself, so the layers read as stacked sheets. Measured rather than guessed: the
      multiply is `0.57` at that boundary and the recovery fits `f^2.5` (a square is visibly too wide, a cube too
      narrow). The dark rim hugs the upper layer's edge on **every** side of a blob — scan across one and the shadow
      is on the high-field side whichever way the field is running — which is what a stack does and a light source
      does not. It is `depth`'s second consumer, one slice after that field arrived.
    - **The offline renderer earned its place again.** Four candidate mappings were rendered in Python and compared
      side by side before a line of Kotlin changed; the first two — a soft roll and a hard clip over a scattered
      count of charges — both matched the reference's *histogram* while getting the morphology plainly wrong, which a
      histogram cannot tell you and a 90-second emulator round trip would have taken all afternoon to.
    - Knobs: `density` → **Complexity** (a [AmountKnob.Fraction], for the plasma's reason — three charges are drawn
      whatever it says), `irregularity` → **Distortion**, `scale` → **Thickness** (bands, running the other way),
      `depth` → **Shadow**, `variant` → **Spread** (their *Contrast*, at three points). Their fourth color mode,
      **Stroke** (outline only), is still missing from `WallpaperColorMode` and is a studio-wide gap rather than this
      design's.
  - **W11h — Triangular Facets. ✅ (2026-09-01)** The identity finding a fourth time, and this one is about *color*
    rather than geometry: the mesh was close, the picture was not the same picture.
    - **Their color is a two-dimensional field of areas; ours was the palette read at a facet's height.** A ramp down
      the frame with corners cut into it is a striped gradient, not a low-poly field, and no amount of tuning gets
      from one to the other. **What settles it is measuring a path, not looking at one:** sampling the render along a
      line from the middle of one region to the middle of another gives a *straight line in RGB between those two
      stops* — it never visits the stops that sit between them in the palette, which is exactly what a scalar field
      read through a ramp would have to do. So the stops are laid over the frame as **areas** and blended, which is
      also what their *Distribution: **Area*** is named for. Ours is now a coarse lattice of nodes each holding one
      stop, blended bilinearly and sampled at each facet's centroid — the same construction as the mesh gradient,
      which is in a real sense this design unfaceted, so the sampler was extracted as `ColorLattice` on its second
      consumer. The lattice is **fixed and coarse**, independent of the resolution knob, for W11g's reason.
    - **Their *Tridimensionality* is what makes a facet field faceted, and it needed a field of its own.** With the
      relief at `0` their render is a plain quantized gradient — the right geometry and the right colors and still not
      the design. It is the *depth* family, the last one in the inventory above with nowhere to live, so
      `DesignParams` gained a sixth field **`depth`** exactly as Ribbons' spread gave it `scale`. Ours gives every
      lattice point a height from a noise sampled in **lattice** coordinates (one unit per cell, so a swell spans a
      couple of facets however fine the mesh) and scales each facet's channels by how far its plane tilts toward a
      fixed light. Heights in **cell units** rather than pixels is what keeps the same depth lighting a coarse mesh
      and a fine one equally.
    - **Their *Thickness* is not a stroke — it insets every facet.** Wound up, the facets shrink to specks on the
      ground; at a sixth of its travel it is leaded glass with an even line between every pair. Ours takes it on
      `scale` (it is plainly the spacing family) as **Leading**, a true uniform inset — the triangle scaled about its
      **incenter** by `(r - inset) / r`, since scaling about the centroid moves each edge in by a different amount and
      the line would visibly thicken around the wider facets.
    - **Their *Distribution* and *Randomness* are one axis, and the app says so by hiding one under the other.**
      Switching *Distribution* to **Random** makes the *Randomness* tab **disappear** — absent, not disabled, the same
      rule this codebase keeps. So ours is a single `variant`, **Colors: Field · Speckled · Scattered**, where
      *Speckled* pulls each facet part of the way toward a random stop and *Scattered* goes all the way, which is
      their *Random*. (Their *Randomness* is a departure along the palette, not a brightness — an orange facet appears
      in a teal region — which is a different thing from the relief and is why both exist.)
    - **At Distortion 0 their diagonal is uniform, not alternating**, which is what gives the rigid end a clean quilt
      where ours drew pinwheels. Ours now splits each cell along its **shorter** diagonal — the cheap local form of a
      Delaunay flip, equal on a rigid lattice so every cell agrees, and the thing that stops a badly stretched quad
      being cut the long way into two slivers. It is also why the jitter can be halved to `0.55` of a cell and still
      shatter properly.
    - **Their border points slide *along* the frame's edge.** Ours pinned both coordinates, which left a ruled frame
      around an otherwise organic field — only the edge cells kept their exact lattice width. Zeroing just the
      component that would leave the frame keeps the tiling exact and lets the edge break up with everything else.
    - Knobs: `density` → **Resolution** (3..20 on the **long** axis, cells square, theirs exactly), `irregularity` →
      **Distortion**, `scale` → **Leading**, `depth` → **Relief**, `variant` → **Colors**. One departure worth
      knowing: **theirs opens with no leading and ours cannot**, because the panel has a single `0.5` default for
      every knob of every design. The response is cubed so that `0.5` is a hairline rather than a cream web, but the
      real fix is the **per-design defaults** W7 and W10 both deferred, and this is the clearest consumer yet.
  - **W11g — Mesh Gradient. ✅ (2026-09-01)** Two findings, and the second is about the *blend* rather than the knobs.
    - **Their *Color distribution* is a layout, not a weighting** — `Random`, `Corner interpolation`, **`Linear
      bottom`** (their default). That default is the whole difference in the design: the palette runs *down the frame*
      as one progression, where ours cycled the stops through the control points and produced a quilt of blotches with
      mud between them. Ours takes all three as `variant` — **Vertical / Corners / Scattered** — with the cycle kept as
      the last, for a palette that is a set of accents rather than a ramp. Note the name collides with Confetti's
      *Color distribution*, which is a weighting; they share nothing but the word.
    - **Their blend is a bilinear mesh, not inverse-distance weighting, and the rigid end is the proof.** At Jitter 0
      their render is a *mathematically exact* gradient. Distance weighting cannot do that at any setting: every node
      keeps a core, and between two same-colored nodes a pixel sits fractionally further from both, so the neighbouring
      row leaks in and the field **beads into vertical stripes**. Softening enough to hide the beading also flattens
      the design into a plain gradient — confirmed by sweeping the parameter space offline rather than by build cycles.
      So the generator was rewritten: a lattice of colored nodes, bilinearly blended, sampled through a *second* field
      of node displacements. It is also `O(1)` per pixel instead of `O(nodes)`.
    - **The warp lattice has to be coarser than the color lattice**, and is fixed at 3 patches. Tied to the colors, a
      hard-pushed node makes a tongue one cell wide, and a tongue as tall as it is wide reads as a **drip** hanging off
      a band rather than as a broad lobe. Fixing it also stops the density knob quietly changing what the warp knob
      does.
    - **A displacement field must be C1 where a color field need not be.** Sampled bilinearly, the warp's slope jumps
      at every node line and those jumps land in the picture as hard creases along the lattice. Easing the cell
      parameter (`3t² - 2t³`) fixes it; the color field is left bilinear, since a kink in a monotone ramp is invisible.
    - Knobs: `density` → **Grid** (2..8 patches), `scale` → **Softness** (each node drawn toward its neighbours' mean,
      twice — a low pass on the colors, since the blend has nothing left to soften), `irregularity` → **Warp**,
      `variant` → **Colors**. Their separate Rows and Columns collapse to one square lattice, as Dot Grid's did.
      `PointScatter` lost a consumer on the way: this needs the lattice's *corners* with the edges pinned, which is a
      different placement wearing the same word.
  - **W11f — Confetti Dots. ✅ (2026-09-01)** Not an identity finding this time: theirs and ours are the same design,
    and ours was simply doing four things worse. All four are now theirs.
    - **A square lattice turned ~12°, jittered — not a Poisson-disk scatter.** The turn is the part that carries the
      look and the easiest to overlook: axis-aligned, a lattice announces itself through any amount of jitter, because
      the eye finds the horizontals. Turned a little, the same lattice reads as an even sprinkle. Ours had built the
      *harder* thing (dart-throwing with a shrinking minimum distance) to arrive somewhere worse, and it had no rigid
      end for its knob either — uniformly-random points cannot be made *more* even, so there was no lattice to snap
      back to. Pitch = long side / Resolution, which is **5..24** there, default 15; ours matches.
    - **The palette is spent unevenly, and this is where the restraint lives.** Their *Color distribution* is a
      segmented ratio preset — `100/100/…`, `100/66/33`, `100/50/25` (default), `100/33/11` — the rate at which the
      pick weight falls off down the stops. At `100/50/25` the first ink is the field and the last is a rare accent; at
      `100/100/…` it is our old even cycle, and it looks exactly as loud as ours did. **Ours takes the falloff as a
      fixed property rather than a knob**, because the color modes already reduce the palette and a weighting over the
      single ink a two-stop palette leaves would be a control that does nothing.
    - **The ground is stop 0, whatever stop 0 is.** A shuffle to a dark-first palette turned their ground near-black
      with bright dots, so it is not "light ground" as a rule — it is the *first* stop. Ours had been using the
      **darkest**, which is why it read as a dark bold confetti against everything else in the catalog.
    - **Focus distance + Focus range are a depth of field, and it is the first real depth in our catalog.** A disc's
      size *is* its distance, and discs outside the focal band blur. Ours folds their two continuous knobs into
      `variant` as **Flat / Near / Far**. Two findings, both of which rendered as a dead knob first:
      the blur must be measured against the frame's **largest** disc and not the disc's own radius (a lens's circle of
      confusion does not care how big the object is, so scaling by the object makes the small discs — the exact ones
      *Near* exists to soften — blur by a fraction of a pixel); and the depth must be **ranked** over the spread that
      actually exists rather than read off the radii, or a mild scatter leaves the knob a fraction of its range.
    - **Their two organic knobs became one.** *Offset distortion* and *Radius variation* are the same question twice, so
      ours joins them as **Scatter**, which is what gives it a genuine rigid end: `0` is an even lattice of identical
      discs — a polka-dot pattern — and `1` a sprinkle of specks and boulders. A consequence, and it is the honest one:
      with no size spread there is no depth, so *Focus* renders sharp at `Scatter = 0`.
  - **W11e — Dot Grid. ✅ (2026-09-01)** Bauhaus's finding, a third time: not quality, **identity**. Theirs is a
    *contained* block of uniform rounded tiles whose **color** steps through the palette in flat bands down the rows;
    ours was a **halftone** — a Perlin field driving each dot's **size**, filling the frame. Same lattice, opposite
    variable, opposite composition. So the halftone split off as **`HALFTONE`**, kept as it was, and **`DOT_GRID`** was
    rebuilt as theirs — catalog **26**.
    - **Their layout, measured off thirty renders rather than guessed.** The block sits in a box that is the frame
      inset by *Spacing* (`0` → full bleed, `50` → the middle half), and the cell is
      `min(boxW / (cols - 1 + fill), boxH / (rows - 1 + fill))` where `fill` is *Square size* as a fraction. The
      `- 1 + fill` is the whole trick and it is what a plausible fit gets wrong: it is the **painted extent** that is
      fitted to the box, not the cell count, so the block's ink lands on its margin rather than half a tile short of
      it. Their degenerate case is the proof — 2 rows put the two dots at exactly `H/3` and `2H/3`, which is
      `1280 / (2 - 1 + 0.5)`, and no cell-count fit produces that number.
    - **Rows and columns are independent there and derived here.** Theirs are two knobs, so the block's proportion is
      the user's; ours takes *Columns* from `density` and fills the box with however many square cells reach the
      bottom — Bauhaus's rule, and the same reason: five knobs to spend, and the block's proportion is worth less than
      the margin. A consequence to know: a wide *Look* must keep the **square** cell's row count, or refilling the box
      triples the rows, closes the vertical gaps and the bars read as columns.
    - **Their Irregularity is a *color* dither, not a position jitter** — the geometry does not move by a pixel at
      either extreme; the noise pushes tiles across a band seam. Ours does the same, with one addition: pushed off the
      *light* end the tile is dropped instead of clamped, which erodes the block's top edge into a fade.
    - **Their palette carries the ground as a separate first stop**, divided off in the strip; the bands walk the
      stops above it. Ours reads the ramp above stop 0 at `n - 1` rungs but **never fewer than three**, and that floor
      is the whole reason the design survives its own default: `BICHROMATIC` leaves two stops, one rung and no ramp at
      all — a flat block with nothing for the dither to trade. Three rungs of a two-stop palette are three real tones,
      and where the palette is long enough the rungs land on its own stops exactly (four rungs of five stops *are* its
      four stops), which is what `DotGridGeneratorTest` pins.
    - **Their eight knobs onto our five.** *Rows*+*Columns* → `density` as **Columns**; *Spacing* → `scale` as
      **Margin**; *Irregularity* → `irregularity` as **Dither**; *Corner radius* + *Square size* + *Aspect ratio* →
      `variant` as **Look** (Dots · Rounded · Squares · Bars · Tiles), because those three are not independently
      useful — a circle filling half its cell and a square filling all of it are two looks, not four sliders.
      **Not ported: *Offset***, their hold-to-repeat nudge that walks the block off center. It wants a two-axis
      control neither `DesignParams` nor the panel has, and it is the one knob with real value left on the table here
      — a launcher wallpaper's motif sitting dead center is a motif under the icons.
  - **W11a — Bauhaus. ✅ (2026-08-31)** The first, and it set the pattern for the rest: the gap was not quality but
    *identity*. Ours was a Mondrian (recursive rects, ruled in the darkest stop) under the Bauhaus name; theirs is an
    even lattice of square tiles each carrying one flat arc. So the Mondrian became its own **`MONDRIAN`** design,
    unchanged, and **`BAUHAUS`** was rebuilt as the lattice — catalog **25**. Shapes are drawn at *cell scale* (a
    quarter disc has the cell's full width for a radius), which is what makes two neighbours read as one larger
    circle; `irregularity` became *variety* (a strict repeat at `0`, a mixed field at `1`) and `variant` became the
    ground treatment (per-tile grounds, or floating on one with plain tiles as bare negative space — their "Tile
    background"). Two things only the render showed: a shape must sit **two** palette stops from its ground, not
    merely a different one, or it is a tone away and has to be hunted for; and square cells cannot divide the height,
    so the overhang is split across both edges rather than left as a sliver row.
  - **W11d — Bauhaus, second pass. ✅ (2026-08-31)** The same treatment W11c gave Ribbons, and it corrected two things.
    **The vocabulary is a quarter disc or nothing** — pushing their *Plain tiles* to maximum isolates the survivors and
    every one is a quarter; the halves and whole circles are **emergent**, formed where neighbours anchor at a shared
    corner. Ours drew them as tiles of their own, which produces shapes sitting *inside* a cell relating to nothing
    beside them: busier to look at and actually less varied. **Coverage is its own knob** — theirs is a *fraction* of
    tiles left plain (it still applies at Resolution 20, so it is not a count), where ours had it pinned inside the
    shape vocabulary at a quarter when variety was full and at *nothing* when variety was zero, putting "a strict
    repeat with a few tiles blank" out of reach. It moved to `scale`, the field W11c added — *Coverage* is a member of
    that same spacing/gaps family. `irregularity` narrows to the turns, a knob theirs does not have. Their Resolution
    runs **4..20** on the long axis (≈1.8–9 columns here); ours widened to 2..9.
  - **W11c — Ribbons, second pass. ✅ (2026-08-31)** Prompted by the obvious question after W11b: *did you try every
    knob theirs has, and what about the glow?* Both were gaps. **Knobs:** theirs exposes four and we exposed two; the
    two missing (*Start area* / *End area*) are the pair that takes the bundle from a tight fan to a full-frame weave.
    They are re-cut here as **Spread** (how wide) and **Shape** (Fan / Weave — which end is tight), which reaches the
    same square of possibilities while asking the questions a person actually has. Spread needed a home, so
    `DesignParams` grew its fifth field, **`scale`** — the *spacing / gaps* family this doc planned from the start,
    and a count's independent partner (twenty small dots and twenty large ones are different pictures). **Glow:** the
    W11b note claiming the Vignette filter covers it was wrong — a vignette darkens corners uniformly and knows
    nothing about where the bundle is. Measuring theirs pixel by pixel settles the mechanism: the **lines are
    hard-edged** (no falloff at all) and the **ground** brightens along a wide ridge following the curve. Ours now
    lays progressively wider, barely opaque copies of each path under the crisp ones, calibrated against the
    reference's own measurements rather than by eye — its ground peaks at **3.1×** its base, the first
    plausible-looking alphas gave **5.8×** and read as a milky smear, and ours now measures **3.05×**.
  - **W11b — Neon Ribbons. ✅ (2026-08-31)** The second, and it **reverses W8's decision to skip this design**. That
    call rested on Flow Lines already covering the fine-line niche; driving theirs shows the two are different looks —
    Flow Lines combs the *whole frame*, Neon Ribbons draws **one** gesture and leaves the rest empty. Ours was a few
    thick outlined streamlines through the flow field, scattered and clipped, and was the catalog's weakest design.
    Now: one cubic spine, its lines offset perpendicular by a spread that *grows* along it, so they nest into a fan
    that converges at one end and opens at the other. `irregularity` is the splay (a ruled sheaf at `0`, a twisted
    bundle at `1`). Two findings: a splay applied *along* the sweep only re-parameterizes the curve and looks like a
    dead knob — it has to act across it; and the ramp the bundle is colored along must stop short of the ground, which
    is the same legibility rule the tile grid needed, now extracted as **`StopContrast`** on its second consumer.
- **Depth pass (fold in) —** Shadow / Blend mode / Refraction where cheap; it is a lot of their premium feel.

**Guiding rule: default to restraint.** Sparse, soft, two-tone. Loud is a variant the user opts into, never the default.

## Read gart first — before designing anything

**`../gart` is a working implementation of most of these designs, by someone who solved these problems already. Open
it before deciding how ours should work.** It is a sibling of this repo (`gart` beside `Morphic-Launcher-2`); `arts/`
is organized by family, so `find . -ipath '*<design>*'` lands on the file. This is not an optional cross-check —
more than once now a mechanism has been *invented* here, written up in this doc as though observed, and shipped,
while gart's own source sat one directory away with the real answer in it.

**W11j is the worked example, and it is worth being concrete about the cost.** Its note in this doc claimed *"curves
are done by warping the plane, not by clipping against curves"*, and gave a reason: clipping against a curve means
solving for the crossings, and a crossing a rounding apart in the two panes is a hairline of ground between them.
The reason is real; the conclusion drawn from it was not. gart's `arts/lines/vitrali/glasscut.kt` **does** clip
against the circle, and answers the hairline **by symmetry** — it clips the same circle twice, once keeping each
side, so both panes sample the same arc from the same crossings over the same sweep, in opposite directions. No
tolerance, no warp. What shipped instead was a knob labeled *Curves* that produced no curves at all, only angle
jitter; this doc described a plane warp that was never written; and the render had a straight edge everywhere.

So, per design, in this order:

1. **Find it in gart** and read the whole art file plus whatever toolkit it pulls in.
2. **Drive the reference** (the rule below) to learn what the *knobs* mean — gart has its own parameters, not theirs.
3. **Write ours from gart's mechanism, with the reference's knob semantics.** Where the two disagree, gart is usually
   right about *how* and the reference about *what the user is choosing*.
4. **Where you depart from gart, say so in the code and say why.** Both of W11j's departures are named there: the
   arc's step count is capped (gart samples an arc into thousands of segments and has a desktop heap, where here
   every bow's samples land on a pane that may be cut again), and *Curves* gates the curved glazing courses that gart
   leaves unconditional (the reference's knob has to leave every cut straight at `0`). A departure nobody wrote down
   is the thing that gets silently reinvented next time.

**Method rule, learned the hard way in W11c/W11d: drive every one of their knobs to *both* extremes before concluding.**
Confirming a design's model and stopping is what hid Ribbons' two missing knobs and its glow, and what hid the fact
that Bauhaus draws exactly one shape. The extremes are also the cheapest probe there is — their *Plain tiles* at
maximum isolates the vocabulary, their *Resolution* at both ends gives the range, and a knob that changes nothing at
either end is a knob you have misread. Where a look is in question, **measure pixels** rather than trusting the eye: a
scanline across their Ribbons showed hard-edged strokes where it looked like a per-line glow, and a brightness grid
found the lit ground that actually causes it.

**The mechanics of both rules live in [tools/refdrive/](../tools/refdrive/) — read its README before driving anything.**
The rules above are the *what*; that is the *how*, and it is there because it was rediscovered from scratch in three
consecutive slices. Two of its traps produce a **wrong reading** rather than a visible failure: `adb` from Git Bash
silently mangles device paths (so the folder you thought you cleared is untouched and every render you pull is the
previous run's), and **not every knob is a ruler** — *Offset* is a four-arrow nudge pad, and swiping one changes
nothing in a way that reads exactly like "this knob does nothing". `measure.py` is the measuring half: `scan`, `tiles`,
`grout` and `slope`, each noting which finding it settled, and all four sharing the one part that is genuinely hard —
that the ground is **not** the most common color (a wide band beats it) but the one touching nearly every row *and*
column.

---

## Open questions

1. **`DesignParams` shape** — flat optional fields (simple, some meaningless per design) vs a sealed per-design type
   (honest, more machinery). Their UI implies a flat bag with per-design *labels*; lean that way, with a small typed
   extension only where a design has genuinely bespoke knobs (Orbs, Path style).
2. **Ruler sliders expose raw integers** (Count = 16, not a 0..1 density). Do we surface integers too, or keep
   normalized floats and label them? Integers read as more direct/tunable.
3. **How many of the missing 9 designs to build**, and in what order — the thin-line family (Ribbon Flow / Flow Lines /
   Polygon Cascade) is the highest visual return; the calm staples are the cheapest.
4. **Live Wallpaper** — they ship a Live Wallpaper tab. Still deferred, but it is first-class in their product.
