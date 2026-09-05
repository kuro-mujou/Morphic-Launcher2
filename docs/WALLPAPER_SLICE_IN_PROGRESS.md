# PLANET — a new design from gart's orbs. Analysis done, nothing built.

Handoff for a slice that has **not started in code**. Delete this file when it lands and its findings move into
[WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md).

**The tree is clean and everything before this is committed.** Nothing is half-done; this is a design brief with the
reference already read, so the next session can go straight to building.

## What the author asked for

> take a look at these orb, we can do new wallpaper called **planet** using this orb design

The orbs are `arts/flowforce/Orb1.kt`, `Orb2.kt`, `Orb3.kt` in gart (`D:\Android\gart` on this machine — run `ls ..`
first, the sibling path differs per machine). Their renders are `orb1.png`, `orb2.png`, `orb3-1.png`, `orb3-2.png`
beside the sources. Catalog is **30**; this would be 31.

## The mechanism, read from the source

**All three are the same program with a different field.** Read them together; the differences are three lines.

1. Dark ground. A **disc** at the frame's centre, radius `400` on a `1024` frame — `0.39` of the side — filled
   `pearlWhite`.
2. `canvas.clipCircle(circle)`. Everything after happens **inside the disc only**. This is the whole idea: it is
   [SprayGenerator]'s mechanism (particles advected through a flow field, each leaving a small translucent dot)
   *contained*, so the marks pile against the rim instead of running off the frame.
3. A pool of 20,000–40,000 points. Each carries a **colour fixed at birth** from its *starting* position, off a long
   palette (`cool101`, `cool85.expandReversed()`, `PalettesOf4.q18`). It never changes as the point travels.
4. Every frame: step each point once through the field (`PointTracer.trace` = `flowField[p].offset(p)`, `null` when
   it leaves) and `drawCircle(np, 2f or 3f, alpha 100)`. **The canvas is never cleared**, so the picture is the
   accumulation over N frames — Orb1 900, Orb2 200, Orb3 100.
5. Points that leave are replaced from the source pool (Orb1/Orb2 top back up to full; Orb3 does not — it lets the
   population die off, which is why Orb3 is sparser and more streaky).
6. **The rim is what makes it an object**: `strokeOf(groundColour, 15f)` with
   `ImageFilter.makeDropShadow(0, 0, 20, 20, BLACK)` — a thick ring in the *ground's own colour* with a soft shadow,
   so the disc reads as inset into the page. Orb1 additionally runs `drawGlassBall(...)` (gart's `glass/glassBall.kt`,
   which `RIBBED_GLASS` already ports) for a refracting sphere.

### The fields — the only thing that differs

| | Field | What it draws |
|---|---|---|
| **Orb1** | `a = 90 + sin(x·0.01)·40 + cos(y·0.005)·40` degrees, magnitude `1` | ~1.6 and ~0.8 cycles across a 1024 frame — a **smooth, gentle** field. Broad banded sweeps: the Jupiter look |
| **Orb2** | five terms summed to `360°·(…)`, including a **cross term** `sin(x·y·0.00005)`, magnitude `0.6 + \|sin((x+y)·0.005)\|·0.8` | The cross term's frequency climbs with distance from the origin, so the field is smooth near one corner and fine elsewhere — the dense marbled filigree |
| **Orb3** | **12 seeded point vortices**: `vx += -s·dy/d²·2000`, `vy += s·dx/d²·2000` with `d² = dx²+dy²+400`, then `angle = atan2(vy,vx) + π/2` | Swirls around a dozen centres — the big soft curl of `orb3-1`. The `+400` is the core guard; the `+π/2` makes it circulate rather than radiate |

**These coordinates are pixels** (`FlowField.of` hands the lambda `x.toFloat(), y.toFloat()` — see the Spray finding
in the teardown). At `0.01`–`0.017` that is a *smooth* field, unlike Spring's `10`. Do not "correct" it to a unit
square; check the cycle count against the frame the way the teardown's Spray row does.

## How to build it here

**Most of this design already exists.** `SprayGenerator` is the particle-advection-and-dots half, `RampTones`,
`LinearGradientGenerator.colorAt` and the `drawPoints` tone-batching are all in place. The new parts are the **disc
clip**, the **rim**, and the **fields**.

Suggested shape, to be argued with rather than followed:

- `WallpaperDesign.PLANET` + `PlanetGenerator` + the studio's display name. Catalog 31.
- `variant = VariantKnob("Field", listOf("Bands", "Marbled", "Vortices"))` — Orb1 / Orb2 / Orb3. Three genuinely
  different pictures from one program is exactly what `variant` is for.
- `scale` = **Size** — the disc's radius as a share of the short side, around gart's `0.39`.
- `amount` = **Density** (`AmountKnob.Fraction`) — how many particles, i.e. how far the accumulation goes.
- `roundness` = **Trail length** and `scale`… careful, `scale` is taken. Trail length wants a field of its own;
  `taper` or `depth` are free. `depth` is the honest one **only if** the glass ball is built (it is out-of-plane);
  otherwise use `taper`.
- `irregularity` = **Turbulence**, scaling the field's amplitude, `0` rigid as ever.
- Ground = palette's **last** stop (dark), disc = the palette's light end, marks read `RampTones.spanBelowGround`.
  Note gart fills the disc `pearlWhite` and paints *dark* marks on it — the opposite of Spray. Check which reads
  better against our palettes before committing to one.

**One pass, not 900 frames.** gart accumulates over frames because it is animating; we render once, so the loop is
`for (step in 0 until steps) { for (p in points) { … } }` with the points advanced in place — the same structure
`SprayGenerator` already has. Reuse it rather than re-deriving.

**Watch the count.** Orb2 is 40,000 points × 200 frames = 8M dots. That is desktop-JVM territory. Spray's answer —
batch by tone into ~24 `drawPoints` calls — applies directly and is what makes this affordable.

## Read this before starting

**Simulate the whole frame in Python against gart's own render before writing Kotlin.** Both previous gart ports
landed wrong and were corrected only after the author looked at the picture; five findings between them, every one
invisible to a unit test. The Impasto rebuild found its last and largest problem in a single Python pass where each
device round trip is five minutes. `scratchpad/impasto_sim.py` from that session is the pattern.

The full record of what those two ports got wrong, and why a generative art's source does not say what it draws, is
**[WALLPAPER_STUDIO_TEARDOWN.md](WALLPAPER_STUDIO_TEARDOWN.md) → "The gart harvest"**. Read it first; the orbs are
the same trap.

## Also still open (from the ours-only pass, unrelated)

- Whether a generator should declare the **period of its angle**, so both flat ramps can sweep a full turn.
- Whether `rotation`'s `0` means *untouched* or its default `0.5` means *shipped* — a catalog-wide call.

Both are Open Questions 6 and 7 in the teardown.
