# Morph Engine

**Status:** M1–M5 built (2026-09-10); M6 under way — Confetti, Soft Overlaps, Voronoi, Diagonal Bands, Waves, Wave
Dividers, Gradient Columns, Plasma, Bauhaus, Truchet, Halftone, Dot Grid, Mondrian, Modern Mosaic, Rounded Tiles, Ribbon Flow, Ribbons, Polygon Cascade, Flow Lines and Triangular Facets rolled out, and the field bucket re-measured and mostly dissolved
(2026-09-11). Drawn
from three screen captures of Smart Launcher's wallpaper studio taken by the author, each of which overturned a
conclusion drawn from the one before.

**Covers:** the render seam both studios draw through — why `Generator.render() → Bitmap` is the wrong shape for a live
transition, what replaces it, and what following it costs each design. It is the *how the picture is made and moved*;
[WALLPAPER_STUDIO_PLAN.md](WALLPAPER_STUDIO_PLAN.md) stays the *what to build and in what order*.

**Supersedes:** that plan's "Motion: the swipe is a discrete re-seed with an animated transition — not a continuous
phase" section, and its open question 2. Both were answered wrong. See "How this was settled" at the end — the record
is kept because this question has now been mis-answered three times, twice from video evidence and once from grepping
the code, and a fourth attempt should start from the disproofs rather than from either.

**Companions:** [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) (per-design knobs and construction),
[ICON_ARCHITECTURE.md](ICON_ARCHITECTURE.md) (the two-renderer hazard this deliberately does *not* repeat, and the
`drawsLive` vocabulary reused below).

---

## What the reference actually does

The second capture (30 frames sampled from a 19-second video of **one slow swipe**, on a stained-glass tessellation)
shows a **scrubbable geometric morph**:

| | frame 0 | frame 8 | frame 13 | frame 18 | frame 29 |
|---|---|---|---|---|---|
| Ground | black | mid gray-olive | sage-gray | pale sage | cream |
| Palette | saturated green | green, gold entering | gold | gold | sage/teal + orange |
| Coverage | full | ~35% | ~20% (minimum) | ~35% | full |
| Geometry | design A | A, dispersed | A remnants + B faint | B growing | design B |

JPEG sizes trace the same curve: 786K → 283K at frame 13 → 487K at frame 29.

**Four properties, and each rules something out.**

1. **Every intermediate frame is made of crisp, fully opaque polygons on a flat ground** — not two translucent
   pictures. This is what disproves a crossfade.
2. **Shape identity survives.** The dark-green blob left of center and the pale double-needle below it are
   recognizably the same objects across frames 8 → 11 → 13 → 15 → 18, translating and resizing continuously. A
   re-generated design at a new seed has *different* polygons; these move. **This is the load-bearing evidence** — see
   the method note at the end for why the others are weaker than they look.
3. **The incoming design fades in as geometry, not as a picture.** The polygon group in the right-hand third, about
   two thirds down, appears faintly at frame 13, stronger at 18, solid at 24, and is present unchanged in frame 29 —
   design B arriving at its final position while A disperses.
4. **The transition is scrubbed by the finger.** Confirmed by the author: stop swiping and it holds at an unfinished
   state. So it is not a timed animation, and it cannot be a re-generation — nineteen seconds of scrubbing against a
   generator run per frame would be a slideshow.

Together those say the reference **plans both designs once at gesture start, then only lerps and redraws.**

## The seam

Returning a `Bitmap` is what forces software raster and blocks all of the above. Split it:

```kotlin
/** Everything the picture is, as plain data — no Canvas, no Paint, no Bitmap. */
fun plan(width: Int, height: Int, params: DesignParams, seed: Long): Plan

/** Paint a plan. Any canvas: a software one for the bake, a hardware one for the scrub. */
fun draw(canvas: Canvas, plan: Plan, palette: Palette)
```

- `render()` becomes `draw(Canvas(bitmap), plan(...), palette)`. The bake is unchanged, still deterministic, still the
  truth for the applied wallpaper.
- The morph becomes `draw(hardwareCanvas, lerp(planA, planB, t), lerp(paletteA, paletteB, t))`.

**This satisfies the standing rule rather than breaking it.** Two draw paths, one shared derivation. The part that
would be *invisibly* wrong — the geometry — is computed in exactly one place and both paths call it. That is
`CellFit`'s lesson applied correctly, not the icon subsystem's two-renderer hazard repeated: there, the two paths
compute the picture twice and are kept honest by intention.

**A `Plan` is whatever that design's picture is a function of** — a set of paths for a primitive design, a struct of
control points and phases for a field one. `draw` issues draw calls in the first case and runs a pixel loop in the
second. One seam, two kinds of plan, and what separates them is a cost profile rather than a capability; see "Which
generators can follow".

**`Plan` holds plain arrays, not `android.graphics.Path`.** `core:graphics`' build file already states the module's
reason for existing — "everything here is arithmetic over an `IntArray` … this is where it can be checked without an
emulator" — and its JVM tests are deliberately scoped to the arithmetic a `Canvas` test cannot reach. A `Plan` of
float arrays extends that scope enormously: a tessellation's cells, a cascade's copies, a flow field's trails all
become assertable data instead of draw calls. A `Plan` of `Path` would throw that away for nothing, since `draw` can
build the `Path` in one line.

**`Plan` stores ramp positions (`0..1`), not resolved colors.** Two payoffs. A palette change stops being a
re-generation and becomes a redraw of the plan already in hand — `setPalette` currently triggers a full render. And
palette *morphing* during a scrub is then lerping two palettes at draw time, which is what the capture shows,
including the ground going black → cream. Baking colors into the plan would instead mean lerping per-primitive RGB
between matched pairs, with the ground needing a special case of its own.

## The matching rule — and the class of design it does not cover

PowerPoint's semantics, which is what the frames show:

- Pair A's primitive with B's by **nearest centroid** — greedy over a spatial grid is enough; there is no need for an
  optimal assignment and no way to perceive one.
- **Matched:** lerp centroid, vertices, ramp position, and the ground.
- **Unmatched in A:** scale toward its own centroid → 0, fade.
- **Unmatched in B:** scale 0 → 1, fade in.

**A triangle and a heptagon must be resampled to a common vertex count** around the perimeter before any lerp. Skipped,
this fails silently — the lerp still runs and still draws, it just draws garbage.

**All of which is right for a scatter and wrong for a subdivision, and M3 found out which by rendering it.** The rule
above assumes the primitives are *independent* — true of Confetti, Spray, Dot Grid, Halftone, and of anything strewn
across a frame. It is false wherever the primitives **partition** the frame, because then two of them share an edge
that one cut made, and pairing them separately tears it. That is a **third bucket**, not a hard case of the first:

> **Subdivision designs — the cells are one structure, so the *cuts* interpolate and the cells are re-derived.**
> Vitrall, Mondrian, Modern Mosaic, Bauhaus, Triangular Facets, Rounded Tiles, Voronoi — every design whose panes
> tile what they are cut from. `GlassTree` is the worked example; the recipe is to keep the construction's own
> recursion instead of discarding it, merge two of them, and clip per frame. Voronoi is the easy member: the structure
> behind its cells is only its seeds, which pair by index, so re-cutting around the moved seeds is the whole morph.

The test for which bucket a design is in is one question: **could two of its primitives be moved independently and
still leave a legal picture?** A scattered dot, yes. A pane, no.

**And the scatter case needs no matcher either, which M6's first design found by reading its construction.** Nearest
centroid assumed scattered primitives have no identity. This catalog's don't scatter at random — Confetti's discs,
and `PointScatter`'s points under Voronoi, Soft Overlaps and Flow Lines, all sit on a **jittered lattice**, so every
primitive belongs to a cell and two seeds on one lattice hold the same cells. The partner is the primitive at the same
index, and neither end has pushed it more than half a pitch off its cell. The pairing rule above survives only for a
design that really does place primitives with no lattice under them, and none has been found yet.

## Which generators can follow — all of them

**The test is not "does it draw shapes". It is: does a continuous path exist between A and B in the generator's own
parameter space?** By that test the whole catalog qualifies, and the buckets below are about **cost**, not capability.

A mesh gradient is the design that proves it, and a capture of one settles it: the pale-green crease at frame 1
*migrates* down-left and softens across twenty frames before vanishing, and the dark band's lower boundary rotates from
a steep diagonal to horizontal. A crossfade cannot walk a feature — it can only fade one out in place while a different
one fades in elsewhere. So a mesh gradient's **control points** are its elements; they are simply *evaluated* rather
than drawn, and they interpolate perfectly.

The same is true wherever the picture is a pure function of a small parameter set: Metaballs' charge positions and
radii, Plasma's harmonic phases, Marble's turbulence phase and vein position, Contour's terrain parameters (whose
isolines slide, merge and split — the flattering case), Linear Gradient's stops and angle.

**Voronoi deserves a specific correction**, because it looks like the hard one and is not. Its sites lerp, and
nearest-site assignment is **continuous in site position**, so every boundary slides smoothly. No Delaunay, no
Fortune's sweep.

**And a second one, because continuity is not affordability (M6, 2026-09-11).** The paragraph above went on to say no
cell polygon need ever be built, and filed the design as a field. Both were wrong. Its picture is flat cells edged by
a seam two pixels wide — *edges*, which a downscale thickens — and evaluated per pixel it costs over a second a frame.
It is a subdivision, and its cells are now built as polygons by half-plane clipping: still no Delaunay, and a bake that
went from seconds to milliseconds. The M6 entry carries the measurements.

### The two buckets, by cost — and each is cheap where the other is not

**Primitive designs — re-draw is `O(elements)`, and they need full resolution (20).** Bauhaus, Confetti, Contour, Dot
Grid, Flow Field, Flow Lines, Halftone, Impasto, Modern Mosaic, Mondrian, Polygon Cascade, Ribbon Flow, Ribbons,
Rounded Tiles, Soft Overlaps, Spray, Triangular Facets, Truchet, Vitrall, Voronoi. Cheap per frame and GPU-rasterizable, but a
downscale would soften the edges that *are* the picture. `plan` here extracts what `render` already computes before it
draws — no new geometry, and the byte-identical bake assertion should hold trivially.

**Field designs — re-evaluation is `O(pixels)`, and they downscale for free (3, measured).** Mesh Gradient, Linear
Gradient, and Plasma at a floor that rises with its frequency. Expensive per full-resolution frame on the CPU, but the
field is smooth by construction, so a scrub frame evaluated at a fraction of the pixels and bilinearly upscaled is
**perceptually identical**. `plan` here is a small parameter struct rather than a set of paths, and `draw` runs the
pixel loop — the same seam, a different kind of plan.

**This bucket held twelve until it was measured, and nine of them left it (2026-09-11).** They were filed here by the
timing fit — cost per pixel, nothing fixed — and the fit says nothing about whether a picture survives being evaluated
small. `FieldDownscaleHarness.surveyRemainingFieldDesigns` measured that, and most of them draw each pixel into a flat
band with no antialiasing, so a downscale stairs every edge. None can afford full resolution per pixel either: the
cheapest costs 47 ms a frame and the dearest 1.7 s. The survey table is under "Field survey" below. Where they went:

- **Shapes drawn a pixel at a time (5) — to the primitive bucket.** Diagonal Bands, Waves, Wave Dividers, Gradient
  Columns and Louvers are straight bands, crest-bounded bands and strips of gradient: paths and gradient shaders,
  written as a pixel loop. Split, they are drawn by the canvas for the bake and the scrub alike, as Voronoi now is —
  their bakes gain antialiased edges, and drop from 47–363 ms to a few. **Four were, and Louvers was not**: it ignores
  the seed, so a shuffle of it is the same picture and there is no scrub to build. Redrawing it would still buy
  antialiased seams and a render of a few milliseconds rather than 116, which is the knob drag's gain rather than the
  morph engine's; the author chose to leave it (2026-09-11).
- **Edges cut from an expensive field (4) — no bucket yet; open question 6.** Metaballs (bands of a warped
  potential), Ribbed Glass (a lens per rib, with hard seams between them), Planet (a per-pixel pigment walk with hard
  band boundaries) and Marble (creased veins over fine turbulence). The field under them is costly *and* the edges
  cut from it are sharp, so neither economy covers them.

**That is the inversion worth holding on to: each bucket is cheap in exactly the way the other is not.** The intuition
that a "cheap draft" helps the expensive designs is right; the intuition that it helps *everything* is wrong, and it is
the primitive designs that must stay at full size.

**The free downscale is not uniform, and the exception is already recorded.** `DraftShortSidePx = 360` exists because
`ContourGenerator` samples its terrain on a lattice 360 cells across the short side — below that its contours *move*
rather than soften, which is a different composition rather than a softer one. So the safe scrub resolution is
**per-design**: a mesh gradient can go far below 360, Contour cannot. One global floor is the right default and the
wrong ceiling.

**AGSL becomes an optimization, not a prerequisite — for the field bucket.** It would speed that bucket, and it is
still API 33+ against a `minSdk` of 26 — but the downscale already makes it affordable, so nothing there is blocked
on it and no second renderer has to exist. The four edge-cut designs are the exception, and the one place AGSL is a
candidate rather than a speed-up: see open question 6.

## Engine verdicts

**GPU: yes for the primitive bucket — by drawing primitives, not by writing shaders.** Compose's `DrawScope` is
hardware-accelerated Skia (Ganesh) at every API level from 21. Drawing an interpolated plan into it is GPU
rasterization with no `RuntimeShader`, no AGSL, and therefore nothing gated behind API 33 against a `minSdk` of 26.
Framing the question as "AGSL versus CPU" was the wrong axis — AGSL is the *field* bucket's optimization, and that
bucket has a cheaper answer available first (the free downscale above), so nothing waits on it.

**C++/NDK: no.** Per scrub frame the work is a few thousand float lerps plus path rasterization. The rasterizer is
Skia — already C++, and on this path already on the GPU. There is nothing left for native code to accelerate. The cost
would be CMake in the build, JNI marshalling, ABI splits, 32 generators as a second implementation to keep in sync, and
the loss of the `core:graphics` JVM test suite, which the seam above exists partly to *grow*.

**`draftThenSettle` does not apply to a scrub, and that is not a regression.** That loop exists because a recipe change
forces a generator run slower than a frame. During a scrub there is no recipe change — only `t` — so there is no run and
nothing to draft. Two loops for two different problems, and both stay:

| | what changes | cost | loop |
|---|---|---|---|
| Knob drag | the recipe | a full generator run | `draftThenSettle` (built, correct, unchanged) |
| Morph scrub | `t` only | a lerp and a redraw | direct draw, full quality, no draft |

**The scrub is view state, not recipe state.** `WallpaperRecipe` is design + seed + palette + params + filters, and no
seed means "62% of the way from A to B". So `t` and the two plans live in the UI layer, the ViewModel commits one end
on release, and none of this reaches persistence.

## The risk this carries: filters have no bitmap to run on

`FilterPipeline` is per-pixel arithmetic over an `IntArray`. A GPU-drawn scrub frame never becomes an `IntArray`, so a
recipe with filters on either drops them mid-scrub — a visible pop at both ends of the gesture — or cannot take this
path at all. **This is the largest open risk in the design, and it is not solvable by making the draw faster.**

The icon subsystem already owns the vocabulary for the answer: `LayerEffect.drawsLive` splits effects by whether the
live path can reach them at all. The wallpaper filters want the same flag, and it lands in a useful place:

- **Survives a scrub at every API** — the color grades (Color, Duotone, Tritone), which are a `ColorFilter`/
  `ColorMatrix` on the hardware canvas, and Vignette, which is a drawn overlay.
- **Survives from API 31** — blur, via `graphicsLayer { renderEffect = … }`.
- **Does not survive** — Ripple, Pixelate, Grain, ChromaticSplit. Per-pixel, and their only live route is AGSL at
  API 33+.

So the honest behavior is that a scrub applies what the hardware canvas can reach and resolves the rest on settle, with
the flag saying which is which. What must **not** happen is the two paths disagreeing silently about a filter both
claim to apply.

## Slices

- **M1 — the seam, on one generator. ✅ (2026-09-10)** `VitrallGenerator` splits into `plan` (a `Plan` of `Pane`s —
  outline, ramp position, flash, gradient angle and lift, plus the two unresolved knobs) and `draw(canvas, …)`, with
  `render()` reimplemented as `draw(Canvas(bitmap), plan(…))`. All three Vitrall renders came back **byte-identical**
  to the pre-split baseline, as did the other 94 (see the note on Planet below). Three things it settled:
  - **`Plan` is resolution-independent, and that is now a test rather than an intention** — `plan(1080, 2400)` and
    `plan(135, 300)` produce the same window, so a scrub can redraw at any size without re-cutting. It is the property
    the whole split exists for and the only one nothing else would have caught.
  - **The per-pane random draws are a fixed sequence — tone, flash, angle, lift — and re-ordering them is silent.**
    The geometry, the pane count and the tone range all survive it; the window merely becomes a different one at the
    same seed. `VitrallGeneratorTest` pins a fingerprint of the whole glazing stream for that reason, and the four
    values are locals rather than constructor arguments so the order is stated rather than inherited.
  - **The seam is not on the `Generator` interface yet.** One generator is one consumer; it lifts when M6 brings the
    second, per "extract on the second consumer".
  - **It also turned up a pre-existing bug:** `PlanetGenerator` renders non-deterministically (`gen_COLORFUL_PLANET`
    differed between two runs of *identical* code, while 95 of 96 were stable), which breaks `Generator`'s
    determinism contract and means that design cannot be verified this way at all until it is fixed.
- **M2 — the live draw path. ✅ (2026-09-10)** And the finding is that **there is no second drawing path to write**.
  `draw` already takes an `android.graphics.Canvas`, which is exactly what a Compose `DrawScope` hands out
  (`drawIntoCanvas { it.nativeCanvas }`) — so the scrub and the bake issue *the same draw calls from the same plan*,
  and the only thing that differs is which rasterizer is on the other end. The agreement is now measured rather than
  argued, by `VitrallLivePathTest`:
  - **Software bake versus GPU: mean difference `0.18` of 255, and `4.2e-4` of pixels differing loudly.** The
    difference map shows pane interiors at pure black with a hairline along pane boundaries — antialiasing, and
    nothing else.
  - **`BlurMaskFilter` survives the hardware canvas, which was the real risk.** A mask filter was for years
    unsupported under hardware acceleration and an unsupported one does not throw — it silently draws nothing.
    Vitrall's rim is a blurred stroke washed inward from every pane edge, so had it been dropped a large share of the
    frame would have lit up. It did not, and `clipPath` came through with it.
  - **The bar is a *share of area*, not a per-pixel tolerance.** An antialiased edge legitimately differs by a lot at
    one pixel (max was `69`); a feature that silently did nothing differs a little over a great many. Only the second
    is a failure, so only the second is asserted.
  - The GPU is reached through `RenderNode` + `HardwareRenderer`, which is what Compose uses underneath — it keeps
    Compose's test infrastructure out of a module that has no Compose in it. **API 29+**, so the test skips below
    that and the scrub will need the software path there; the launcher's floor is 26.
- **M3 — the matcher. ✅ (2026-09-10), and it is not a matcher.** Built first as one — nearest-centroid pairing,
  arc-length resampling, rotational alignment, unmatched scale-and-fade, thirteen passing unit tests — and the render
  harness refuted the whole approach on its first run. **A subdivision's cells are not independent objects**, so
  pairing them off and interpolating each against its partner cannot hold the partition: two panes are neighbours
  *because one cut made both*, and partners chosen pane by pane pull a shared edge in two directions. Measured on the
  device: the lead went from 18% of the frame at `t = 0` to **40% at `t = 0.5`**, panes detached from their own
  boundaries, and the bones — meaningful only *as* boundaries — came loose and swept over open ground as free strokes.
  Neither bound helped, and that is the tell: tightening the travel and area limits refuses more pairs and collapses
  *more*, loosening them buys the self-intersection scribble the limits exist to prevent.
  - **What replaced it is `GlassTree`: the *cuts* interpolate, and the cells are re-derived per frame.** `panes()` now
    records each cut as a recipe over any region rather than throwing it away once the polygons fall out, `plan()`
    derives the window from that tree, and a morph merges two trees into one shared structure. The partition stops
    being something to preserve and becomes an *invariant*: whatever the cuts are at a moment, applying them in order
    divides the frame, because that is what cutting is. `the frame stays whole at every moment of a scrub` asserts it
    at twenty-one values of `t`, which is the test the old mechanism could not have passed at one.
  - **Three things the shape of the problem forced.** A cut is carried as **curvature**, not radius, because
    flattening a bow means its radius running to infinity and lerping a radius toward zero *tightens* the arc instead
    — the window curling up rather than going straight. A cut's two sides are named by its own normal rather than by
    the order the geometry produced them, or a bow flattening through zero silently swaps two subtrees. And where one
    tree cuts and the other does not, the missing cut is supplied **flattened out past the frame's edge**, so a pane
    with no counterpart grows out of an edge or shrinks back into one, and the recursion into the vanishing side
    needs no special case.
  - **The bake survives, to 21 pixels of 2,592,000 at one 8-bit level.** `t = 1` is byte-identical to the
    pre-rework render and `t = 0` differs in a single 15×17 box at a junction of three leads — the same *set* of
    strokes drawn in the tree's order rather than the subdivision's pop order, which rounds differently where two
    antialiased strokes of one color overlap. Restoring the old order would mean collecting bones a second way,
    which is the second source of truth this rework exists to remove. The glazing-stream fingerprint is unchanged,
    so the random draws and the pane order are exactly as they were.
  - **`PolygonMorph` is deleted, with its tests.** Pairing, resampling and rotational alignment have no consumer once
    the cuts interpolate, and a scatter design that might want them later is not a consumer today. What survived is
    two lines of arithmetic, now in `GlassTree`.
  - **A coarse middle, and the fix was one distance.** The first build ran ground share 18% → **7%** → 16%: where
    the trees diverge high up, many cuts flattened at once and panes merged, so the scrub passed through a state
    reading as the same design at a *lower density*. The cause was that a flattened cut was pushed by **the frame's
    diagonal** — the obvious way to guarantee one side is empty — which takes it out of the pane it divides about a
    fifth of the way through the gesture, after which that whole subtree contributes nothing. Pushed instead just
    clear of **the region it actually divides**, the same cut spends the entire scrub crossing it, so what is leaving
    shrinks smoothly and the density holds: **18% → 13.3% → 16.3%**, against ends of 18% and 16%. It cost `merge` the
    regions, so both trees are now re-cut as the merge descends — one frame's worth of clipping, once per gesture —
    and the side that leaves is chosen by **area** rather than by leaf count, a large pane shrinking away being the
    thing that gets noticed. The lesson generalizes past this design: **a vanishing element should spend the whole
    gesture vanishing**, and a bound picked to be safely large is how it ends up spending a fifth.
- **M4 — the gesture. ✅ (2026-09-10)** The studio's swipe scrubs. `WallpaperMorph` + `WallpaperMorphs.between` is the
  public seam onto all of this — deliberately narrower than the engine, since what a screen needs is "can these two be
  scrubbed between, and if so paint me `t`", and widening it further would put a `Plan` in a composable.
  `ShuffleSwipe` is the gesture; the ViewModel prepares the next shuffle at the end of every settled render, so the
  front cost — planning two windows and merging their cuts — is never paid in the frame a gesture starts on.
  - **`between` returns null rather than a degraded scrub**, and the studio falls back to the dissolve it had. Four
    refusals: a design with no plan seam (Vitrall is still the only one), two *different* designs, two different
    palettes, and any filter on either side. Each is a way of putting a different picture on screen than the one that
    would be applied, so each is a refusal instead of an approximation.
  - **The scrub's `t` is the screen's state, not the ViewModel's**, which is the split MVVM actually wants here: the
    recipe has no field meaning "62% of the way to the next window", and pushing `t` through the state flow would
    recompose the studio sixty times a second to move one float that only the draw pass reads. What the ViewModel
    keeps is what a scrub *is* and what committing one does.
  - **The handoff is what M2 was measured for.** The last frame of the gesture is the finishing window drawn on the
    hardware canvas; the bitmap that replaces it is the same plan drawn by the software path. So the commit does not
    dissolve, and the screen holds the scrub until the *settled* pass lands — a draft would read as the window going
    soft the instant the finger lifted. `WallpaperStudioState.landing` is that hold.
  - **Verified on emulator-5554**, three paths: a committing drag re-cuts continuously at full density; a
    sub-threshold release returns the screen **pixel-identical** to before the touch (0.00% of pixels differing, against
    61% mid-drag); and a design with no seam shows **nothing** mid-drag and re-rolls on release, as it always did. Across
    a ten-frame burst after a committing release the picture moved 69% (the spring), then 0.30%, then exactly zero
    seven times over — no jump where the canvas hands over to the bitmap.
- **M5 — the field bucket, on one generator. ✅ (2026-09-10)** Mesh Gradient split into `plan` / `draw`, with a
  `Morph` beside them and `WallpaperMorphs.between` widened to two designs — one per bucket. Four findings:
  - **A field plan takes no size at all.** Vitrall's needs one for its aspect; a `Mesh` lives in the unit square and
    is read at whatever resolution it is asked for. That *is* the bucket difference, stated as a signature.
  - **One pixel loop, two entry points.** `render` runs it straight into the bitmap it returns and `draw` runs it into
    whatever buffer a scrub can afford, then blits. Writing the loop twice would have been the same hazard as two
    renderers and quieter — the two would agree at every setting anyone checked. `theBlitIsExactAtFullSize` pins that
    a 1:1 blit **is** the bake, pixel for pixel, which is this bucket's version of `VitrallLivePathTest`.
  - **This design's plan carries resolved colours, against the rule above, and it is the one that cannot follow it.**
    A node's colour is not a ramp position: under *Corners* it is a blend of four ramp samples, and `soften` then
    draws every node channel-wise toward its neighbours' mean — a colour average with no meaning in ramp space.
    Keeping positions would mean softening a different quantity, which is a different design. The cost is that a
    palette change re-plans rather than re-draws, and that this design in particular cannot morph two palettes.
  - **A field morph has nothing to pair.** Same-shaped lattices put every node opposite its counterpart by
    construction, so a moment is one linear pass over two arrays — no correspondence to find, resample or align. All
    of `GlassTree` exists because a subdivision cannot say that. Lattices of *different* sizes are refused rather than
    resampled, which is only reachable by scrubbing between two densities, and nothing does.
  - **The number: 120, not 360** (`FieldDownscaleHarness`, swept over every corner of the knob space at nine
    resolutions against the full-resolution render). Worst case is the finest colour lattice under *Scattered* at full
    warp, where a short side of 120 leaves a mean difference of **1.2 of 255** and **0.001%** of pixels differing by
    more than four levels. At 60 that share is 1.7%; at 30 it is 36% — the lattice being lost rather than softened.
    A third of the short side is a **ninth of the pixels**: a full-frame phone scrub is 2.6M, this is 32 thousand.
  - **Verified on emulator-5554.** The scrub runs live and the handoff is invisible: across a burst after a committing
    release the wallpaper's frame-to-frame difference is mean 0.16 of 255, and the only samples above 20 sit in
    y = 135..159 — the status-bar clock. Worth knowing about this design specifically: **its seed moves only the
    warp**, since the node colours are a function of position and palette alone, so a mesh shuffle is inherently
    subtle and the scrub is faithful to that rather than underpowered.
- **M6 — roll out**, one generator at a time, each with the byte-identical bake assertion. Left, after the field survey:
  four primitive extractions and four edge-cut designs waiting on open question 6. **Linear Gradient and Louvers are owed no scrub at all**: both ignore the seed, so a
  shuffle of either is the same picture.
  - **The seam is on `Generator` now (2026-09-11): `scrub(width, height, palette, params, from, to)`**, defaulting to
    null, with `WallpaperMorph` a `fun interface` each design returns a one-line lambda of. `WallpaperMorphs.between`
    keeps only the refusals that are the studio's rather than a design's, and one was added: **two different knob
    settings.** A scrub is a shuffle — the table under "Engine verdicts" already said a scrub changes `t` and a knob
    drag changes the recipe — so a design plans both ends against one set of knobs and never has to say what a scrub
    between two densities would mean. Open question 2 would widen it again if the reference turns out to drive a knob.
    Palettes are now compared *resolved*, since the color mode is a knob that reduces the palette.
  - **Confetti ✅ (2026-09-11) — the first scatter, and it is not a scatter.** `plan` / `draw` / `Morph` beside
    `dots()`, which already was the plan. The discs sit on a jittered lattice, so a disc's partner is the disc at its
    own index and the morph is a per-disc lerp — a third kind beside the subdivision's re-cut and the field's re-read,
    and the cheapest of the three. The matching rule above is amended for it.
  - **The bake: 961 of 961 harness renders byte-identical** to the pre-split baseline, the whole harness rather than
    the 26 Confetti renders alone, since `WallpaperMorphs` is shared.
  - **The plan stays in pixels**, where Vitrall's lives in a frame of its own. The lattice pitch is a share of the
    long side, so a plan is one picture at every size of one shape, and `draw` reaches another size by scaling the
    canvas. A round trip through unit coordinates would round the odd disc edge differently, and the bake assertion
    is worth more than the symmetry.
  - **The plan takes the palette's stop count, not its colors** — the first ink is common and the last rare, spread
    over however many stops there are. A recolor at the same size stays a redraw.
  - **A disc's ink blends, it does not switch.** A stop is a choice, so `Vitrall`'s precedent (a flash switches at the
    midpoint) was on the table — and wrong here: a shuffle changes the ink of most discs on a full palette, and they
    would all flip in one frame. So a moment's disc carries two stops and a mix.
  - **The painter's order is re-sorted per moment**, by depth, and depths cross. Two discs cross only at equal depth,
    which is equal size, so a swap flips only the overlap of two same-sized discs of different inks. Measured at
    forty steps (colorful, *Near* focus, the worst setting): frame-to-frame mean 0.87–0.99 of 255 at every step, with no
    spike anywhere — neither the order swaps nor the quantized blur levels show.
  - **Refused when the lattice differs** — a different frame or resolution, or a different size, which moves the cull
    at the frame edge. The cull is monotone in its margin, so on one lattice equal counts mean equal sets.
  - **Verified on emulator-5554**, bichromatic and colorful. The drag changes the picture 2.0% per step, evenly; after
    a committing release the spring settles in two frames and every frame after is **pixel-identical** to the settled
    bake. A sub-threshold release returns the screen with **0.000%** of pixels differing.
  - **What it leaves open: the midpoint is muddy on a full palette.** A disc blending navy to orange passes through
    brown and khaki, and at `t = 0.5` every changing disc is mid-blend at once, so the middle of a colorful scrub
    reads as a duller palette. Bichromatic, the default, has one ink and never shows it. A per-disc stagger — each
    disc's color changing over its own short window, perhaps in the swipe's direction — would keep the middle
    saturated. It is a design choice rather than a fix, so it was not made.
  - **Soft Overlaps ✅ (2026-09-11) — the second scatter, and the one with nothing to recolor.** Its forms sit on
    `PointScatter`'s jittered lattice, so they pair by index as Confetti's discs do; and a form's tone follows its index
    rather than the seed, so partners are already the same color and the midpoint cannot go muddy. A form's ring is
    lerped factor by factor — each scales the radius at a *fixed* angle, so any mix of two rings is still one closed
    curve around its center, where lerping two outlines' vertices can fold.
  - **Its plan takes no size and no palette**, like a field plan and unlike Confetti's: the placement is the unit
    square and every size a share of the short side, and `draw` applies both in the order the render always did — so
    the plan is resolution-independent *and* the bake byte-identical (943 of 961; the other 18 are below).
  - **The live-path test found a bug that predated the morph.** Seven of the eight look × blend pairs agreed with the
    bake as closely as Vitrall does (at most `4.5e-4` of pixels past 24 levels); **Glow × Multiply disagreed over
    4.5% of the frame**. The cause was the bake, not the GPU: `PorterDuff.Mode.MULTIPLY` is Skia's *modulate*, which
    multiplies alpha too, so every Multiply form punched a translucent hole in the ground — 81% of a Fill frame at
    alpha 157, a Glow frame down to fully transparent, and the two rasterizers disagreed only about how to cover a
    translucent rim. A full scan of the harness found it in those 18 renders and nowhere else in the catalog. Fixed
    by laying a Multiply form as the opaque color it multiplies to — its tone lerped toward white by its opacity —
    which over an opaque ground *is* the multiply blend, at every API level. After it, Glow × Multiply agrees to a
    max of 3 levels, and the test now also asserts every bake is opaque.
  - **`LivePath`** is the instrument both live-path tests now share: the second consumer arrived, and a bar that
    two tests hold separately is two bars.
  - **Verified on emulator-5554**, and Vitrall, Mesh Gradient and Confetti re-checked through the lifted seam. The
    drag changes the picture evenly (4.5–5.6% mean per step), the spring settles in two frames and every frame after
    is pixel-identical to the bake, and a sub-threshold release returns **0.000%** of pixels changed.
  - **Voronoi ✅ (2026-09-11) — filed as a field, measured as a subdivision, and rebuilt as one.** The downscale the
    field bucket runs on was measured before anything was split, and fails: evaluated at a short side of 360 and blown
    up, **2.9–3.9%** of pixels differ past 24 levels, and the seams come out three times their width; at 240, 4.7–6.3%
    and four and a half. Nor is full resolution affordable per pixel — a full 1080×2400 render took **1.3–2.1 s** on the
    emulator. The picture is flat cells and thin edges: a primitive design's picture at a field design's cost, which is
    the one combination neither bucket's economy covers.
  - **So the cells are polygons now — the author's call, since it changes the bake.** Each seed's cell is the frame
    clipped by its bisector with every other seed (`GlassCut.clip`, the one-sided half-plane clip that file's header
    already named as its job), in an aspect-true frame so "nearest" is nearest on screen. Fills and a stroked seam
    through the canvas, for the bake and the scrub alike. **The render went from 1.3–2.1 s to 8–9 ms**, and re-cutting
    one moment of a scrub costs 0.5–1.3 ms.
  - **The bake changes, by design and only a little.** Same seeds, same cells, same colors; the seams are antialiased
    strokes rather than aliased pixel pairs, and their width is a share of the short side (two pixels at 1080) where it
    was two pixels at every size — so a draft is the same picture smaller rather than one with triple-weight leading.
    Against the old bake: mean **0.49** of 255, **0.65%** of pixels past 24 levels, all along seams. Exactly the 11
    Voronoi renders changed; the other **950 of 961** are byte-identical.
  - **The plan holds ramp positions, and the scrub walks the ramp.** A cell's color was always `colorAt(position)`,
    so the plan keeps the position — a cell moving between two tones passes through the palette's own colors between
    them rather than a mix of the two ends.
  - **The partition is an invariant here as it is for `GlassTree`**: `the frame stays whole at every moment of a
    scrub` sums the cells' areas at 21 values of `t`, and `every corner of a cell is nearer its own seed` pins the
    definition on a phone-shaped frame, where a bisector taken in the unit square would fail it.
  - **Verified on emulator-5554.** The drag re-cuts evenly at 3.1% mean per step, the spring settles in two frames and
    every frame after is pixel-identical to the bake, and a sub-threshold release returns **0.000%** of pixels changed.
  - **Diagonal Bands ✅ (2026-09-11) — the first of the field survey's shape designs, redrawn as shapes.** Each band
    is a quad across the band axis, built from `FrameAxis`' own two ends so its edges lie exactly where the axis reads
    the boundaries — `a band's drawn edges lie where the axis reads its boundaries` pins that at every angle, since a
    quad worked out from the degrees again could land a quarter turn or a pixel center off and still look like bands.
    Each is drawn from its own start to the slab's far end and the next covers the rest, so every shared edge is
    antialiased once over solid color and the ground never shows between two bands as a hairline.
  - **The scrub is the plainest in the catalog**: the seed only sets band widths, so the edges slide and nothing else
    moves, and two sorted edge lists interpolated edge for edge stay sorted — no band can turn inside out. At
    *Variation* `0` two seeds are one picture and the scrub is a still one, which is faithful rather than broken.
  - **The plan takes no size and no palette**: every number is a share of the axis, and a band's color follows its
    index.
  - **The bake changes by its edges alone.** The 40 Diagonal Bands renders changed and the other **921 of 961** are
    byte-identical; against the old bake the default differs on 0.88% of pixels with 0.29% past 24 levels, the worst
    recipe (thirty bands at 45°) 1.48% past 24 — all of it along band edges, now antialiased where they were stepped.
    **The render went from 47 ms to 6.8 ms** a full frame.
  - **Waves ✅ (2026-09-11) — counted bands, painted.** A pixel's band is how many crests sit at or above it, and
    crests cross; so each column's crests are sorted and the `k`-th of them, traced across the frame, is the edge band
    `k` is filled below. The last fill over a pixel is then exactly the count, which `the traced edges give every pixel
    the band the crests count for it` checks at full distortion, where crests cross and swallow each other. (The class
    note had said per-column sorting would *lose* that swallowing; it keeps it, and the note now names what would —
    a band per fixed pair of crests.) Traced every 4 px, a chord departs from the curve by a fraction of a pixel.
  - **The shadow is a bitmap mesh laid along each traced edge**, a fade stretched over a sixteenth of the frame, because
    the fade follows a curve and a gradient shader can only follow a line. `drawBitmapMesh` is drawn by the hardware
    canvas at every API level the launcher runs on, where `drawVertices` is only from 29 — and a dropped call draws
    nothing. `WavesLivePathTest` measured it at full shadow in both fills: **6.7e-5** of pixels past 24 levels between
    GPU and bake, so the mesh is drawn.
  - **Two ends of a ripple's phase are angles**, so the scrub turns them the short way — the second consumer of
    `lerpAngle`, which moved out of `GlassTree` into `Angles.kt` with it.
  - **The bake changes by its edges alone**: the 23 Waves renders changed and the other **938 of 961** are
    byte-identical; the worst differs on 0.17% of pixels past 24 levels, all along crests. **The render went from 129
    ms to 27 ms** a full frame.
  - **Wave Dividers ✅ (2026-09-11) — a shuffle is a phase, so the scrub slides the waves.** The seed sets only where
    the shared wave starts its cycle; everything else is a knob. So the scrub turns that phase the short way and the
    whole stack travels along its length, and at *Wave depth* `0` it is a still one.
  - **The design is two perpendicular `FrameAxis` readings, and a traced point is the two axes' ends combined** —
    `alongStart + l·(alongEnd − alongStart)` plus the same across — exact because the axes are perpendicular and each
    spans the frame corner to corner. `a traced point lands where both axes read it, at every direction` pins it,
    since that combination is the one piece of geometry the drawn bands add.
  - **The trace step is set by the wave's steepest bend**, so a chord strays no more than a quarter pixel: at full
    depth and the tightest wavelength a divider swings hundreds of pixels in a period of about a hundred and needs a
    sample a pixel, where a straight one needs one every eight.
  - **The bake changes by its edges alone**: the 40 Wave Dividers renders changed and the other **921 of 961** are
    byte-identical. The default differs on 0.38% of pixels past 24 levels; full depth on 2.9%, because a stack folded
    that far is mostly near-vertical flank, every pixel of which is an edge. **The render went from 363 ms to 8.5 ms.**
  - **Gradient Columns ✅ (2026-09-11) — Diagonal Bands' slabs with two shades laid over them.** Each column is a flat
    slab of its stop; both shades are black laid on top, which over an opaque color scales it by one minus the black's
    opacity — so the two layers multiply exactly as the per-pixel `edgeShade × rakeShade` did. The seam shadow is a
    gradient across the last 35% of each column; the rake is one gradient down the whole frame whose 17 stops sample its
    smoothstep, since a shader only ramps linearly between stops — `the rake's stops follow its smoothstep to within a
    level` holds that bound at full relief, where too few stops would read as banding nobody could name.
  - **`FrameAxis.slab` is shared now**, on its second consumer: Diagonal Bands' quad moved onto the axis with its test,
    with `xAt`/`yAt` beside it so the seam shadows' gradient anchors sit where the axis reads the boundaries too. The
    arithmetic is operation for operation what Diagonal Bands did, and its 40 renders stayed byte-identical.
    `Bands.bandAt` went with the pixel loops — nothing classifies a pixel into a band any more.
  - **The bake changes by its edges alone**: the 13 Gradient Columns renders changed and the other **948 of 961** are
    byte-identical, Diagonal Bands' included. The worst differs on 0.093% of pixels past 24 levels, along the seams,
    with one-level steps where an 8-bit shadow alpha rounds differently from the old direct scale. **The render went
    from 214 ms to 15 ms.**
  - **Plasma ✅ (2026-09-11) — the field bucket's second member, and the one that found the bucket's bug.** A shuffle
    turns four wave phases the short way and trades one domain warp for another; a moment blends the two warps, since
    a warp is a point pushed by a field and a point pushed part-way between two pushes moves continuously.
  - **The survey's verdict on it was mostly a misregistration in the shared loop, not the design.** The pixel loop
    read buffer pixel `x` at `x / (buffer − 1)` — the frame's formula applied to the buffer — where the blit puts that
    pixel's center at `(x + ½) · frame / buffer`, so every scrub evaluated the field stretched by up to half a buffer
    pixel toward each edge. On the mesh gradient's gentle colors that hid; on the plasma's steep run round a looped
    palette it was most of the error. Read where the pixels land (`FieldRaster`, now the loop both field designs share),
    the default plasma at 120 goes from **26%** of pixels past four levels to **0.6%**. At full size the two formulas
    are the same, and every bake stayed byte-identical.
  - **Its floor rises with frequency — 120 at the broadest waves to 240 at the busiest**, where the mesh gradient's is
    one number. Held to one bar across the knob (about 0.6% of pixels past four levels, none past twenty, at either
    end of the turbulence), measured by `FieldDownscaleHarness.measurePlasma`.
  - **It costs four times the mesh gradient's scrub** — a 65 ms median frame on the emulator against 17 — and the cost
    is spread over the plasma's own arithmetic, about 470 ns a pixel, rather than any one call: a table sine measured no
    faster than `sin` here and was taken back out. The author judged it smooth enough on device. If a slower phone
    disagrees, the answers are a fixed 120 (twice the mesh gradient's cost, busy plasmas a little soft mid-scrub) or
    the loop split across cores, which would speed the mesh gradient too.
  - **Bauhaus ✅ (2026-09-11) — the first design whose seed decides nothing continuous.** Decorated or bare, one
    corner or another, one stop or another: every roll is a choice, so a moment is not a plan of tiles. It is two plans
    and a `t`, and `draw` takes exactly that, with the bake as the moment `0` of a plan and itself. The lattice is the
    knobs' and the frame's, so two seeds hold the same tiles and a tile's partner is the tile in its own place, as
    Confetti's discs are.
  - **Each tile moves the way its two rolls ask.** A quarter decorated at both ends **turns** from one corner to the
    other the short way about the tile's center (clockwise between opposite corners): a quarter at another corner is
    the same shape turned, and turning is the one motion a Bauhaus poster could make. A quarter at one end only
    **blooms** out of its corner or shrinks back into it. Grounds and shapes blend their colors.
  - **At a whole turn the anchor is read off the corner table, and only between turns is it worked out**, so the bake
    draws exactly the circle it always drew. `a turning quarter arrives at each corner the table names` pins that the
    two are one path: turned the wrong way, a quarter would still sweep smoothly, then jump at the end.
  - **The plan is columns and rows**, which is all the frame's shape decides, so it is one picture at every size of one
    shape. Refused when the lattice differs, which only a different density or variant does.
  - **The bake: 961 of 961 harness renders byte-identical.** Confirmed on device by the author.
  - **It shares Confetti's open item.** On a colorful palette every recoloring tile is mid-blend at `t = 0.5`, and the
    middle of the scrub reads as a duller palette of gray-browns. The stagger that would answer Confetti would answer
    this too, and it stays unmade for the same reason.
  - **Truchet ✅ (2026-09-11) — a flip is a quarter turn, so a shuffle turns tiles.** The seed decides one coin flip
    per cell and nothing else: the grid is a knob, the arc's color is its row's, the line weight is a knob. And the
    two orientations are one tile turned a quarter, which is the Truchet trick seen from the other side. So a cell that
    flips turns clockwise about its center with both arcs carried round, and every other cell holds still. Mid-turn
    the arcs leave the edge midpoints and the loops through that cell break; they join again as it lands. The plan and
    the moment are Bauhaus's shape, for Bauhaus's reason.
  - **Clockwise either way**, since both ways round are a quarter turn and neither is shorter. `turnAt` counts turns
    from the unflipped tile, and `a flipping cell … lands flipped the other way` pins that a flip ends on an odd
    count: on an even one the tile would sweep smoothly and then jump back when the bake took over.
  - **`tileCornerAt` is shared now**, on its second consumer: Bauhaus's quarter anchors moved to `TileTurns.kt` with
    their test. A corner stands at a table entry at a whole turn and is turned about the center between two. The
    two consumers differ in one way worth knowing: Truchet's cells are not square wherever the grid's rows do not
    divide the frame, so the fractions are scaled by the cell's width and height separately. A corner then lands
    exactly on the corner the bake draws, and the path between is stretched with the cell. The arcs stay circles.
  - **The bake: 961 of 961 harness renders byte-identical**, Bauhaus's included through the extracted helper. A whole
    turn reads the table, and multiplying a corner of `0` or `1` by the cell is exact, so the four arcs are the same
    calls in the same order. Across the harness's eleven frames on the coarsest grid, the per-step change rises
    smoothly from 5.7% of pixels to 8.4% at the middle and back, with no step at either end.
  - **Verified on emulator-5554.** The drag changes the picture evenly at 3–6% of pixels per step, the spring settles
    in three frames and every frame after is pixel-identical to the bake, and a sub-threshold release returns
    **0.000%** of pixels changed.
  - **What it leaves open: the middle is a maze in pieces.** With about half the cells flipping in unison, `t = 0.5`
    is a field of crescents and no loop survives it. That is what turning tiles looks like, and it recovers by the
    last tenth. A stagger in the swipe's direction would keep most of the maze whole at any moment. That is the same
    design choice as Confetti's, and it is not made here either.
  - **Halftone ✅ (2026-09-11) — a scatter on a lattice, whose seed is a whole noise field.** The seed decides two
    things: each dot's jitter off its cell, and the noise field that sizes and tones every dot. The dots pair by index
    as Confetti's do, and each drifts within its own cell. A dot the field leaves bare at one end stays in the plan and
    grows out of nothing, since a dot's radius is continuous at the floor where it vanishes. The plan holds positions
    in shares of the frame, where the render always put them, so it takes no size and no palette.
  - **The field turns rather than blends, and the numbers are why.** Two seeds' fields are unrelated, and a straight
    average of two unrelated fields swings only about 0.7 as far from its middle as either one does. Measured over
    eight shuffles of the default screen, a straight blend's midpoint keeps **0.70** of the ends' spread, so the middle
    would pass for the same screen with its bare paper filling in and its biggest dots shrinking. Weighted by the cosine
    and sine of a quarter turn, the squares of the two weights sum to one, and the midpoint keeps **1.00**.
    `the middle of a scrub keeps the screen's contrast, where a straight blend flattens it` measures both, so the reason
    for the turn stays checked. In the frames the effect shows directly: bare paper holds at 87.6–88.2% of the frame
    across the whole scrub, and the clusters appear to slide rather than fade in place, because a turn between two
    smooth fields moves their features.
  - **The field walks the ramp.** A dot's tone was always the field read off the ramp, so a dot changing tone passes
    through the palette's own colors between them, as Voronoi's cells do.
  - **The bake: 961 of 961 harness renders byte-identical.** Across the harness's ten steps at full jitter the
    per-step change holds at 4.5–4.7% of pixels, with no step at either end.
  - **Verified on emulator-5554.** The drag changes the picture evenly at 2.7% of pixels per step, the spring settles
    in three frames and every frame after is pixel-identical to the bake, and a sub-threshold release returns
    **0.000%** of pixels changed.
  - **Dot Grid ✅ (2026-09-11) — the seed is only a drift, and a tile switches rather than blends.** The lattice, the
    look and the margin are all knobs. The seed decides one noise field, the drift that pushes tiles across band seams,
    and only while *Dither* is above `0`. With no dither the scrub is a still one, which is faithful. So the scrub turns
    each tile's drift from one seed's field to the other's, and the intrusions along each seam move with it.
  - **`turnNoise` is shared now**, on its second consumer: Halftone's turn moved to `NoiseTurn.kt`, and Halftone turns
    about its field's middle through it. Here the straight blend's failure would be plainly visible: seams ruling
    straighter through the middle of a scrub and the top edge eroding less, the dither knob appearing to move.
  - **A tile never blends, in a scrub or out of one.** Every moment's drift sorts every tile into a band exactly as
    the bake does, so every frame of a scrub is a block this design could have baked. Confetti's rule, blending a
    changing tile's color, was rejected. It would put tones on the block that belong to no band, and Confetti's
    reason for it doesn't arise: the drift carries each tile across its seam at its own moment, not all of them in
    one frame. A quarter turn has at most one peak, so the drift under a tile crosses any seam at most twice in a
    scrub. `a tile's band turns back at most once in a scrub, so no tile flickers` checks that on every tile at full
    dither over four shuffles.
  - **The plan stays in pixels**, as Confetti's does. The lattice is fitted to the frame's pixels, and `draw` reaches
    another size by scaling the canvas.
  - **The bake: 961 of 961 harness renders byte-identical.** Halftone's scrub frames moved by 1–8 pixels of 2.6
    million on dot edges, and never at the ends: `turnNoise` sums the two weighted terms before the middle is added
    back, which rounds differently. Keeping the old order would mean writing the formula twice, which is what the
    extraction removed.
  - **Verified on emulator-5554.** At the default the scrub is subtle: the shuffle measured moved about thirty
    tiles on the seams, so the drag changes at most 0.3% of pixels per step, some steps none. That is what the seed decides here,
    as with Mesh Gradient's warp. At full dither and no margin, the harness frames change 0.5–1.7% of pixels per step,
    lumpier than a continuous design, as tiles that switch must be, and no seam ever switches in one frame. The spring
    settles in three frames to a frame pixel-identical to the bake, and a sub-threshold release returns **0.000%** of
    pixels changed.
  - **Mondrian ✅ (2026-09-11) — a subdivision whose cuts may only slide.** The frame is halved recursively, so the
    recipe is a tree of halvings, and the scrub merges two trees as `GlassTree` merges Vitrall's. `subdivide` now
    records which way it halved each block instead of keeping only the pieces. It makes the same random draws in the
    same order, and halves in place so its walk of the tree is the order the pieces always came out in.
  - **`GlassTree` was not reused, for two reasons.** Where one tree cuts vertically and the other horizontally, it
    pairs the two and turns one into the other, so the rulings would tilt mid-scrub, and a Mondrian whose rulings
    tilt is not a Mondrian. Its cuts are also absolute lines clipped through polygons, where a Mondrian halving is
    `left + width / 2`; the line intersection rounds differently, which would cost the byte-identical bake.
  - **So a cut pairs only with one on the same axis, where it holds still.** A cut only one tree makes, or two made
    across each other, is one-sided: it slides from the middle of its region to the edge, taking the side with fewer
    blocks away with it. The other side is merged against the whole of the other tree's region, which is `GlassTree`'s
    flattening, confined to one axis. **A cut stands at a share of its own region**, not at a line on the frame, so a
    side on its way out carries its whole subdivision down in proportion, and every moment is a partition of
    rectangles. `the frame stays whole at every moment of a scrub` sums and overlap-checks the blocks at 21 values of
    `t` over six shuffles, and `a scrub starts on one Mondrian's blocks and ends on the other's` pins that both ends of
    the walk are the two plans exactly, tones included. The halvings are dyadic, so that holds with no tolerance.
  - **One function turns a cut into two rectangles**, for the subdivision and every moment of a scrub, so a cut that
    holds still lands on the pixels the bake put it on.
  - **A block's color blends.** A shuffle re-tones a large share of the blocks, and nothing here carries each block
    across at its own moment as Dot Grid's drift does, so switching would flip them all at the midpoint. It shares
    Confetti's open item as a result: on a colorful palette the middle passes through olives and khakis.
  - **The plan takes the accent count, not the colors**, as Confetti's takes its inks, and lives in the unit square
    with no size. The one refusal is two plans made for different accent counts.
  - **The bake: 961 of 961 harness renders byte-identical.** In the harness's ten frames at the finest density the
    per-step change rises from 6.7% of pixels to 10.1% and back to 6.2%, with no spike at either end. A leaving
    sliver keeps a single ruling until it is narrower than the ruling itself, and then merges into the edge.
  - **Verified on emulator-5554.** A Mondrian shuffle changes most of the frame, so the drag moves 5–14% of pixels
    per step, rising and falling evenly. The spring settles in three frames and every frame after is pixel-identical
    to the bake, and a sub-threshold release returns **0.000%** of pixels changed.
  - **The tree stays in Mondrian until Modern Mosaic arrives.** That design is also cut by axis-aligned guillotine
    cuts, at fractions other than a half, and is the natural second consumer.
  - **Modern Mosaic ✅ (2026-09-11) — Mondrian's tree with cuts that slide, and a skew that turns.** The seed decides
    three things: the cuts (their direction where a tile is near square, their share, and which side takes the larger
    one), a displacement field that pushes every corner off square, and each tile's tone. So the scrub needs all three.
  - **`GuillotineTree` is shared now**, on its second consumer: Mondrian's tree moved to its own file with its merge
    rule, and a piece carries an index into whatever its design keeps. The one generalization Mosaic needed is that a
    paired cut **slides from one share to the other** instead of holding at a half. Mondrian's scrub frames are
    byte-identical through the move. `GuillotineTreeTest` pins the rule on trees small enough to read: two cuts along
    one axis slide, and two across each other never pair, one leaving by its emptier side while the other arrives
    across what stays.
  - **The skew is read again at every moment, where each corner then is**, from the two seeds' fields turned together
    (`turnNoise`, its third consumer), and never lerped per corner. It is one field at every moment, so two tiles
    meeting at a corner still move it together and the grout stays the even band this design is built on. The turn
    keeps the middle as skewed as the ends. `a scrub starts on one mosaic's tiles and ends on the other's` checks every
    corner at both ends at full skew, which is the check that the turn lands on each seed's own field.
  - **The plan stays in pixels**, as Confetti's does, because whether a tile survives its grout is decided in
    pixels. A tile narrower than its grout is not drawn and draws no tone, so which tiles those are is the tone
    stream's order. Mid-scrub a tile's tone blends between its two ends; one arriving or leaving keeps its own the
    whole way.
  - **One set of functions draws every tile**, for the bake and each moment: corners through the field, the grout
    inset, the rounding.
  - **The bake: 961 of 961 harness renders byte-identical.** In the harness frames at full skew the per-step change
    rises from 6.5% of pixels to 9.5% and back to 7.8%, with no spike at either end. The ground rises from 33% of the
    frame to 37% in the middle, since the tiles of both mosaics are present at once, each with its own grout.
  - **Verified on emulator-5554.** The drag changes 4–10% of pixels per step, evenly, the spring settles in three
    frames and every frame after is pixel-identical to the bake, and a sub-threshold release returns **0.000%** of
    pixels changed.
  - **What it leaves open: a leaving tile is a needle before it goes.** Narrowing into an edge is what a leaving tile
    does in both guillotine designs, but a Mondrian is full of thin blocks and this design's tiles are nearly square,
    so here the passing needles read as foreign. Shrinking a leaving tile toward its center instead would not hold the
    partition, and the partition is the mechanism. The colorful midpoint goes muddy, as it does for every flat-color
    design so far.
  - **Rounded Tiles ✅ (2026-09-11) — one phase, so the rank slides.** The seed decides one number: where the rank of
    bars sits across its lanes, up to half a lane either way. Every other quantity is a knob, including the aim,
    despite the class note saying the aim was seeded. That paragraph, and the one naming *Rotation* as the fan, were
    both stale and are corrected. So the plan is the knobs and the phase, and the scrub lerps the phase: the whole rank
    slides as one, each bar keeping its angle, gradient and color, at most one lane per shuffle. It is Wave Dividers'
    kind of scrub.
  - **The bake: 961 of 961 harness renders byte-identical.** With the fan open and the bars overlapping, the harness
    frames change exactly evenly, 4.49% of pixels at every step.
  - **Verified on emulator-5554**, over four shuffles. The drag changes the picture exactly evenly (11.2% of pixels per
    step on one shuffle, 7.4% on another). The spring settles in three frames and every frame after is pixel-identical
    to the bake, and a sub-threshold release returns **0.000%** of pixels changed.
  - **How far a shuffle moves the rank is luck, and it can be almost nothing.** Two random phases lie a third of a lane
    apart on average, but the first two shuffles measured moved the rank 2 px and 20 px of a 180 px lane. A probe of
    2,000 random pairs confirmed the spread is healthy (median 0.27 of a lane), so those were low draws rather than a
    correlated seed. A shuffle near the bottom of that range is a scrub that barely moves, which is faithful to what
    the seed decides.
  - **Ribbon Flow ✅ (2026-09-11) — a turned field, and the ordering bound it found wrong.** The seed decides one noise
    field, the one that combs the lines off their lanes, so the scrub turns one seed's field into the other's
    (`turnNoise`, its fourth consumer). The plan is the knobs and the field; a moment is a plan whose field is the turn.
  - **The design promises its lines never cross, and at high *Distortion* they did.** The amplitude is capped by a
    bound on the field's slope across the rank, and the bound assumed a slope of `2`; sampled, this Perlin field
    climbs to about `2.75` where its quintic fade is steepest. On the densest rank at the default detail, 29 of 1,470
    neighboring pairs crossed over thirty seeds at full *Distortion*, and none at the default *Distortion*. Every line
    still looked like a line, so nothing had caught it: the test beside the bound held it against the same assumed `2`
    on both sides. **The author chose to correct the bound to `2.8`** over leaving the bake or clamping the knob's top.
    It changes the bake: every render whose slope ceiling binds wanders about 29% less, the default included, and the
    14 such harness renders changed (8.7% of the default's pixels past 24 levels, 15% at full *Distortion*). The other
    947 are byte-identical. At full *Distortion* the old bake pinched pairs of lines together; the new one keeps them
    apart. Two tests now guard it: one samples the field's slope against the bound, the other checks lane order on the
    render's own offsets over every detail and count. Both fail at the old `2`.
  - **The turn is steeper than either field, and that was the scrub's real decision.** Where both fields climb
    together, a turn climbs up to `√2` as steeply, so the ordering bound built on one field's slope stops being a
    guarantee mid-scrub. Measured over every detail and count at full *Distortion*, 2 of 13,050 neighboring pairs touch
    at the midpoint, by under a pixel. At the default *Distortion* the headroom covers the `√2`, and a test holds every
    lane in order through the scrub there. A straight blend would hold the bound exactly, at the cost of a third of the
    wander at the midpoint, which reads as the knob dipping mid-swipe. **The author chose the turn.**
  - **The bake: the scrub's split is byte-identical** to the corrected bake, 961 of 961. The harness frames at full
    *Distortion* on the densest rank change 10.9–11.7% of pixels per step, and the middle wanders as much as the ends.
  - **Verified on emulator-5554.** The drag changes the picture evenly at 3% of pixels per step, the spring settles in
    three frames and every frame after is pixel-identical to the bake, and a sub-threshold release returns **0.000%** of
    pixels changed.
  - **Ribbons ✅ (2026-09-11) — a bundle mirrored by its seed, read from one side.** The seed decides where the spine
    starts, where it ends and how far it overshoots, all continuous, and whether it is mirrored across the frame or
    upside down, both choices. The continuous three interpolate, and so does the upside-down flip: its S flattens
    through a straight line and bends the other way.
  - **Mirroring across the frame is the hard case, and it has a reading rather than a rule.** A mirrored spine's
    control points interpolated against an unmirrored one's all meet at the frame's middle halfway, and the bundle
    folds into a vertical line. But a curve mirrored left to right is the same curve read from its other end, so the
    scrub reads both spines left to right (`forward`) first. Read that way, they share their `x` exactly, only the S
    bends, and the fan's pinch slides along the bundle to the other side. That needed `Spine` to carry its spread and
    splay **per control point** rather than per end: reading a spine backward is then just reversing four arrays, and
    `a spine read forward draws the same lines, backwards` pins that it is the same bundle, point for point. The bake
    keeps its own orientation, so its paths are exactly as they were.
  - **The lines stay nested through the scrub**, since every moment is a mix of two nested bundles and the offsets are
    linear in it. `a scrub between opposite sweeps keeps the bundle across the frame and its lines nested` checks both
    halves at full splay.
  - **The bake: 961 of 961 harness renders byte-identical.** Between two opposite sweeps at full splay the harness
    frames change 7.1% of pixels per step falling evenly to 6.3%, with no step at either end, where the handoff swaps
    a curve read backward for the bake's own.
  - **Verified on emulator-5554**, over two shuffles. The drag changes 6–11% of pixels per step, evenly, the spring
    settles in three frames and every frame after is pixel-identical to the bake, and a sub-threshold release returns
    **0.000%** of pixels changed.
  - **Polygon Cascade ✅ (2026-09-11) — a run turned by its heading, never by its ends.** The seed decides three
    things: the *Wobble*'s harmonic phases, the run the copies march along (a bearing and a length), and which way the
    cascade turns. All three are continuous underneath. The phases turn the short way, so the wobble's bends travel
    round the shape. The sense lerps from one sign to the other, so a cascade changing direction untwists through a
    straight stack and twists back.
  - **The run is the trap.** Two runs pointing opposite ways have swapped endpoints, and interpolating those pulls
    both to the frame's middle halfway, stacking every copy on one center: the rosette the class note says the
    construction cannot survive. So a run is kept as what it is drawn from, `RunDraw`'s bearing and length share, and
    turned as those: the bearing the short way, the length straight. It then swings about the frame's center and never
    shortens below its minimum, which `a run turned to the opposite heading keeps its length and its centre` checks at
    21 moments.
  - **Both halves live in the files they belong to**, since Flow Lines shares them: `SeededHarmonics.turnedTo`, and
    `RunDraw` beside `TweenRun` with `tweenRun` now just a `RunDraw` laid down at once. Its arithmetic and its draws
    are unchanged, and `a run drawn at once is the run a plan lays down` pins that. The plan is the knobs, the
    harmonics, the run and the sense, and it is drawn at any size.
  - **The bake: 961 of 961 harness renders byte-identical.** Filled, shadowed, fully turned and wobbled, between two
    cascades turning opposite ways, the harness frames change 17–23% of pixels per step: a shuffle re-aims the whole
    cascade, and it does so evenly, with no step at either end.
  - **Verified on emulator-5554**, over two shuffles. The drag changes 10–18% of pixels per step, evenly, the spring
    settles in three frames and every frame after is pixel-identical to the bake, and a sub-threshold release returns
    **0.000%** of pixels changed.
  - **Flow Lines ✅ (2026-09-11) — the cascade's scrub on an open curve.** The two designs are one construction, and
    the seed decides the same three things in both: the base curve's harmonic phases, the run, and the twist's sense.
    So the plan and the scrub are Polygon Cascade's, through the same `SeededHarmonics.turnedTo` and `RunDraw`. The
    fan swings about the frame's center, its waves travel along the curve, and a twist changing sense unwinds through
    none. `tweenRun`, which only laid a run down at once, lost its last caller and is deleted.
  - **The bake: 961 of 961 harness renders byte-identical**, and the cascade's scrub frames with them. Between two
    fans twisting opposite ways at full waviness, the harness frames change 25–38% of pixels per step, with no step
    at either end: a shuffle re-weaves the whole fan.
  - **Verified on emulator-5554**, over three shuffles. The drag changes about 27% of pixels per step, evenly, the
    spring settles in four frames and every frame after is pixel-identical to the bake, and a sub-threshold release
    returns **0.000%** of pixels changed.
  - **What it leaves open: a shuffle that reverses the twist unweaves the fan halfway.** The default twist is about
    150°, and the envelope it weaves is this design's character. A sense interpolated between its signs passes
    through no twist at all, so the middle of such a shuffle is a plain rank of parallel waves that re-weaves the
    other way; the *Turn* knob appears to dip to zero mid-swipe. Two of the three shuffles driven did it, and about
    half do. The cascade does the same, less visibly, since its default turn is smaller and its copies are shapes
    rather than an envelope. No continuous path between the two senses avoids it; the alternatives are switching at
    the midpoint, a pop, or holding one end's sense, which ends the scrub on a picture that is not the bake.
  - **Triangular Facets ✅ (2026-09-11) — four seeded parts, each with its own interpolation.** The seed decides the
    lattice's jitter, the relief's noise, the color field's node stops, and every facet's speckle accent. The points
    lerp: the lattice is fixed, so a point's partner is itself, and border points still slide only along their edges.
    The relief turns (`turnNoise`, its fifth consumer), so the lighting keeps its contrast through the middle. The
    field and the accents blend in RGB, which is already how the field mixes its regions; the plan carries resolved
    colors for that reason, as the mesh gradient's does.
  - **A cell changes diagonal when its two diagonals pass through equal length**, because every moment is split by the
    design's own shorter-diagonal rule. Its two facets change shade in one frame, one cell at a time at its own moment,
    the way Dot Grid's tiles switch band, and every frame is a sheet the design could have baked.
  - **Found on the way: the bake itself folds at high *Distortion*.** The class note says folding begins "past about"
    `MaxJitter`; measured over twenty seeds, triangles already invert over their neighbors from *Distortion* `0.75`, and
    at full *Distortion* in 17 to 20 of 20 seeds at the finer densities (up to 0.8% of the frame covered twice). None
    fold at the default. The scrub does not cure or cause it: over the same twenty shuffles its worst fold stayed below
    the worst baked sheet's, though a single moment can fold more than its own two ends. A test asserting it never
    does was written and failed, which is how that was found. The guarantee that holds is the default's: `at the default
    distortion the facets tile the frame at every moment of a scrub`. Fixing the fold changes the bake, so it is split
    off as its own task rather than done here.
  - **The bake: 961 of 961 harness renders byte-identical.** At full relief with a hair of leading, the harness frames
    change 10–14% of pixels per step, with no step at either end.
  - **Verified on emulator-5554**, over two shuffles. The drag changes about 8% of pixels per step, evenly. The first
    spring frame after release changes half the frame or more, since every facet is mid-blend and the spring covers
    most of the remaining travel at once. It settles in three frames, every frame after is pixel-identical to the bake,
    and a sub-threshold release returns **0.000%** of pixels changed. The colorful midpoint goes browner, as every
    flat-color design's does.

**Measure before M1 — the instrument exists now.** `GeneratorTimingHarness` (`core:graphics`, androidTest) times every
generator at six sizes from full-screen down to a 64th of the pixels and least-squares each design's cost curve into a
**fixed** and a **per-megapixel** term. That is the `plan`-versus-`draw` split this plan is sized by, and it is
obtainable *before* anything is split, since the two terms are separable from the curve alone. Three readings to take
from it:

- **`fixed` near zero with a good `r2`** confirms a design belongs in the field bucket by measurement rather than by
  reading its source — which is the check the classification above went without, twice.
- **`fixed` large** names the designs that will hitch at touch-down, and therefore what M4's speculative pre-planning
  is actually for.
- **`r2` poor** means the composition changes with the size rather than resolving finer — so the fit is a detector for
  *the rest of Contour's problem*, and the cheapest way to find the per-design floor open question 3 asks for.

Run it **on the phone and alone**: an emulator has no thermal ceiling and a different Skia build, and the PNG sweeps in
`GeneratorRenderHarness` heat the device enough to slow everything measured after them. Its KDoc carries the command
and the rest of the method.

### First run — emulator, 2026-09-10, shape only

A smoke run on an x86_64 emulator (`ranchu`, API 36). **The absolute milliseconds are not a phone's and must not be
quoted as budgets**; what carries over is `planShareAtFull` and `r2`, which are ratios taken within one design on one
machine. Three findings, and two of them change decisions above.

**1. The bucket split is confirmed by measurement, and Voronoi with it.** Thirteen designs come back
`planShareAtFull ≤ 0.02` with `r2 ≥ 0.998` — Linear Gradient, Mesh Gradient, Voronoi, Plasma, Ribbed Glass, Marble,
Metaballs, Waves, Louvers, Diagonal Bands, Gradient Columns, Wave Dividers, Rounded Tiles. Pure pixel cost, zero fixed
cost, perfectly linear. **Voronoi is `planShare = 0.000, r2 = 1.0000`**, which settles the earlier claim that it would
need a Delaunay sweep: it plans nothing, and its downscale is exactly free.

*Corrected by M6 (2026-09-11): the cost half of that is right and the downscale half is not.* `planShare` and `r2`
measure what a design **costs** at each size; they cannot say whether its picture **survives** being evaluated
smaller, and Voronoi's does not — its seams thicken. Cost and fidelity are two measurements, and a design's bucket
needs both: the timing fit for the first, `FieldDownscaleHarness` for the second.

**2. Most of the primitive bucket already renders full-frame inside a frame budget — so M2 is less urgent than it
looks.** Eleven designs land **under 10ms at full 1080×2400 on an emulator**: Mondrian 4.6, Bauhaus 5.9, Confetti 6.2,
Dot Grid 6.6, Triangular Facets 7.1, Modern Mosaic 7.2, Polygon Cascade 7.3, Soft Overlaps 7.6, Halftone 7.6, Truchet
8.5, Ribbon Flow 9.1. Software raster is already fast enough to scrub these at full resolution. The Compose hardware
canvas is still the right target and still costs nothing to adopt, but it is a headroom decision rather than a
prerequisite — which is worth knowing before M2 is sequenced ahead of M3.

**3. `r2` found more than Contour, which widens open question 3.** Contour is the worst at **0.4541**, exactly as the
`DraftShortSidePx` lattice note predicted. But **Flow Lines (0.7070)** and **Flow Field (0.8966)** are nonlinear too,
and for a different reason: their trail walks scale their *step counts* with the frame, so the planning work itself
changes with the size rather than the fill. That is a second class of "the downscale is not free", it was not
predicted, and it means the per-design floor is owed to at least five designs rather than one.

The plan-bound designs — the ones M4's speculative pre-planning exists for — are Flow Field (`0.850`), Spray (`0.648`),
Flow Lines (`0.500`), Contour (`0.469`) and Impasto (`0.362`). Everything else can be planned on demand.

### Field survey — emulator, 2026-09-11

`FieldDownscaleHarness.surveyRemainingFieldDesigns`, run over every design still filed as a field. Each is rendered at
a short side of 360, 240, 180, 120 and 60 and blown up with the scrub's bilinear filter, over its own knobs one at a
time, both corners and every choice, and compared against its full-size render. Columns are the share of pixels more
than 4 levels off at 360 — the worst recipe and the default — against Mesh Gradient's 0.001% at 120, and the
full-frame cost from `GeneratorTimingHarness` on the same machine. Every noise floor was zero: two full renders of
one recipe agreed exactly, Planet's included, so the failures are the downscale's.

| design | worst, 360 | default, 360 | full frame | what it draws |
|---|---|---|---|---|
| Linear Gradient | 0.000% (at 60, too) | 0.000% | 332 ms | a true field — that ignores the seed |
| Plasma | 3.99% | 0.54% | 1389 ms | smooth, finer as its frequency rises |
| Diagonal Bands | 3.53% | 1.94% | 47 ms | straight flat bands on a ground |
| Waves | 2.07% | 0.80% | 129 ms | flat bands between smooth crests |
| Wave Dividers | 9.31% | 2.92% | 363 ms | flat bands between identical waves |
| Gradient Columns | 5.18% | 0.64% | 214 ms | flat columns, edge- and rake-shaded |
| Louvers | 5.34% | 1.52% | 116 ms | strips each showing a shifted ramp |
| Metaballs | 2.75% | 1.57% | 904 ms | bands of a warped potential |
| Ribbed Glass | 4.96% | 2.60% | 1164 ms | refraction per rib, hard rib seams |
| Planet | 3.08% | 1.51% | 436 ms | per-pixel pigment walk, hard band edges |
| Marble | 24.76% | 9.02% | 1690 ms | creased veins over fine turbulence |

**The pictures matter more than the numbers.** A downscaled band design does not come back soft, it comes back
*stepped* — each pixel was classified into a band with no coverage in between, so the blow-up is a staircase along
every edge, which a softened edge would at least not be. Marble's veins break into rows of dots. The ribbed designs
blur a crease that is meant to be sharp.

**And a pixel loop is slow on its own terms.** Linear Gradient — one ramp lookup per pixel — costs 332 ms at full frame,
128 ms per megapixel. Whatever a design does per pixel, doing it for every pixel of a phone on the CPU is not a frame.

## Open questions

1. ~~**What a release below threshold does**~~ — **answered: it snaps back (2026-09-10)**, and the evidence was
   already in this document. The 2026-08-30 probing recorded that a sub-threshold drag on the reference left the design
   as it was; that was filed at the time as evidence *against* there being a scrub at all, which was the wrong
   conclusion from the right observation — what it had found was this threshold. A drag with no way to change your
   mind would also be the odd one out among every other gesture on the device. `ShuffleSwipe.CommitAt` is `0.4`.
2. **Whether the scrub also drives `DesignParams`**, or only the seed and palette. The coverage collapse is consistent
   with either an inset knob or a scale-out of unmatched primitives.
3. ~~**Whether the reference's field designs morph**~~ — **answered *yes* (2026-09-10), on Mesh Gradient**, and the
   **per-design scrub resolution** is answered with it for that design: **120**, measured, against `DraftShortSidePx`'s
   360. What remains is that every other field design owes its own number, and `FieldDownscaleHarness` is now the way
   to get one. Contour is the design that will want the largest, and Flow Field and Flow Lines are the ones where the
   `r2` fit says a downscale is not free at all — those three are the ones to measure before assuming anything. **And
   run it before trusting the bucket at all**: Voronoi passed the timing fit perfectly and failed this outright. *Run
   over the rest of the bucket on 2026-09-11*: see "Field survey" — only Linear Gradient passes outright, Plasma's
   floor depends on its frequency and is owed a number when it is split, and nine designs have no floor at all.
4. **Where the ground color comes from** — the palette, or its own field on the plan. It has to lerp either way; only
   the ownership is open.
5. **Whether `Plan` lives in `core:graphics` or earns a module.** It is data and wants to be plain, but `core:model` is
   plain *Kotlin* by rule and a plan is not small. `core:graphics` is the honest first home.
6. **What scrubs a design whose edges are cut from an expensive field** — Metaballs, Ribbed Glass, Planet, Marble.
   Downscaling stairs the edges and full resolution costs 0.4–1.7 s. Two candidates, neither cheap: **trace the edges**
   — evaluate the field on a small lattice, march its band thresholds into paths, and let the canvas draw them crisp,
   which changes the bake to vector edges as Voronoi's did; or **AGSL** — the same per-pixel code as a shader at full
   resolution, API 33+, with the dissolve below that and a second implementation of each field to keep in step.

## How this was settled, and why the record is kept

The same question has now been answered wrong twice from video evidence, in opposite directions:

- **2026-08-30 — a video read as a continuous `phase` parameter** bound to swipe delta. Rejected on emulator probing: a
  sub-threshold drag did nothing, and a shuffle changed the palette, which a geometric phase would not touch. That
  rejection was *correct about its evidence* and wrong about the mechanism — what it had found was the threshold below
  which a scrub does not start, not the absence of a scrub.
- **2026-09-10 — a first capture read as a crossfade.** Fifteen frames spanning several swipes, sampled far enough
  apart that settled states and transitions interleaved. Crossing strokes at partial alpha read as bent single strokes;
  a frame showing two orb sets read as two superimposed pictures. Wrong, but the frames genuinely do look like a
  crossfade at that sampling rate.

- **2026-09-10, later the same day — the catalog split by implementation rather than by capability.** With the
  mechanism finally right, the *classification* went wrong: generators were sorted by whether they call `canvas.draw*`
  or `setPixels`, and "writes pixels" was read as "has no elements to match". A capture of a **mesh gradient** morphing
  disproved it — a mesh gradient's elements are its control points, which are evaluated rather than drawn. The error
  cost two false claims that had been argued at length: that a third of the catalog could not morph at all, and that
  Voronoi would need a Delaunay implementation. Both came from measuring the code instead of the design.
- **2026-09-11 — a design filed by its cost curve.** Voronoi's timing fit was perfect (`planShare = 0.000`,
  `r2 = 1.0000`) and was read as "its downscale is exactly free". The fit only says its cost is per pixel; a
  downscaled render thickened every seam. The cost was measured and the picture was not — the same error as the one
  above, one level up.

What settled the mechanism was a capture of **one** swipe, deliberately slow, plus the author's report that pausing the
finger pauses the transition. What settled the classification was the author simply naming a design the table had
called impossible. Three lessons worth carrying:

- **Sample rate is the whole problem.** Frames far apart cannot distinguish a transition from a settled state, and a
  transition sampled at its midpoint looks like whatever you already believe.
- **The strong evidence is identity, not opacity.** "The intermediates look opaque" is weak — a *slow* scrub is the
  best case for a draft-and-settle scheme, so sharp frames prove less than they appear to. "The same polygon is present
  in five consecutive frames at five positions" is not weak, and it is what no re-generation can produce. On a smooth
  field, where a crossfade and a morph both look smooth, the same test still works and is the only one that does: a
  crossfade fades a feature out *in place*, so a crease that **migrates** across twenty frames rules it out.
- **How a generator rasterizes says nothing about whether it can morph.** That is the mistake above, and it is easy to
  repeat because the implementation is the part that is greppable. Ask what the picture is a *function of*; if that is
  a small parameter set, it interpolates, whatever the code does with it afterward.
