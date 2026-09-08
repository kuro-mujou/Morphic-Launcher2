# Onboarding Plan — a look in one tap, the rest when it is asked for

**Status:** design locked (2026-09-08, author-confirmed), **nothing built**. This is the *what and in what order*;
the open questions at the end are real.

**Covers:** what a fresh install does before the user has chosen anything, the one screen they meet, the preset
format that screen applies, and the "finish setup" hub that carries every deferred decision afterwards.

**Companions:** [SETTINGS_PORT_PLAN.md](SETTINGS_PORT_PLAN.md) — which deferred this ("onboarding flag: not a
setting; L2 has no setup wizard — defer"), and whose slice machinery the preset format is built out of.
[HOME_SETTINGS_HUB_PLAN.md](HOME_SETTINGS_HUB_PLAN.md) for the settings list this hub sits at the top of.
[WALLPAPER_STUDIO_PLAN.md](WALLPAPER_STUDIO_PLAN.md) → W6+ for the community seam a look becomes shareable through.

**L1 reference:** `../Morphic-Launcher` — `feature/shell/setup/` (3 files, ~500 LOC) and
`data/settings/preset/` (3 files, 116 LOC). It is an answer key for what to avoid, and is read that way below.

---

## The problem: what a fresh install is today

Three facts, and the first is the one that matters.

**1. The APPS surface is unreachable.** `SurfaceRegister.Default` binds **no edge**, and `SurfacePager` reads the
swipeable set straight off the map's keys — so on a fresh install there is no gesture anywhere that opens the app
list. The only route to it is Settings → Surface register → bind an edge, which is a thing a new user has no reason
to know exists. This is not an oversight; the default's own KDoc says so and names its owner:

> *Empty rather than "APPS somewhere sensible" on purpose. Which edge opens the app list is a **product** decision,
> and seeding one here would make this file the place it was decided — quietly, in the data layer, where nobody looks
> for product decisions. It belongs to whatever onboarding or default-preset step chooses it.*

**This plan is that owner.** Nothing below may push the decision back into `data:settings`.

**2. Nothing asks to be the launcher.** There is no `RoleManager` reference in the tree. The APK declares
`category.HOME`, so it appears in the system chooser — and that is the whole of it. A user who installs and opens
Morphic from their app list gets the launcher surface without it being their home app, and nothing says so.

**3. Home fills itself, silently and arbitrarily.** `HomeViewModel.seedIfEmpty` places the first `rows-1 × cols` apps
in whatever order `AppRepository` emits. That is a reasonable default and it stays — but it is *invisible*: nothing
tells the user this arrangement is theirs to change, and the dock beside it is empty.

Against that: `feature:settings` already holds ten sections, both studios, and live previews of most of it. **The
material is built. What is missing is the path into it.**

---

## What L1 does, and why this is not a port

L1's `SetupWizard` is Welcome → a picker of five presets *or* "Customize" → a 10-step linear interrogation →
a progress bar that bakes every icon before letting you in.

| What L1 does | Why it loses the user | What we do instead |
|---|---|---|
| 10 questions (`CustomStep`) in plain text, before anything has been seen | "Drawer search bar: Top / Bottom / Hidden" is unanswerable by someone who has not yet seen a drawer | **One question, answered by looking.** Live previews, not labels. Everything else moves past the first run |
| Presets named `iOS`, `Samsung`, `Xiaomi`, `Smart Launcher` | Names other people's products, and means nothing to a user who has not used them | Named for **what they are** — the arrangement, not the vendor |
| `Preset` is a flat data class of 14 chosen fields | A second place where settings shapes live, so it drifts — L1's already carries no icon, grid, wallpaper or effect field | A preset **is** the storage format (see below), so it cannot fall behind |
| `LoadingStep` bakes every icon behind a progress bar | Makes the first launch the slowest one the user will ever have | Icons bake lazily per cell, as they already do. **No prewarm gate** |
| `setupComplete` is a field on the settings blob | Not a preference; it rode along because there was one flow for everything | Its own slice, carrying the hub's state as well |
| Composables call `koinInject<SettingsRepository>()` and write directly | The house rule this codebase exists to enforce | A ViewModel per screen, as everywhere else |

**The one thing L1 gets right:** the preset picker is the *second* screen and the first is a single sentence. Even
its custom flow is escapable from step one. Keep the shape, delete the interrogation.

---

## The shape: three tiers

**Tier 0 — a launcher nobody has to configure.** The picker is unskippable, but only because every way out of it
applies something: there is no path that reaches home with no edge bound. A look is one tap, and "Not now" applies
the recommended one rather than leaving a launcher with no app list.

**Tier 1 — one screen, one question, answered by looking.** Four tiles, each a live render of the arrangement it
would apply. Tap → applied → home. No welcome page, no carousel, no progress bar.

**Tier 2 — the rest arrives later, in a hub.** A dismissible "Finish setup" card at the top of the settings list and
a row on the home surface menu, listing what has not been done — be the default launcher, pick a wallpaper, choose an
icon style, add a widget. Each row deep-links into the section that already exists. The card disappears when its list
empties.

**Progressive means the questions arrive when the user has context for them, not that they arrive in a queue.**

---

## Locked decisions (2026-09-08, author-confirmed)

**1. One first-run screen, then home.** Not zero (the moment a user is most willing to choose a look is the moment
they first see it) and not three (each extra question is answered with less information than the last).

**2. A preset is a sparse map of settings slice blobs, not a data class.** `SettingsSlice` already stores each
slice as JSON under a named key, decodes unknown fields away, and falls back to defaults on anything unreadable — so
a preset written as `Map<sliceName, json>` *is* the storage format. Three consequences, all of them the point:
it cannot drift behind settings the way L1's did; "save my current look" is a capture rather than a mapping; and a
look is shareable by construction, which is what W6+ community sharing will want. The schema-drift risk is the one
`SettingsSlice` was already built to absorb.

**3. A step's doneness is *derived*, never recorded.** Whether a wallpaper has been set, whether the icon recipe is
still `IconAppearance.Base`, whether we are the default home app, whether any widget is placed — each is a question
its own subsystem can answer. **Only dismissals and the completion flag are stored.** A recorded checklist is two
implementations of one fact kept honest by intention, which is the failure mode this codebase keeps rediscovering:
it would eventually tell a user to pick a wallpaper they had already picked.

**4. The hub lives in settings *and* on the home surface menu, and is one composable in both.** Settings is where
someone goes to look for it; the home menu is where they are when they wonder what else this thing does.

**5. No icon prewarm, no progress bar, no splash.** While the completion flag is unresolved the launcher composes
**nothing** — and on a transparent `windowShowWallpaper` window that is not a blank screen, it is the wallpaper,
which is what the launcher is about to show anyway. A splash would be an app's answer to a launcher's question.

**6. The default-launcher request is a hub step, not a first-run one.** It is a system dialog, it is the one step
that can fail for reasons outside the app, and its doneness is derivable — so it belongs where a failed or declined
step can sit visibly unfinished rather than blocking the way in.

---

## The preset format, and what a look may not carry

A look writes **the slices that describe an arrangement**, and never the slices that describe *your stuff*.

| Slice | In a look? | Why |
|---|---|---|
| `surface_register` | yes | The whole point — HOME's pairing, which edge opens APPS, in which layout |
| `surface_metrics` | yes | Grid counts, icon sizing, extents, padding — what makes "dense" differ from "airy" |
| `apps_chrome` | yes | Search placement and the category tab edge |
| `alphabet_strip` | yes | On/off and style; part of how a list-shaped look reads |
| `surface_paging` | yes | Wrap and remember-page |
| `backdrop_effect` | yes | The frost is a look |
| `icon_appearance` | yes | With `icon_applied_preset` stamped in the same write — see below |
| `icon_applied_preset` | carried | Never written *independently*. A look that sets a recipe re-stamps this, exactly as `setIconAppearance(preset =)` does, or the icon library marks a tile whose look is no longer on |
| `icon_presets` | no | The user's saved library. A look is not entitled to replace what they made |
| `icon_studio_background` | no | Workspace state — the paper, not the drawing |
| `icon_studio_workspace` | no | Same |
| `home_item_gestures` | no | Keyed by `GridItem`, i.e. by *this device's* installed apps. A shared look would carry gestures for apps the recipient does not have |
| `onboarding` | no | A look that could mark setup complete could also un-mark it |

**Built-ins are captured, not hand-written.** A dev-harness action serializes the current device's in-list slices to
a look file; the four shipped looks are made by configuring a device and pressing it. Hand-writing JSON for a format
whose defaults are deliberately *not encoded* would produce blobs that silently disagree with the settings screen.

**Applying is one transaction over the in-list keys**, and it writes only the keys the look carries: a look that
says nothing about the frost leaves the frost alone. That is what makes a look composable with the hub — picking a
wallpaper afterwards does not fight the look that was applied first.

---

## The four looks (names are an open question; the shapes are not)

| Look | HOME | Edge → APPS | Reads as |
|---|---|---|---|
| **Classic** | `PAGER_WITH_DOCK` | bottom → `PAGER` | The familiar Android home screen |
| **Library** | `PAGER_WITH_DOCK` | right → `CATEGORY_CARD` | Browse by category rather than by name |
| **Minimal** | `LIST_WITH_WIDGET_AREA` | bottom → `VERTICAL_LIST` | A short list of what you actually use |
| **Index** | `PAGER_WITH_DOCK` | bottom → `VERTICAL_GRID`, A–Z strip on | Everything, reachable by letter |

Three of four bind the **bottom** edge, which is deliberate: it is the one edge whose swipe no user has to be taught,
and a look is not the place to be clever about gestures. `Library` earns the right edge because a category browser is
a place you go, not a drawer you pull.

---

## Slices

**O1 — the gate.** The `onboarding` slice (completion flag + dismissed step ids), read by `app` to choose the start
destination; composes nothing while it is unresolved. A stub picker that applies `Classic` and completes. *Ships a
launcher that is never unreachable* — everything after this is quality.
**Verify on device:** fresh install → the wallpaper, then the stub, then a home screen whose bottom edge opens APPS.

**O2 — the look format.** `Look` (name + slice map), `LookRepository.apply`, the in/out list above enforced in one
place, and the capture action in the dev harness. Tests: an unknown slice name in a look is ignored; no out-list key
is ever written; a look carrying `icon_appearance` re-stamps `icon_applied_preset`; a look absent a key leaves the
stored value untouched.

**O3 — the preview lift.** The settings previews the tiles need move to `core:designsystem` on their **second
consumer**, which is the rule rather than a convenience: a mockup drawn independently in onboarding would be the
"two implementations kept honest by intention" hazard, and the user would choose a look that then rendered
differently. `feature:onboarding` must not depend on `feature:settings`.

**O4 — the picker.** `feature:onboarding` — its own module, its own `NavKey` (a module may declare its own; see
`Routes.kt`), a ViewModel, four live-preview tiles, "Not now" = apply the recommended look. Replaces O1's stub.

**O5 — the hub.** `SetupHub`: derived steps, stored dismissals, one composable used by the settings list header and
the home surface menu, gone when empty.

**O6 — the steps.** Default launcher (`RoleManager.createRequestRoleIntent(ROLE_HOME)`, `ACTION_HOME_SETTINGS`
below API 29; doneness by resolving the HOME intent), wallpaper, icon style, first widget — each deep-linking into
its existing section.

**O7 — start over.** A settings action that clears the `onboarding` slice and re-arms the picker. Small, and it is
how every slice above gets tested twice.

---

## Rejected

- **A prewarm gate** (L1's `LoadingStep`) — makes the first launch the slowest, to hide work that is already lazy.
- **Seeding `SurfaceRegister.Default` with an edge** — moves a product decision into the data layer, which its own
  KDoc argues against at length. The picker is unskippable instead.
- **A recorded checklist** — see locked decision 3.
- **Onboarding inside `feature:settings`** — it is a surface shown *before* home, not a section of settings.
- **A full theme bundle now** (settings + wallpaper recipe + icon recipe + widget template as one shareable object).
  It is the right long-term object and the slice-map format is deliberately a subset of it — but building it now
  makes onboarding wait on a format W6+ deferred, and the key-name seam lets the format grow later without a
  migration.

---

## Open questions

1. **The four names.** `Classic / Library / Minimal / Index` are placeholders. They must not name other vendors
   (L1's mistake), and they have to mean something to someone who has never used a third-party launcher.
2. **Do the tiles preview the user's *real* apps, or sample icons?** Real is far more convincing and is what makes
   the choice feel like a choice — but it needs the app cache warm at the first moment the app has ever run.
3. **Does the hub belong on the home surface menu at all,** or does that menu stay about *this surface* and the hub
   live only in settings? Decision 4 says both; it is the least-argued of the six.
4. **What happens to a look when the user has already customized?** "Start over" clears everything, but applying a
   look from settings later (a real want — it is how you try the others) overwrites the in-list slices silently.
   A confirm, a preview, or an undo?
5. **Does the picker eventually gain a fifth tile — "restore a shared look"?** That is the W6+ seam arriving, and it
   is the reason the format is a slice map rather than a script.
