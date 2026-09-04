# Quality pass over the ours-only designs — in progress

Handoff for the pass in progress. Delete this file when it lands and its findings move into
[WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md).

**The 1-1 check against Smart Launcher is finished** — 21 of their 22 driven, Shape Trail off the list by the author's
call. This is the follow-on: the **nine designs with no reference to drive**, which every W11 slice therefore skipped.
They are `LINEAR_GRADIENT`, `PLASMA`, `RINGS`, `RAYS`, `MONDRIAN`, `HALFTONE`, `GRADIENT_COLUMNS`, `VORONOI`,
`TRUCHET`.

## Read this first: the state of the tree

**Four commits landed and are verified.** One change is **in the working tree, uncommitted and unverified** — see
"Where it stopped" below. The emulator (`Medium_Tablet(AVD) - 16`) **died** during the last harness run and `adb
devices` is empty; that run's failure was `device 'emulator-5554' not found`, an infrastructure failure and not a
code one. Start the emulator before doing anything else.

## What the pass found, and what has been done about it

The method here is not the W11 one — there is no reference to drive. It is: render every design at its default and
across every knob it declares, judge against the teardown's own five aesthetic principles, and **measure** anything
that can be measured rather than judged.

### ✅ 1. Five of the nine measured geometry in the unit square — `be052528`

The largest and most objective finding. `nx` and `ny` are each a share of *their own side*, so any `hypot` or `atan2`
of the two runs in a space the frame stretches — on a 1080×2400 phone, by 2.22×. It is the bug `ContourGenerator`'s
KDoc names and W11u fixed there; nobody reached these five.

- **Rings** drew *ellipses*, at every setting, forever. The design is named Rings.
- **Rays** took its bearing from that metric, so a fan of evenly cut wedges arrived as a couple of broad blocks and a
  handful of slivers — why it read as an accident rather than a starburst.
- **Plasma** spread the same wave count over the height as the width, so every swell was drawn twice as tall as wide.
- **Voronoi** is the pair that must agree: seeds placed in one metric, ownership measured in another, so the cells
  were neither the shape of the lattice nor of the frame. `PointScatter.gridJitter` gained an `aspect` (default `1f`,
  so **Soft Overlaps is untouched byte for byte**).
- **Halftone** had the mismatch inside one design — a frame-shaped dot lattice reading a unit-square field.

Guards added. The Rays one is measured **off-axis on purpose**: the stretch leaves the four axes exactly where they
are and moves only what is between them, so a test comparing due-right with directly-below passes either way.

### ✅ 2. The Mondrian drew graph paper at the mode it opens in — `0971647e`

Measured: at `BICHROMATIC` — the **default** colour mode — the render was **96.5% bare ground**, 2.6% ink, no accent
anywhere. `blockColor` opened with `palette.size <= 2 -> ground`, and the colour mode reduces every palette to exactly
two stops. Fixed by reading the ramp through `RampTones` (dropping its final tone, which *is* the ink). A six-stop
palette still accents with exactly its four middle stops.

### ✅ 3. The radial pair had no organic axis — `62153ac0`

Rings gained a **Wobble** and Rays an **Unevenness**; `0` is the picture each already drew, so no stored recipe moves.
Rings scales the measured *distance* by a `SeededHarmonics` factor on the bearing — a monotonic stretch along every
bearing, so ring `n` stays inside ring `n+1`; displacing each ring's radius on its own would let two meet and tear.
Rays walks each wedge edge off its even position, with the order held by the travel bound alone (`0.4` of a gap each
way, so a wedge keeps a fifth of its share). **A first pass set that bound at `0.5`, which lets two neighbours close
the whole gap** — a zero-width ray.

### ✅ 4. The Truchet drew one line weight — `f4ac9cb7`

Its arc width was a constant. It has a **Thickness** now, curved so `0.5` resolves to the shipped `0.34` of a cell.
The ends are two more designs: a fine tracery, and arcs so wide they meet and the *ground* becomes the pattern.

### ⏸ 5. The two flat ramps could only point one way — **uncommitted, unverified**

`LINEAR_GRADIENT` had **no knobs at all** and ran top-to-bottom only; `GRADIENT_COLUMNS`, whose whole content is a
direction, had no control over it. Both now declare `rotation` and read it through `frameAxis`, with `0` reproducing
exactly what each always drew.

**This is where it stopped, and there is a real open question in it.** The first cut swept a **full turn**, because a
ramp is *directed* — light-at-the-top-right and light-at-the-bottom-left are the same axis and two different
pictures, so a half turn leaves three directions unreachable. `GeneratorKnobTest.everyDeclaredKnobChangesThePicture`
**failed it**, correctly: at `rotation = 1` a full turn wraps to the same angle as `rotation = 0`, so the knob's two
ends render identically and the guard reads that as a declared knob that does nothing.

The tree currently holds the **half turn**, with both KDocs stating the limit honestly rather than claiming coverage
they do not have. That version compiles, passes `check`, and passed a full harness run *before* the full-turn
experiment — but it has **not** been re-verified on a device since being reverted, because the emulator died.

**To finish it:** start the emulator, run
`adb shell rm -rf /sdcard/Pictures/genharness && gradle :core:graphics:connectedDebugAndroidTest`, confirm
`GeneratorKnobTest` passes, eyeball `turn_*_LINEAR_GRADIENT_*` and `turn_*_GRADIENT_COLUMNS_*`, and commit. Do **not**
edit the guard — it is right, and my own standing note says a test failing on a change is evidence against the change.

The better answer, if one is wanted later, is a way for a generator to declare *the period of its angle* so the guard
compares two points that are not a whole turn apart. That is a change to `DesignStyle`/`GeneratorKnobTest` and a
separate idea from this pass.

## Still open, judged but not acted on

- **Plasma has no organic axis** and no knob but *Frequency*. It is much better since the metric fix (it reads as
  marbling rather than a smear) but it is still the design with the fewest ways to change it.
- **Gradient Columns remains the weakest of the nine as a wallpaper** even with its new direction — flat panels
  stepping a ramp, with no variation along the columns. Worth asking whether it earns its slot beside `LOUVERS`.
- **Rays is still the loudest thing in the catalog**: hard edges at maximum contrast, filling the frame, against the
  teardown's first principle. A soft edge between wedges (`roundness`) is the obvious next knob.
- **Voronoi's colour is a vertical ramp** with a per-cell jitter. That is a *layout*, and a good one — but it is the
  only layout, where the driven designs got a `colorLayout` chooser.
- **No dead knobs anywhere**, checked: five of the nine render byte-identically at both ends of *irregularity*, but
  none of those five **declares** it, so the panel never offers it. The `DesignStyle` discipline has held.

## How to re-render and compare

```bash
adb shell rm -rf /sdcard/Pictures/genharness
gradle :core:graphics:connectedDebugAndroidTest      # ~5 min; walks every design and every sweep
adb pull /sdcard/Pictures/genharness
```

Clear the folder first, every time — the harness cannot overwrite on the emulator and a plain filename pulls the
*previous* run. `MSYS_NO_PATHCONV=1` for any `adb` block from Git Bash. Both traps are in
[tools/refdrive/README.md](../tools/refdrive/README.md).
