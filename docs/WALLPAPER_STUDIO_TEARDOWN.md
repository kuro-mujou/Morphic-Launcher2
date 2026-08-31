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
slider or a segmented variant control below it. **Almost every design exposes six.** Our `DesignParams` is
`density: Float` + an unused `variant: Int`. That is the gap in one sentence.

### Full per-design parameter inventory (all 22, observed directly)

| # | Design | Parameters (tabs, in order) |
|---|---|---|
| 1 | Diagonal Bands | Count · Rotation · Coverage · Spacing · Offset · Variation |
| 2 | Modern Mosaic | Count · Spacing · Frame · Ratio · Roundness · Irregularity |
| 3 | Gradient Columns | Rotation · Columns · Irregularity · Start column · Shadow · Progression smoothness |
| 4 | Flowing Blobs | **Color mode** · Shades · Complexity · Contrast · Shadow |
| 5 | Triangular Facets | Resolution · Distortion · Thickness · Distribution · Randomness · Tridimensionality |
| 6 | Bauhaus Blocks | Resolution · Plain tiles · Tile background |
| 7 | Confetti Dots | Resolution · Offset distortion · Max radius · Radius variation · **Color distribution** · Focus distance |
| 8 | Mesh Gradient | Columns · Jitter · **Color distribution** · Softness |
| 9 | Dot Grid | Rows · Columns · Irregularity · Corner radius · Square size · Aspect ratio |
| 10 | Layered Waves | Count · Spacing · Distortion · Palette gradients |
| 11 | Neon Ribbons | Count · Variation · Start area · End area |
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
  `density`, but named per design and often exposed as a raw integer).
- **Spacing / gaps** — *Spacing, Margin, Offset, Coverage*. How much air between elements.
- **Orientation** — *Rotation, Direction, Delta rotation, Rotate delta*. Which way it points / turns.
- **Organic noise** — *Irregularity, Distortion, Jitter, Randomness, Variation, Offset distortion*. The single most
  common family — the knob that takes a rigid generator to an organic one. **Ours have none of this**, which is part of
  why ours read as mechanical.
- **Color** — *Color mode, Color distribution, Shades, Palette gradients*. How the palette is applied (see below).
- **Stroke / shape** — *Thickness, Roundness, Corner radius, Curves, Wideness*. Line weight and corner softness.
- **Rendering / depth** — *Blend mode, Contrast, Shadow, Refraction, Vibrancy, Tridimensionality, Real glass*. The
  lighting/translucency that gives their output *depth*. **Ours are all flat.**
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

- **Confetti is a *distorted grid*, not Poisson-disk.** Their tabs are Resolution + Offset distortion — a lattice
  pushed around, not dart-throwing. Ours built the (harder) Poisson sampler; theirs is simpler and just as even. Not
  wrong, but not what they do.
- **Mesh is a grid + jitter** (Columns + Jitter), not our random control points. Same look, simpler control.
- **Every design has an organic-noise knob.** Ours are deterministic-and-rigid with no way to loosen them.

---

## The five aesthetic principles behind their look

Styling defaults, not algorithms — this is why "some of ours are bad."

1. **Restraint & negative space.** Dark/white grounds, motif as accent, air around it (Confetti, Dot Grid, Neon
   Ribbons, Flow Lines, Topography-contour). Ours fill every pixel with saturated color.
2. **Thin-line rendering is a first-class family.** Topography (Contour-lines variant), Ribbon Flow, Flow Lines, Neon
   Ribbons, Polygon Cascade — fine strokes on a ground. **We have no thin-line renderer.** Highest-value visual add.
3. **Soft edges & smooth gradients.** Flowing Blobs, Mesh, Soft Overlaps, Layered Waves are liquid. Ours favor hard
   flat bands everywhere (Metaballs onion-rings, flat cells).
4. **Color mode, not "cycle everything."** Their harmony comes from defaulting to Mono/Bichromatic. Ours always
   Colorful.
5. **Tuned, sparse defaults + depth.** Every design opens tasteful, and most carry a Shadow/Blend/Refraction depth
   knob. Ours open at `density = 0.5`, full-palette, flat.

Plus: **Bauhaus uses real shapes** (quarter-circles, triangles — "Plain tiles" toggles the decoration); ours draws only
rectangles.

---

## Design-by-design: their 22, our 16, the verdict

| Theirs | Ours | Verdict & fix |
|---|---|---|
| Topography | **Contour** | Biggest gap. Add **Contour-lines variant** (thin lines, their default + community favorite) alongside our filled "Embossed". |
| Flowing Blobs | **Metaballs** | Ours hard onion rings. Theirs smooth + a Color mode (Mono/Bi) + Shadow. Soften, add color mode. |
| Confetti Dots | **Confetti** | Re-base on a **distorted grid** (Resolution + Offset distortion), tiny sparse dots, dark ground. Ours' Poisson is over-built and over-bold. |
| Neon Ribbons | **Ribbons** | Ours thick outlined. Theirs fine bundled lines + glow + Start/End area. Re-do. |
| Bauhaus Blocks | **Bauhaus** | Add the shape vocabulary + a "Plain tiles" toggle. |
| Dot Grid | **DotGrid** | Add Corner radius / Square size / Aspect ratio; contain it (negative space). |
| Triangular Facets | **Facets** | Add Distortion + Tridimensionality (shading); soften color. |
| Mesh Gradient | **Mesh** | Re-base on grid + jitter; add Color distribution + Softness. |
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
- **Depth pass (fold in) —** Shadow / Blend mode / Refraction where cheap; it is a lot of their premium feel.

**Guiding rule: default to restraint.** Sparse, soft, two-tone. Loud is a variant the user opts into, never the default.

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
