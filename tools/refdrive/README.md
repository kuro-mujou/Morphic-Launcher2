# Driving the reference wallpaper studio

Tooling for the *how* of [WALLPAPER_STUDIO_TEARDOWN.md](../../docs/WALLPAPER_STUDIO_TEARDOWN.md)'s two standing rules
— **read gart first**, then **drive every knob to both ends and measure pixels rather than trusting the eye**. The
rules say what to do; this says how, because the mechanics were rediscovered from scratch in three consecutive slices
(W11j, W11k, W11l) and two of the traps below produced a *wrong reading* rather than an obvious failure.

The reference is Smart Launcher's `net.smartlauncher.wallpaperstudio`, installed on the emulator. Nothing here touches
the launcher's own build; it is a measuring instrument, not part of the app.

---

## The traps, first — each of these has already cost a wrong conclusion

**`adb` from Git Bash mangles every absolute device path.** `adb shell rm -rf /sdcard/Pictures/x` becomes
`rm -rf 'C:/Program Files/Git/sdcard/Pictures/x'`, which **exits 0 having deleted nothing**. Export
`MSYS_NO_PATHCONV=1` for any block that talks to the device. The failure is silent, and combined with the render
harness's inability to overwrite (see its KDoc) it means every PNG you pull is the *previous* run's. Confirm with
`adb shell ls` before the run — it must say *No such file or directory*.

**Not every knob is a ruler, and a swipe on the others does nothing at all.** The panel has three control kinds:

| kind | how to work it | designs seen |
|---|---|---|
| ruler slider | **drag** it; dragging **right lowers** the value | most knobs |
| segmented | tap an option, or drag the strip to bring one to the centre | *Ratio*, *Color mode*, *Distribution* |
| four-arrow nudge | **tap** ← ↑ ↓ →, hold to repeat | *Offset* (Dot Grid, Diagonal Bands) |

Swiping a nudge pad silently changes nothing, which reads exactly like "this knob does nothing" — the conclusion the
method rule warns is always a misreading. It produced three identical *Offset* captures in W11l before the control was
recognised for what it is.

**Always read the number back after driving.** The drag is roughly **23 units per 500px** (at the shape below), but it
is not linear near the ends and a fling overshoots. W11k left *Frame* pinned at 100 for several captures by dragging
the wrong way, and every render taken meanwhile was of a collapsed block.

**A burst of drags can leave the preview showing the *previous* picture.** After `knob down 8` on Layered Waves'
*Count* the ruler read `1` while ten bands stayed on screen — through three screenshots and a close-and-reopen of
the panel. One further small drag repainted it. So the number and the picture are two separate claims: **nudge the
knob once more and re-shoot** before reading a render as that value's, or a stale frame gets written up as "this knob
does nothing to the picture", which is the misreading the method rule keeps warning about.

**Swiping the tab row can also change the selection.** `input swipe 250 2120 950 2120` scrolls the row back toward the
first tab, but the gesture sometimes lands as a tap on whatever tab it starts over — which is how the *Count* drag
above happened while *Distortion* was supposedly selected. Read the selected tab off the panel crop after every scroll;
tapping a specific tab at `<x> 2120` is the reliable move.

**Scroll the tab row to its stop before claiming a knob count.** The row scrolls and the last tabs sit past the fold —
Dot Grid turned out to have eight knobs rather than six that way (W11e), and *Vitrall* seven rather than six (W11j).

---

## Getting to a design

The reference is easiest to read in a phone shape. On the tablet AVD:

```bash
export MSYS_NO_PATHCONV=1
adb shell wm size 2400x1080
adb shell wm density 400
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1     # portrait for this AVD's natural landscape
adb shell am force-stop net.smartlauncher.wallpaperstudio
adb shell monkey -p net.smartlauncher.wallpaperstudio -c android.intent.category.LAUNCHER 1
```

Put it back with `adb shell wm size reset && adb shell wm density reset` when finished.

**All coordinates below are for that shape — 1080×2400 at density 400.** They are taps, so they move if the shape
does.

| target | tap |
|---|---|
| new design (the `+`) | `1010 2255` |
| design picker (grid icon) | `264 2226` |
| colours (palette icon) | `456 2226` |
| **Style** (ruler icon) | `642 2226` |
| filters | `813 2226` |
| a tab in the Style tab row | `<x> 2120` |
| the ruler / segmented strip | `<x> 2240` |
| the nudge pad's ← ↑ ↓ → | `173 / 418 / 662 / 903` at `2238` |

The bottom bar is **hidden while the Style panel is open** — close the panel before reaching for the palette.

`drive.sh` wraps the parts worth wrapping:

```bash
bash tools/refdrive/drive.sh shape                 # reshape + relaunch
bash tools/refdrive/drive.sh shot before           # screenshot -> before.png (+ a downscaled _s and panel crop)
bash tools/refdrive/drive.sh tabs                  # scroll the tab row one step and shoot
bash tools/refdrive/drive.sh knob up 8             # drag the selected ruler toward its maximum
bash tools/refdrive/drive.sh knob down 8           # ... and toward its minimum
```

Shots land in `$REFDRIVE_OUT` (default: a `refdrive` folder under the system temp dir), never in the repo — they are
screenshots of someone else's copyrighted UI and must not be committed.

---

## Measuring

`measure.py` is the "measure pixels" rule made runnable. One file with three subcommands so they share the part that
is actually hard:

**Finding the ground is the non-obvious step, and the obvious way is wrong.** The ground is *not* the most common
colour — a big tile or a wide band beats it easily, which is what happened on the first pass at the Mosaic. It is the
colour that touches nearly every **row** *and* nearly every **column**: a tile's pixels are a compact block, the
ground's run the whole frame. Picking it that way is palette-independent and has not been fooled since.

```bash
python tools/refdrive/measure.py scan  shot.png --at 540 --down       # runs of colour along a line
python tools/refdrive/measure.py tiles shot.png --crop 250 2050       # every non-ground region's bounding box
python tools/refdrive/measure.py grout shot.png --crop 300 2000       # gap positions and width spread
python tools/refdrive/measure.py slope shot.png                       # the angle of the first boundary
```

What each one has already settled, so it is clear what they are *for*:

- **`scan`** — Diagonal Bands' ground is a stop the bands never take (five bands over a *sixth* colour), and its band
  widths are exactly equal at *Variation* 0 (W11l). Also Vitrall's band multiply of `0.57` (W11i).
- **`tiles`** — the Modern Mosaic is rows-then-columns at one count and columns-then-rows at another, which is what
  proved it a *recursive subdivision* rather than a grid or a packing (W11k). Line-finding could not tell one boundary
  running the whole frame from three that happen to line up; reading the regions could.
- **`grout`** — the Mosaic's gap holds at 36–43px from *Irregularity* 0 to 100, which is what proved its corner jitter
  moves **one shared lattice** rather than each tile on its own (W11k).
- **`slope`** — ours draws its shallow bands at `20.01°`, confirming the projection is in pixels and not in the unit
  square (W11l).

Needs Pillow (`pip install pillow`). Pure measurement — nothing here writes to the device or the repo.

---

## Then compare against ours

Ours is rendered by `GeneratorRenderHarness` (`core:graphics` androidTest), which walks the design enum and every
sweep, so a new design and a new knob are both picked up with no edit. **Read its KDoc before the first run** — it
cannot overwrite its own output on the emulator, so the folder has to be cleared by `adb shell` first or every plain
filename you pull is stale:

```bash
export MSYS_NO_PATHCONV=1
adb shell rm -rf /sdcard/Pictures/genharness
gradle :core:graphics:connectedDebugAndroidTest
adb pull /sdcard/Pictures/genharness
```

An occasional PNG is written short on the device (Pillow: *image file is truncated*). The byte count matches across
`adb pull` and `adb exec-out cat`, so the file on disk really is incomplete — re-run rather than debug it.
