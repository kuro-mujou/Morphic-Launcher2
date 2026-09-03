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

- [ ] 1. Read our generator, and record what it actually does
- [ ] 2. Read gart for the mechanism
- [ ] 3. Drive theirs: full tab inventory with ranges and defaults
- [ ] 4. Drive every knob to both ends, with the pixel measurements
- [ ] 5. Decide the knob mapping onto `DesignParams`
- [ ] 6. Build
- [ ] 7. Verify: unit tests, dead-knob guard, harness render, live studio
- [ ] 8. Fold into the teardown doc, delete this file, commit

---

## Findings

_Nothing yet._
