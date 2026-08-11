# Icon Studio (B9)

**Status:** S1–S8 landed (2026-08-11); S1 and S2 verified on device. **Remaining: S9 (presets).** Shadows are
deferred with reason — see S6.
**Covers:** the per-app + global icon editor, its persistence, and the render path it needs.
**L1 reference:** five docs, read in full — `ICON_STUDIO_PLAN`, `ICON_SKIN_PLAN`, `ICON_LAYER_STUDIO_PLAN`,
`ICON_DASHBOARD_PLAN`, `ICON_STUDIO_UI_PLAN`. This is one plan replacing all five, for the reason below.

---

## What L1's five docs are, and why they are one plan here

Read in date order they are a churn log rather than a spec — the same feature rediscovered four times:

| L1 doc | Locked | Fate |
|---|---|---|
| `ICON_STUDIO_PLAN` (07-02) | Flat cosmetic engine: `IconStyle`(shape/bg/fgScale/legacyScale/normalize/mono/tint), global studio + per-app bottom sheet, `mergedWith` field merge | Superseded twice |
| `ICON_SKIN_PLAN` (07-16) | A backing plate *under* the icon; custom-image or live-backdrop source | Custom-image half killed the next day (it is just a layer); backdrop half survives, deferred |
| `ICON_LAYER_STUDIO_PLAN` (07-17) | fg/bg become an editable **layer stack**; everything becomes layers | The actual end state |
| `ICON_DASHBOARD_PLAN` (07-18) | Icons settings → hub; editor escapes to a full-screen route | Chrome only |
| `ICON_STUDIO_UI_PLAN` (07-18) | Procreate-style workspace: Haze blur, MVI, a new component set | Half-built; U4–U6 never landed |

The persistence model reversed **three times inside one doc**: S0 specified a single JSON blob, S1b.2 shipped
eight flat columns, S2 shipped flat-columns-plus-an-additive-list with a `LayerSlot` enum, and S4.3 threw all of
it out, returned to the blob and retired `LayerSlot`. Bill: **DB v20 → v24, four destructive bumps**. That is the
"four destructive DB bumps learning this" already recorded in CLAUDE.md — and the reason L2 adopted the end state
directly rather than replaying the sequence.

**So this doc takes L1's *conclusions* and skips its *route*.** Where it departs from a conclusion, it says so.

## What L2 already has (B3), and what is actually missing

`core:icon` landed the layer end-state directly — L1 never had this cleanly at any single moment:

- `IconLayerSet` / `IconLayerSpec` / `LayerRole` / `LayerSource`, with the **fg-above-bg invariant enforced in
  `init` and in `moveUp`/`moveDown`** (an illegal reorder returns the set unchanged rather than throwing).
- `IconRenderer` already iterates layers, each into **its own bitmap**, with a per-layer transform matrix and a
  per-layer shape mask — no stack-level mask, which is L1's S4.3 end-state.
- `IconId(component, layerSet, sizePx)` — the S0 re-key, done, so value equality gives correct invalidation free.
- `LayerEffect` is a **sealed list**, not one nullable column per effect — the explicit anti-L1 decision.
- `IconShape` is by-id over vector drawables; all seven are in `res/drawable`.
- `AppDefaultMonochrome` is a **foreground source**, not a third stack layer — modelled right first time.

Missing, and the whole of B9:

1. **Persistence + resolution.** `data:icons` does not exist. `IconOverrideEntity` is still L1's 20-column
   stringly row. `LocalIconLayerSet` is a hardcoded `IconLayerSet.Base` — nothing per-app reaches `LauncherIcon`.
2. **A live render path.** `IconRenderer` bakes to a bitmap and nothing else. The editor half of the hybrid —
   layers as Compose nodes, responding per frame — has no implementation.
3. **Effects.** The bag is empty; the render loop has no effect stage and `IconLayerSpec` has no
   opacity/blend.
4. **Legacy background detection.** `DrawableParser` explicitly defers edge-pixel sampling.
5. **Custom-image storage.** No `CustomIconStore` equivalent, and no file lifecycle.
6. **The editor itself.** Nothing.

## Locked decisions (2026-08-11, author-confirmed)

- **A dashboard hub in settings, and a full-screen studio behind it.** The `ICONS` section returns to
  `SettingsSection` — the name that section KDoc has been reserving — as a hub with **Edit all icons**, **Edit
  specific apps**, and a **Presets** placeholder. The editor is its own full-screen destination.
- **The studio never shows the system wallpaper, and never uses `wallpaperBackdrop`.** Its canvas backgrounds
  are owned and drawn: black, white, checkerboard, and the two mixed variants. L1's sixth option (the launcher's
  owned wallpaper image) is **dropped**.
- **Its floating chrome is Haze** — tool rail, extras, the settings container, the layer popup. It blurs the
  **live canvas** beneath each surface, which is what makes them read as glass over the work rather than as
  opaque panels parked on top of it.
- **Icon packs are in scope, and land last.** A pack replaces the **content of the foreground and background
  layers**; every decoration layer the user added is untouched. Built after the core editor works.
- **First slice is persistence + resolution, with no UI.** It is what every later slice sits on, and it is the
  layer L1 rewrote four times.

### Why the studio is the one place with a second blur system

This launcher already has a blur subsystem — `wallpaperBackdrop` — and a rule that a near-copy of an existing
mechanism is the mistake this rewrite keeps un-making. Haze is nonetheless right here, and the two do **not**
overlap, because they answer different questions:

- **`wallpaperBackdrop` samples a *pre-blurred wallpaper bitmap* by position.** One blur for the screen, shared
  by every surface, so a panel sliding across it continues the picture. It can only ever show the wallpaper.
- **Haze blurs whatever is actually drawn beneath the node, live.** In the studio that is the canvas — and the
  canvas is deliberately not the wallpaper.

So they are not two ways to do one thing; the studio is the only screen in the launcher whose backdrop is
**content the launcher itself is drawing**, and it is the only screen `wallpaperBackdrop` structurally cannot
serve. Two consequences worth keeping straight:

- **The "no wallpaper" decision is what *guarantees* Haze works here.** Haze needs a real drawn node to sample,
  and a transparent punch-through to the OS wallpaper (which is how every settings preview works) leaves it
  nothing. L1 recorded that as a caveat it had to be careful about; here the canvas is an owned drawn node by
  construction, so the hazard cannot arise.
- **There is plenty to blur, which is the part that is easy to get wrong on paper.** Judging it by the
  *background* alone suggests otherwise — a blurred flat black returns flat black. But the canvas's main content
  is the **hero icon**, at full size, and the panels overlap it; frosting its colours is precisely the
  drawing-app material. Blurred checkerboard reads as a fine neutral frost, and the dark tint gives every surface
  one consistent material whichever background is chosen.

**Neither dependency is new — both are already in `gradle/libs.versions.toml` and consumed by nothing**, so the
studio is their first consumer, as L1 planned. Haze `2.0.0-alpha03`, modular (`haze` + `haze-blur` +
`haze-blur-materials`), API `Modifier.hazeEffect(state) { blurEffect { blurRadius; colorEffects } }` over a
`hazeSource`-marked canvas. Confirm it resolves against the pins at add-time (Kotlin 2.4.0, compose-bom
2026.06.00, material3 1.5.0-alpha22) — the catalog comment records the API but no module has compiled against it.

### One thing not taken

**`kmp-showcase-compose`** (also already in the catalog, also unused) — L1 needed a coach-mark tour because it
chose icon-only tool buttons with no labels. Labelling the tools is the cheaper fix. Leave it declared and
unconsumed; it is there if labels prove not to be enough.

## Where the model lives — move `layer/` and `IconShape` to `core:model`

The global default layer set is a **preference** (the user's chosen look for all icons), so by
`data:wallpaper`'s own rule — bookkeeping stays local, the preference-shaped half goes to `data:settings` — it
belongs in a settings slice. But `IconLayerSet` is in `core:icon`, and `data:settings` should not depend on a
module that renders bitmaps.

The whole layer model is **pure Kotlin** — `IconLayerSet`, `IconLayerSpec`, `LayerRole`, `LayerSource`,
`LayerEffect`, `IconShape` touch no Android type. Only `IconShapes` (which maps ids to `R.drawable`), the parser,
the renderer and the Compose layer are Android. That is exactly the split this codebase has made twice already:
**`BackdropEffect` is in `core:model` while its rendering is in `core:designsystem`**, and **`DeviceConfiguration`
is a pure enum in `core:model` with detection in `core:designsystem`**.

So: move `core:icon/layer/*` and `IconShape` into `core:model`; `IconShapes` stays in `core:icon` (it is a
resource mapping). `data:settings` then holds the slice with no new dependency, and `feature:settings` reads the
model it already has on its classpath.

*Alternative, rejected:* leave the model where it is and let `data:settings` depend on `core:icon`. Acyclic and
it compiles, but it puts a bitmap-rendering module on the settings store's classpath to reach four pure data
classes, and it is the third time this codebase would have faced this question and answered it differently.

## Persistence

**Two stores, split by who the value belongs to.**

- **The global default → a fifth `data:settings` slice**, key `icon_layer_set`, holding the bare `IconLayerSet`
  with `IconLayerSet.Base` as its absent value. One `@Serializable` document under one key, per the slice rules:
  fully-defaulted fields, `ignoreUnknownKeys`, no version int, the key name as the seam for a semantic break. It
  is the **only slice whose type validates itself on construction** (exactly one foreground, one background, fg
  above bg), so a well-formed blob can still be an illegal value — which lands on the same fallback-and-report
  path as a corrupt one, and is pinned by a test.
- **Per-app overrides → Room, via `data:icons`.** `IconOverrideEntity` **collapses** from twenty stringly
  columns to two: `component` (PK) + `layerSet` (a JSON blob). That is CLAUDE.md's stated consequence for B2,
  now cashed. **DB v2 → v3, destructive** — nothing has shipped, and the entity is unreferenced today.

**Full-snapshot detach, as L1 finally locked.** Opening an app in the editor snapshots the current global
default into that app's row; the app is thereafter independent and ignores later global changes. Reset deletes
the row and re-attaches it. No field-merge and no variable-length list diffing — which is the thing L1's
`mergedWith` could not survive and spent three model revisions discovering.

## Resolution — how a per-app set reaches an icon

`LocalIconLayerSet` stays the **global default**. A second local carries the overrides:

```
LocalIconOverrides: Map<ComponentKey, IconLayerSet>   // provided at the shell, from the repository flow
LauncherIcon:       overrides[component] ?: LocalIconLayerSet.current
```

A map re-provided at the shell recomposes every `LauncherIcon`, and that is **cheap by construction**: each cell
re-runs its `remember(component, layerSet, sizePx, generation)`, and for every app but the edited one the layer
set is unchanged, so `peek` returns the cached bitmap and nothing re-bakes. Only the edited icon gets a new
`IconId` and a new bake. This is the same shape as `IconRenderManager.generation` and needs no new invalidation
signal — the key does the work, which is what that key was built for.

## Render — two paths, one resolver

The hybrid is already locked in CLAUDE.md: **display bakes to one flat bitmap; the editor renders live.** Only
the bake path exists. The live path is a new composable — each resolved layer as a Compose node, transform via
`graphicsLayer`, effects via colour filter / blend, shape via a clip — so a slider drag responds per frame with
no bake, and a commit invalidates that icon's baked entry.

**The real risk is the two drifting**, and it is worth naming because it is silent: an icon that looks right in
the editor and wrong on the home screen is a bug the editor cannot show you. Three things keep them honest, and
all three are structural rather than discipline:

- **Both drive off `IconLayerResolver`.** It is already pure and already injected its one impure step (image
  decoding), so the live path reuses it unchanged. Layer order, visibility filtering and "what content does this
  source mean" therefore cannot disagree.
- **The shape mask resolves through the same `IconShapes` drawable.** The clip is built from the same vector
  silhouette in both paths; adding a shape stays "drop in a drawable".
- **Transforms are expressed in the same normalized square box.** `IconLayerSpec`'s offsets are fractions of the
  box and its zoom/rotation are about its centre, which is what makes `Matrix` and `graphicsLayer` agree without
  a conversion either side could get wrong.

## Compositing properties vs effects — opacity and blend go on the spec

`IconLayerSpec` gains `opacity: Float = 1f` and `blend: LayerBlend = Normal`; they are **not** `LayerEffect`
variants. The distinction: an **effect changes what the layer is** (tint, shadow, gradient), while opacity and
blend describe **how the layer joins the stack**. Every layer has them, always, with a meaningful default — which
is what a field is for and what a list membership is not. L1 had both as spec fields too; it just never said why.

`LayerEffect`'s variants then land incrementally, each additive: monochrome/tint, saturation, brightness, hue
(all one `ColorMatrix` stage), then outer/inner shadow, then gradient overlay. The list model means each is a
new variant and **no schema change**.

## Legacy background detection (designed by L1, never built there)

Sample the legacy bitmap's edge ring; when it is one flat opaque colour, that becomes what the background layer
resolves to. Otherwise the background stays empty. **No matting** — there is no reliable way to cut a glyph out
of a rasterized icon, and L1 rejected it for the same reason.

**L1 has no implementation to port.** Its `parseLegacy` fills the background with a hardcoded `LEGACY_PLATE_COLOR`
and the edge sampling never left the plan, so the thresholds here are ours.

**The "invisible until the foreground moves" claim is only true if it is made true**, which is the one thing
building this changed. A fill is safe to apply by default exactly while the foreground already covers it — and a
rounded legacy icon does not: its corners are transparent, so painting the plate colour behind it would **square
the icon off**, and a drop shadow's soft edge would fill in the gap the shadow leaves. Both are visible changes to
icons nobody asked to change. So the solid-fraction threshold is near-total (95%) rather than a majority, which
declines those cases and leaves the promise literally true for the ones it accepts. Setting a background colour by
hand is still one tap in the studio, which is where a rounded icon's plate belongs anyway.

**The colour is resolved, not written into the recipe.** It lands on `ParsedIcon.background`, so `AppDefault` on
the background layer resolves to it; nothing is persisted and no recipe changes. Two consequences worth having:
the app's recipe still reads "app default" (so Reset and inheritance behave normally), and an app that updates its
artwork gets its colour re-detected rather than keeping one frozen from a previous version.

The decision is split from the sampling — `LegacyBackground` is arithmetic over an `IntArray` and unit-tested
without an emulator, where rasterising the drawable stays in `DrawableParser`. Same split as `SettingsSlice` and
`IconLayerResolver`. The tests that matter are the **refusals**: getting one wrong does not fail loudly, it
silently restyles an icon.

## The editor

**Plain MVVM, per CLAUDE.md's hard rule.** One `IconStudioViewModel` with one `StateFlow<IconStudioState>` and
typed methods. **L1's `ICON_STUDIO_UI_PLAN` specified MVI (State/Action/Effect) and that is not ported** — it is
the exact ceremony that rotted L1's home screen into a 500-line `when(event)`.

**One editor, two modes**, as L1 finally reached: GLOBAL edits the default set (live-commit, previewed on a
sample app); INDIVIDUAL edits one app's snapshot (Save / Reset). Same scaffold, differing in the preview subject
and the commit affordance.

- **Canvas.** Full-bleed, marked `hazeSource`, the icon centred in a **square bound that clips overflow** — the
  same bound the real renderer scales into, so what overflows here overflows there. A background button cycles
  black / white / checkerboard / black-outside-checkerboard-inside / white-outside-checkerboard-inside.
- **Everything else floats over it on Haze.** One `hazeState` for the screen and one shared surface modifier
  (dark blur + tint, white content on top), applied by the tool rail, the extras, the settings container and the
  layer popup — so a new floating surface cannot arrive with a different material. The rails are **adaptive**:
  a row at the bottom with the extras at the side in portrait, a column at the side with the extras along the
  bottom in landscape, driven off `currentDeviceConfiguration()` like every other adaptive screen here.
- **Layer list, reordered by buttons rather than drag.** L1 locked buttons with a good reason (precise and
  predictable) and then reversed itself in `ICON_STUDIO_UI_PLAN`'s U5 for a reorderable drag list. **Take the
  first answer.** A stack is three to six rows with a hard invariant, and a *refused drag* is an interaction with
  no way to explain itself, where a **disabled move button** says which move is illegal before it is attempted.
  It also keeps the drag toolkit for surfaces, which is what it was built for.
- **Live edit is non-negotiable.** Every slider applies to the preview *while dragging*, not on release. Note
  the trap already documented in CLAUDE.md: the sliders must not be "fixed" back onto
  `rememberSliderState`/`rememberRangeSliderState`, whose init block freezes the first composition's callback and
  range.
- **Undo/redo is nearly free here, and L1 could not tell.** It left this an open feasibility question because
  its state was a bag of flat fields. `IconLayerSet` is an immutable data class, so an undo stack is a
  `List<IconLayerSet>` and a step is an index. Worth building in from the first editor slice rather than retrofitting.

### The app picker is L2's first, and three unrelated things are waiting on it

INDIVIDUAL with no component needs a picker. There is no app picker anywhere in L2, and **three other unbuilt
verbs are each blocked on one**: HOME's surface menu "Add app", the home vertical list's "Add apps" row (without
which its contents are whatever the seed put there), and a folder's add-via-picker. Build it as a shared
component from the start rather than a private one in the studio — this is the one slice where it can be paid
for once.

## Icon packs (last, and they are a *source*, not a mode)

L1 modelled a pack as a field on `IconStyle` plus a parallel resolution path in `PackAwareIconSource`. Here it is
one more `LayerSource` variant, selectable on **fg/bg only**:

```
LayerSource.IconPack(packPackage: String, drawableName: String? = null)
```

Consequences, all of which are the author's stated model falling out of the type rather than being coded:

- **"Apply a pack to everything" is not a feature.** It is setting the global default set's fg/bg source — so it
  goes through the same commit, the same cache key and the same invalidation as any other edit.
- **Decoration layers are untouched by a pack**, because a pack only ever occupies an fg/bg slot.
- **A per-app pack pick is not a separate feature either** — it is that app's fg source, in its snapshot.

What still has to be built when this lands: pack detection (theme-intent actions), `appfilter.xml` parsing to map
a component to a drawable name, and — for browse/search — a drawable *lister*, which L1 never finished
(`ICON_STUDIO_PLAN` S3.2b is still TODO there).

**One detail to settle then, not now:** a pack icon is usually **one flat image**, not an fg/bg pair. So a pack
pick most naturally lands on the **foreground**, leaving the background as whatever the user has (empty, a solid
fill, a custom image) — which preserves the "decoration is untouched" rule. Whether a pack's `iconback` should
feed the background layer is the open question.

## Slices

- **S1 — model move + persistence + resolution. No UI. — DONE (2026-08-11), verified on device.** Invisible by
  design: every icon renders exactly as it did before. Four parts, four commits:
  - **S1a** — `layer/` + `IconShape` → `core:model.icon`; `IconShapes` stays in `core:icon` (it maps ids to
    `R.drawable`). Blast radius was `core:icon` alone. It also took the `kotlin.serialization` plugin **out** of
    `core:icon` — the persistence concern followed the model out, leaving that module purely a renderer.
  - **S1b** — the global default as a fifth settings slice, key `icon_layer_set`, storing the bare `IconLayerSet`
    (`BackdropEffect`'s shape: the recipe *is* the setting, so a wrapper record would be a bag with one field).
    `SettingsRepository.iconLayerSet` / `setIconLayerSet`.
  - **S1c** — `icon_override` collapsed to `component` + `layerSet` (**DB v2 → v3, destructive**); `data:icons`
    with `IconOverrideRepository` + `IconLayerSetCodec`. An unreadable row is **skipped, not deleted** — a recipe
    a later build could read is not the current build's to throw away.
  - **S1d** — `LocalIconOverrides`, resolved **in `LauncherIcon`'s `layerSet` default**, so an explicit argument
    bypasses it (which is what the studio's live preview of an uncommitted set needs). Provided by
    `ProvideIconRecipes` in `app`, around the **whole nav graph** rather than the shell — the launcher surfaces,
    the settings previews and the studio must agree about what an icon looks like.

  **One correction to this plan, found while building it: there is no "invalidate on commit".** `IconId` already
  carries the layer set, so an edited icon simply *is* a different cache key — it misses, bakes, and the stale
  bitmap ages out of the LRU. Calling `IconRenderManager.invalidate` would additionally bump `generation`, whose
  entire job is the one input the key *cannot* see (an app replacing its own artwork) and which recomposes every
  icon on screen. Spending that on a change the key already handles is work for nothing.
- **S2 — the live render path. — DONE (2026-08-11), verified on device: the two paths render identically, and
  the live one stays smooth under a dragged slider.** `IconLayerStack` draws the layers as Compose nodes, so a
  slider costs a redraw rather than a bitmap per frame. Three parts:
  - **S2a** — `LayerTransform`, the offset/zoom/rotation arithmetic as one pure type both paths use
    (`IconRenderer` via `toMatrix`). Unit-tested, and the tests pin *conventions* rather than results: an offset
    is a fraction of the box and not a pixel count, negative Y is up, zoom and rotation pivot on the centre. None
    of those would fail loudly if they diverged — the icon would just quietly sit somewhere else.
  - **S2b** — `ParsedIconLoader`, one answer to "what are this app's layers?", now used by `IconRenderManager`
    too. **Deliberately not cached**, and the KDoc says why: a `ParsedIcon` holds `Drawable`s, which the
    compositor mutates via `setBounds`, so sharing one across concurrent bakes would let them scribble over each
    other. It is also **blocking rather than suspending**, so the manager can run it *inside* its bounded bake
    dispatcher — a loader that hopped for itself would escape the parallelism cap that exists to keep cores free.
  - **S2c** — the `IconLayers` dev-harness playground: the same set, same app, baked and live side by side, with
    live controls. Comparing pixels needs instrumentation this project has no setup for, so the comparison is made
    by eye, deliberately and repeatably.

  **What guards the two paths is now three shared *things* rather than three intentions**: `ParsedIconLoader`
  (what the layers are), `IconLayerResolver` (which draw and what each means) and `LayerTransform` (where they
  sit). Only the drawing API differs, which is unavoidable — and the shape mask, being the one piece each path
  implements in its own API, is the first place to look if the playground ever shows a difference.
- **S3 — editor shell + route + entry points. — CODE LANDED (2026-08-11); on-device verification pending.** Four
  parts:
  - **S3a** — Haze proven and `studioSurface` stood up. The API was read out of the published sources rather than
    taken from L1's notes, which were a guess (that plan marks the imports "assumed" and never compiled). Two
    decisions: the content colour is **fixed white**, the studio being the one zone whose backdrop the *user*
    switches between black and white; and the fallback background sits **before** `hazeEffect` in the chain, so it
    is covered by the blur rather than doubling its wash.
  - **S3b** — route, plain-MVVM ViewModel, canvas. The key is a **sealed pair** (`Global` / `App(component?)`)
    rather than L1's mode-plus-nullable-component, which can express a global route carrying an app.
  - **S3c** — the layer stack and per-layer transform / shape / source. Undo is **punctuated**: the live path
    records nothing and `commitEdit` lands one history entry per gesture. **Explicit Save in both modes**, which
    departs from L1's live-committing global studio — a slice is one JSON blob, and a global edit restyles every
    icon on the device.
  - **S3d** — the shared `AppPicker` (`core:designsystem`) and the item menu's **Edit icon** verb, routed through
    `app` so `feature:shell` never learns the studio exists.

  **One thing the model made unnecessary: L1's "this layer / whole icon" scope split.** Its UI plan left that an
  open question — a segmented toggle, or a separate "Icon settings" entry? In L2 all six of its whole-icon tools
  have gone elsewhere: the tile shape became a *per-layer* shape (there is no stack-level mask), the background is
  the background layer's source, theming is `AppDefaultMonochrome` on the foreground, sizing is `data:settings`
  and another screen, the skin is deferred, and a pack will be a per-layer source. Everything acts on one layer,
  so the question does not arise.
- **S4 — the dashboard. — CODE LANDED (2026-08-11); on-device verification pending.** `SettingsSection.ICONS`
  returns, in the Personalization group, as a hub: Edit all icons / Edit specific apps / a Presets placeholder,
  adaptive (portrait two cards over the presets; landscape a narrow action column beside them). **The name means
  what it said in L1 at last** — that section was shape, background and layers, and this codebase has been holding
  the name back for it while grid and icon *sizing* lived in each surface's own section.
  - It navigates through `LocalNavigator`, the same shape `WallpaperDetail` uses, because the destination belongs
    to *this* feature and there is nothing for `app` to be told. That is not a contradiction of the shell taking
    `onOpenSettings` as an action: the shell's rule is about a module learning of a destination that is **not its
    own**.
  - The Presets placeholder **says it is not built** rather than showing disabled cards, per the settings
    sections' own rule that a control which changes nothing is worse than a missing one. It is present at all
    because it is the slot the real feature fills, and the persistence model already supports it — a preset is a
    named `IconLayerSet`, so no schema change is owed.
  - **Every dev chip is now retired**: Settings → Icons reaches both dashboard actions and a long-press on any app
    icon reaches "Edit icon". A shortcut kept beside a real route is how two paths to one screen start behaving
    differently.
- **S5 — legacy background detection. — CODE LANDED (2026-08-11); on-device verification pending.** Edge
  sampling in the parser, resolved into the background layer rather than written into the recipe. See the section
  above for the two things building it corrected: L1 never implemented this, and the "invisible until the
  foreground moves" promise had to be *made* true by declining rounded and shadowed icons rather than assumed.
- **S6 — effects. — DONE (2026-08-11) as opacity, blend, colour and gradient; shadows deferred with reason.**
  - **Opacity and blend went in *with* the colour group rather than before it**, which reverses this plan's own
    ordering, because they turn out to be one mechanism: all three are a single paint applied as the layer joins
    the stack. Splitting them would have meant building that paint twice.
  - **The paint is applied at the join, not while the content is drawn.** A blend mode has to mean "against
    everything beneath this layer", and inside a layer's own bitmap there is nothing beneath. The live path needs
    one extra thing for the same reason — the whole stack composites **offscreen**, or a `MULTIPLY` on the bottom
    layer would multiply against the studio canvas instead of against nothing.
  - **`LayerFilter` joins the shared set**, beside `LayerTransform`: one colour-matrix derivation for both
    renderers. It is free to share because Android's and Compose's `ColorMatrix` are each a row-major
    `FloatArray(20)`, so neither side converts anything. Unit-tested by pushing colours through the matrices
    rather than asserting on entries — the question is whether saturation 0 greys a pixel, not what row 1 holds.
  - **Recolouring is one `LayerEffect.Color`, not four effects.** Hue, saturation, brightness and tint compose
    into a single matrix in a fixed order, so four list entries would mean their *order* silently changed the
    result — a way to be wrong this shape does not have. Monochrome is `saturation = 0` plus a tint rather than a
    variant of its own (and is a different thing from `LayerSource.AppDefaultMonochrome`, which swaps in artwork
    the *app* ships).
  - **The schema promise held**: the spec gained two fields and the slice test asserting the exact stored JSON of
    `IconLayerSet.Base` still passes, because defaults are not encoded.
  - **The gradient overlay landed next**, and was genuinely additive as predicted: a `LayerEffect.Gradient`
    variant, `LayerGradient` joining the shared set (which way an angle runs is pure convention, and therefore
    exactly what two renderers drift on), and source-atop in both paths so it colours the artwork rather than
    covering the icon with a rectangle. Its **strength doubles as the on/off switch** — at zero the effect is
    identity and is dropped from the list, so there is no toggle to disagree with the slider.
  - **The per-layer order is content → shape mask → gradient → composite**, the same on both sides for
    different-looking reasons: statement order in one function, modifier nesting in the other.

  ### Shadows are deferred (decided 2026-08-11), because they are the one effect that is not additive

  Every other effect has been a variant plus a few lines per renderer. A shadow is not, because it derives from
  the layer's **finished silhouette** — after the transform and after the shape mask, since an outer shadow has to
  extend beyond the shape and must not be clipped by it. The baked path has that silhouette as a bitmap and can
  blur it with `BlurMaskFilter` on any API. The live path has *nodes*, and Compose's only blur is `RenderEffect`,
  which is **API 31+** against this project's `minSdk` of 26. No option is simultaneously cheap, live, and
  identical on every device: gating the effect on API 31+ denies it where the bake could manage it, blurring via
  `RenderEffect` makes the editor lie below 31, and rasterising in the live path re-bakes a shadowed layer per
  frame while its sliders move.

  **So S6 stops at opacity, blend, colour and gradient.** Nothing else in this plan is waiting on shadows: the
  effect list takes a new variant with no schema change and no reshape of either renderer, so the decision costs
  only the effect itself and can be revisited whenever one of the three trades becomes acceptable — most likely by
  `minSdk` reaching 31, which retires the fork entirely.
- **S7 — custom image layers. — CODE LANDED (2026-08-11); on-device verification pending.** `CustomIconStore`
  plus the pick, in both the studio's own layers and as a replacement for an app's foreground or background.
  - **Nothing is written until Save**, which is the whole file-lifecycle design and the fix for the orphan leak
    L1 recorded and accepted. Decode and write are two steps: the path is *reserved* up front so the recipe can
    refer to an image that does not exist yet, the preview draws it from memory, and backing out leaves nothing
    behind because nothing was created. On Save the images go down **before** the recipe — a recipe pointing at
    an unwritten file would render as a missing layer, where a written file nothing points at is collectable.
  - **Orphans are swept, not deleted per action.** `retainOnly` asks "what does any recipe still refer to?", which
    is one question with one answer, against per-action deletes that have to be right at every site that can drop
    a reference (remove, reset, undo past a pick, replace an image) and leak invisibly when one is missed. It runs
    after a save and reads the *stores*, so it accounts for every other app's recipe rather than this screen's.
  - **No crop screen, unlike L1.** A layer already has offset, zoom and rotation, so a crop step would be a second
    and *destructive* way to do the same thing. Images are fitted into a transparent square on the way in instead,
    which also means the renderers need no aspect-ratio special case and so cannot disagree about one.
  - **The colour picker landed with it** (`MorphicColorPicker`, `core:designsystem`) — a saturation/value panel
    over a hue bar, and L1's is finally ported. It has **no alpha channel**, deliberately: every colour here sits
    somewhere that already carries opacity (a layer has one, a gradient has a strength), and `LayerEffect.Color`
    already states that a tint's alpha is ignored because two ways to set one thing is one too many. Hue is held
    as state rather than re-derived from the colour, because hue is undefined at black, white and every grey — a
    picker that recomputed it would jump under the user's finger the moment they dragged into a corner.
  - It replaced **three** near-identical swatch rows (solid fill, tint, gradient stops) with one `ColorField`.
    The swatches stayed alongside the picker rather than being replaced by it: swatches are how a colour is chosen
    *quickly*, the picker how one is chosen *exactly*, and making every black require a drag across a panel would
    have been slower for the common case in exchange for precision nobody wanted there.
- **S8 — icon packs. — CODE LANDED (2026-08-11); on-device verification pending.** `IconPackManager` (theme-intent
  detection, `appfilter.xml` parsing, component → pack drawable) behind `IconPackImages`, a seam `core:icon`
  declares on the consumer side so it never learns what a pack is; `LayerSource.IconPack` through both render
  paths; a chooser in the Source tab, **absent rather than disabled** when no pack is installed.
  - **The narrow `<queries>` block is the part that would have failed silently.** `queryIntentActivities` is
    subject to package visibility filtering on API 30+, so without it detection returns an empty list on every
    modern device — not an error. L1 never hit it because it held `QUERY_ALL_PACKAGES`, which this launcher does
    not request anywhere.
  - **Browsing a pack's drawables landed too**, and it turned out cheaper than this plan assumed. The deferral
    said it needed a `drawable.xml` lister; in fact the `appfilter.xml` we already parse and cache has drawable
    names as its **values**, so the list is a projection of data a pack loads anyway. `LayerSource.IconPack`
    gained a defaulted `drawableName` — no schema change, exactly as predicted.
    - **Individual mode only, and that is the model rather than a scoping call.** A named drawable on the global
      default is inherited by every app, so it would give all of them the same picture; the browser is absent in
      the global studio rather than offered and refused.
    - The grid decodes **only cells that scroll into view** (`produceState`, so a fast flick cancels rather than
      queueing thousands of decodes) over a bounded LRU in the manager, since a pack maps hundreds to thousands.
    - Still not carried: drawables the pack author shipped but mapped to **no** app, and the category grouping a
      `drawable.xml` holds. Both are additive on top of this.
- **S9 — presets (deferred).** A named `IconLayerSet` blob; the dashboard placeholder is already its slot.

Rationale for the order: persistence first because it is what everything sits on and what L1 got wrong four
times; the live path before the editor because it is the risky part and can be verified against the bake path
without any UI; the dashboard after the studio because a hub linking to a screen that does not work is untestable.

## Deliberately not ported from L1

- **MVI** (`ICON_STUDIO_UI_PLAN`) — forbidden by CLAUDE.md's plain-MVVM rule, and for L1's own demonstrated reason.
- **`kmp-showcase-compose`** — see above; label the tools instead. (**Haze *is* taken** — see the blur section.)
- **The skin / backing plate** (`ICON_SKIN_PLAN`) — stays deferred, as CLAUDE.md already records. Its
  custom-image half is *already* subsumed (that is just a custom layer below the background, which is what killed
  it in L1 one day after it was written). Only the **live backdrop plate** is left, and it cannot be baked into
  the layer bitmap, so it is a Compose layer around the icon and a genuinely separate feature.
- **`IconStyle` and `mergedWith`** — the flat style and its field-merge are the thing the layer set replaced.
  Not ported in any form, not even as a migration bridge (L1 kept `toLayerSet()` as a transitional converter; L2
  has nothing to convert *from*, because nothing shipped).
- **`foregroundUniform` / `normalize`** — L1's two foreground-consistency flags, kept fg-only through every
  revision. **Left out of this plan pending a consumer**: they exist to make foreground *content* visually
  consistent across apps, which is a real problem, but they are one more pair of fields on the spec and no part
  of the editor design above asks for them. Add them when the on-device look says they are needed — which is the
  "no model in a vacuum" rule, and cheap here because the effect list and the spec both take additive change.
