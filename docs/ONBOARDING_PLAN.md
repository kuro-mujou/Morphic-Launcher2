# Onboarding Plan — a look in one tap, the rest when it is asked for

**Status:** design locked (2026-09-08, author-confirmed); **O1–O2 built** (2026-09-14), the rest not. This is the *what and in what order*;
the open questions at the end are real.

**Amended 2026-09-14 by a survey of four shipping launchers** — see "What shipping launchers do". The locked
decisions are unchanged; what the survey added is under "Amendments", **author-confirmed the same day**.

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

**2. Nothing asks to be the launcher.** ~~There is no `RoleManager` reference in the tree.~~ The APK declares
`category.HOME`, so it appears in the system chooser — and that is the whole of it. A user who installs and opens
Morphic from their app list gets the launcher surface without it being their home app, and nothing says so.

> **Answered ahead of the rest, 2026-09-12.** `O6`'s first step is built: `DefaultLauncherRole` in
> `data:apps` and a `SetupRow` above the settings index, drawn only while this launcher is not the home
> app. It was pulled forward because it is the one gap here a user hits on the first run and cannot work around, and
> because it needs none of `O1`–`O5` — its doneness is derived, so there is no slice to read and no gate to pass. The
> rest of this paragraph still stands: nothing says so *on the home surface*, which is `O5`'s half of decision 4.

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

## What shipping launchers do (surveyed 2026-09-14)

Four launchers walked from a fresh install on the emulator, every screen captured: Mur 1.3.3, Nova, Niagara, Smart
Launcher. Read the way L1 is read — for what to avoid as much as for what to take.

| | First-run questions | Default-launcher ask | Ends on |
|---|---|---|---|
| **Mur** | Welcome → five-card feature carousel (a permission toggle inside one card) → a page cross → **five** "Setup Appearance" steps over one shared live preview | A button on the last screen | "All set" with an analytics opt-in, then home |
| **Nova** | Welcome (with *Restore backup* and *Skip setup*) → three pages of settings rows: search, app drawer, icon shape | The **first** thing after "Get started", and again on "Go home" | A paywall, then home |
| **Niagara** | A terms screen → **one** question: pick 4–8 favorites from the real app list | An explainer (what switching does, how to switch back) → the system dialog → a calm sheet if declined | "Give it a few days", a paywall, then its real home with **one** tooltip |
| **Smart Launcher** | A tagline carousel → one screen of permission toggles, **on by default**, beside a terms checkbox | A dismissible card on its home | Home |

**What the survey says about this plan.**

- **The one-screen shape holds.** The two that ask least (Niagara, Smart Launcher) still land on a usable home; the two
  that ask most ask what cannot be judged yet. Mur's layout-scale step went 1.0× → 1.2× with no visible change in its
  own preview, and Nova's steps are settings rows under a progress bar — L1's interrogation, with a picture or without.
- **One large live preview reads better than small tiles.** Mur's is the user's real apps, re-rendering the moment a
  theme is picked, with the choices listed beneath it. Evidence for open question 2, and for A1.
- **None of them teaches the gesture into the app list** except Mur's page cross, which asks for a model of edges before
  a single swipe has been made. Niagara's one tooltip over the real home is the lighter answer, and it sits where the
  gesture actually happens. A2.
- **Two show a marketing carousel, two a paywall, two a terms gate.** None of them is a question the user came to
  answer. See Rejected.
- **The default-launcher ask ranges from first (Nova, twice) to last (Mur, Smart Launcher).** Niagara's is the one
  worth reading: it says what switching does and that the old home screen survives, *before* the system dialog. A3.

---

## The shape: three tiers

**Tier 0 — a launcher nobody has to configure.** The picker is unskippable, but only because every way out of it
applies something: there is no path that reaches home with no edge bound. A look is one tap, and "Not now" applies
the recommended one rather than leaving a launcher with no app list.

**Tier 1 — one screen, one question, answered by looking.** Four tiles, each a live render of the arrangement it
would apply. Tap → applied → home. No welcome page, no carousel, no progress bar. A1 sets how
this screen is drawn, and A2 what home shows next.

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

> **Amended by the author, 2026-09-14: the launcher also asks on its own.** When the launcher surface resumes while
> it is not the home app, `DefaultLauncherPrompt` (`feature:shell`) puts up a dialog — at most once every three days,
> stamped in `default_launcher_ask` when shown. It stays a request rather than a gate: declining leaves the launcher
> usable and the settings row in place. It is what a user sees when the role is lost after they chose it, which the
> hub alone would leave for them to notice.

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
| `default_launcher_ask` | no | When this install last asked to be the home app — about the device, not the look |
| `home_item_gestures` | no | Keyed by `GridItem`, i.e. by *this device's* installed apps. A shared look would carry gestures for apps the recipient does not have |
| `onboarding` | no | A look that could mark setup complete could also un-mark it |
| `home_gestures` | no | HOME's swipe actions name this device's apps and shortcuts, as `home_item_gestures` does |
| `orientation_settings` | no | Its independent-layout flag is only correct changed together with placement writes `data:layout` owns |

**Built-ins are captured, not hand-written.** `LookCaptureHarness` (`app`'s instrumentation tests) serializes the current device's in-list
slices to a look file; the four shipped looks are made by configuring a device and running it. Hand-writing JSON for a format
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

## Amendments (2026-09-14, from the survey — author-confirmed)

Each names the slice it lands in. None reopens a locked decision: A1 changes how decision 1's one screen is drawn, and
A3 sequences decision 6's amendment against the gate.

**A1 — one preview and four named rows, not four tiles.** Four screen-shaped tiles do not fit a phone at a size worth
choosing from: a 2×2 of 20:9 tiles 160dp wide is ~720dp tall before a title or a "Not now". That is the reason
`SurfaceRegisterCross` already gives for dropping its mockups — at small sizes a mockup is a smudge. Mur's shape answers
it: one large live preview, the four looks as named rows beneath, a tap on a row re-renders the preview, and a confirm
applies. Three of the four looks differ mainly in *the surface behind an edge*, so the preview shows HOME **and** that
surface — a crossing that plays when a row is picked is the natural form, and it teaches the gesture the look binds.
It costs a second tap against tier 1's one, and buys seeing a look before it is applied, which a tile applied on tap
never offered. *Lands in O4; O3's lift is unchanged — one preview needs the same renderers four tiles would.*

**A2 — land on home with one hint naming the bound edge.** After a look is applied, HOME shows a single hint at the edge
it bound ("Swipe up for your apps"), read from `surface_register` so it cannot name an edge that is not bound. It goes
on the first crossing to any side surface, or when tapped away — a **dismissal**, which decision 3 already allows
storing, in the `onboarding` slice. One hint, never a tour. It must be announced to TalkBack: a swipe is not something a
screen-reader user finds by exploring, and those are the users with no other route to the app list. *New slice O4b.*

**A3 — the default-launcher ask: after home has been seen, and said honestly.**
- *Sequence.* `DefaultLauncherPrompt` sits in the shell above every surface and fires on resume, so on an install
  opened from the app list it would land on top of the picker, or on A2's hint. It should stay down while the gate is
  open and through the resume that closed it; the first ask is on a later resume. Nova's ask-before-anything, repeated
  on the way out, is the pattern this avoids.
- *Copy.* Today it says "Morphic isn't your default launcher, so pressing home opens a different one." Add the two things
  Niagara's explainer says and ours does not: the current home screen keeps its layout, and this can be changed back in
  system settings. Both are true, and both answer the worry that stops the tap.
*Lands in O4b (the sequence) and O6 (the copy).*

**A4 — a fresh install is not an empty store.** `app` declares `allowBackup="true"` with no `dataExtractionRules`, so
Android's Auto Backup restores the DataStore file and the Room database on a new device before the first launch.
*Not yet seen on a device — confirm with `adb shell bmgr` before O1 relies on it.* Three consequences:
- A restored completion flag skips the picker. That is correct, and O1 should say so rather than discover it.
- `default_launcher_ask` is restored too, so a new phone — where Morphic is certainly not the home app — waits out the
  **old** phone's cooldown before asking. The preset table already calls that slice "about the device, not the look";
  it should be excluded from backup for the same reason.
- Restored home placements name apps Play has not reinstalled yet. Not onboarding's to fix, but O1's restored-install
  check is where it would first show.
*Lands in O1.*

**A5 — real apps in the preview, blank cells while the cache is cold.** Answers open question 2. Mur drew the user's own
apps on the first screen of a fresh install, so a warm-enough cache at first run is achievable in practice. Until
`AppRepository` has emitted, the preview draws empty icon-shaped cells — never sample brand icons, which would show a
home screen the user does not have. *Lands in O4.*

**A6 — "Fill your dock" as a hub step.** `HomeViewModel.seedIfEmpty` leaves the dock empty on purpose, and its KDoc
names what it waits for: dock apps chosen "with a picker" rather than guessed. Niagara's favorites step is that picker,
and it is the one first-run question in the survey about the user's own things rather than configuration — which is
why it belongs in the hub, where it can wait, and not beside the look. `AppPicker` (`core:designsystem`) exists.
Doneness is derived: the dock zone of the current arrangement holds any placement. Shown only while HOME is
`PAGER_WITH_DOCK`. *Lands in O6.*

---

## Slices

**O1 — the gate.** The `onboarding` slice (completion flag + dismissed step ids), read by `app` to choose the start
destination; composes nothing while it is unresolved. A stub picker that applies `Classic` and completes. *Ships a
launcher that is never unreachable* — everything after this is quality.
**Verify on device:** fresh install → the wallpaper, then the stub, then a home screen whose bottom edge opens APPS.
Then a **restored** install (A4 — the restored flag skips the stub, and nothing carried from the old phone holds back
the default-launcher ask).

> **Built 2026-09-14, and four things differ from the paragraph above.**
> - **A gate, not a start destination.** `OnboardingGate` wraps `LauncherNavHost` in `MainActivity`. As a `NavKey` the
>   first-run screen would be the stack's bottom entry — the home button's `goHome` pops *to* it — and finishing would
>   need a `resetTo` the `Navigator` does not have. Outside navigation, finishing swaps what is composed, and O7 becomes
>   one write.
> - **`feature:onboarding` exists from O1**, holding the stub, because a stub in `app` is the prototype-then-extract
>   that CLAUDE.md rules out.
> - **The slice holds `completed` only.** Dismissed step ids arrive with their first reader (O4b, O5).
> - **An absent flag on an install with a stored surface register reads as completed** (`OnboardingResolution`).
>   Otherwise every install configured before the flag existed — and every restore of a backup taken before it — would
>   meet a first-run screen whose one action overwrites the register. A stored `completed = false` still opens the gate.
>
> **A3's sequencing moved to O4b.** While the gate is open the shell is not composed, so `DefaultLauncherPrompt` cannot
> appear over the first-run screen; holding it past the resume that closed the gate matters only once there is a hint
> for it to land on.

**O2 — the look format.** `Look` (name + slice map), `LookRepository.apply`, the in/out list above enforced in one
place, and the capture action in the dev harness. Tests: an unknown slice name in a look is ignored; no out-list key
is ever written; a look carrying `icon_appearance` re-stamps `icon_applied_preset`; a look absent a key leaves the
stored value untouched.

> **Built 2026-09-14.** `Look` is opaque outside `data:settings` — read from a file or captured, never assembled — and
> `LookScope` is the in/out list, held to every slice by `LookScopeTest`; a slice left out of `SettingsSlices` fails at
> startup. Four decisions the paragraph above did not make:
> - **Capture is an instrumentation harness, `LookCaptureHarness` in `app`**, because the in-app dev harness is gone and
>   only a process of the launcher can read its store. It runs through `am instrument`: `connectedDebugAndroidTest`
>   uninstalls the app when it ends, and with it the arrangement being captured.
> - **A capture spells out every carried slice, defaults included.** A sparse capture would land differently on every
>   device, leaving whatever the receiver held in each slice the capturing device never touched. Applying stays
>   sparse, so a curated look can still say less.
> - **`home_gestures` and `orientation_settings` are excluded** — the two rows added to the table above.
> - **Applying is safe only before HOME is arranged.** `surface_metrics` can shrink a grid, and a shrink needs
>   `data:layout` to re-home what it displaces. First run applies before anything is placed; open question 4's
>   apply-from-settings owes that companion write.

**O3 — the preview lift.** The settings previews the tiles need move to `core:designsystem` on their **second
consumer**, which is the rule rather than a convenience: a mockup drawn independently in onboarding would be the
"two implementations kept honest by intention" hazard, and the user would choose a look that then rendered
differently. `feature:onboarding` must not depend on `feature:settings`.

**O4 — the picker.** `feature:onboarding` — a ViewModel, one live preview over four named rows (A1; A5 for what it
draws), "Not now" = apply the recommended look. Replaces O1's stub, inside O1's gate.

**O4b — the edge hint.** A2: one hint on HOME naming the edge the applied look bound, stored as a dismissal in the
`onboarding` slice. It carries A3's sequence too: `DefaultLauncherPrompt` waits past the resume that closed the gate,
so it never lands on the hint. **Verify on device:** apply each look in turn; the hint names that look's edge, is announced
by TalkBack, and is gone after one crossing.

**O5 — the hub.** `SetupHub`: derived steps, stored dismissals, one composable used by the settings list header and
the home surface menu, gone when empty.

**O6 — the steps.** Default launcher (`RoleManager.createRequestRoleIntent(ROLE_HOME)`, `ACTION_HOME_SETTINGS`
below API 29; doneness by resolving the HOME intent), wallpaper, icon style, first widget, fill your dock (A6) — each deep-linking into
its existing section. The default-launcher dialog's copy, and when it may first appear, are A3.

**The default-launcher step is built**, out of order and alone — see the note under finding 2. What exists is
`DefaultLauncherRole` (both mechanisms, both API branches, doneness by resolving HOME) and one `SetupRow` rendered
by `SettingsList` above the index, fed by `SettingsShellViewModel.defaultLauncherRequest` and re-derived on every
`ON_RESUME`. Three things it deliberately does **not** have, all of which belong to `O5`: it is not dismissible (a
step that cannot be dismissed needs no stored id, and this is the one step nobody should be able to hide), it is not
on the home surface menu, and it is not in a card with other steps. The state is a single nullable `Intent` — non-null
means "ask, like this" — which is the shape `O5` turns into a list. Nothing here has to be deleted to build the hub;
the row moves into it.

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
- **A welcome page or feature carousel** (Mur, Smart Launcher). Tier 1 already excludes them; the survey adds that
  Mur's "Next" skipped a card nobody had opened, so a carousel's content is not even reliably seen.
- **A terms screen or an analytics opt-in** (Niagara, Smart Launcher, Mur). `PrivacyPolicy.kt` says the app collects
  nothing and holds no `INTERNET` permission; a consent step would suggest the opposite. The policy stays in About.
- **A paywall in the first run** (Nova, Niagara). Nothing is sold, and if that changes it is not onboarding's to show.
- **A permission request in onboarding** (Mur's notification toggle inside a feature card; Smart Launcher's three
  toggles, on by default). A permission is asked where its feature is turned on — `GestureServicePrompt` is the model.
- **A settings wizard** (Nova's pages of rows, Mur's five appearance steps). The screens exist; walking them in order
  before home is L1's `CustomStep` with better styling.
- **A page cross in the first run** (Mur). The right *settings* screen and the wrong first question. A1's crossing and
  A2's hint teach the same thing by showing it.
- **A sheet after the system chooser is declined** (Niagara). The settings row and the next due ask already are the
  "try again"; a second screen on top of a declined system dialog is a nag.
- **Asking to be default before anything has been seen** (Nova). See A3.
- **A multi-step tour.** One hint (A2) is the ceiling.

---

## Open questions

1. **The four names.** `Classic / Library / Minimal / Index` are placeholders. They must not name other vendors
   (L1's mistake), and they have to mean something to someone who has never used a third-party launcher.
2. **Do the tiles preview the user's *real* apps, or sample icons?** Real is far more convincing and is what makes
   the choice feel like a choice — but it needs the app cache warm at the first moment the app has ever run.
   *Proposed answer: A5.*
3. **Does the hub belong on the home surface menu at all,** or does that menu stay about *this surface* and the hub
   live only in settings? Decision 4 says both; it is the least-argued of the six. *Survey: Smart Launcher's
   default-launcher card on its own home is the nearest shipped equivalent, and being closable is what keeps it
   from reading as clutter — mild evidence for both.*
4. **What happens to a look when the user has already customized?** "Start over" clears everything, but applying a
   look from settings later (a real want — it is how you try the others) overwrites the in-list slices silently.
   A confirm, a preview, or an undo?
5. **Does the picker eventually gain a fifth tile — "restore a shared look"?** That is the W6+ seam arriving, and it
   is the reason the format is a slice map rather than a script. *Survey: Nova puts "Restore backup" as a quiet link
   on its first screen rather than as a choice beside the others — the placement to copy. Moving to a new phone is Auto
   Backup's job (A4), so this link would only ever be for a shared look.*
6. **Does the list-shaped look get a favorites picker?** `Minimal` seeds `home_list_item` from the grid's reading
   order, which is whatever `AppRepository` emits first — and a list is the look whose whole promise is *what you
   actually use*. Niagara, a list launcher, makes that pick its only first-run question. Asking it here is a second
   first-run screen for one look; not asking leaves that look's home arbitrary. A6 covers the dock; this is the list's
   version, and it is undecided.
