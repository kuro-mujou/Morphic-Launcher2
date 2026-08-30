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
(the effect pipeline this reuses), [STATUS.md](STATUS.md) (`data:wallpaper`'s existing state).

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

The icon effect passes live in `IconRenderer` and operate on a **square icon bitmap**, but the *silently-wrong* math
is already extracted into **pure, bitmap-size-agnostic helpers** (`LayerRipple.sampleDistancePx`,
`LayerGrain.displace`, `LayerDither.quantize`, `LayerTritone.apply`, `Oklab.mix`, `LayerPixelate.averageArgb`, …).
Those take `pixels + coords`, not "an icon". So the wallpaper `FilterPipeline` **reuses the helpers directly** and
writes its own loop over a `width × height` bitmap — no duplication of the risky arithmetic, which is exactly the
shared-derivation rule the codebase runs on.

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
  Delaunay** (even facets, no slivers; the irregular-shard look is Voronoi/*Vitrall*'s job), and generator *looks* are
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
- **W4 — filters.** The `FilterPipeline` panel, reusing the icon effect helpers — Ripple, Pixelate, ProgressiveBlur,
  Grain, Chromatic, Vignette, Color grading first (all reuse), then the new ones (Kaleidoscope, Scanlines, CRT).
- **W5 — the rest of the generators.** Fill out toward the 22, group by group, each with its Style parameters.
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
