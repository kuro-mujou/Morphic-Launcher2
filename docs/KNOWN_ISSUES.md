# Known issues

Defects found by using the launcher and **not yet fixed**. One entry each: what happens, how to see it, what is *not*
affected, and where the cause appears to be.

This is deliberately not the other three kinds of note in `docs/`. A **plan** says what is going to be built; a
**design doc** says why something is shaped the way it is; CLAUDE.md's *standing gaps* are things never built (no
accessibility semantics, no Gradle wrapper). What lands here is narrower: something that **is** built, is wrong, and
is being left wrong for now.

An entry says whether it was reproduced or only reported, and by what. "Reported" is not a weaker claim about the
bug — it is an honest one about the evidence.

---

## 1. A pager forgets its page when the surface is navigated away from

**Reproduced on device (2026-08-25).**

Swipe HOME to its second page, open anything that is a *destination* — a container's settings, the launcher's own
settings — and come back. HOME is on page one. Whatever page you were reading is gone, along with any scroll
progress within it.

**What is not affected, and it is the part that names the cause:** switching to another **app** and returning keeps
the page. Verified the same session — page two, out to the system settings app, back, still page two. The activity
is only stopped there, so the composition survives; a navigation *within* the launcher disposes it.

### Scope

`rememberLauncherPagerState` has three callers, and they are exactly the three paged surfaces:

| Caller | Surface | Evidence |
|---|---|---|
| `HomePagerSurface` | HOME's main area | reproduced |
| `AppsPager` | the APPS pager layout | reported; same call, not separately reproduced |
| `AppsCategoryPager` | APPS `PAGER_WITH_CATEGORY` | reported; same call, not separately reproduced |

The two APPS ones were not reproduced only because the surface is reached by an edge swipe, and on the test device
an edge swipe from `adb shell input` is taken by the system's back gesture before it reaches the launcher. They call
the same function in the same way, so there is no reason to expect them to differ.

### Where it comes from

```kotlin
fun rememberLauncherPagerState(…): LauncherPagerState = remember { LauncherPagerState(…) }
```

Plain `remember`. `NavDisplay` disposes a `NavEntry`'s composition when it is navigated away from, and the
saveable-state decorator restores `rememberSaveable` only — so the state is rebuilt from scratch, at page zero.

### What a fix has to decide

Not just "use `rememberSaveable`". Two questions come with it:

- **`LauncherPagerState` takes three lambdas** (`pageCount`, `dragMode`, `infiniteScroll`). A `Saver` can only carry
  the page, and the lambdas must come from the call site on restore — the same shape, and the same hazard, as the
  note in CLAUDE.md about `rememberSliderState` freezing its constructor arguments. Restoring a page against a
  *different* page count is the case to get right: a saved page 3 on a layout that now has two pages.
- **How far should it persist?** `rememberSaveable` also survives process death, so HOME would reopen on page three
  a day later. That may be right for the APPS pager and wrong for HOME, and it is a product decision rather than a
  mechanical one.
