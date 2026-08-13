# Foreground size — making every app's icon look the same size

**Status:** built 2026-08-13, after one earlier attempt was reverted whole (§5). **Not fully verified** — most icons
are right on device, some are not, and the failing ones are not written down yet (§7).
**Covers:** how large an app's own artwork is drawn, and what "the same size" can be made to mean.
**Not covered:** icon *sizing* as a user setting (cell fractions, dp guardrails) — that is `SurfaceMetrics` and
`docs/SETTINGS_PORT_PLAN.md`. This is about the geometry *inside* the icon box.

---

## 0. The one constraint everything else follows from

**An icon's visible size is whatever its author drew, and nothing bounds it.**

Android publishes a convention — an adaptive icon is authored on a 108-unit canvas whose inner 72 is the viewport,
so content outside that may be cropped — but it is a *convention*, not a guarantee. Real apps split three ways:

| Authoring | How common | Ink as a share of the canvas |
|---|---|---|
| Respects the safe zone, glyph roughly fills it | most | ~0.6–0.7 |
| Paints right across the full canvas | a real minority | ~1.0 |
| Small glyph in a wide margin | a real minority (Reddit) | ~0.2–0.4 |

Any scheme that scales by an *assumed* convention is wrong for two of those three rows, and any scheme that scales
by *measurement* is guessing at what the author meant. **Only a clip makes extent equal by construction** — which is
why every shipping launcher, L1 included, masks.

So the honest framing is a fork, and §1 must be answered before §2 is worth reading.

## 1. First decide what "the same size" means

These are three different targets and they do not agree. Picking one is the author's call.

- **(a) Same *extent*.** Every icon occupies the same outline. Only a mask delivers this, and it delivers it
  perfectly. Artwork outside the outline is cropped.
- **(b) Same *bounding box*.** Every icon's ink has the same longest side. Cheap to measure. Its flaw is that a
  solid disc and a thin diagonal glyph with equal boxes look nothing alike in weight — the disc reads far heavier.
- **(c) Same *optical weight*.** Every icon covers roughly the same visible area. Closest to what an eye means by
  "the same size", and the hardest to compute; still an approximation.

L1 shipped **(a)**. AOSP's `IconNormalizer` aims at **(c)**, and uses it only where it has no mask to fall back on.
**L2 shipped (b)**, at the author's call and against a target of *filling the box* rather than matching a small
shared size — see §6 for why that combination avoids the flaw (b) usually has.

## 2. The mechanisms, each with its real cost

### A. Bound the extent — a mask

**A1. Stack-level tile mask.** Mask the *finished composite* to one shape. This is L1's `IconRenderer`: it
composites every layer, then draws `tileShape`'s path and `SRC_IN`s the content through it, with `IconStyle.shape`
defaulting to `RoundedSquare`.

- Delivers (a) exactly. No measurement, no per-icon guessing, no heuristic to tune.
- The scale conventions in **B** become *correct* under it, because overflow is cropped rather than displayed.
- **Cost: it reverses a documented L2 decision.** CLAUDE.md states twice that there is no stack-level mask and that
  the tile shape "became a per-layer shape". That decision was made for the layer editor's sake and is right *for a
  layer editor*; it just leaves nothing that bounds the icon.
- **Cost: it forces a look**, unless the default is `None` — in which case it delivers nothing until a user asks.
- Model shape: a `tileShape: IconShape?` on `IconLayerSet`, distinct from `IconLayerSpec.shape`. The distinction is
  real and worth stating in the type: a per-layer shape masks **content** (a creative tool, one layer at a time), a
  tile shape bounds **the icon**.

**A2. Per-layer mask on the two app-artwork layers.** ~~Approximates A1.~~ **Rejected, and it was tried.** It
misses custom decoration layers entirely, and it clips each layer *before* compositing rather than clipping the
result, so it is not the same operation. It looked worse on device.

### B. Scale by the declared convention — no measurement

**B1. Adaptive ×1.5, legacy ×0.7.** L1's `normalize`, default on. 1.5 is `1 + 2 × getExtraInsetFraction()`, the
platform stating its own geometry; 0.7 shrinks a full-bleed legacy bitmap so it sits inside the tile instead of
filling it.

- Free, faithful, no per-icon work.
- **Alone it is wrong for row 2 of the table** — an app painting across its canvas gets enlarged 50% past what it
  drew and, unmasked, simply overflows. This is exactly what was reported on device.
- **Under A1 it is correct for all three rows**, because the overflow is cropped the way a stock launcher crops it.
- Legacy icons need the ×0.7 regardless of A, since masking a full-bleed square slices its corners off. That pairing
  — shrink onto the plate `LegacyBackground` already recovers — is AOSP's `wrapToAdaptiveIcon` expressed in layers.

### C. Scale by measurement — content-aware

Common to all of these: rasterize the foreground small, scan it, derive a scale, fold it into the layer transform
so both render paths get it through `LayerTransform`. Measurement belongs in `DrawableParser` (beside
`LegacyBackground`, which already rasterizes and scans), never in the resolver, which is pure.

**C1. Bounding-box fit.** Measure the ink's bounding box, scale its longest side to a target fraction. L1's
`foregroundUniform`, default off. **Built and reverted this session** — see §5. Delivers (b), inherits (b)'s flaw,
and needs a cap to stop a sparse glyph exploding.

**C2. Area-based normalization — AOSP's `IconNormalizer`.** Measure the *area* of visibly-opaque pixels, not just
the box, and scale so that area meets a target. It additionally estimates whether the silhouette is near-circular
or near-square and allows the rounder ones to be drawn larger, since a disc inscribed in a box reads smaller than
the box.

- The best-known approximation of (c), and battle-tested against every app on the Play Store.
- Strictly better than C1 at the one thing C1 is worst at (solid vs thin).
- **Still a heuristic**, and note what AOSP does with it: it is used to fit *legacy* art onto a generated plate
  inside `wrapToAdaptiveIcon`, **not** as a substitute for masking. AOSP masks too.
- If built, read the constants out of the AOSP source rather than from anyone's memory of it — the alpha floor, the
  circle-area factor and the scale slope are all tuned numbers.

**C3. Perceptual weight.** Alpha-weighted mass, centroid, radius of gyration — a genuinely better model of "looks
the same size" than area. No reference implementation to port, unbounded tuning, and no way to evaluate it here
(see §3). **Not recommended**; listed so it is visibly considered and set aside.

### D. Replace the artwork entirely

**D1. Icon packs.** A pack author has already normalized every icon by hand — the only source of *designed*
uniformity available. Already built in L2 as a `LayerSource`, already applicable globally. Covers only the apps a
pack maps (`appfilter.xml`), which is never all of them; the rest fall through to whatever B/C do.

**D2. Themed (monochrome) icons.** Authored to a stricter convention, so they are markedly more uniform than
ordinary foregrounds. Already built. Not a general answer — it changes the look entirely and only some apps ship one.

### E. Don't automate it

**E1. Per-app manual zoom.** Already built: the studio's Transform zoom, per app, undoable, persisted. Whatever
automatic scheme is chosen, this stays the escape hatch for the handful it gets wrong — and it is the reason no
automatic scheme has to be perfect.

**E2. Nothing.** Accept authored sizes. Current state, and the state the reverted attempt returned to. Worth naming
because it is the only option with no failure modes, and because two of the three attempts made things *worse* than
this.

## 3. The gap that caused the failures: nothing here can be evaluated

Three mechanisms were built this session; each looked right in the studio and wrong on the home screen, and each was
found wrong only by the author noticing one app. **There is no way to compare strategies except by eye, one icon at
a time, on a device.**

That is the first thing to fix, and it is cheap. The `IconLayers` dev-harness playground already draws one set two
ways side by side; what is needed is a page that draws **many real installed icons in a grid, under each candidate
strategy**, so an outlier is visible immediately instead of being discovered a week later. Reddit would have been
obvious in one screenshot.

**Recommendation: build this before implementing any of §2.** It is the difference between choosing a strategy and
guessing at one.

## 4. Combinations worth considering

Nothing here is exclusive, and the real answer is probably layered:

| Combination | Delivers | Notes |
|---|---|---|
| **A1 + B1** | (a), exactly | L1's shipping answer. Reverses the no-stack-mask decision; forces a look unless default `None`. |
| **B1 only** | nothing reliable | Already shown to fail on device. Do not ship alone. |
| **C2 + B1** | (c), approximately | No forced look. Best option if the mask stays rejected. |
| **A1 (opt-in) + C2 (default)** | (c) by default, (a) if asked | Most work; lets the user choose guaranteed uniformity over untouched artwork. |
| **D1 + anything** | (a) for mapped apps | Orthogonal — a pack overrides whatever the rest does. |
| **E2** | nothing | The honest baseline. |

## 5. What was tried on 2026-08-12, and reverted

Recorded so it is not repeated. All three were built, all three failed on device, and the whole lot was reverted to
`0a34c52` at the author's call.

1. **B1 alone** (flat ×1.5 / ×0.7). Fixed the common case; made apps that ignore the safe zone visibly enormous,
   because nothing cropped the overflow.
2. **C1** (bounding-box fit) replacing it. Fixed those; broke the opposite case — a small glyph in a wide margin
   divided to a huge multiplier, which is what happened to Reddit.
3. **A cap** on C1 at ×1.5. At that point the design was three heuristics deep, each patching the previous one's
   casualty, which is the point the author called it a hack. Correctly.
4. **A2** (per-layer mask defaulted to a circle). Looked worse *and* contradicted the documented design; reverted
   immediately.

**The decisive fact, found only after all four:** L1 achieves uniform size with a **stack-level tile mask**, and L2
deliberately deleted that mechanism. Everything above was an attempt to synthesize it. That should have been the
first thing checked, and it is one `grep` in `IconRenderer`.

Two smaller findings from the attempt, both still valid and both cheap:

- **The live path does not clip.** `IconLayerStack` relies on `CompositingStrategy.Offscreen` bounding its buffer
  rather than saying `clip = true`. Invisible today because nothing scales above 1 by default — but reachable now by
  dragging the zoom slider past 1, where the editor may show overflow the bake crops. Worth fixing on its own terms,
  independent of everything in this doc.
- **Measurement is affordable.** Sampling at 64² and scanning costs a fraction of a bake, and `LegacyBackground`
  already does the same thing at 32². Cost is not the reason to avoid C.

## 6. What was built (2026-08-13)

**One rule, and it has no tuning value in it:**

> Scan the layer's artwork for opaque pixels, take those bounds, scale them to fill the icon box, center the result.

- **`ContentMetrics`** (`core:icon/parse`) is the scan: four edges as fractions of the canvas, pure, unit-tested,
  split from the rasterizing exactly as `LegacyBackground` is. An alpha floor of 16 is what makes "opaque" mean
  something — real icons carry near-invisible washes that at a zero floor measure as full coverage and silently
  defeat the whole measurement.
- **`DrawableParser`** measures at 64², on **the app's own artwork only**: its foreground and its themed layer.
  Backgrounds are not measured, and neither is anything else.
- **`IconLayerResolver.normalized()`** applies it, per resolved layer, folded into the layer's zoom and offset so
  `LayerTransform` carries it and both render paths get it with no change of their own.
- **`IconLayerSpec.normalize`** is the toggle, defaulted on, in the studio's Source panel beside Monochrome.

**Four properties, none of them enforced by a check — they fall out of the shape:**

- It can only ever **grow** artwork (bounds cannot exceed the canvas they were measured in).
- It can never **overflow** (filling the box exactly is what it computes). Earlier cuts needed an explicit cap.
- A **pack drawable, an imported image and a flat fill are untouched**, because the parser never measured them, so
  they arrive with no metrics. No list of sources is checked, and a new source cannot be normalized by accident.
- The **background is never resized**, and the mechanism does not consult one. Whether a plate is drawn — or is
  switched off in the studio — changes what sits *behind* the artwork, not how large the artwork should be, so an app
  resolves to the same size either way.

**Where the size finally lands is the per-layer zoom's job.** A shared baseline is what makes that control possible:
one foreground zoom in the global studio moves every app's artwork together. That is also why no constant is needed
here, and why the two earlier attempts to pick one were the wrong shape.

**Two corrections found late, both worth keeping:**

- **The fit must read the content that actually resolved**, not `ParsedIcon.foreground`. It measured the latter and
  applied it to whichever layer held the foreground role — so an app shipping a **themed** icon had its silhouette
  scaled by a factor measured from a different picture. Silent, and only on apps that support theming.
- **Making the fit conditional on a plate was wrong.** A version that skipped plated icons ("they already fill the
  box") broke the requirement outright: the same app has to resolve to the same size whether its background is on or
  off.

## 7. Found: the sample size was too small for drawables with absolute padding (2026-08-13)

**Diagnosed from a log, not from reasoning** — and worth reading as a case for building the instrumentation §7 kept
saying was optional. Four hypotheses were argued over three rounds (bleed, the alpha floor, an inverted render, the
bounds latching onto the glyph's holes) and *every one of them was wrong*. One line of `IconMeasure` output settled
it in seconds:

```
com.reddit.frontpage foreground  BitmapDrawable -> side=0.531 box=[0.23,0.25,0.77,0.72] coverage=15.2% scale=1.88
com.reddit.frontpage monochrome  InsetDrawable  -> side=0.125 box=[0.44,0.44,0.56,0.56] coverage=1.0%  scale=8.00
```

**The cause.** `ContentSampleSize` was a flat 64px, resting on the assumption that "what fraction of the box is ink"
is scale-invariant. It is not, for any drawable carrying **absolute** padding. Reddit ships its themed layer as an
`InsetDrawable` whose insets are dp — the ordinary way to ship a glyph with a margin — so it subtracts the same 28px
a side whatever bounds it is handed. In a 64px box that leaves 8px of artwork: `side = 8/64 = 0.125`, and the fit
dutifully magnified the icon **eight times**. The three tells are all in that line — the wrapper class, a perfectly
symmetric box, and a side that is a clean pixel fraction.

**Why it was invisible.** At real render size the same insets are the margin their author intended, so the icon drew
perfectly whenever normalize was off (the author's first screenshot). Measure and render disagreed about the same
drawable, and only the measurement was wrong.

**The first fix — measure at `intrinsicWidth`, clamped (96..192) — was right but insufficient, and the way it failed
is the real lesson.** The log confirmed it worked as far as it went:

```
monochrome InsetDrawable -> side=0.406 coverage=7.5% scale=2.46 sample=192px intrinsic=354px
```

`scale` 8.00 → 2.46. But the icon was then **correct in the drawer and overflowing in the studio**. Solving
`side = f × (1 − 2i/B)` across the two samples (64px→0.125, 192px→0.406) gives `i ≈ 25px` and `f ≈ 0.55`: the ink
fraction is a function of the box size. A larger box makes a fixed inset matter less, so the artwork covers
proportionally more of it — and the studio's preview is several hundred pixels where a drawer cell is under two
hundred. **One measured scale cannot be correct at two render sizes**, so picking a better sample size only moved
which size was right.

**The fix that closes it: rasterize what is measured.** `DrawableParser.rasterized` renders each measured layer to a
bitmap of the box and stores *that* as the layer's content. A `BitmapDrawable` scales proportionally, so its ink
fraction is a property of the artwork again — measurement matches render, and both renderers match each other, at
every size. It generalizes past `InsetDrawable` to anything with fixed padding, which is the point: the three
failures here were all one shape, *measured under conditions the renderer does not reproduce*, and this removes the
shape rather than another instance of it.

Three consequences worth knowing:
- **It chooses a canonical appearance** — the one at `sampleBoxSize`. That mostly washes out under normalization
  (the artwork is rescaled to fill the box anyway) and matters when it is off, which is why the clamp sits near the
  size an icon is really displayed at.
- **Rasterized at `RasterOversample`× resolution via a canvas scale, not larger bounds**, so the appearance stays the
  box's while the pixels are captured at twice it. Without that the studio preview would be visibly soft.
- **The bleed headroom is gone with it.** It was added for a case that turned out not to exist (this one looked like
  bleed and was not), and the raster clips at the box exactly as both renderers already do — so there is no
  regression, only an unproven feature not gained. `ContentMetrics` fractions are 0..1 again and the fit only grows.

**Not verified on device.** The check is the studio and the grid showing the same app at the same size.

**Not Reddit-specific.** Any inset-based themed layer, and anything with fixed padding, measured wrong the same way —
so expect several of the remaining bad icons to come right at once.

### Still open

The leads below are unchanged and were *not* what bit here, so they remain untested guesses:

1. **Artwork with a stray pixel or a faint edge element.** The bounds are a rectangle around *everything* above the
   alpha floor, so one dot in a corner makes the measured box far larger than the artwork looks, and the icon comes
   out correspondingly small. This is the most likely cause of a "too small" outlier, and the alpha floor is the
   first knob — 16 is L1's number, not one chosen against evidence here.
2. **Artwork that is legitimately not square.** A wide, short logo is fitted by its longer side, so it fills the box
   horizontally and leaves a lot of vertical space. Correct by this rule, and it may still read as "smaller" beside
   square icons. If so, that is the (b)-vs-(c) question in §1 reopening, and coverage is the metric to add back.
3. **The two render paths disagreeing.** Both drive off the same resolver, so they should not — but if the studio
   and the home screen show different sizes, that is where to look first, not at the arithmetic.
4. **Legacy icons with a detected plate.** `LegacyBackground` fills the background layer, the artwork is grown to
   the box over it, and a full-bleed legacy bitmap over its own plate may double up oddly.

**And a way to see it now exists**, which reverses this section's own "deliberately not built". `DrawableParser`
logs one `IconMeasure` line per measured layer — bounds, **coverage**, the resulting scale, and the sample size —
and dumps an ASCII silhouette of what was actually drawn whenever a measurement is implausibly small. Coverage is
the discriminator and is deliberately *not* on `ContentMetrics`, which dropped it on purpose so nothing can make a
decision from it: a solid glyph is 30–60%, while a stub reads at 1%. Debug builds only, behind `Timber.treeCount`
checked before any string is built.

It is still not a harness — no side-by-side, no golden images — and the remaining leads above may yet want one. But
the specific thing this section asked for, "a way to see what the measurement saw", is answered, and it paid for
itself on the first icon it was pointed at.
