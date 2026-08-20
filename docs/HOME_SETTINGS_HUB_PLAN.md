# Home Settings Hub Plan — one entry per surface

**Status: built** (H1–H3, 2026-08-19), verified on an emulator in both single- and two-pane. Follows
[SETTINGS_PORT_PLAN.md](SETTINGS_PORT_PLAN.md), which is complete — this is not a port. Neither L1 nor this plan's
predecessor answered the question below; both split HOME the way this stopped splitting it. Three things came out
different from the plan and are recorded under *What actually happened*.

---

## The problem

The settings list applies **two different rules** to two surfaces:

| surface | rows in the list | split by |
|---|---|---|
| APPS | one (`APPS`) | nothing — five arrangements live behind a chip row inside it |
| HOME | two (`HOME_GRID`, `DOCK`) | **zone** — main area in one row, side zone in another |

`AppsDetail`'s own KDoc states the rule it follows: *"the layouts differ only in arrangement, so what a user
configures is 'the paged one' or 'the list'"* — one row, because there is one **surface**. HOME breaks that rule and
splits by zone instead, which is a level below the one the list is organized at.

The code already records the discomfort. `SettingsSection.meta` takes a `HomeLayout` **for exactly one reason**:

```kotlin
SettingsSection.DOCK -> if (isList) {
    SettingsSectionMeta("Widget area", "Size and grid", Icons.Outlined.Widgets)
} else {
    SettingsSectionMeta("Dock", "Height, grid and icons", Icons.Outlined.Dock)
}
```

Two rows rename themselves under the user as a setting *in a different section* changes. A row that changes name is a
row that is not naming a stable thing — it is naming half of a pairing, and the pairing is the thing.

And the surface register already says as much. `HomeLayout` is one enum precisely so illegal pairings are
unrepresentable: `PAGER_WITH_DOCK` and `LIST_WITH_WIDGET_AREA` are **coupled** main-area + side-zone combos. The
settings list is the one place in the codebase that takes that couple apart.

### What L1 does (nothing better, but one thing worth taking)

L1 is *more* fragmented, not less — six surface rows where L2 has four:

```
LAYOUT_REGISTER  HOME  APP_DRAWER  APP_LIBRARY  DOCK  FOLDER
```

and it renames `DOCK` → "Widget area" through a `displayMeta(homeSurface)` of its own, so the instability above is
inherited rather than invented. L2 already fixed the APPS half of this by deleting `feature:appdrawer` +
`feature:applibrary`; this plan is the same collapse applied to HOME.

**The one thing L1 gets right and L2 moved away from:** L1's pairing switch lives **in its Home section**, as a scroll
row of two mockup cards (`SettingsPreviewOptionScrollRow`, "Classic" / "Minimalist"), with the rest of the section
conditional on the selection. L2 moved that switch to the register's HOME card (`HomeLayoutPicker`) when the second
pairing landed. See Decision 1 — this plan moves it back, and the fact that L1 had it there is corroboration rather
than the argument.

---

## What "one entry per surface" means, and what it does not

The list gets **one row per surface**. What is *inside* a row differs, because the surfaces differ:

- **APPS is one surface with one zone**, arranged five ways. A chip row selects which arrangement to configure and
  every control below addresses it. No hub needed, and none is proposed. **APPS is not touched by this plan.**
- **HOME is one surface with two zones**, paired two ways. Two zones cannot share one set of controls (a main area and
  a dock have different extents, different grids, and one of them may hold no icons at all), so HOME needs a level
  that APPS does not: a **hub** that picks the pairing and then routes to a zone.

That asymmetry is honest and worth stating plainly, because "make HOME look like APPS" would otherwise be read as
"give HOME a chip row and one long pane", which is Decision 3's rejected option.

---

## Decision 1 — the segmented button *switches* the pairing, and becomes the only switch

APPS' chip row **writes nothing**, deliberately: which layout a user gets is per home edge, so a user can genuinely
have `VERTICAL_LIST` on the left edge and `PAGER` on the right, both live at once. Configuring one you are not looking
at is a real act there.

**HOME has no such state.** There is exactly one HOME and exactly one pairing in force, always — `HomeScreen` is a
`when` over a single `HomeLayout`. So:

- A selector that only *selects* would spend the screen configuring a home that does not exist, and would sit one
  section away from `HomeLayoutPicker`, which looks identical and *does* switch. **Two controls with the same two
  options, one live and one not, is worse than either alone.**
- A selector that *switches* is one control for one setting, in the section named after the thing it changes.

So the segmented button switches, and **`HomeLayoutPicker` is deleted**. The register's HOME card returns to
**gear-only** — the state it was in before the second pairing existed, which `SurfaceRegisterCross` already supports
(its body is drawn from a nullable action, and HOME's was null).

**This reverses a call already on record, and that call was made with L1 in view.** `SurfaceRegisterCross`'s own KDoc
says it: *"L1 put its two-way choice in the Home section instead. Here the register is where 'what is HOME' is
answered"* — so L1's placement was seen and rejected, and citing it above is corroboration, not new evidence. The
reason to reverse anyway is not that the earlier reasoning was wrong — a pairing genuinely is *"what goes in this
slot?"*, which is what the cross asks — but that once **Home** is the one screen where home is shaped, having *"which
home?"* answered in a different section is the exact split this plan exists to close, moved up a level rather than
removed. The register keeps what it is for: **where** surfaces are, one card per edge, HOME in the middle with a gear.

**Alternative kept on record:** the button selects and does not write, mirroring APPS exactly, and `HomeLayoutPicker`
stays in the register. Everything else in this plan works unchanged. Take this if the register cross is judged to be
the better home for *all five* "what is bound here?" answers, and accept that the Home section then shows a pairing
that may not be the one running.

### What it looks like

A `MorphicSegmentedButtons` of two, over **one large mockup of the selected pairing** — not L1's row of two small
mockup cards. Reasons, in order:

- The mockup has a second job: it is the picture of **both zones at their real proportion**, which is what makes the
  two rows below it legible as *"the top part"* and *"the strip at the bottom"*. `GridEditor`'s companion split already
  draws exactly that (main area + companion zone, on the side `CompanionSide.of(edge)` names), and with both bounds
  null it draws the frame alone with no buttons — which `AppsDetail` already does for the list.
- Showing the **selected** pairing large beats showing both small. The register cross rejected mockups at 88dp because
  *"at 88dp a mockup is a smudge, five at once turn a picker into a wall of texture"*; that reason does not transfer to
  one mockup at the width of a pane, and the opposite reason applies — this is the one screen where the shape of home
  is the subject.
- The user asked for the segmented control and it is the right control: two mutually-exclusive options is what it is
  for, and the register section's own finding (*"chips beat the segmented control"*) was about **six** options per
  edge.

---

## Decision 2 — not a pager

The proposal under discussion was a `HorizontalPager` of two pages under the segmented button. Rejected, three reasons,
the third fatal:

1. **It is the segmented button twice.** Two bound controls for one selection is legitimate as a tab strip, but it buys
   nothing here and doubles the ways to change one thing.
2. **Gesture conflict.** Every page holds sliders and a `GridEditor`; horizontal drags are already spoken for, and a
   pager would compete with both.
3. **The pager must have a height and the content has not got one.** `SurfaceDetail` is a `LazyColumn` whose
   `stickyHeader` pins the icon group and its live preview, inside `PunchThroughPane` — an offscreen layer with
   overscroll nulled. Nesting that in a bounded pager gives a scroller inside a scroller: the pin has nothing to pin
   against, and *"shared settings below the pager"* is precisely what forces the pager to be bounded. `PunchThroughPane`
   documents the related failure — a stretch re-composites the scrolling content and the icon preview's punch stops
   reaching the window while it lasts.

A pager is only viable when its pages are **short**, i.e. rows rather than settings — which is what Smart Launcher's
home screen actually is. And once the pages are two rows each, the pager is an elaborate way to swap two rows: they are
**mutually-exclusive states, not siblings you page between**, so `AnimatedContent` says the same thing for nothing.

---

## Decision 3 — rows that navigate, not both zones inline

The cheap alternative is one long pane: segmented button, then the main zone's controls, then the side zone's, then
shared. **Rejected because two zones means two icon groups**, and `SurfaceDetail` can pin exactly one — the sticky
header is a single block of (heading + live preview), and two of them in one `LazyColumn` fight for the pin. The icon
controls are only legible *through* their preview; a pane where the second group's preview scrolls away is a pane where
half the controls say nothing.

So the hub holds **two rows that open the panes that already exist**. `HOME_GRID` and `DOCK` stay in the enum, stay
mapped in `SettingsDetail`, and stay exactly the panes they are — they come out of `settingsGroups` and gain a parent.

The mechanism is already built and proven: `onOpenSection(section, layout)` is how the register's gear jumps into the
APPS pane from inside another pane.

---

## Target shape

```
Settings                              Home
──────────────                        ──────────────────────────────────────
 Personalization                       [ Pages + dock │ List + widget area ]
   Wallpaper                                   ↑ switches HomeLayout
   Effects
   Icons                                ┌────────────────────┐
 Layout                                 │                    │   the selected pairing,
   Layout        (register)             │                    │   both zones at real
   Home          ← one row now          │                    │   proportion, no buttons
   Apps                                 │▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│   (GridEditor, bounds null)
   Folders                              └────────────────────┘

                                         ›  Grid       Rows, columns and icons
                                         ›  Dock       Height, grid and icons
                                        ──────────────────────────────────────
                                        (shared — empty today, see below)
```

Switching the segment swaps the mockup's shape **and** the two rows, in place, through one `AnimatedContent`. On the
other pairing the rows read **List** (row height and icons) and **Widget area** (size and grid) — the strings
`SettingsSection.meta` already produces.

Navigation depth becomes, on a phone: list → Home hub → Grid. On a tablet: list | (hub, then hub replaced by Grid in
the detail pane).

---

## What changes

| file | change |
|---|---|
| `SettingsSection.kt` | add `HOME` (the hub); `HOME_GRID` + `DOCK` leave `settingsGroups` but stay in the enum; add `parent: SettingsSection?` and `depth` |
| `SettingsSection.kt` | `meta(homeLayout)` keeps the pairing argument — the *rows in the hub* and the *pane titles* still need it; the **list** stops changing under the user |
| `home/HomeDetail.kt` *(new)* | the hub: segmented button, mockup, two rows |
| `SettingsScreen.kt` | map `SettingsSection.HOME → HomeDetail(onOpenSection)`; single-pane back walks `parent`; two-pane gains an `onCloseChild`; the slide direction reads `paneDepth` |
| `home/HomeHubViewModel.kt` *(new)* | reads the pairing and the side zone's stored extent, writes the pairing. See *What actually happened* — the plan put the write on `SettingsShellViewModel`, which stayed read-only |
| `component/SettingsNavRow.kt` *(new)* | the list's row, extracted on its second consumer so the hub cannot name a zone differently from the way the list does |
| `register/HomeLayoutPicker.kt` | **deleted** |
| `register/SurfaceRegisterCross.kt` | HOME's card body becomes null again — gear only, which the card already supports (`onClick` is nullable) — and its gear repoints from `HOME_GRID` to `HOME`, so it lands on the hub rather than past it |
| `register/SurfaceRegisterViewModel.kt` | `setHomeLayout` removed (moved to the shell VM) |
| `grid/GridSizeDetail.kt`, `dock/DockDetail.kt` | **unchanged** |
| `apps/*` | **unchanged** |
| `LayoutLabels.kt` | `HomeLayout.label` shortened to "Pages + dock" / "List + widgets" — one vocabulary, not two |

Nothing in `data:settings`, `core:model` or any `feature:home` file changes. There is no storage change and no
migration: this is entirely where controls are *reached from*.

---

## The two real wiring costs

Both are small, and both are the kind that is silently wrong rather than broken:

1. **Single-pane back needs a parent.** `SettingsSinglePane` does `onCloseDetail = { selected = null }`, so backing out
   of the Grid pane would skip the hub and land on the list. Add `SettingsSection.parent` (a property, not a stack —
   the hierarchy is one level deep and has no reason to grow) and close to `selected = selected?.parent`. A stack is
   the wrong shape here for the same reason a `NavKey` per section was: these are panes.
2. **Tablet highlight.** With `HOME_GRID` out of `settingsGroups`, `selected` can be a section the list does not
   contain, and `SettingsList` would highlight nothing. Highlight `selected.parent ?: selected`.

One thing to watch that is *not* a cost: the `AnimatedContent` transition spec in `SettingsSinglePane` keys on
`targetState != null` to decide slide direction. With a hub in the middle, going hub → Grid and hub → list both have a
non-null target, so the *back* direction would animate forwards. Compare depth (`parent` chain length) rather than
nullness.

---

## The shared group is empty today, so it is not built

Every control in the two panes is zone-scoped:

| control | scope |
|---|---|
| grid rows/cols, row height | main zone |
| extent (height/width) | side zone |
| side margin | **per zone** — each stores its own, which is why one slider cannot inset both |
| infinite scroll | main zone, and **pager pairing only** |
| icon sizing | per zone |

Nothing is shared between the pairings. Smart Launcher's *"Add new apps to home screen"* has no counterpart here —
nothing auto-places installed apps.

So the hub ships with **no shared block**, by the rule this codebase already states in three places: *a control appears
when the thing it configures exists* (which is why `transition` still has no control, and why unbuilt menu verbs are
absent rather than disabled). An empty "Shared" heading would be the settings-list equivalent of L1's grayed-out
"Rename".

The block appears with the first genuinely-shared setting. Candidates, none of which exist: auto-add on install, page
management, HOME orientation.

---

## Phases

Small enough to be one commit each, in this order. Each ends with something working on device.

- [x] **H1 — the hub, with a working switch.** `SettingsSection.HOME` + `parent` + `depth`, the `HomeDetail` pane,
      `HOME_GRID` + `DOCK` out of `settingsGroups`, `SettingsNavRow` extracted from `SettingsList`. Single-pane back,
      the two-pane highlight, and the slide direction. The register's picker still worked at this point, which is what
      made the slice checkable by comparison.
- [x] **H2 — the duplicate removed.** `HomeLayoutPicker` deleted, register HOME card back to gear-only (and its gear
      repointed at the hub), `SurfaceRegisterViewModel.setHomeLayout` removed. After this there is exactly one control
      for the pairing.
- [x] **H3 — the mockup.** The `GridEditor` frame with both bounds null and the companion split at real proportion, so
      the segmented button visibly changes the shape of home. This is the slice that grew `HomeHubViewModel`.

**Deferred with reasons:** the shared block (nothing to put in it, above); giving the hub its own live icon preview
(the zone panes own theirs, and a hub previewing icons would be a third opinion about the same cells); an
`AppsDetail` hub (APPS has one zone, so its chip row is already the right shape).

---

## What actually happened

Four departures from the plan above, kept here because each was a decision rather than a detail.

- **The switch was wired in H1, not H2, and H2 became purely the removal.** The plan shipped H1 with the segmented
  control inert so a regression would be visible against the register's picker. On device an inert control does not
  read as staged work — it reads as broken, and it left the slice with nothing to test. The comparison the staging was
  for survives anyway, since the picker is not deleted until H2. Each commit is still coherent: H1 *adds* the hub, H2
  *removes* the duplicate.
- **The hub got a ViewModel, where the plan put `setHomeLayout` on `SettingsShellViewModel`.** H3 is what forced it:
  drawing the companion zone at its *real* proportion needs the side zone's **stored** extent, which is a read keyed
  by slot × device over two flows — a section's shape, not a shell's. Drawing the blueprint default instead was the
  alternative, and a hub showing a 96dp dock one tap from a pane showing the 200dp one the user set is the exact
  contradiction a preview exists to prevent. `HomeHubViewModel` also puts the one writer in the section that owns the
  one control, so `SettingsShellViewModel` went back to being read-only, which is what it was.
- **Two-pane had no "up" at all, and the plan did not notice.** It listed the single-pane back and the two-pane
  *highlight*; what it missed is that `SettingsTwoPane`'s `BackHandler(onBack = onBack)` leaves settings outright — so
  from a zone pane, back would skip the hub that opened it. That was deliberate before there was a hierarchy (the
  detail is always showing beside the list, so there was nothing to close). It now takes an `onCloseChild`, null for
  every section that is a list row, which is the old behavior exactly.
- **`HomeLayout.label` got shorter rather than gaining a second, shorter form.** The segmented control is a two-up row
  on a phone pane, where "Pages with a dock" / "List with a widget area" wrap. Adding a `segmentLabel` would have been
  a second name for one concept — the fault `LayoutLabels` exists to fix — so the one name became "Pages + dock" /
  "List + widgets" everywhere, and the register card reads better for it. `HOME_GRID`'s own meta moved the other way,
  from "Home" to "Grid" / "List" with a glyph each, since it is a row *inside* a pane titled Home now.

One thing the plan predicted correctly and is worth keeping as a warning: the single-pane `AnimatedContent` really did
pick its slide direction from `targetState != null`, and really would have animated *back* as *forward* out of a zone
pane. It reads as depth going the wrong way, which is the kind of fault nobody files.

---

## Decisions on record

| decision | settled as | argued in |
|---|---|---|
| One list row per **surface** | HOME collapses to one row; APPS already is one | *The problem* |
| The pairing switch | **switches**, and lives in the Home hub; `HomeLayoutPicker` deleted | *Decision 1* |
| Register's HOME card | back to **gear-only**; the cross keeps *where*, not *what* | *Decision 1* |
| Switch control | segmented button over **one large** mockup, not L1's two small cards | *Decision 1* |
| Pairing content | swapped by `AnimatedContent`, **never a pager** | *Decision 2* |
| Zone controls | reached by **navigating rows**; the two panes are unchanged | *Decision 3* |
| Hub depth | `SettingsSection.parent`, not a back stack — these are panes | *Wiring costs* |
| Where the pairing is written | `HomeHubViewModel` — the section that owns the control | *What actually happened* |
| The mockup's extent | the **stored** one, not the blueprint default | *What actually happened* |
| Shared block | **not built**; a control appears when the thing it configures exists | *The shared group* |

---

## Smells not to reproduce

- **A row that renames itself is a row naming half a thing.** That was the tell here, and the fix was to stop having
  the row rather than to improve the name. Watch for it wherever `meta` takes an argument.
- **Two controls for one setting.** Whatever is decided in Decision 1, do not end with a segmented button in Home *and*
  a picker in the register. L1 shipped the launcher-wide version of this (`pager.infiniteScroll`: one flag, three
  pagers, one control, in the wrong section), and `SurfacePaging` exists because of it.
- **A scroller inside a scroller to make one screen hold two groups.** The pin and the punch both stop working, and the
  failure is a preview that quietly scrolls away rather than an exception.
