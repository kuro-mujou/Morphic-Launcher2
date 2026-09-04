# Wallpaper slice in progress — W11y, Rounded Tiles

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
adb shell wm size 1080x2400 && adb shell wm density 400   # the shape every coordinate below assumes
adb shell monkey -p net.smartlauncher.wallpaperstudio -c android.intent.category.LAUNCHER 1
```

Then, in the reference app: `+` FAB at `1010 2255` → design picker at `264 2226` → **Rounded Tiles** is the third
tile of row 5 in the picker grid, at about `700 1845`. Style panel is the ruler icon at `642 2226`; **`KEYCODE_BACK`
closes it** and brings the bottom bar back, and re-picking the design from the grid resets every knob.
Tab row: tap `880 2120` to advance exactly one tab; the ruler is at `<x> 2240`.

Ours: the design chip is labelled **Truchet** in our studio's chip row.

---

## The design

| | |
|---|---|
| Theirs | **Rounded Tiles** (#19 on the checklist) |
| Ours | `TRUCHET`, chip label *Truchet* |
| Built | W5, and the verdict already hedges: *"reasonable analog; theirs is diagonal rounded bars w/ Blend mode"* |
| Recorded knobs (a floor, not a count) | Count · Margin · Spacing · Blend mode · Rotation · Direction |

---

## Progress

- [x] 1. Read our generator, and record what it actually does
- [x] 2. Read gart for the mechanism
- [ ] 3. Drive theirs: full tab inventory with ranges and defaults
- [ ] 4. Drive every knob to both ends — the numbers *and* what each does to the picture
- [ ] 5. Decide the knob mapping onto `DesignParams`
- [ ] 6. Build
- [ ] 7. Verify: unit tests, dead-knob guard, harness render, live studio
- [ ] 8. Fold into the teardown doc, delete this file, commit

---

## Findings

### 1. What ours does (`TruchetGenerator`)

**A classic Truchet maze.** Every cell draws two quarter-circle *arcs* centred on opposite corners, turned one of two
ways by a coin flip; because each arc meets a cell edge at its **midpoint**, arcs line up with the neighbours'
whichever way each cell fell, so the loops run on across the grid unbroken. That continuity is the whole trick and it
is emergent rather than authored.

- ground = the palette's **lightest** stop.
- arcs stroked at `0.34` of the cell, round caps, colored down the frame from `ArcRampFloor = 0.45` of the ramp to
  its end, so the maze shifts tone top to bottom.
- **one knob**: `density` → *Resolution* `4..14`. `DesignStyle(amount = Amount)` and nothing else — no variant, no
  scale, no irregularity.

**Against the six their studio records, ours answers to one.** *Margin*, *Spacing*, *Blend mode*, *Rotation* and
*Direction* have no counterpart here at all, and the verdict's own note — "theirs is diagonal rounded **bars**" —
says the tile vocabulary is different too: a bar is a lozenge lying in a cell, where ours draws arcs that *join*.
Nothing here is confirmed until the drive.

### 2. gart — `ticktiletock` is a framework, and the arcs are ours

Our KDoc cites `arts/ticktiletock`, and unlike the last two slices the citation is fair as far as it goes: it is a
**tile-painter framework** — `splitBox(d, parts)` cuts the frame into a square grid of `Tile(x, y, d)`, and a painter
function draws each one. `Painters.kt` holds a shelf of them, including `paintTile2`, the classic Truchet of a
diagonal line turned one of two ways, and `paintTile4`, which adds the two half-way orthogonal lines.

**But no painter there draws arcs**, so ours' quarter-circles are its own; and none draws a rounded bar either. What
gart supplies is the *structure* — a grid of square tiles, one painter per tile — which is likely what theirs is
built on too. Worth re-reading with a target once the drive says what a tile of theirs contains.
