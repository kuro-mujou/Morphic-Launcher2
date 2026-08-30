# Harvesting from gart — techniques for the icon studio and the wallpaper studio

*Assessed 2026-08-30. Reference material, read on demand — not a rule every session needs loaded.*

**gart** is a ~400-file Kotlin generative-art micro-framework by Igor Spasić, cloned at **`D:\Android\gart`**
(a sibling of this repo). It is the toolbox Smart Launcher draws on for its separately-charged wallpaper studio,
and it is the reference we mine for two things: a handful of additive effects for the **icon studio**, and the
generative engine for the planned **wallpaper studio** ([REWRITE_PLAN.md](REWRITE_PLAN.md) has no entry for the
latter yet — it is design-stage).

This doc is the standing record of *what is worth taking, in what order, and what it costs*. It is an assessment,
not a plan: nothing here is committed to a slice.

---

## The two constraints that shape every port

**gart is not a dependency.** It targets desktop **Skija** (`org.jetbrains.skia`); we are Android Compose /
`android.graphics`. Both sit on Skia, but the surface APIs differ, so every item below is *re-implemented from the
algorithm or the SkSL*, never linked. License is **BSD-2-Clause** (Copyright 2022, Igor Spasić) — permissive;
retain the copyright notice in any file that ports non-trivial gart code.

**Our icon renderer is two paths, and per-pixel work is written twice.** The bake owns a software bitmap and runs
CPU `IntArray` math at every API down to `minSdk` 26; the live path previews in Compose and can only reach
per-pixel effects through AGSL `RuntimeShader` (API 33+). This is exactly why `LayerEffect.drawsLive` exists. It is
also *good news for porting*: almost every gart effect is a CPU pixel loop, which drops straight onto our bake path
in the `LayerGrain` / `LayerRipple` idiom (`drawsLive = false`, previewed from the bake). See
[ICON_ARCHITECTURE.md](ICON_ARCHITECTURE.md) for the two-renderer hazard in full.

**Starting position: the icon studio is already mature — 20 effects** (see `LayerEffect`), and some of ours are
*better* than gart's equivalent. Our `Grain` is multi-octave gradient noise with a quintic fade; gart's `addGrain`
(`fx/grain.kt`) is single-octave value noise. So this is not a catch-up list — it is a short list of genuinely
additive things.

---

## For the icon studio

Recommended order: **glass → dither → OKLCH ramps → palettes.** One killer new effect, one stylistic family,
one quality lift, one content win — all clean ports.

**The icon-studio harvest is done: `Glass`, `Dither`, `Tritone` and the curated palettes are all shipped and
device-verified.** What remains of the study is the wallpaper-studio engine (below), a separate arc.

The palettes landed as a `ColorPalettes` library (`core:designsystem`) plus a swatch ribbon in `MorphicColorPicker`,
so every color field across every effect — and the wallpaper studio — gets them. **Two tiers:** a `featured` dozen
(hand-picked, named; four seeded from gart's theming files, the rest curated) and the wider `coolPalettes` bank of
~179 aesthetic sets harvested wholesale from gart's `cool.kt` (each capped at eight colors), the two concatenated as
`ColorPalettes.all`. Still not gart's data-viz colormaps (viridis and the like), which read garish. The ribbon is a
`LazyRow` to carry the count. A named, filterable list is where community palette-sharing would extend it.

The perceptual-color item (#4 below) landed as **`Tritone`**, not as a change to `Bloom` or `Duotone`. Tracing the
real ramp code overturned the premise here: every ramp in `LayerGradient` (Bloom, Gloss, Vignette) is a *single
hue* fading to its own transparent, so OKLCH does nothing there. The only two-hue interpolation is `Duotone`'s, and
that is a `ColorMatrix` shared with ten authored filter presets — OKLCH-ifying it in place would either break that
shared derivation or force rewriting the whole `Filter` matrix path. So perceptual color went in additively as a
new three-color grade (`Oklab` + `LayerTritone`), leaving the live matrix `Duotone` untouched. **OKLab, not OKLCH**
— a straight perceptual line is predictable for a curated ramp where OKLCH's hue arc can swing the long way round.

### 1. Glass / refraction — new effect class, highest value

`glass/glassBall.kt`. Per-pixel Snell's-law spherical refraction: read the pixels underneath, bend them through a
sphere, write them back, then paint Fresnel rim darkening + a diffuse highlight + a specular spot on top.

Why it matters: **every current effect draws over or behind the artwork; none refracts it.** This is the
"Liquid Glass" look (current since iOS 26), and it is the one new *class* of thing in gart we do not already have.
The port is clean — pure `IntArray` bilinear sampling plus radial-gradient overlays, exactly our bake idiom,
`drawsLive = false`. It is also a direct dividend for the wallpaper studio (`glassPath.kt` generalizes it to an
arbitrary shape). **If we port one thing, this is it.**

### 2. Dithering — new effect, on-brand for a stylized launcher

`dither/` (29 kernels: Floyd–Steinberg, Atkinson, ordered Bayer 2×2–8×8, blue-noise, Sierra, Stucki, …).
Distinct from our `Pixelate`, which quantizes to a grid of one color per cell; dither gives the 1-bit / riso /
newsprint look through *error diffusion*. All CPU pixel loops. One `LayerEffect` variant with a `kernel` enum
covers the whole family. Pairs naturally with gart's `shader/filterRisograph.kt` SkSL if we ever want it live.

### 3. Halftone / CMYK — new effect, medium value

`halftone/` (CMYK separation + angled dot screens). The classic print-dot look, different enough from `Pixelate`
and dither to earn its own slot, but lower priority than the two above.

### 4. OKLAB/OKLCH perceptual color — the *improvement*, not an addition

`color/space/ColorOKLAB.kt` (+ `ColorOKLCH.kt`). Our `Bloom` gradients and any ramp interpolate in **sRGB**, which
muddies midtones (blue→yellow passes through gray). OKLAB mixing fixes that. Two honest bounds:

- Our `Duotone` is a **color matrix** — linear, sRGB by construction — so it *structurally cannot* do OKLAB
  without becoming a per-pixel pass. This improves `Bloom` / `Pattern` ramps and any future gradient stops,
  **not** `Duotone`.
- Low risk, small surface, and it is the same math the wallpaper studio's palettes will want.

### 5. Noise library — hold until wallpaper

`noise/` (Simplex, OpenSimplex, curl, fbm). Our `Grain` field is already excellent, so this is **not** a Grain
upgrade. Curl/simplex/fbm are the backbone of displacement and of every generative wallpaper — port them *when the
wallpaper studio needs them*, not for icons.

### 6. Palettes + PaletteGenerator — cheap content win

`color/palettes/` (~300 curated palettes: ColorBrewer, Matplotlib, Nippon, Tableau, …) + `PaletteGenerator`.
Directly useful as duotone/bloom presets **and** as the seed for the community theme-sharing feature. Low effort.

---

## Not for icons — the wallpaper-studio engine

*Now planned in detail: [WALLPAPER_STUDIO_PLAN.md](WALLPAPER_STUDIO_PLAN.md), drawn from a walkthrough of Smart
Launcher's studio. Its headline finding — the studio's whole **filter** layer is already built as the icon effect
pipeline, so only the **generators** below are genuinely new.*


Overkill for a 96px icon; **exactly** what a wallpaper studio is made of. When that plan starts, gart is less "a
source of techniques" and more "the reference implementation of the whole feature":

- Attractors (18: Lorenz, Clifford, De Jong, …), n-body / Barnes–Hut, orbital mechanics (WHFast).
- Fluid: Navier–Stokes, Lattice-Boltzmann, particle rendering.
- Reaction-diffusion (Gray-Scott, FitzHugh-Nagumo) with gradient coloring.
- Flow fields + evenly-spaced streamline tracing; cellular automata (elementary rules, Belousov–Zhabotinsky).
- Stippling (Voronoi/Lloyd, Wang-tile blue noise), triangulation (Delaunay/Voronoi), circle packing, JFA
  distance fields.
- 3D scene / z-buffer / meshes; Box2D rigid-body + particles (`gart-box2d` module).
- Generators: spirograph, harmonograph, midpoint-displacement terrain.

The cost argument this reframes: Smart Launcher ships launcher + icon studio + wallpaper studio as three
separately-charged apps (~3× to the user). One Kotlin/Skia codebase can be the engine for both studios at once —
which is the differentiator behind building the wallpaper studio *in* rather than beside.

---

## The one architectural decision to settle before any shader port

gart's SkSL effects (`shader/`: marbled, risograph, sketching-paper, neuro) are ready-made looks, but
SkSL→AGSL `RuntimeShader` is **API 33+**, while our **bake must work at 26**. So a shader-based effect is either:

- **(a)** written twice — AGSL for the live preview (33+), CPU `IntArray` for the bake (all APIs) — matching the
  existing `drawsLive` split; or
- **(b)** run `RuntimeShader` against the bake bitmap too and accept the effect is **33+-only**.

Decide this once, up front. Our current per-pixel effects all took route (a).

---

*Companions: [ICON_ARCHITECTURE.md](ICON_ARCHITECTURE.md) (the render subsystem), [ICON_EFFECTS_PLAN.md](ICON_EFFECTS_PLAN.md)
(the effect expansion, whose §8 is the phase-2 assessment this doc feeds into).*
