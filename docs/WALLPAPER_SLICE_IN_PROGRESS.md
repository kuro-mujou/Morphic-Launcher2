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
- [x] 3. Drive theirs: full tab inventory with ranges and defaults
- [x] 4. Drive every knob to both ends — the numbers *and* what each does to the picture
- [x] 5. Decide the knob mapping onto `DesignParams`
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

### 3. Theirs — the inventory, and the identity finding is in *Direction*

**Seven knobs, not the six recorded** (the note missed *Inner shadow*), and the row stops there. No *Color mode* tab.

| # | Tab | Kind | Default |
|---|---|---|---|
| 1 | Count | ruler | **7** |
| 2 | Margin | ruler | **20** |
| 3 | Spacing | ruler | **44** |
| 4 | Blend mode | segmented, **two**: Normal · **PLUS** | PLUS |
| 5 | Rotation | ruler | **0** |
| 6 | Direction | ruler | **0** |
| 7 | **Inner shadow** | segmented: **Off** · On | Off |

**Worth noting before anything else: "Blend mode" here is two options, where Soft Overlaps' is nine.** The name
recurs across their designs and does not mean the same thing twice — the same warning the teardown already records
for *Color distribution*.

**The design is a fan of long rounded bars, and *Direction* is what makes it one.** At its default the bars are
parallel and span the frame, which is why the picture opens as a stack of stripes and why the name looks wrong.
Driving *Direction* to `22`, `45`, `68` spreads each bar to its own angle so they **radiate from a common origin** —
and at that point the **rounded caps** come into the frame and the name explains itself. Ours draws a Truchet maze of
quarter-arcs that *join* across a grid. There is no reading on which these are the same design.

**Rotation is the whole fan's angle, and it is `0..100` mapped to `0..180°`.** Traced one gap right across the frame
and fitted it (residual under a pixel):

| Rotation | measured angle |
|---|---|
| 0 | `0.00°` |
| 11 | `19.79°` |
| 23 | `41.38°` |
| 100 | `0.00°` |

`19.79 / 11` and `41.38 / 23` are both **`1.799`**, so the knob is degrees scaled by `1.8`. Its top is `180°`, which
for a stripe pattern draws the same geometry as `0` with the palette running the other way — and that is what an
earlier note in this file mistook for a stale preview frame. It is not stale; `180°` is genuinely `0°`.

**Method note.** `measure.py slope` could not read this: it takes the *first* boundary per column, and with diagonal
bands that is a different band at each column, so it fitted noise (residuals of 200–500px). What works is tracing
**one** gap — find a dark run at the centre column, then walk outward choosing the nearest run each step — which
fits to half a pixel. Worth folding into `measure.py` if a third design needs it.

### 4. The seven knobs, driven

| Knob | Range | Default | What it does |
|---|---|---|---|
| Count | `1..10` | 7 | The bars, which always fill the frame — so it sets their thickness too. At `1` a single bar covers the whole frame, and its rounded corners are the only thing that gives it away |
| Margin | `0..100` | 20 | **Shortens every bar from both ends.** At `100` each has collapsed to a **circle**, which is the proof the shape is a *capsule*: a bar whose cap radius is half its thickness degenerates to a circle when its length reaches its thickness |
| Spacing | **`−50..50`** | 44 | The gap between bars, and it is **signed** — negative overlaps them, and under the default blend the overlaps blow out into a bright wash with no ground showing at all |
| Blend mode | Normal · **PLUS** | PLUS | Only visible where bars overlap, so at the default spacing the two are nearly identical and at negative spacing they are two different pictures |
| Rotation | `0..100` → `0..180°` | 0 | The whole fan's angle — §3 |
| Direction | `0..100` | 0 | **The fan.** `0` leaves every bar parallel; climbing gives each its own angle so they radiate from a common origin |
| Inner shadow | **Off** · On | Off | A soft dark inset just inside each bar's edge |

**Every bar also carries a gradient along its own length**, in every capture at every setting — the same construction
Gradient Columns has, and the reason `LouversGenerator` exists here.

### 5. The mapping, and the one decision that is not a mapping

**This is built as a new design beside `TRUCHET`, not as a rebuild of it.** Ours is a Truchet maze of joined arcs and
is a perfectly good picture that happens to have nothing to do with theirs; the established move is to build theirs
beside it, as W11a did for the Mondrian, W11e for the Halftone and W11m for Louvers. That also means no stored recipe
changes meaning.

| Ours | Theirs | Why |
|---|---|---|
| `density` | Count `1..10` | Theirs' own range |
| `scale` | Spacing | A bar's thickness within its lane — the *coverage wins* precedent from Diagonal Bands, Bauhaus and Dot Grid |
| `roundness` | Margin | The bar's length read as how round it is: at `1` every bar has shortened to its own cap, which is a circle. The Mosaic's own case is "a narrow tile becomes a pill"; this is that axis one step further |
| `rotation` | **Direction** | The fan spread — a rotation *per bar*, and the design's identity |
| `depth` | Inner shadow | Their toggle read as an amount; `0` is off, which is the field's rigid end and theirs' default |
| `finish` | Blend mode | Normal / Plus — the third consumer of that field |
| *seeded* | Rotation | See below |

**Two orientation knobs, one field, and the identity wins** — the same squeeze Soft Overlaps had in the organic-noise
family. *Direction* takes `rotation` because it is what makes this a fan rather than a stack of stripes; the whole
fan's **overall angle is seeded** instead, which is exactly what the cascade does with its heading, and it means a
shuffle re-aims the picture.

`irregularity`, `taper`, `depthScale`, `colorLayout` and `variant` are all unread — theirs has no organic knob, no
size spread, no color layout and no sub-look, so declaring any of them would be offering a control that does nothing.
