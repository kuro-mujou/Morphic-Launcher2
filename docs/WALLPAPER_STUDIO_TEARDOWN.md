# Smart Launcher Wallpaper Studio — live teardown & our gap analysis

**Captured 2026-08-31** by driving the installed `net.smartlauncher.wallpaperstudio` on the emulator over adb — every
design opened, every design's Style panel scrolled through tab by tab (screenshots in the session scratchpad, not
committed: it is their copyrighted UI). This is what [WALLPAPER_STUDIO_PLAN.md](WALLPAPER_STUDIO_PLAN.md) was missing.
W5 built sixteen generators and they render correctly, but two things are wrong and neither is the maths:

1. **Every design has ~6 tunable parameters; ours have one** (`density`). This is not a small gap — it is the whole
   product. See the full inventory below.
2. **The styling defaults are loud where theirs are restrained.** Aesthetic principles in the second half.

Companion to the plan (the *what/when*) and [GART_HARVEST.md](GART_HARVEST.md) (engine source). Read before the next
wallpaper slice.

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
| 1 | Diagonal Bands | Count · Rotation · Coverage · Spacing · Offset · Variation |
| 2 | Modern Mosaic | Count · Spacing · Frame · Ratio · Roundness · Irregularity |
| 3 | Gradient Columns | Rotation · Columns · Irregularity · Start column · Shadow · Progression smoothness |
| 4 | Flowing Blobs | **Color mode** · Shades · Complexity · Contrast · Shadow |
| 5 | Triangular Facets | Resolution · Distortion · **Thickness** · **Distribution** · Randomness · Tridimensionality — **six** (W11h). *Resolution* counts cells along the **long** axis, **3..20** (default 10), cells square. *Distortion* **0..100** (default 50) jitters the lattice; at `0` every cell takes the *same* diagonal. *Thickness* **0..100** (default **0**) is not a stroke — it **insets every facet**, so the ground shows between them as leading; at 100 the facets are specks. *Distribution* is a two-option segmented control, **Random** / **Area** (default), and it is a **color** distribution, not a point one: *Area* paints a smooth two-dimensional field, *Random* gives each facet a flat random stop. *Randomness* **0..100** (default 25) is how far a facet departs from that field — **and the tab disappears entirely under *Random***, which is the app saying the two are one axis. *Tridimensionality* **0..30** (default 5) is a per-facet brightness, the relief. The ground is **stop 0**, which the field never paints with |
| 6 | Bauhaus Blocks | Resolution · Plain tiles · Tile background — *Resolution* counts cells along the **long** axis, **4..20** (cells are square, so the columns fall out and the grid bleeds sideways); *Plain tiles* is a **fraction** left undecorated, not a toggle and not a count; *Tile background* is Off/On. The tile vocabulary is **a quarter disc or nothing** — halves and circles are emergent |
| 7 | Confetti Dots | Resolution · Offset distortion · Max radius · Radius variation · **Color distribution** · Focus distance · **Focus range** — **seven** (W11f). The lattice is a square grid *turned ~12°* off the frame, pitch = long side / Resolution (**5..24**, default 15). *Color distribution* is a **segmented ratio preset** — `100/100/…`, `100/66/33`, **`100/50/25`** (default), `100/33/11` — i.e. how fast the pick weight falls off down the stops, not a hue choice. *Focus distance* + *Focus range* are a real **depth of field**: a disc's size is its distance, and everything outside the focal band is blurred |
| 8 | Mesh Gradient | **Rows** · Columns · Jitter · **Color distribution** · Softness — **five** (W11g). Rows and Columns are both **2..10**, default 4×4. *Color distribution* here is a **layout**, not a weighting: `Random` · `Corner interpolation` · **`Linear bottom`** (default) — and the default is why theirs reads as a soft progression down the frame where ours read as a quilt. At Jitter 0 the render is a **mathematically exact** gradient, which is what proves the blend is a bilinear mesh rather than distance-weighted points |
| 9 | Dot Grid | Rows · Columns · Irregularity · Corner radius · Square size · Aspect ratio · **Spacing · Offset** — **eight**, not six (W11e; the last two are past the fold in the tab row and were missed on the first pass). *Spacing* is the margin around the whole block, not the gap between tiles; *Offset* is a four-arrow **nudge**, hold-to-repeat, that walks the block off center; *Aspect ratio* is a **segmented** 1/1 · Golden · 2/1 · 4/1 |
| 10 | Layered Waves | Count · Spacing · Distortion · Palette gradients |
| 11 | Neon Ribbons | Count · Variation · Start area · End area — one bundle of curves sharing a spine; *Start/End area* are **percentages** (1.3% against 5% by default) and that asymmetry is the fan; *Variation* splays the bundle rather than reshaping the gesture |
| 12 | Wave Dividers | Rotation · Count · Irregularity · Wideness · Waves · Offset |
| 13 | Vitrall | Density · Spacing · Curves · Slices · **Color distribution** · Randomness |
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
  room each takes. **Built as `scale` (W11c)**, on Ribbons' spread; the designs whose element size is still fixed
  (Confetti's radius, Soft Overlaps' radius, Rounded Tiles' margin) are where it pays off next. Dot Grid spends it on
  the **margin** instead (W11e) — its own tile size rides on the `variant` look, because a contained block and a
  full-bleed field is the bigger of the two questions by a distance.
- **Orientation** — *Rotation, Direction, Delta rotation, Rotate delta*. Which way it points / turns.
- **Organic noise** — *Irregularity, Distortion, Jitter, Randomness, Variation, Offset distortion*. The single most
  common family — the knob that takes a rigid generator to an organic one. **Ours have none of this**, which is part of
  why ours read as mechanical.
- **Color** — *Color mode, Color distribution, Shades, Palette gradients*. How the palette is applied (see below).
  **Watch the name: *Color distribution* means two unrelated things.** On Confetti it is a *weighting* (a ratio
  preset deciding how often each stop is picked); on Mesh it is a *layout* (Random / Corner interpolation /
  Linear bottom, deciding where each stop goes). Reading one for the other loses the design.
- **Stroke / shape** — *Thickness, Roundness, Corner radius, Curves, Wideness*. Line weight and corner softness.
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

| Theirs | Ours | Verdict & fix |
|---|---|---|
| Topography | **Contour** | Biggest gap. Add **Contour-lines variant** (thin lines, their default + community favorite) alongside our filled "Embossed". |
| Flowing Blobs | **Metaballs** | Ours hard onion rings. Theirs smooth + a Color mode (Mono/Bi) + Shadow. Soften, add color mode. |
| Confetti Dots | **Confetti** | ✅ **W11f.** Re-based on their turned, jittered lattice; the ground moved to the palette's **light** end (theirs is stop 0, whatever that is), the palette is now spent with a geometric falloff so the last stops are rare accents, and it gained a **depth of field**. The Poisson sampler is gone. |
| Neon Ribbons | **Ribbons** | ✅ **W11b.** Rebuilt as one fanning bundle of fine curves. The W8 decision to skip this was wrong: Flow Lines combs the whole frame, theirs draws *one* gesture — not the same look. |
| Bauhaus Blocks | **Bauhaus** | ✅ **W11a**, refined in **W11d**. Rebuilt as their arc lattice (what ours had been — recursive rects, ruled — was a *Mondrian*, and is now a design of that name), then narrowed to their real vocabulary: one quarter disc, everything else emergent, with coverage on its own knob. |
| Dot Grid | **DotGrid** | ✅ **W11e.** Same identity finding as Bauhaus: ours was a *halftone* (a field driving each dot's **size**, full-bleed) under their name, and theirs is a **contained** lattice of uniform tiles where only the **color** moves. The halftone split off as its own design; DotGrid was rebuilt as theirs. |
| — | **Halftone** | Ours only, split out of DotGrid by W11e — the noise-sized dot screen, kept unchanged. |
| Triangular Facets | **Facets** | ✅ **W11h.** The identity finding again: ours read the palette at a facet's *height*, theirs paints a **two-dimensional field of areas** — proved by measuring a path between two regions and finding a straight RGB line that skips the stops in between. Rebuilt on a coarse color lattice (`ColorLattice`, shared with the mesh gradient), plus the **relief** (which is what `DesignParams.depth` arrived for), the **leading**, and a shorter-diagonal split that kills the slivers. |
| Mesh Gradient | **Mesh** | ✅ **W11g.** Grid + jitter landed in W7; W11g added the **Colors** layout (Vertical / Corners / Scattered) and **Softness**, and replaced inverse-distance weighting with a **warped bilinear mesh** — the only blend that is an exact gradient at the rigid end, as theirs is. |
| Layered Waves | **Waves** | Closest we have. Add Distortion + Palette-gradients toggle. |
| Modern Mosaic / Vitrall | **Voronoi** | Ours ≈ Modern Mosaic. Add **Vitrall** variant (curved slices, light leading) + Roundness/Irregularity. |
| Flow Field | **Flow** | Add **Orbs** (the moons), Style variant, lower density default. |
| Rounded Tiles | **Truchet** | Reasonable analog; theirs is diagonal rounded bars w/ Blend mode. |
| — | **Plasma, Rings, Rays** | Ours only (SL lacks). Keep, but give MONO/BICHROMATIC defaults — they are our loudest. |
| Diagonal Bands / Gradient Columns / Wave Dividers / Ribbed Glass | — | **Missing, all easy + restrained.** Calm staples. Diagonal Bands is trivial (Count/Rotation/Coverage/Spacing/Offset). |
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

**Method rule, learned the hard way in W11c/W11d: drive every one of their knobs to *both* extremes before concluding.**
Confirming a design's model and stopping is what hid Ribbons' two missing knobs and its glow, and what hid the fact
that Bauhaus draws exactly one shape. The extremes are also the cheapest probe there is — their *Plain tiles* at
maximum isolates the vocabulary, their *Resolution* at both ends gives the range, and a knob that changes nothing at
either end is a knob you have misread. Where a look is in question, **measure pixels** rather than trusting the eye: a
scanline across their Ribbons showed hard-edged strokes where it looked like a per-line glow, and a brightness grid
found the lit ground that actually causes it.

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
