# Morph Engine

**Status:** M1–M3 built (2026-09-10); M4 next. Drawn from three screen captures of Smart Launcher's wallpaper
studio taken by the author, each of which overturned a conclusion drawn from the one before.

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
> Vitrall, Mondrian, Modern Mosaic, Bauhaus, Triangular Facets, Rounded Tiles — every design whose panes tile what
> they are cut from. `GlassTree` is the worked example; the recipe is to keep the construction's own recursion
> instead of discarding it, merge two of them, and clip per frame.

The test for which bucket a design is in is one question: **could two of its primitives be moved independently and
still leave a legal picture?** A scattered dot, yes. A pane, no.

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
nearest-site assignment is **continuous in site position**, so every boundary slides smoothly without any cell polygon
ever being built. No Delaunay, no Fortune's sweep, no re-implementation.

### The two buckets, by cost — and each is cheap where the other is not

**Primitive designs — re-draw is `O(elements)`, and they need full resolution (19).** Bauhaus, Confetti, Contour, Dot
Grid, Flow Field, Flow Lines, Halftone, Impasto, Modern Mosaic, Mondrian, Polygon Cascade, Ribbon Flow, Ribbons,
Rounded Tiles, Soft Overlaps, Spray, Triangular Facets, Truchet, Vitrall. Cheap per frame and GPU-rasterizable, but a
downscale would soften the edges that *are* the picture. `plan` here extracts what `render` already computes before it
draws — no new geometry, and the byte-identical bake assertion should hold trivially.

**Field designs — re-evaluation is `O(pixels)`, and they downscale for free (13).** Diagonal Bands, Gradient Columns,
Linear Gradient, Louvers, Marble, Mesh Gradient, Metaballs, Plasma, Ribbed Glass, Voronoi, Wave Dividers, Waves,
Planet. Expensive per full-resolution frame on the CPU, but the field is smooth by construction, so a scrub frame
evaluated at a fraction of the pixels and bilinearly upscaled is **perceptually identical**. `plan` here is a small
parameter struct rather than a set of paths, and `draw` runs the pixel loop — the same seam, a different kind of plan.

**That is the inversion worth holding on to: each bucket is cheap in exactly the way the other is not.** The intuition
that a "cheap draft" helps the expensive designs is right; the intuition that it helps *everything* is wrong, and it is
the primitive designs that must stay at full size.

**The free downscale is not uniform, and the exception is already recorded.** `DraftShortSidePx = 360` exists because
`ContourGenerator` samples its terrain on a lattice 360 cells across the short side — below that its contours *move*
rather than soften, which is a different composition rather than a softer one. So the safe scrub resolution is
**per-design**: a mesh gradient can go far below 360, Contour cannot. One global floor is the right default and the
wrong ceiling.

**AGSL becomes an optimization, not a prerequisite.** It would speed the field bucket, and it is still API 33+ against
a `minSdk` of 26 — but the downscale already makes that bucket affordable, so nothing is blocked on it and no second
renderer has to exist.

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
- **M4 — the gesture.** `t` bound to the finger, commit on release, and speculative pre-planning of the next seed while
  idle so the plan is in hand before touch-down.
- **M5 — the field bucket, on one generator.** Mesh Gradient, since it is the design the third capture proves and the
  one with the smallest parameter set. `plan` is a struct of control points and ramp positions; `draw` is the existing
  pixel loop; the scrub evaluates at a fraction of the size and upscales. This is where the per-design safe resolution
  gets a number rather than a guess.
- **M6 — roll out**, one generator at a time, each with the byte-identical bake assertion. Eighteen extractions left in
  the primitive bucket and twelve parameter structs in the field one, and neither is a rewrite.

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

## Open questions

1. **What a release below threshold does** — snap back to A, or commit B anyway. The capture is one complete swipe and
   does not say.
2. **Whether the scrub also drives `DesignParams`**, or only the seed and palette. The coverage collapse is consistent
   with either an inset knob or a scale-out of unmatched primitives.
3. **Whether the reference's field designs morph — answered *yes* (2026-09-10), on Mesh Gradient.** What is still open
   is the **per-design safe scrub resolution**: `DraftShortSidePx`'s 360 is set by Contour's lattice and is far more
   than a mesh gradient needs. M5 is where that stops being a guess.
4. **Where the ground color comes from** — the palette, or its own field on the plan. It has to lerp either way; only
   the ownership is open.
5. **Whether `Plan` lives in `core:graphics` or earns a module.** It is data and wants to be plain, but `core:model` is
   plain *Kotlin* by rule and a plan is not small. `core:graphics` is the honest first home.

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
