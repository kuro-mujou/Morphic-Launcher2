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
- [ ] 4. Drive every knob to both ends, with the pixel measurements
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

### Still to do on the drive

- Ranges and both ends for **Thickness · Iterations · Rotate delta · Size · Scale delta** (five rulers, none driven).
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
