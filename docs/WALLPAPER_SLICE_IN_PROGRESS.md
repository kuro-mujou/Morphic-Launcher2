# Wallpaper slice in progress — W11x, Soft Overlaps

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

Then, in the reference app: `+` FAB at `1010 2255` → design picker at `264 2226` → **Soft Overlaps** is the second
tile of row 5 in the picker grid, beside Polygon Cascade. Style panel is the ruler icon at `642 2226`.
Tab row: tap `880 2120` to advance exactly one tab; the ruler is at `<x> 2240`.

Ours: the design chip is labelled **Overlaps** in our studio's chip row.

---

## The design

| | |
|---|---|
| Theirs | **Soft Overlaps** (#18 on the checklist) |
| Ours | `SOFT_OVERLAPS`, chip label *Overlaps* |
| Built | W9, from the verdict table's one-line note — **never driven** |
| Recorded knobs (a floor, not a count) | Count · Blend mode · Mode · Position jitter · Radius · Size variation |

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

### 1. What ours does (`SoftOverlapsGenerator`)

**Big soft-edged discs on the palette's darkest stop.** Each disc is a [RadialGradient] from its color at full-ish
opacity in the centre to the same color at zero alpha at the rim, so overlaps add up and melt rather than butting.
Concretely:

- ground = the palette's **darkest** stop; the discs take the remaining stops in order (`i % discColors.size`).
- centres from `PointScatter.gridJitter(count, irregularity, seed)` — an even lattice loosened by the knob.
- radius `0.18 .. 0.36` of the short side, drawn per disc from a **salted** stream so the jitter knob moves centres
  without resizing anything.
- centre alpha fixed at `0x9C`.
- knobs: `density` → *Discs* `8..26`; `irregularity` → *Jitter*; `variant` → *Blend* `Normal / Additive`
  (a `PorterDuff.Mode.ADD` xfermode).

**Three of their six knobs have no counterpart, and two of ours' constants are two of them.** Their *Radius* and
*Size variation* are exactly `MinRadius`/`MaxRadius`, fixed here; their *Mode* is a second segmented control beside
*Blend mode* and nothing here answers to it. Confirm by driving before believing any of it.

### 2. gart — no match found, and our KDoc's citation is loose

Our KDoc cites `arts/monet`. **It is not this design.** `monet1` and `monet2` are a **brush-stroke painting
simulation**: a zig-zag line sampled to 200 points, each point exploded into 50 deformed octagons drawn at alpha
`20`–`25`, which builds an impasto texture out of thousands of tiny strokes. Ours draws a few dozen large radial
gradients. The word "translucent" is all the two share.

Nothing else in `arts/` obviously matches either — checked the ones whose names promise it:

- `palecircles` is a **12×12 matrix of circle sets**, animated, on white — a grid, not overlaps.
- `cotton` is distributed **rectangles** with a noise shader, plus rule-of-thirds lines.
- `monet` as above.

Untouched and worth a look **once the drive says what the mechanism is**, rather than before: `bubbles/*` (which
holds `Blobs`, `Spuma`, `Pinna`, `pebble`), `circledots`, `layers/*`. The rule is to read gart before *writing*, and
what to read is decided by what theirs turns out to draw — so this stays open until step 3 lands.
