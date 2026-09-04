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
- [x] 3. Drive theirs: full tab inventory with ranges and defaults
- [x] 4. Drive every knob to both ends — the numbers *and* what each does to the picture
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

### 3. Theirs — the tab inventory, and the identity finding

**Ten knobs, not the six recorded**, and the row stops at *Blur*. No *Color mode* tab.

| # | Tab | Kind | Default |
|---|---|---|---|
| 1 | Count | ruler | **4** |
| 2 | Blend mode | segmented, **nine**: Normal · **Screen** · Lighten · Plus · Overlay · Multiply · Darken · Color Burn · Color Dodge | Screen |
| 3 | Mode | segmented: **Fill** · Glow | Fill |
| 4 | Position jitter | ruler | 50 |
| 5 | Radius | ruler | **300** |
| 6 | Size variation | ruler | 40 |
| 7 | **Complexity** | ruler | 8 |
| 8 | **Irregularity** | ruler | 25 |
| 9 | **Distance from c…** (centre) | ruler | 0 |
| 10 | **Blur** | ruler | 0 |

**The identity finding, and it is visible before any knob is touched: their shapes have *hard edges*.** Theirs is
**four** enormous flat translucent forms — a rust round, a slate squircle, an orange round, a cream ellipse — laid
over a dark ground, overlapping so the *colors* mix while every silhouette stays crisp. Ours draws seventeen discs
that each fade to nothing at the rim, so it is a misty wash with no edge anywhere. The "soft" in *Soft Overlaps* is
the **shapes** being soft — big, round, organic — and the *overlaps* being soft in color. It is not soft edges.

Everything else follows from that:

- **The shapes are generated blobs, not circles.** *Complexity* `8` and *Irregularity* `25` are two knobs that only
  make sense on a closed curve built from lobes — nothing else in the list can be what makes the slate form a
  squircle and the cream one an ellipse. Ours draws `drawCircle`, so it cannot make any of them.
- **Their overlaps are a real blend mode, and the default is not Normal.** Nine of them, opening on **Screen** —
  which is why the picture glows where forms cross. Ours offers Normal and Additive.
- **`Mode: Fill / Glow` is a second look, and *Glow* is very likely what ours built.** A soft radial falloff is
  exactly what a glow is; if so, ours has been drawing their alternate look as the whole design, and their default —
  the flat, hard-edged one — is unreachable.
- **Count `4` against ours' `17`.** Theirs is a composition of a few huge forms; ours is a scatter. Their *Radius*
  opens at `300`, a big number whose unit the drive still has to settle.
- **Four knobs have no counterpart at all**: *Complexity*, *Irregularity* (the blob's, not a scatter's),
  *Distance from centre* and *Blur*. Ours' *Jitter* is their *Position jitter*.

### 4. *Mode = Glow* is what ours built, and the shape is a blob

**Driven, and it settles the identity claim: their *Glow* is our design.** Switching *Mode* from Fill to Glow draws
the same four shapes as soft radial falloffs on the dark ground, edges dissolving into it — which is what
`SoftOverlapsGenerator` renders at every setting. So ours did not merely differ from theirs; it built **their
alternate look as the whole design**, leaving their default — the flat, hard-edged one — unreachable. Same shape of
finding as Bauhaus (ours was a Mondrian, W11a) and Dot Grid (ours was a halftone, W11e).

**The shape is a closed curve of `Complexity` points around an ellipse, each pushed off its radius by `Irregularity`.**
Both knobs driven to both ends, and the ends prove it:

| Knob | Range | Default | At its ends |
|---|---|---|---|
| Complexity | `3..16` | **8** | `3` draws smooth eggs with one or two gentle lobes; `16` draws many-lobed organic forms with several bulges each |
| Irregularity | `0..100` | **25** | **`0` is a perfect ellipse** — smooth and symmetric, and Complexity does nothing there; `100` is a hooked amoeba with deep concavities |

That `0` is the tell. A knob whose rigid end is an *exact ellipse*, under a second knob that counts something, is a
radius-per-control-point construction and nothing else — and it is why theirs can draw a squircle, an egg and a
circle from one generator. Ours calls `drawCircle`, so its shape vocabulary is one shape.

### 5. gart has no match for this either, and that is the finding

Now that the mechanism is known, the two candidates worth reading were read:

- `arts/bubbles/src/blob/Blobs.kt` is a **metaball field** — a per-pixel product of distances to Lissajous-moving
  points, mapped through a palette. That is our `MetaballsGenerator`'s family, not this one.
- `gfx/deformPath` (the helper `monet` uses) roughens a path by **inserting a Gaussian-offset midpoint per segment**,
  doubling the point count each pass. Structurally near — points pushed off a polygon — but it cannot be theirs: at
  zero offset it leaves an N-gon where theirs leaves an *ellipse*, and its point count is `2^k · n` rather than the
  `Complexity` it was given.

So **gart is not where this mechanism is**, and the KDoc citation of `arts/monet` should go rather than be corrected
to something else. The construction is a standard blob and needs no source beyond the two measurements above.

### 6. The ten knobs, driven — ranges, defaults, and what each does

Every one pushed to both ends, the number read back off the panel each time.

| Knob | Range | Default | What it does, and what ours has |
|---|---|---|---|
| Count | **`1..10`** | 4 | Blobs. `1` is a single egg alone on the ground — a rigid end. Ours is `8..26`: its *whole range sits above theirs* and it can never draw the design's default, let alone one shape |
| Blend mode | nine options | **Screen** | How overlaps combine, and it re-composes the picture completely — *Multiply* is dark and rich, *Color Burn* nearly black, *Plus* washed bright. Ours offers two |
| Mode | Fill / Glow | **Fill** | **Ours is Glow, always** — see §4 |
| Position jitter | `0..100` | 50 | How far centres leave a regular arrangement; at `0` they sit evenly spaced and symmetric. **Ours has this one** (*Jitter*) |
| Radius | **`60..400`** | 300 | The blob's size. Ours has `MinRadius`/`MaxRadius` fixed. **Careful: driving it re-rolls the shape**, so bounding boxes at two settings are not the same blob and their ratio means nothing |
| Size variation | `0..100` | 40 | The spread of sizes around *Radius*; `0` draws every blob identical, and climbing it makes most of them *smaller*, so it subtracts rather than spreading both ways |
| Complexity | `3..16` | 8 | The blob's control points — §4 |
| Irregularity | `0..100` | 25 | How far each is pushed off its radius; `0` is an exact ellipse — §4 |
| Distance from centre | **`0..50`** | 0 | Pushes every blob radially *outward*, hollowing the middle of the frame |
| Blur | `0..100` | 0 | A blur over the **whole composed picture**, not a per-shape softening: at `100` the shapes dissolve into one smooth wash |

**Two of those are not really this design's to own.**

- *Blur* is a whole-image grade, which is the same argument that sent Ribbed Glass's *Vibrancy* to the **Filters**
  stage in W11v — and ours already has a Blur filter there, so building it per-design would be a second control over
  one effect.
- *Distance from centre* is a composition knob with a very small range (`0..50`) whose whole job is to hollow the
  middle. Worth building only if a field is spare.

**The ground agrees with ours**, as far as this palette shows: it measures `(50, 20, 11)`, darker than any of the
four shapes, which is what `palette.colorAt(size - 1)` gives. Not confirmed against a light palette.
