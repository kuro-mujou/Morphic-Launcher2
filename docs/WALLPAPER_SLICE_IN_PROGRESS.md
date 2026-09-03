# Wallpaper slice in progress — W11w, Polygon Cascade

**This file is a handoff, not a design record.** It exists so a slice that spans more than one session can be picked
up from git rather than from a transcript. When the slice lands, its findings fold into
[WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) (the inventory row, the checklist row and the verdict
row) and **this file is deleted**. If it is here and the checklist still shows the design unticked, the slice is
unfinished and the state below is where it got to.

---

## How to resume

1. Read [WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) — the method, the traps and the checklist.
2. Read [../tools/refdrive/README.md](../tools/refdrive/README.md) — how to drive the reference, and the traps that
   have already produced wrong readings.
3. Read `../gart` for this design before writing anything (the standing rule; it is where the mechanism usually is).
4. Pick up at the first unchecked step under **Progress** below.
5. **Append to this file after every analysis step**, and commit. That is the whole point of it.

### Getting to this design on the emulator

```bash
bash tools/refdrive/drive.sh shape        # 1080x2400 @ 400dpi, reference relaunched
```

Then, in the reference app: `+` FAB at `1010 2255` → design picker at `264 2226` → **Polygon Cascade** is the first
tile of row 5 in the picker grid, at about `140 1780`. Style panel is the ruler icon at `642 2226`.
Tab row: tap `880 2120` to advance exactly one tab; the ruler is at `<x> 2240`.

Ours: the design chip is labelled **Cascade** in our studio's chip row.

---

## The design

| | |
|---|---|
| Theirs | **Polygon Cascade** (#17 on the checklist) |
| Ours | `POLYGON_CASCADE`, chip label *Cascade* |
| Built | W8d, from the verdict table's one-line note — **never driven** |
| Recorded knobs (a floor, not a count) | Shape · Mode · Thickness · Iterations · Rotate delta · Size |

---

## Progress

- [x] 1. Read our generator, and record what it actually does
- [x] 2. Read gart for the mechanism
- [x] 3. Drive theirs: full tab inventory with ranges and defaults
- [x] 4. Drive every knob to both ends — ranges, pictures and both pads; the mechanism is in §15
- [ ] 5. Decide the knob mapping onto `DesignParams`
- [ ] 6. Build
- [ ] 7. Verify: unit tests, dead-knob guard, harness render, live studio
- [ ] 8. Fold into the teardown doc, delete this file, commit

---

## Findings

### 1. What ours does (`PolygonCascadeGenerator`)

**One regular polygon drawn `iterations` times**, each copy turned a fixed `RotateDelta = 0.22 rad` further and scaled
linearly inward from `1.0` to `MinScale = 0.12` of `maxRadius`. It is not a spirograph — it is a rotating, shrinking
stack. Concretely:

- ground = the palette's **darkest** stop; the polygons climb the remaining ramp (`colorAt(t, ramp)`) as they shrink.
- centre at `(width/2, height * 0.42)`, outer radius `0.92 * shortSide / 2`.
- stroke `0.0016 * shortSide` — one hairline weight for every copy.
- knobs: `density` → *Iterations* `16..60`; `variant` → *Sides* `3..8`; `irregularity` → *Wobble*, a per-vertex jitter
  up to `0.03 * shortSide`.
- **`RotateDelta`, `MinScale`, `RadiusFraction`, `CenterHeightFraction` and `StrokeFraction` are all fixed constants.**
  Their recorded knob list has *Rotate delta*, *Size* and *Thickness*, which are three of those five — so on the face
  of it ours has three dead constants where theirs has three knobs. Confirm by driving before believing it.

### 2. gart

`arts/spirograph` (`Sg1.kt`, `Sg2.kt`) is a **real spirograph**: `createSpirograph(d, path, radius, degrees, samples,
repetitions)` rolls a circle of points along a closed path and strokes the trace, with the path itself built by
union-ing circles. `arts/harmongraph` is the harmonograph. **Neither is what ours draws**, so our KDoc's citation of
`arts/spirograph` is loose — ours has no rolling and no path. Whether *theirs* is a rolling spirograph or a rotating
stack is the first thing the drive has to settle, because it decides whether this is an identity finding.

`arts/rotoro` is unrelated (grid-of-circles compositions), despite the name.


### 3. Theirs — the tab inventory, and the identity finding

**Nine knobs, not the six recorded**, and the row stops at *Last shape center*. No *Color mode* tab.

| # | Tab | Kind | Default |
|---|---|---|---|
| 1 | Shape | segmented: Circle · **Star** · Triangle · Hexa… (scrolls; not driven to its end yet) | Star |
| 2 | Mode | segmented: **Stroke** · Fill | Stroke |
| 3 | Thickness | ruler | 5 |
| 4 | Iterations | ruler | 10 |
| 5 | Rotate delta | ruler | 57 |
| 6 | Size | ruler | 16 |
| 7 | Scale delta | ruler | 4 |
| 8 | **First shape center** | four-arrow **nudge pad** | — |
| 9 | **Last shape center** | four-arrow **nudge pad** | — |

**The identity finding, and it is in those last two.** Theirs interpolates the shape's *centre* from a first position
to a last one, so the copies **march across the frame** — at its default a diagonal trail of stars falling from
top-right to bottom-left, each smaller and turned further than the one before. **Ours stacks every copy at one fixed
centre**, so it draws a concentric rosette. Same ingredients, different picture; "Cascade" is meant literally.

Everything else follows from that:

- their **ground is the palette's lightest stop** and the shapes are stroked down the ramp as they fall (cream → tan →
  brown → slate → blue). Ours grounds on the **darkest** stop.
- their stroke is **much heavier** than ours' hairline — and it is a knob (*Thickness*), where ours is
  `StrokeFraction = 0.0016` fixed.
- their shape vocabulary includes **Circle** and **Star**; ours is regular 3..8-gons only, so it can draw neither.
- *Rotate delta*, *Size* and *Scale delta* are knobs; ours has `RotateDelta`, `RadiusFraction` and `MinScale` as fixed
  constants. **Five of ours' constants are knobs of theirs.**

**The nudge pads are the trap the refdrive README documents** — a swipe on one silently does nothing, which reads as
"this knob is dead". They must be **tapped**: ← ↑ ↓ → at `173 / 418 / 662 / 903`, all at `y = 2238`, hold to repeat.

### Still to do on the drive (step 4)

- Scroll the *Shape* strip to its end — how many shapes, and which.
- *Mode* = Fill: what a filled cascade looks like (probably where the palette really shows).
- Ranges and both ends for Thickness · Iterations · Rotate delta · Size · Scale delta.
- The two nudge pads: how far they travel, and whether the interpolation is linear between them.
- Whether the colour ramp is spent over the *cascade* (first shape to last) or over something else.

### 4. The drive, continued

**Shape is a named vocabulary of six, not a side count:** `Circle · Star · Triangle · Hexagon · Square · Rectangle`,
and the strip stops at Rectangle. Ours is regular `3..8`-gons, so it can draw Triangle / Square / Hexagon, **cannot**
draw Circle, Star or Rectangle, and offers a pentagon, heptagon and octagon theirs does not.

**Mode `Fill` is a second look, and it swaps a knob.** Filled, the cascade is a stack of opaque overlapping shapes
running light-to-dark down the frame — much bolder than the stroked default, and the palette really shows. The tab row
under Fill reads `Shape · Mode · Shadow · …`, so **there is a *Shadow* tab that does not exist under Stroke** (and
*Thickness*, which a fill has no use for, is presumably what it replaces — not yet confirmed). That is a
`styleFor(variant)` case, the same shape as Flow Field's *Dots*.

**Uncertain, flag for whoever picks this up:** the Fill capture was taken with *Shape* left on **Rectangle**, because
swiping the Shape strip to read its options also *changed the selection* — the refdrive README's documented trap,
confirmed again here. The corners in that render are visibly rounded; whether that is the Rectangle shape's own
rounding or a global corner radius is **not established**.

### 5. The five ruler ranges — driven, numbers read back

| Knob | Range | Default | Against ours |
|---|---|---|---|
| Thickness | `1..100` | **5** | ours fixed at `StrokeFraction = 0.0016` of the short side |
| Iterations | `1..100` | **10** | ours `16..60` — cannot reach either of their ends, and opens at 38 against their 10 |
| Rotate delta | **`−180..180`** | **57** | ours fixed at `0.22 rad ≈ 12.6°` — **4.5× less turn per step, and one direction only** |
| Size | `1..100` | **16** | ours fixed at `RadiusFraction = 0.92` of half the short side — theirs opens *small* |
| Scale delta | `1..100` | **4** | ours fixed: a linear run from `1.0` down to `MinScale = 0.12` over the whole cascade |

Two of these are worth pulling out. **Rotate delta is signed** — the cascade can turn either way and `0` is no turn at
all, which is a rigid end ours cannot express. And **their defaults are small**: 10 iterations of a shape at Size 16
shrinking by Scale delta 4, where ours opens at 38 iterations of a shape filling 92% of the frame. Ours is not a
tuning away from theirs; it is a different composition.

### 6. The cascade's geometry, measured off their default render

Their default draws **ten shapes and gives each its own colour**, so the whole cascade can be measured from one
screenshot by clustering ink on colour. Every shape's visible centroid and bounding box, first to last:

| i | cx | cy | width |
|---|---|---|---|
| 0 | 619.3 | 597.4 | 741 |
| 1 | 600.9 | 729.8 | 675 |
| 2 | 584.2 | 863.7 | 604 |
| 3 | 566.4 | 994.4 | 533 |
| 4 | 545.4 | 1113.9 | 488 |
| 5 | 532.9 | 1255.2 | 435 |
| 6 | 515.8 | 1389.1 | 375 |
| 7 | 498.6 | 1516.9 | 311 |
| 8 | 485.5 | 1643.2 | 245 |
| 9 | 467.4 | 1775.5 | 186 |

**The centre is interpolated linearly.** Steps in `x` are `−18.4 −16.7 −17.8 −21.0 −12.5 −17.1 −17.2 −13.1 −18.1`
(mean `−16.9`) and in `y` `+132.4 … +132.3` (mean `+130.9`) — constant, no trend. So *First shape center* and *Last
shape center* are two points and the copies are spaced evenly along the segment between them. At the default that
segment runs `(619, 597) → (467, 1776)` on a 1080×2400 frame: mostly straight down, drifting `152px` left.

**The size shrinks linearly, not geometrically.** Widths fall by `−61.7px` per step on average with no trend, while
the *ratios* `0.911 … 0.759` trend clearly downward — so it is a constant subtraction, not a constant factor. From
`741` to `186`, i.e. the last shape is a quarter of the first.

**Caveat on the knob mapping:** *Scale delta* is `4` at this default, and `4%` per step over nine steps cannot produce
a 75% loss — so the knob is **not** a straight percentage of the first size. Partly settled in §7 below.

**The palette is spent over the cascade's length, read continuously.** The ten tones run cream `(230,213,184)` through
browns and greys to a blue `(173,196,206)` — more distinct tones than a curated palette has stops, so it is a
continuous ramp with the first shape at position `0` and the last at `1`.

**And the ground is the palette's *first* stop, with the shapes on the ramp above it.** The ground measures
`(251,248,239)`, lighter than the first shape's `(230,213,184)` — which is `RampTones.aboveGround`'s exact semantics,
the helper this codebase already has. **Ours grounds on the palette's *darkest* stop and ramps the rest**, so its
whole tonal arrangement is inverted against theirs.

Measured from `pc_default.png` by clustering ink pixels on colour; the first shape's box is slightly truncated at the
top because their toolbar overlays it, which does not affect the step measurements.

### Still to do on the drive

- What each ruler does to the *picture* at its ends (only the numbers were read, not the renders). **Scale delta is
  the one that matters**, per the caveat above.
- The *Shadow* knob under Fill: confirm it replaces Thickness, and measure it.
- The two nudge pads — how far the centre travels, and whether the interpolation between first and last is linear.
- Whether the colour ramp is spent over the cascade's length (first shape → last) or over something else. The default
  render strongly suggests the former: cream at the first shape through to blue at the last.

### Working notes for the resume

- Re-picking a design in their studio **resets its knobs and keeps the field/seed**, so it is a clean way to isolate
  one knob: drive one, shoot both ends, re-pick, drive the next.
- Their design picker: Polygon Cascade is the first tile of row 5, about `140 1780`.
- Ours renders through `GeneratorRenderHarness`; mind the stale-render rule in its KDoc, and note it can exit `255`
  with every test passed and every PNG written.

### 7. Scale delta — driven, and it is not a shrink rate

Three points, widths measured the same way:

| Scale delta | widths, first → last | per-step |
|---|---|---|
| `1` | `741 662 579 496 418 352 282 207 131 55` | `−76.2`, linear |
| `4` (default) | `741 675 604 533 488 435 375 311 245 186` | `−61.7`, linear |
| `100` | shapes **overflow the frame** (bounding boxes clip at 1079) | — |

So **the knob does not set how fast the cascade shrinks — it sets the size *change* per step, and that change goes
positive.** Low values shrink hard (at `1` the last shape is 7% of the first), the default shrinks to a quarter, and at
`100` the cascade *grows* and runs off the frame. There is a neutral value somewhere in between where every copy is the
same size, which is a rigid end ours cannot express — ours runs a fixed linear shrink from `1.0` to `MinScale = 0.12`
and can only ever get smaller.

**The shrink is linear at both driven settings** (constant subtraction per step, no trend in the differences), which
agrees with §6 and rules out a geometric factor.

### 8. Scale delta, pinned — and a correction to §7

Driven again on a **fresh pick**, in short 100px drags (three knob units each) with the number read back off the panel
every time, so this run is five points of one design rather than a comparison across picks:

| Scale delta | 4 | 7 | 10 | 13 | 16 |
|---|---|---|---|---|---|
| per-step size change (px) | 70.9 | 56.2 | 41.6 | 26.7 | 12.0 |

**Linear at `−4.9 px` per knob unit**, with the differences `−14.7 −14.6 −14.9 −14.7` over three units each — as clean
a fit as this pass has produced. Extrapolated, the step reaches **zero at Scale delta ≈ 18**. The earlier pick (§7)
gives `−4.83` per unit and a zero at `≈ 17`, from completely different captures — two independent confirmations, slopes
agreeing to 1.5%.

**So: Scale delta sets the size *difference* between the ends of the cascade, and it goes through zero at about 17.**
Below that the copies change size fast, at 17-ish every copy is the same size, above it they change fast again the
other way. One end stayed pinned at `825px` through all five captures while the other swept `187 → 717`, so the knob
**pivots the cascade about one end** rather than scaling it as a whole.

**Correction to §7.** That section read `100` as "the cascade grows" and the low end as "shrinks hard". The magnitude
story is right; the *direction* claim was not. Which end of the cascade is the larger one differs **between picks** —
this run at Scale delta `4` grew downward where §7's pick at the same `4` shrank downward — so direction is set by the
first/last configuration and not by this knob. §7's `100` capture overflows the frame because the *magnitude* is large
again past the neutral, not because the knob means "grow".

**Method note worth keeping:** the earlier reading compared captures from two different picks without noticing, and
their studio re-randomizes something on each pick (W11m found the same on Gradient Columns). **Read the number back
off the panel every capture and keep a sweep inside one pick**, or a knob's own effect and the pick's randomization
get attributed to each other.

### 11. *Shadow* confirmed, and it is a halo with no throw

Under Mode = **Fill** the third tab really is **Shadow** where Stroke has *Thickness* — the two swap, confirming §4's
guess. **Shadow `0..100`, default `0`**, and it is the design's one dark knob.

**It is a blurred silhouette of each shape drawn *behind* it with no offset at all** — an outer glow in black, not a
thrown shadow. Three measurements, all from one pick at Shadow `100` against the same picture at `0`:

- Scanning **across** a shape's left edge and its right edge gives the *same* profile mirrored — darkest at the edge,
  gone by about `70 px` on a 1080-wide frame, on **both** sides. A directional throw cannot do that.
- Scanning **down** the cascade, each shape's visible sliver is brightest at its top and darkest where the next shape
  in front of it begins. Nothing darkens it from above, because the shape above is *behind* it — so the halo is cast
  only forward-to-back, exactly as a per-shape shadow drawn under each shape would be.
- The cast-shadow bounding box (`x 282..934, y 573..1603`) sits **inside** the shapes' own (`x 269..953, y 552..1620`).
  With a throw the shadow would clear the silhouette on the throw side; with a halo it never can, because every shape
  that could cast outward is the outermost one and its own halo lies under the shapes in front.

**The knob is opacity, not radius.** At `54` the reach is still ~75px while the depth at an edge goes `0.29 → 0.56`
of the underlying color. So: radius ≈ **6.5% of the frame width**, fixed; the knob scales how dark it gets.

### 12. The four remaining rulers, and what each does to the picture

Driven on one pick (Star, Stroke), reading the number back off the panel every capture.

- **Thickness `1..100`, default 5.** The stroke measures `13px` at `5` and `35px` at `13` on a 1080-wide frame, so it
  is linear at about **`0.0025 × T` of the frame width** — `0.0125` at the default, against ours' fixed
  `StrokeFraction = 0.0016`. Theirs opens **eight times heavier than ours**, and its joins are **round** (the star
  tips are visibly capped at `26`).
- **Iterations `1..100`, default 10 — a pure subdivision, and this is the finding.** Driven `10 → 17` the ink's
  bounding box is **identical to the pixel** (`x 256..966, y 538..1633`). So the endpoints *and both end sizes* are
  fixed and the knob only decides how many copies are spread between them. Ours multiplies a per-copy rotation and a
  per-copy scale by the index, so its count moves the whole composition.
- **Rotate delta `−180..180`, default ~57–59.** It is **degrees per copy**, `i × R`, and the reason a 5-pointed star
  at `59` still looks nearly upright is the star's own **72° symmetry** — `59°` reads as `−13°` per step. That is why
  the default looks like a gentle fan rather than a jumble, and it means the knob's *useful* range depends on the
  shape's symmetry.
- **Size `1..100`, default ~15–16.** The first shape's bounding width is `710px` at `15` and `848px` at `18` on a
  1080-wide frame — **`0.0435 × S` of the frame width**, and the same constant falls out of §6's independent pick
  (`741px` at Size 16 → `0.0429`). It scales the shapes only: the centers do not move.

### 13. Scale delta re-derived — it is the *total* change, which is why §7 and §8 disagreed with §6

§8 pinned the per-step size change and its neutral; putting it beside §12's *Iterations* finding gives the unit.
Because **Iterations does not change the end sizes**, what the knob sets cannot be a per-step amount — it is the
**whole cascade's** size change, divided by however many copies are in it:

| pick | Iterations | Scale delta | first width | change per step | as a fraction of the first, over the whole cascade |
|---|---|---|---|---|---|
| §6's | 10 | 4 | 741 | `−61.7` | `0.749` |
| this one | 17 | 7 | 848 | `−31.7` | `0.598` |

Two equations, one model: **total fractional change ≈ `0.050 × (18.9 − D)`**, spread linearly over the copies. It
reproduces §8's independently-measured slope (`−4.9 px` per knob unit at 10 iterations is `0.053` fractional per unit)
and its neutral at ≈17–19. Below the neutral the cascade shrinks, at it every copy is the same size, above it grows.

**And it is a fraction of the *frame*, not of the segment.** `first width / (Size × frame width)` is `0.0438` here
and `0.0429` on §6's pick, where `first width / (Size × segment length)` is `0.065` against `0.039` — the frame reading
is consistent across two picks and the segment reading is not.

### 14. Correction to §10 — the endpoints do **not** resize the shapes

§10 read a graded `dw` while holding *Last shape center* and concluded a shape's size is tied to the two endpoints.
It is not. Driven again on a pick that stays **inside the frame** — one 700ms hold on `→`, which moved the last
center `+86px` in `x` — every one of the seventeen widths is unchanged (`848 816 785 753 721 689 658 626 594 563 530
499 467 436 404 373 341`, against `848 816 785 753 721 689 658 626 594 563 530 499 467 435 405 373 341` before).
The centers stay linearly spaced along the new segment and nothing else moves.

§10's own hold pushed half the cascade off the left edge, and a bounding box measured on clipped ink is short by
however much was clipped — most for the shapes nearest the endpoint being moved, which is exactly the gradient it
reported. **So the shape sizes are a function of *Size*, *Scale delta*, the count and the frame — and of nothing
else.** The trap generalizes: this measurement reads *visible ink*, so anything that clips or occludes a shape reads
as that shape changing size.

### 15. The mechanism, in one place

Everything above, as the thing to build against:

- Two centers `P0`, `P1`, **randomized per pick** and nudgeable four ways. `n` copies at `lerp(P0, P1, i/(n−1))`.
- Copy `i` is one shape from a **named vocabulary of six** (Circle · Star · Triangle · Hexagon · Square · Rectangle),
  turned `i × R` degrees, at a size interpolated linearly from the first to the last.
- First size `≈ 0.0435 × Size` of the frame width; the cascade's total size change `≈ 0.050 × (18.9 − ScaleDelta)`
  of that, so the knob's neutral is a cascade of identical copies and either side of it shrinks or grows.
- Drawn **first (largest) to last**, so later copies sit in front.
- **Stroke** (round joins, `0.0025 × Thickness` of the frame width) or **Fill** (with *Shadow*, a black halo of fixed
  ~6.5%-of-width radius and knob-controlled opacity, drawn behind each copy).
- Ground is the palette's **first** stop; the copies are the ramp above it, read continuously over the cascade.

---

## Where this stopped

**The drive is finished — §15 is the mechanism, and nothing on it is still open.** Every knob has been pushed to
both ends, every ruler's effect on the picture measured rather than judged, and the one claim §10 got wrong is
corrected in §14. What follows is a build decision, not more measurement.

**Nothing has been built.** `PolygonCascadeGenerator` is untouched; the checklist row for Topography's neighbour
(#17) is still unticked.

**The one thing the build has to answer, and it is a model question.** Theirs has nine knobs and ours has nine
`DesignParams` fields, but they do not line up: *Iterations* → `density`, *Size* → `scale`, *Shape* → `variant` and
the taper → `depth` are all straightforward, while **Rotate delta has no field at all** — it is the *orientation*
family, the last one the teardown says has none, and unlike the four designs that spend `variant` on a discrete
direction this one is a **continuous signed angle**. And *Mode* wants `variant` too, which *Shape* has already taken.
Both are open for the author; see the summary of this session.

### 9. The nudge pads — partly driven, and one thing it settled by accident

Driven inside one pick: baseline capture with *First shape center* selected, then three ← taps, then three ↑ taps,
matching shapes across captures **by colour** (each shape has its own tone, §6).

**What it settled, and it was not what I was aiming at: this pick laid the cascade out *horizontally*** — all ten
shapes at `cy ≈ 1199`, spread across `x`. Their default render (§3) is a diagonal falling top-right to bottom-left.
So the first/last centres really are **re-randomized per pick and can point any direction**; the diagonal is not
intrinsic to the design. That is a second, independent confirmation of the randomization noted in §8, and it matters
for the build: our version needs the two endpoints as real state, not a hardcoded diagonal.

**The ↑ taps give the cleanest signal.** Three of them moved every shape up, by `−6.0` to `−11.8 px` depending on the
shape — **graded, not uniform**. A graded shift across the cascade is exactly what moving *one* endpoint of an
interpolation does, so this is the §6 linearity confirmed from the other direction.

**Per tap the travel is small** — roughly `2–4 px` per tap at this scale. The README's "hold to repeat" is not a
convenience, it is how the control is meant to be used.

**Caveat — the x measurement did not resolve, do not build on it.** After the first ← tap the shapes' `dx` spread from
`−295` to `+122 px`, i.e. *both signs*, and the second and third taps then changed almost nothing (`~2 px`). Moving one
endpoint of a linear interpolation should shift every shape the same way, tapering to zero — both signs is not that.
Either the first tap did something other than nudge (focus? a snap?), or the colour-matching mis-pairs shapes when the
cascade is horizontal and several tones sit close together. **Re-drive this on a pick whose cascade is diagonal**, so
`x` and `y` separate, and hold the arrow rather than tapping it.

### 10. *Last shape center* — the decisive one, and it resolves §9

§9 drove *First shape center* and got both signs of `dx`, which settled nothing. Driving **Last shape center** instead
gives the clean signature immediately. One pick, arrow **held** rather than tapped, shapes ordered along the cascade by
descending size:

| shape | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|---|
| `dx` after one hold | −1.3 | −1.9 | −5.5 | −94 | −144 | −280 | −373 | −466 | −559 | −646 |

**Monotone and single-signed, from ~0 at the first shape to −646 px at the last.** That is exactly one endpoint of a
linear interpolation being moved, and it confirms §6's model from the strongest possible direction. The consecutive
differences settle at about `−92 px` from the middle of the cascade onward. **So §9's both-signs result was a
measurement artefact of that horizontal pick, not the design** — the mechanism is what §6 said.

**A second finding that was not being looked for: moving the last centre also *resizes* the shapes.** `dw` runs
`0 0 0 −105 −121 −140 −157 −174 −192 −209` — monotone, in step with `dx`. So a shape's size is **not** independent of
the two endpoints; it is tied to them (most likely to the distance between them, with *Size* and *Scale delta* read
against that). That is worth pinning before building, because it decides whether our version stores a size in pixels
or as a fraction of the cascade's own span.

**Still open on this:** shapes `0..2` barely move while `3..9` ramp, which a pure linear interpolation from the first
endpoint would not do. It may be an ordering artefact — two shapes in this pick have near-equal widths (`723` and
`715`) and their `cy` values are out of order, so the size ordering is unreliable at the top. Re-check by ordering on
`cy` on a cleanly diagonal pick.

Holding the arrow for `1.5 s` moves the endpoint far enough to push half the cascade off the frame on the second hold,
so **hold in short bursts** and re-measure between them.
