# Known issues

What is wrong or wanted in the launcher as it stands, and **not yet done**. One entry each: what happens (or what is
asked for), where it lives, and what it touches or depends on.

Two kinds of entry, kept in two lists because they are closed differently:

- **Defects**: something that **is** built and is wrong. Closed by making it right.
- **Requested changes**: something built that works as designed, where the design is now wanted to be different, plus
  the few pieces of new scope that came in with them. Closed by building it. This is often a *reversal* of a decision
  a KDoc or a design doc argues for, and when that is so the entry names the document. Change both together, or the
  doc goes on defending the old behavior.

This is still not a plan. A plan orders work and says how it is built; an entry here only records that it is owed.
Anything that grows a design moves to its own `docs/*_PLAN.md`, and its entry here shrinks to a link.

An entry says whether it was reproduced or only reported, and by what. "Reported" is not a weaker claim about the
bug — it is an honest one about the evidence.

Everything below was **reported by the author on 2026-09-14**. None of it has been reproduced yet. The "where" lines
come from reading the code, not from a debugger.

---

## Defects

None open.

---

## Requested changes

### Context menu

#### R6. Redesign the context menu

Wanted: a better-looking menu. No direction given yet. Get one (a reference, or a mockup) before building. Scope is
`MenuSurface`, `MenuRow`, `MenuHeader` and `ChevronMark` in `ContextMenu.kt`. The inline rendering and staging
(`MenuStage`) are behavior, not look, and stay. The menu's content is also misaligned (reported without the case); the
redesign absorbs that rather than fixing it first. Placement is `MenuAnchoring.kt`, and it must not move into a `Popup`:
a drag has to continue on the same pointer stream.

### Wallpaper studio

#### R11. Credit gart in the wallpaper studio

Wanted: the studio says where its generators come from: gart (`D:\Android\gart`, BSD-2). **After** the in-progress
studio fixes (the W11 passes). Do not start it on a studio still being reworked.

**Not only courtesy:** BSD-2 requires the copyright notice and license text to go with binary distributions. gart is
harvested source, not a Gradle dependency, so the AboutLibraries list in About does **not** pick it up on its own. The
credit needs a license entry there as well as the note in the studio. `docs/GART_HARVEST.md` records what was taken.

---

Firebase, in-app updates and the paywall moved to their own plan: [FIREBASE_PAYWALL_PLAN.md](FIREBASE_PAYWALL_PLAN.md).
