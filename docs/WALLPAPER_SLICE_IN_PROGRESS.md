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
- [ ] 3. Drive theirs: full tab inventory with ranges and defaults
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

