# Firebase and paywall plan

Covers analytics, crash reporting, remote config, updates and the paywall: everything that makes the launcher talk to
a Google service or take money. None of it exists yet. There is no Firebase, no Play library, no billing dependency
and no premium flag anywhere in the build.

**Status: scoping.** The Firebase products and both update channels are decided enough to build. The paywall is not:
it has nothing to gate until what is paid is decided (see [Paywall](#paywall)).

**Two install channels shape all of it.** The launcher is installed from Play, and also sideloaded by users on custom
ROMs with no Google Play services. Anything that needs Play services (In-App Updates, Play Billing) has an answer for
the second group too, or it is scoped as Play-only on purpose.

---

## Firebase: Analytics, Crashlytics and Remote Config

**Taken:** all three. Analytics was left out at first. It came back for what it tells us about use: which layouts and
settings people choose, and where setup gets abandoned. It also turns on two Crashlytics features and makes Remote
Config experiments possible (both below).

**Build:** The Firebase BoM, plus the `google-services` and Crashlytics Gradle plugins, applied on `:app` only. A
feature module never touches the Firebase SDK itself. It logs through an `AnalyticsLogger`-style command, and reads
config through a repository, so the SDK can be swapped or turned off in one place. `google-services.json` is
per-project. Decide whether it is committed or supplied locally before the first commit that needs it.

**Debug builds must not report:** turn Analytics and Crashlytics collection off for debug, or every crash and tap made
while developing is mixed in with real ones.

### Analytics

- **Advertising ID off.** A launcher has no use for it. Set `google_analytics_adid_collection_enabled` to `false` in
  the manifest, and do not declare `AD_ID`. That keeps the data safety form and the privacy policy lighter.
- **Events are named in one place**, the logger, and never as string literals at call sites. An event renamed in one
  file and not another splits a metric in two, and nothing warns about it.
- **Consent:** EU users need a consent choice before collection, through Firebase's consent mode. Decide where it is
  asked. First-run setup is the obvious place, and its flow lives in `docs/ONBOARDING_PLAN.md`.

### Crashlytics

- Catches fatal crashes, `recordException` non-fatals, and ANRs (Android 11+, read from `ApplicationExitInfo` and
  uploaded on the next launch, which for a launcher is immediate).
- With Analytics on, it also gets **breadcrumbs** (the events before a crash) and the **crash-free users** percentage.
- **Custom keys** carry the launcher-specific context: the active `HomeLayout` / `AppsLayout`, the surface on screen,
  whether a drag was in flight.
- **Play Console's Android vitals** reports crash and ANR rates for Play installs with no SDK. Those are the
  thresholds Play enforces, so watch both dashboards.

### Remote Config

What it is for here, in order of when it is needed:

1. **Web update offer.** The update channel for users without Play services. See
   [Updates](#updates-two-channels).
2. **Minimum supported version.** The same threshold serves both channels: below it, the update offer is pushed
   harder.
3. **Kill switches.** A flag that turns off a feature misbehaving on specific devices (GPU-heavy rendering such as the
   frosted backdrop or the label glow) without shipping a release. Only features that were built with a flag can be
   switched off this way, so decide which ones get a flag while building them.
4. **Paywall tuning.** Copy, which offer is shown, and when it is shown. With Analytics, Firebase A/B Testing runs
   these as experiments.

Values are read through a repository with **in-code defaults for every key**, so a device that has never fetched, or
cannot, behaves as the defaults say. The production minimum fetch interval is 12 hours, so a flag takes effect on the
next fetch, not instantly.

### Devices without Play services: verify, do not assume

Crashlytics and Remote Config talk to Firebase over its own HTTP APIs, through Firebase Installations, and should work
without Play services. Analytics uses Play services' measurement service where it exists, and falls back to running
inside the app where it does not, possibly with less data. **This is expected, not tested.** Check it on an emulator
image *without* Google APIs (plain AOSP) before the web update channel depends on it. That channel only works if
Remote Config works on exactly those devices.

---

## Updates: two channels

The channel is chosen by **where the installed copy came from**, not by whether Play services happen to be present:

```kotlin
packageManager.getInstallSourceInfo(packageName).installingPackageName == "com.android.vending"
```

(API 30+; `getInstallerPackageName` below that). Installed by Play means the Play channel. Anything else (a browser,
a file manager, an ADB install) means the web channel.

### Play channel: In-App Updates

The library is `com.google.android.play:app-update-ktx`.

**Checking.** `AppUpdateManager.appUpdateInfo` reports `updateAvailability`, `availableVersionCode`,
`clientVersionStalenessDays` and `updatePriority` (0–5). The priority is set per release through the **Play Developer
Publishing API** only, not in the Play Console website. Testing goes through internal app sharing, or
`FakeAppUpdateManager` in tests.

**Flow: flexible, never immediate.** An immediate update is a full-screen Play screen that blocks the app until the
install finishes. On a launcher, that app is HOME, so it would leave the user unable to reach anything else. The
flexible flow downloads in the background, and `completeUpdate()` restarts the process when it is done. That is
acceptable because the system relaunches the default home straight away. Call it from settings rather than from HOME,
so the restart does not land mid-gesture.

**Fallback** when the flow fails on a Play install: open `market://details?id=inkspire.morphic.launcher`, then the
`https://play.google.com/store/apps/details?id=…` URL if no store app handles it.

### Web channel: Remote Config plus an APK on the website

For installs not made by Play. Remote Config carries the latest web version code and the download page URL. When the
installed version is older, the launcher offers the update and **opens the download page in the browser**. The
browser downloads the APK, and the system installer installs it.

Three constraints, each of which fails **silently** or at review if missed:

1. **Never shown to a Play install.** Play's policy forbids an app installed from Play from updating itself by any
   route other than Play. The install-source check above is what enforces this, and it has to be the only way into
   the web offer. It must not be a fallback the Play channel drops into when In-App Updates fails.
2. **Opening a page, not installing an APK.** Installing from inside the app needs `REQUEST_INSTALL_PACKAGES`, which
   Play restricts to apps whose core purpose is installing packages, and a launcher's is not. Handing the URL to the
   browser needs no permission, so one build can serve both channels. If an in-app installer is ever wanted, it goes
   in a separate `web` product flavor that never reaches Play, not behind a runtime check.
3. **The website APK must be signed with the app signing key**, the same key as the copy it replaces. Otherwise
   Android refuses the install as a signature conflict, and the user's only way forward is to uninstall, which loses
   their home layout. Under Play App Signing, Google holds that key and the upload key is a different one. Take the
   website APK from Play Console → App bundle explorer → **signed universal APK**, which Play signs with the app
   signing key. Do not sign a local build. A copy moved between channels (Play to web, or back) then updates cleanly
   too.

**Shape.** Per the repository/command split: `UpdateRepository` is one stream of whether an update exists, how urgent
it is, and which channel carries it. It combines Play's answer or the Remote Config version with the minimum
supported version. `AppUpdater` is the command, and it either runs the flexible flow or opens the download page. The
UI is a row in About, plus a HOME-menu entry while an update is urgent. The "Finish setup" offer
(`feature:shell/SetupMenuOffer.kt`) is the existing precedent for a conditional menu entry.

---

## Paywall

Wanted: a paywall, in a module of its own.

**Open — decide before building anything:**

- **What is paid.** The paywall has nothing to gate until this is decided. Candidates are implied by the product
  direction (the icon, wallpaper and widget studios; community theme sharing), but none has been chosen.
- **How a locked feature appears.** "Absent, not disabled" means a control that does nothing must not be shown. A
  locked feature is a different case: it *does* something, which is opening the paywall. So a visible lock is
  defensible, but it is a deliberate exception to the rule and should be recorded as one in CLAUDE.md, not decided
  separately on each screen.
- **One-time purchase or subscription.** This changes what the entitlement stream has to handle: expiry, grace
  periods, restore.
- **Users without Play services cannot pay through Play Billing.** They get the paid features free, have no way to
  buy, or pay through another processor with its own license check. That is a real product decision, because the
  web-channel users are exactly the ones Play Billing cannot reach. And Play's payments policy forbids steering *Play*
  users to an outside payment, so any outside route is web-channel only, gated the same way as the web update offer.

**Shape, proposed:**

- `data:billing`: the entitlement **repository** (a stream of what the user owns, backed by Play Billing) and the
  purchase **command**, as two types. Purchasing is a side effect, and folding it onto the repository is what L1 did
  with launch.
- `feature:paywall`: the screen, with its own ViewModel.
- `app` only assembles, per the feature-module rule. A gated feature depends on `data:billing` for the entitlement and
  never on `feature:paywall`. It asks the shell to navigate there.

---

## Privacy: lands in the same change that turns collection on

- **Analytics** sends usage events, a Firebase app instance ID, and coarse device and location data (derived from IP).
  The advertising ID is off, as above.
- **Crashlytics** sends crash traces, device model and OS version, and a Firebase installation ID.
- **Remote Config** uses the installation ID.
- **Play Billing** shares purchase state with Google.

Before the first build that collects any of it:

- edit `PrivacyPolicy.kt`, then regenerate and commit `privacy-policy.html`. `PrivacyPolicyHtmlTest` fails until the
  regenerated file is committed, so this cannot be skipped;
- update the Play data safety form to match;
- ship the consent choice for EU users (see [Analytics](#analytics)).

Each goes in the same commit that turns on the thing it discloses, not in a later cleanup.
