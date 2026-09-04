# Wallpaper Studio

**Status:** design draft, **nothing built** (2026-08-30). Drawn from a walkthrough of Smart Launcher's Wallpaper
Studio (`net.smartlauncher.wallpaperstudio`, driven over adb) and from the [gart harvest assessment](GART_HARVEST.md).
This is the *what and in what order*; it is not committed to slices yet, and the open questions at the end are real.

**Covers:** a built-in generative wallpaper editor — a sibling to the icon studio — plus the seam to the community
sharing feature (which stays deferred here).

**Why in-house:** Smart Launcher charges for launcher + icon studio + wallpaper studio as three apps (~3× to the
user). One Kotlin/Skia codebase serving both studios is the differentiator. See [[wallpaper-studio-plan]] memory and
CLAUDE.md's cost note.

**Companions:** [GART_HARVEST.md](GART_HARVEST.md) (the engine source), [ICON_ARCHITECTURE.md](ICON_ARCHITECTURE.md)
(the effect pipeline this reuses), [STATUS.md](STATUS.md) (`data:wallpaper`'s existing state), and — **read this before
the next wallpaper slice** — [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md), a live teardown of Smart
Launcher's studio (every design's ~6 parameters, the color-mode system, the aesthetic gap). It reframes W5: the sixteen
generators are the *engine*, but each of theirs carries ~6 tunable parameters where ours carry one, so the *studio* is
still largely ahead of us.

---

## The thesis: most of this is already built

The single most important finding of the walkthrough is that Smart Launcher's studio is **three axes over a live
preview** — a *design* (generator), a *palette*, and a stack of *filters* — and **we have already built two of the
three** while harvesting gart for the icon studio.

- **Filters** — their list is Ripple, Kaleidoscope, Pixelate, Progressive blur, Grain, Color grading, Scanlines,
  Chromatic aberration, Vignette, CRT curvature, Noise. Our `core:icon` `LayerEffect` pipeline already implements
  **Ripple, Pixelate, ProgressiveBlur, Grain, ChromaticSplit, Vignette, and the color grades (Color / Duotone /
  Tritone / Dither)** — most of the list, device-verified. A wallpaper filter is one of our per-pixel effect passes
  run on a *generated bitmap* instead of an icon.
- **Palette** — their color panel is an editable N-color palette strip, curated *suggested palettes*, shuffle, lock,
  and a per-slot picker. We have `ColorPalettes` (`core:designsystem`) and `MorphicColorPicker` already.
- **Design** — the ~22 generators are the **genuinely new** work, and they are exactly what **gart** is
  (Delaunay, Voronoi, flow fields, marching squares, Poisson, metaballs, …).

So the icon-studio harvest was not a detour: it pre-built the wallpaper studio's whole effect layer. The new work is
the **generative engine** and the **editor UI** around it.

---

## What Smart Launcher's studio actually is (the reference)

A live full-screen preview with save/apply/undo up top, a **Vertical / Squared** aspect toggle, and a bottom toolbar
of four panels:

1. **Designs** — swipe up/down (or a grid) cycles ~22 generators; every one renders in the active palette.
2. **Color** — an editable N-color palette strip (+add stop), **Suggested palettes**, **Shuffle**, **Lock** (pin the
   palette while swapping designs), per-slot picker (SV + hue + **opacity** + hex, Picker/Palette tabs).
3. **Style** — per-design parameters (Flow Field: a variant selector *Eclectic/Pearls* + *Density*).
4. **Filters** — post-process passes, each a toggle + parameters.

Plus a **Community** feed (Populars / New / Top week/month, author attribution, likes) — the sharing surface.

### The 22 designs, mapped to gart primitives

**Guessed from thumbnails, before any of them was driven — [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md)'s
design-by-design table supersedes every row it has reached.** Kept because the *grouping* is still how the work was
ordered, but the primitives are already wrong in the ones that have been measured: the Modern Mosaic is a recursive
subdivision rather than a Voronoi, the Polygon Cascade is a tween between two shapes rather than a tessellation at all,
Rounded Tiles is a fan of separate capsules rather than a tiling, and Ribbon Flow is a displaced rank of parallel lines
rather than anything traced through a flow field.

| Design | Gart primitive | Group |
|---|---|---|
| Triangular Facets | Delaunay triangulation | tessellation |
| Modern Mosaic, Vitrall | Voronoi (+ Lloyd relax for even cells) | tessellation |
| Polygon Cascade | Delaunay/Voronoi variant | tessellation |
| Flow Field, Neon Ribbons, Ribbon Flow, Flow Lines, Shape Trail | flow field + streamline tracer (curl noise) | flow |
| Topography | contour tracing (marching squares / JFA distance field) | field |
| Flowing Blobs | metaballs / thresholded noise | field |
| Mesh Gradient, Soft Overlaps | multi-point gradient / overlapping translucent discs | gradient |
| Confetti Dots | Poisson-disk sampling | scatter |
| Dot Grid | regular grid sampling | scatter |
| Bauhaus Blocks, Rounded Tiles | geometric tiling (quarter-circles, rounded rects) | tiling |
| Diagonal Bands, Gradient Columns, Layered Waves, Wave Dividers, Ribbed Glass | procedural gradients + wave dividers | gradient |

Every group has a gart implementation to port. **Flow, tessellation, and field are the three that carry the
"wow"** and are the ones to lead with.

---

## Architecture

**One pipeline, three axes, one output.** A wallpaper is `render(design, params, palette) → base bitmap`, then
`filters.fold(base) → final bitmap`, then handed to `WallpaperRepository`.

```
WallpaperRecipe ─┐
  design + params │→ Generator.render(size, palette) → base Bitmap
  palette         ┘
  filters ─────────→ FilterPipeline.apply(base) → final Bitmap ──→ WallpaperRepository.setImage/apply
  aspect (V/Sq)
```

- **`Generator`** — a design's renderer: `render(size: IntSize, palette: Palette, params: DesignParams, seed): Bitmap`.
  Deterministic in `seed` (so a recipe re-renders identically and *shuffle* is just a new seed). Lives in
  `core:graphics` alongside `BitmapBlur`.
- **`FilterPipeline`** — an ordered list of per-pixel passes, **reusing the icon studio's pure effect helpers**
  (`LayerRipple`, `LayerGrain`, `LayerPixelate`, `LayerDither`, `LayerTritone`, `Oklab`, …). See "Filters" below.
- **`WallpaperRecipe`** — the stored unit (design id + params + palette + filter list + aspect + seed), one serialized
  blob **per orientation** (the studio's Vertical/Squared, and portrait/landscape). Follows the icon studio's
  one-blob-per-detached-thing persistence, *not* flat columns — the lesson CLAUDE.md already records.

### Module map

| Piece | Module | Notes |
|---|---|---|
| `WallpaperDesign` (id enum), `DesignParams`, `WallpaperRecipe`, `Palette` | `core:model` | plain data + serialization, like `IconAppearance` |
| Generators (Delaunay, Voronoi, flow, contour, …) + `Generator` interface + `FilterPipeline` | **`core:graphics`** | the gart port; currently holds only `BitmapBlur`, so it grows into the engine |
| Perceptual color, noise fields shared with generators | `core:graphics` (or reuse `core:icon`'s `Oklab`) | see "the one refactor" |
| Curated palettes, picker (+opacity, +palette tab) | `core:designsystem` | extend `ColorPalettes` + `MorphicColorPicker` |
| Persistence of recipes | `data:wallpaper` | it already owns `WallpaperRepository.setImage/apply/setRotatingImage` and a live-wallpaper service |
| The editor screen (preview + Designs/Color/Style/Filters panels) | **`feature:wallpaperstudio`** (new) | MVVM per screen, mirrors `feature:settings/iconstudio` |
| Community feed + sharing | deferred | its own feature + backend, out of this plan |

### Motion: the swipe is a discrete re-seed with an animated transition — not a continuous phase

The studio's premium feel is a **motion layer** the first walkthrough missed: swiping mutates the current design in
place. A video analysis (Gemini, 2026-08-30) read this as a *continuous `phase` parameter* bound to the swipe delta
(a Z-axis in the noise). **Probing it on the emulator contradicts that** and matters for the architecture:

- Swiping past a threshold on *Confetti Dots* re-rolled the dot arrangement **and the palette** together, as one
  undoable step. A pure geometric phase would never touch the palette.
- A small, slow sub-threshold drag did **nothing**. A continuous phase would give a small visible morph for a small
  input.

So the mechanism is a **discrete re-seed on a threshold/fling, with a smooth transition animation between the two
states** — not a continuous function of the finger. (The video *does* show real motion; that is the transition, which
a post-release screenshot cannot capture. Both readings see motion; they disagree on whether it is a parameter or a
transition.)

**This is the better model for us, not just the truer one.** A continuous phase forces every generator to be
continuous in its input — easy for 3D-noise designs (flow, metaballs, contours), but **impossible to do without pops
for a tessellation**, whose *topology* changes discretely. The transition model keeps generators as plain static
`render(seed) → bitmap` functions and puts the motion in a layer above them:

- **`Generator.render(size, palette, params, seed): Bitmap`** stays **static and deterministic** — no `phase`. This is
  also what keeps a **recipe = seed** (a saved wallpaper is a seed + palette + filters, nothing to animate).
- A **`TransitionController`** animates between the outgoing bitmap and a freshly re-seeded one. The **universal**
  transition is a **crossfade** (works for all 22 designs for free); a **per-generator interpolated morph** (points
  drifting, discs gliding) is an *optional* enhancement layered on the designs where it is cheap — the noise-based
  ones — and never required.
- A **swipe** re-rolls the seed (and the palette, unless **locked**); **switching design** from the grid crossfades
  old→new. Both are the same transition machinery.

Whether the noise-based designs *also* carry an in-drag continuous morph (which the video may show and a screenshot
cannot) is left open below — but it is an enhancement on those specific generators, not the base mechanism.

### The one refactor worth doing: a shared bitmap-filter runner

**Blocked by a module boundary — the reason W4's filters are drawn fresh instead.** The idea below is still the right
end state, but it cannot be built as written: the icon helpers live in `core:icon`, which **depends on** `core:graphics`
(for `BitmapBlur`), so `core:graphics`'s `FilterPipeline` cannot reach back into them without a dependency cycle. The
fix is to lift the pure per-pixel helpers *down* into `core:graphics` (or a new leaf both modules see) — a real move,
deferred until a second consumer makes it pay. W4 shipped its four passes (Blur/Vignette/Grain/Scanlines) written
directly in `core:graphics` in the meantime; only Grain overlaps the icon helpers, so the duplication bought by waiting
is one small function, not the whole list.

The icon effect passes live in `IconRenderer` and operate on a **square icon bitmap**, but the *silently-wrong* math
is already extracted into **pure, bitmap-size-agnostic helpers** (`LayerRipple.sampleDistancePx`,
`LayerGrain.displace`, `LayerDither.quantize`, `LayerTritone.apply`, `Oklab.mix`, `LayerPixelate.averageArgb`, …).
Those take `pixels + coords`, not "an icon". So a wallpaper `FilterPipeline` **could reuse the helpers directly** and
write its own loop over a `width × height` bitmap — no duplication of the risky arithmetic, which is exactly the
shared-derivation rule the codebase runs on — *once the modules allow it*.

Two things a wallpaper filter must decide that an icon bake did not:

- **"Fraction of the box" becomes fraction of the short side.** Icon effects scale by `sizePx` (a square). A
  wallpaper is not square, so a ripple amplitude or a grain cell is a fraction of `min(width, height)` — decided once,
  in the runner.
- **Silhouette effects do not apply.** Glow, Shadow, Outline, InnerShadow, InnerGlow, Bevel and Glass all read the
  layer's *alpha* as a shape; a wallpaper is fully opaque, so it has no silhouette. The reusable subset is exactly the
  **non-silhouette per-pixel effects** — which is also exactly Smart Launcher's filter list. Kaleidoscope, Scanlines
  and CRT curvature are the genuinely new filters to add (Noise ≈ our Grain).

---

## Color / palette

Reuse and extend what the icon studio has:

- **`ColorPalettes`** already ships a dozen curated sets — the studio's *Suggested palettes*.
- **`MorphicColorPicker`** already has the SV panel + hue bar + the palette ribbon. Add: an **opacity slider** (a
  wallpaper color legitimately carries alpha, unlike an icon tint — this is the one place the icon picker's "no alpha"
  rule is reversed) and a **Picker/Palette tab**.
- **New for the studio:** an editable N-stop palette strip, **shuffle** (re-seed the palette from a suggested set or
  from harmony rules), and **lock** (keep the palette while swapping designs — a `palette-locked` flag on the editor
  state, not the recipe).

Generators consume a `Palette` (ordered colors) and sample it — a tessellation fills cells from it, a flow field
colors strokes along it, a gradient interpolates it (perceptually, via `Oklab` — the Tritone work pays off again).

---

## Phase plan

Sequenced so each phase is a usable slice, leading with the pieces that carry the look and reuse the most.

- **W0 — model + engine skeleton. ✅ (2026-08-30)** `WallpaperDesign` / `DesignParams` / `WallpaperRecipe` / `Palette`
  in `core:model`; the `Generator` interface + a total `Generators` `when` + `FilterPipeline` seam in `core:graphics`;
  one trivial `LinearGradientGenerator` to prove recipe → bitmap. Recipe persistence deferred to W2 (no consumer yet).
  The design enum grows one value per built generator, so the registry stays total.
- **W1 — first three generators, end to end. ✅ (2026-08-30)** **Mesh Gradient** (inverse-distance blend), **Flow
  Field** (`PerlinNoise2d` + streamline tracing), **Triangular Facets** (jittered-grid low-poly) — each rendering a
  full 1080×2400 wallpaper, device-verified. Two decisions worth carrying forward: **Facets uses a jittered grid, not
  Delaunay** (even facets, no slivers; the irregular-shard look is Voronoi/*Vitrall*'s job) — half right, and W11h
  says which half: the grid stays, but the *fixed* diagonal it came with is what makes the slivers, so each cell now
  takes its shorter one — and generator *looks* are
  verified by **`GeneratorRenderHarness`** — an instrumentation test that paints every `WallpaperDesign` to a PNG in
  `/sdcard/Pictures/genharness` for a human to judge (the one thing an `IntArray` test cannot). *Not* yet set as
  wallpaper — that seam is W2.
- **W2 — the editor + apply. ✅ (2026-08-30)** Built as **`feature:settings/wallpaperstudio`** (a subpackage, mirroring
  `iconstudio` — *not* its own module; that settles the open question below). **W2a:** the editor screen — a full-bleed
  live preview, the design picker (tap + horizontal-swipe-to-shuffle), a **`Crossfade`** transition, off-thread
  rendering at the preview's pixel size, reached via Settings→Wallpaper→"Design a wallpaper". **W2b:** *applying* it —
  `WallpaperRepository` gained a **bitmap `setImage`** (it was URI/file-only), and the studio's check button
  stores-then-applies the on-screen bitmap to HOME+LOCK. Device-verified: design a Flow Field, tap apply, it is the
  launcher's wallpaper. **Deferred:** the Vertical/Squared **aspect toggle**, and undo. The per-generator interpolated
  morph stays deferred; the crossfade is the motion for now.
- **W3 — color. ✅ core (2026-08-30)** A color chooser in the studio's bottom bar: a palette toggle flips the chooser
  between the designs and the **191 palettes** (`ColorPalettes.all` — the featured dozen + the harvested cool bank),
  and tapping a palette recolors the current design with a crossfade (`setPalette` → re-render). Device-verified.
  **Deferred to a later pass:** an editable per-stop strip, color opacity, a palette **lock** across design changes,
  and a palette **shuffle**.
- **W4 — filters. ✅ (2026-08-30)** A third bottom-bar chooser (a *tune* toggle beside the palette one) flips to filter
  chips; each is a switch that turns a `WallpaperFilter` on at a default strength and re-renders with a crossfade. Four
  passes shipped — **Blur, Vignette, Grain, Scanlines** — all device-verified on a Flow field. `FilterPipeline` became
  a concrete `object` (`apply(bitmap, Map<WallpaperFilter, Float>)`): blur reuses `BitmapBlur`, the other three are
  small pure `IntArray` passes with their own `FilterPipelineTest`. The recipe gained `filters: Map<WallpaperFilter,
  Float>` (defaulted empty, so old recipes read back).
  **The plan's "reuse the icon effect helpers" did not survive contact:** `core:graphics` cannot depend on `core:icon`
  (the arrow runs the other way, for `BitmapBlur`), so the passes are drawn fresh here rather than borrowed. Unifying
  the two studios' per-pixel math — the "shared bitmap-filter runner" below — is the real refactor left for later; it
  is not a prerequisite for shipping filters. A strength **slider** per filter is the obvious next refinement (the
  recipe already stores a `Float`, so no model change); chips commit a fixed default for now.
- **W5 — the rest of the generators.** Fill out toward the 22, group by group, each with its Style parameters.
  Sourced by *looking* at gart's own rendered gallery (`D:\Android\gart\README.md` maps each `arts/*` folder to its
  PNGs), picking the ones that hold up full-bleed on a phone, and re-implementing the algorithm — never the Skija code
  — in the `PerlinNoise2d` / `colorAt` idiom the first four established. The **catalog is ours to shape**: Smart
  Launcher's 22 is the seed list, not a spec. Text pieces (`alien`, `lettero`) and busy iso-scenes (`skyscraper`) are
  out; strong generative fields gart has and Smart Launcher does not (plasma, topography, truchet) are in.
  - **W5a — mosaic + field batch. ✅ (2026-08-31)** Four generators, device-verified via `GeneratorRenderHarness`:
    **Voronoi** (nearest-seed mosaic with dark seams — *not* gart's Delaunay-dual polygon Voronoi, which is the fragile
    geometry Facets already rejected), **Plasma** (summed-sine interference read through a *looped* palette, from
    `arts/plasma`), **Contour** (a two-octave noise field quantized into inked height bands — the `arts/layers` relief
    look from the field, not marching-squares polylines), **Waves** (sine-crest dune bands filled back-to-front, from
    `arts/layers` undula/strata + `arts/hills`). Voronoi/Contour reuse the boundary-detection trick on a scalar field;
    Plasma's ramp loop is shared with `LinearGradientGenerator.lerpArgb` (now `internal` — its second consumer).
  - **W5b — tiling + scatter + radial batch. ✅ (2026-08-31)** Three more, covering three groups the first six did not,
    device-verified: **Bauhaus** (recursive rect subdivision, from `arts/rects/mondrian` — but Mondrian's three
    hard-coded primaries generalized onto the palette, since the palette is what carries color here), **Confetti**
    (Poisson-disk dart-throwing, from `stipple/util/PoissonDisk` — evenly *strewn* discs, not a clumping uniform
    scatter), **Rings** (concentric echoes off an off-center seeded point, from `arts/sun`/`arts/spiral`). The looped
    ramp sampler `colorLooping` is now shared in `LinearGradientGenerator` — Plasma and Rings both wrap their field
    through it. Nine generators total now (four gradient/field, two tessellation... — see the registry).
  - **W5c — tiling + field + flow batch. ✅ (2026-08-31)** Three more, taking the catalog to **fourteen**,
    device-verified: **Truchet** (quarter-arc tiles turned at random that join into a maze, from `arts/ticktiletock`
    — the connection is emergent because every arc meets an edge at its midpoint), **Metaballs / Blobs** (summed
    `r²/d²` potential fields that *merge*, snapped to flat palette **stops** so they read as glowing onion rings, from
    `arts/blob` — banded, unlike Mesh's smooth blend), **Ribbons** (thick outlined streamlines that reuse
    `FlowFieldGenerator.trace` — the same field as Flow drawn as broad ribbons, from `arts/flowforce/glst`). Metaballs'
    banding had to snap to *flat stops*, not an interpolated `colorAt` fraction, or the "onion rings" the KDoc promised
    were a smooth wash — caught on device. The catalog now spans gradient, field, flow, tessellation, tiling, scatter
    and radial; the text pieces and busy iso-scenes gart also has stay out by choice.
  - **W5d — Rays + Dot Grid, closing the list. ✅ (2026-08-31)** Two more to **sixteen**, device-verified: **Rays**
    (hard-edged angular wedges from an off-center point — the angular sibling of Rings, `atan2` where Rings uses
    distance, from `arts/rayz`/`arts/sf`) and **Dot Grid** (a *regular* lattice of noise-sized dots fading to bare
    paper — the halftone opposite of Confetti's scatter, from `arts/palecircles`). **Dot Grid was the one item named in
    Smart Launcher's 22 that no earlier generator covered**, so the sixteen now span the whole reference list: its five
    flow entries, three gradient/column entries, and Mosaic+Vitrall each collapse into one parameterized generator, and
    Plasma / Rings / Truchet are ours on top. **W5 is complete as a catalog** — remaining work on these is the *Style*
    panel (density/variant sliders; every generator already reads `DesignParams.density`) and per-generator morphs, both
    their own slices, not more designs.
- **W5 — the generator *engine* is done (2026-08-31); the *studio* is not.** Sixteen generators span every group in
  Smart Launcher's 22 and render correctly. But a live teardown (2026-08-31,
  [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md)) found each of their designs exposes **~6 parameters**
  (Count/Spacing/Rotation/Irregularity/Color mode/… from shared families) and defaults to *restraint* (Mono/Bichromatic,
  sparse, soft, with depth), where ours expose **one** (`density`), always full-palette and flat. So the next arc is
  **W6 (grow `DesignParams` + a `WallpaperColorMode`), W7 (styling pass + the organic-noise knob), W8 (thin-line family),
  W9 (the calm staples we lack), W10 (the Style-panel UI)** — all done; see the teardown's revised plan. The per-generator morphs
  stay deferred.
  - **W6 — the color-mode system. ✅ (2026-08-31)** `WallpaperColorMode` (Mono/Bi/Colorful) on `DesignParams`, applied by
    reducing the palette before each generator, default Bichromatic. See the teardown doc.
  - **W7 — the styling pass. ✅ (2026-08-31)** An `irregularity: Float` on `DesignParams` (default `0.5`, restrained),
    read by the nine generators with an organic-noise axis — each mapping it onto its own jitter/warp and scaled so
    `0.5` reproduces the shipped render, `0` is rigid, `1` chaotic. Voronoi and Mesh became grid+jitter via a shared
    `PointScatter`; the loud field designs (Plasma/Rings/Rays) were softened toward broader swells. Engine-only until
    W10 surfaces it. Device-verified via the harness (now with an irregularity sweep). Full record in the teardown doc.
  - **W8 — the thin-line family. ✅ (2026-08-31)** Four designs, catalog now **20**: a shared `Streamlines.pathOf` +
    **Flow Lines** (dense combed hairlines), **Contour-lines** (Contour's `variant`, now its default look), **Ribbon
    Flow** (broad ribbons with the palette running along each), **Polygon Cascade** (a rotating-polygon spirograph
    rosette, the first non-field design). Re-doing Ribbons was skipped by choice — Flow Lines covers the fine-line niche.
    Full record in the teardown doc.
  - **W9 — the calm staples. ✅ (2026-08-31)** Five designs, catalog now **24**: Diagonal Bands, Gradient Columns, Soft
    Overlaps, Wave Dividers, Ribbed Glass. Two shared helpers extracted on their second consumers — `Bands` (variable-
    width banding) and `Shades` (channel darken). Full record in the teardown doc.
  - **W10 — the Style panel UI. ✅ (2026-08-31)** The knobs made reachable: a fourth chooser opens a panel above the
    bottom bar carrying a **tab row of the current design's own parameters** (*Levels · Variation · Look · Color* on
    Contour; *Color* alone on the plain gradient) over a ruler slider or a segmented control. Each **generator declares
    its own knobs** (`DesignStyle` on the `Generator` interface) rather than the UI tabulating them, because a knob the
    panel offers and the generator ignores fails silently; the amount slider offers the generator's **real counts**
    through one shared mapping. Color mode moved out of the palette row into the panel, so Style is every knob in
    `DesignParams`. Still deferred: a draft-quality render during the drag (the panel commits on release — open
    question 1 below, now with a consumer), the frosted material under the bottom bar, and per-design defaults. Full
    record in the teardown doc.
  - **W11 — the design-by-design quality pass. In progress — 16 of their 22 driven.** The engine and the panel are
    built, so what is left is per design: open theirs, render ours, compare, fix one. The **checklist of which
    designs have actually been driven** (and which were built from a one-line note instead) is in
    [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) → "W11 checklist"; keep it ticked as slices land. **W11a — Bauhaus ✅ (2026-08-31):** ours was a *Mondrian*
    under the Bauhaus name, so the Mondrian became its own design and Bauhaus was rebuilt as their arc lattice —
    catalog **25**. **W11b — Neon Ribbons ✅ (2026-08-31):** rebuilt as one fanning bundle of fine curves, reversing
    W8's decision to skip it — Flow Lines combs the whole frame where this draws a single gesture, so they were never
    the same look. **W11c — Ribbons, second pass ✅ (2026-08-31):** the two knobs theirs had and we lacked (*Start
    area* / *End area*) re-cut as **Spread** + **Shape**, which needed `DesignParams`' fifth field **`scale`** (the
    *spacing / gaps* family); plus the **ground glow**, whose mechanism was measured off theirs — hard-edged lines
    over a ground that brightens along the bundle, at ~3× its base. The teardown's verdict table is the running
    record. **W11d — Bauhaus, second pass ✅ (2026-08-31):** their vocabulary is **one quarter disc or nothing** (halves
    and circles are emergent, not drawn), and their plain-tile fraction is a knob of its own — ours moved it onto
    `scale` as *Coverage*, freeing `irregularity` to mean the turns. **W11e — Dot Grid ✅ (2026-09-01):** the
    identity finding a third time — theirs is a *contained* lattice of uniform tiles where only the **color**
    moves, ours was a full-bleed **halftone** where a noise field moves each dot's **size**. The halftone split
    off as `HALFTONE` and `DOT_GRID` was rebuilt as theirs — catalog **26**. It also corrects the teardown's
    parameter inventory: their tab row scrolls, and this design has **eight** knobs, not six.
    **W11f — Confetti Dots ✅ (2026-09-01):** same design as theirs, done four ways worse — re-based on their
    turned, jittered lattice (the Poisson sampler is gone), the ground moved to the palette's first stop, the
    palette now spent with a geometric falloff so the last stops are rare accents, and a **depth of field** on
    `variant` (Flat / Near / Far) — the catalog's first real depth rather than lighting.
    **W11g — Mesh Gradient ✅ (2026-09-01):** their *Color distribution* is a **layout** (Random / Corner
    interpolation / Linear bottom) and its default is what makes theirs a progression down the frame where ours
    was a quilt — ours takes all three on `variant`. Their blend also turns out to be a **bilinear mesh**, not
    inverse-distance weighting: at Jitter 0 theirs is an exact gradient, which distance weighting cannot reach
    without flattening the design, so the generator was rewritten as a color lattice sampled through a separate,
    coarser field of node displacements.
    **W11h — Triangular Facets ✅ (2026-09-01):** the identity finding a fourth time, about **color** this time —
    ours read the palette at a facet's *height* (a striped gradient with corners cut into it) where theirs paints a
    two-dimensional **field of areas**. What proved it was measuring a path between two regions and finding a
    straight RGB line that skips the stops in between, which a scalar field read through a ramp cannot do. Rebuilt on
    a coarse lattice of stops blended bilinearly (`ColorLattice`, extracted on its second consumer — the mesh
    gradient builds its picture the same way), plus their **Tridimensionality** as a lit relief, which is what
    `DesignParams` gained its sixth field **`depth`** for: the *depth* family, the last in the teardown's inventory
    with nowhere to live, and the knob without which the right geometry and the right colors are still a blurred
    gradient. Their *Thickness* turns out to inset every facet rather than stroke it, so it lands on `scale` as
    **Leading**; their *Distribution* and *Randomness* are one axis (the app hides the second under the first) and
    land together on `variant` as **Colors: Field · Speckled · Scattered**. Also: at Distortion 0 their diagonal is
    uniform, so ours splits on the **shorter** diagonal — a clean quilt at the rigid end and no slivers at the loose
    one.
    **W11i — Flowing Blobs ✅ (2026-09-01):** the verdict table's own note ("ours hard onion rings, theirs smooth")
    named the symptom and missed the cause. Their *Complexity* is a **domain warp's frequency**, not a count of
    blobs — driven end to end it leaves the same two or three systems in the same corners and only convolutes their
    contours — so ours, which read it as a charge count topping out at nine, could never reach their default. Rebuilt
    as three fixed charges read through a warp (frequency on `density`, amplitude on `irregularity`, which gives it a
    true rigid end), with the band count taken off the palette's length — one band per stop left the *default* color
    mode drawing two, which is the design dead at its own default — and their **paper-cut shadow**, measured off
    theirs: each band darkens to `0.57` of itself at its boundary with the band above, easing as `f^2.5`, on every
    side of a blob rather than in a light's direction. `depth`'s second consumer, one slice after it arrived.
    **W11l — Diagonal Bands ✅ (2026-09-01):** the first W9 staple driven, and the finding is composition rather than
    geometry — **their bands do not fill the frame.** *Coverage* is the extent of the band **slab** across the band
    axis (measured: `0.49` of the frame's projected extent at Coverage 50), and the rest is ground; ours was stuck at
    full bleed, which is the teardown's first aesthetic principle in one knob. The ground is stop 0 and the bands
    cycle the tones above it — `RampTones`' third consumer — where ours cycled the whole palette and so had no ground.
    Their *Rotation* is continuous `−180..180°` opening on a shallow `20°`; ours samples five angles on `variant`, and
    the **orientation field the family wants is deferred** to a slice that moves its three consumers together. Their
    *Spacing* and *Offset* are not ported, with reasons. gart has nothing for this design. Full record:
    [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) → W11l.

    **W11m — Gradient Columns → Louvers ✅ (2026-09-01):** the identity finding a fifth time, and about the *axis*.
    Theirs runs the palette ramp **along** each strip and slides it a little from strip to strip; ours steps the
    palette *sideways* and fills each column flat. Their *Columns* `1` is a plain gradient with no seams, a rigid end
    sideways stepping cannot reach — so theirs is **built beside ours as `LOUVERS`, catalog 29**, and ours keeps
    `GRADIENT_COLUMNS` rather than re-pointing a stored key at a different picture. Ten knobs there, not the six the
    teardown recorded (four past the fold); their four *Start/End center/spread* knobs re-cut as **Spread** + **Drift**,
    their *Shadow* onto `depth`, their *Rotation* sampled onto `variant` as three directions. `FrameAxis` extracted on
    its second consumer. Full record: [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) → W11m.

    **W11k — Modern Mosaic ✅ (2026-09-01):** the design W11j handed off, and **catalog 28**. Not a packing but a
    recursive **subdivision** — at their *Count* `1` the frame is one rounded tile — so it is `MONDRIAN`'s construction
    with the opposite finish: tiles pulled back onto a light ground, corners rounded, shared corners skewed through one
    displacement field. Their *Ratio* is the **least share a cut may leave** (`1/2` halves exactly; the default is the
    golden minor), which is what makes the tile sizes a related set. `DesignParams` gained its seventh field,
    **`roundness`** — the *shape* family's first home, and here the design's identity rather than a refinement.
    `GlassCut.inset` (gart's) and `RampTones` were both extracted on their second consumer. Their *Frame* is not
    ported. Full record: [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) → W11k.

    **W11j — Vitrall ✅ (2026-09-01):** the verdict table mapped *two* of their designs onto our Voronoi and was wrong
    about both. Their **Modern Mosaic** is a packing of rounded rectangles with a wide grout — nearer our Mondrian,
    and left for its own slice. Their **Vitrall** cuts the frame with **edge-to-edge chords**, so its panes are long
    shards and slender wedges where a Voronoi's are compact blobs; drive their Density to `1` and a single chord
    crosses the whole frame, which no point-based diagram can do. Built as a design of its own — **catalog 27** — by
    recursive area-weighted splitting, with curves done by **clipping against a circle twice, once keeping each side**
    — both calls sample the same arc from the same crossings, so the two panes weld along it; that is gart's own
    answer, and an earlier note here claimed a plane warp instead, which was invented and never written — plus a
    gradient fill and a translucent came rim per pane on `depth`, which is most of why glass reads as material. Our
    Voronoi keeps its name and stops claiming to be either of theirs. **The lasting lesson is the process, not the
    design:** see [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) → "Read gart first".
- **W6+ — community/sharing.** Its own arc: a feed, upload/download of recipes (recipes are small blobs, so sharing a
  *recipe* is far cheaper than sharing a bitmap), attribution, likes. Needs a backend — out of this plan.

---

## Key decisions & deferrals

- **A recipe is a seed + parameters, not a bitmap.** Generation is deterministic, so the stored unit is tiny and
  *shuffle* is a re-seed. This is also what makes community sharing cheap (share the recipe, re-render locally) and
  what lets a recipe re-render at any resolution / aspect.
- **Persistence is one blob per orientation**, following the icon studio — never flat columns (the four-DB-bump lesson).
- **Filters reuse the icon pipeline's pure helpers**, generalized to a non-square bitmap by one runner; no fork of the
  risky arithmetic.
- **Silhouette effects are out**; only non-silhouette per-pixel filters apply to an opaque wallpaper.
- **Live/animated wallpapers are deferred.** `data:wallpaper` already has a `RotatingWallpaperService`, and gart's
  simulations (fluid, reaction-diffusion, n-body) are *animated* by nature — an obvious phase-2, but the first cut is
  **static** bakes, matching Smart Launcher's default and keeping battery/perf out of the critical path.
- **Community is deferred** to W6+ and needs a backend decision.

---

## Open questions (for the planning conversation)

1. **Performance during the transition/interaction.** Re-rolling a full-screen generate + the filter stack on every
   swipe (and animating a crossfade over it) is heavy in software. Draft quality during the drag (reduced scale, or
   filters skipped) and a high-quality bake on release is the likely answer; the noise-based filters may want AGSL
   (API 33+) to stay smooth, which would mean the wallpaper filter path is GPU for the live preview and the reused
   **CPU** icon helpers only for the final bake. This is the biggest technical risk and W2/W4 have to prove it.
2. **Is there an in-drag continuous morph on the noise-based designs?** The evidence says the base mechanism is a
   discrete re-seed + crossfade, but a video may show the flow/contour/metaball designs *also* morphing continuously
   during the drag. If wanted, that is a per-generator enhancement (3D-noise `z = swipe`), not the base model — decide
   whether it is in v1 or a later pass.
3. **Generator subset for v1.** All 22, or a strong ~8–10 across the groups? (Recommend the latter — lead with flow,
   tessellation, field, gradient.)
2. **`core:graphics` vs a new `core:art` module** for the engine. `core:graphics` is the honest home (it is already
   "bitmap work"), but the engine is large; a dedicated module may earn its place.
3. **Static-only v1, or animated live wallpaper in scope?** (Recommend static first.)
4. **Where the editor lives** — a new `feature:wallpaperstudio`, or a section under `feature:settings` beside the
   existing `wallpaper/` screen. (Recommend its own feature; it is a full surface, like the icon studio.)
5. **Community timing** — designed in from the recipe format now (so recipes are shareable by construction), or fully
   deferred?
