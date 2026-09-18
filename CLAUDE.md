# CLAUDE.md

Guidance for AI coding assistants working in this repository. Humans may find it a useful map too.

## What this is

An Android app that tracks the user's location in the foreground and background, adds a map marker
every 100 m, shows the address of a tapped marker, and keeps the route across restarts until reset.

## Commands

```bash
./gradlew assembleDebug      # build
./gradlew :core:test         # the business rules, pure JVM, seconds
./gradlew installDebug       # install on a connected device or emulator
ANDROID_SERIAL=emulator-5554 ./gradlew :data:connectedDebugAndroidTest   # Room SQL; pin one device
```

`local.properties` needs `sdk.dir` and `MAPS_API_KEY`. Both stay out of git.

## Modules and boundaries

```
:app ──► :feature:tracking ──► :core
  └────► :data ───────────────► :core
```

- `:core`: pure Kotlin/JVM. Domain model, `DistanceGate`, `RecordFixUseCase`, and the contracts the UI
  needs (`RouteRepository`, `TrackingController`, `LocationAccess`, `AddressLookup`). No Android imports,
  ever; the build enforces this.
- `:data`: Room route storage, DataStore session flag, `TrackingService` (fused location), notifications,
  Geocoder, `dataModule`.
- `:feature:tracking`: Compose UI, the pure `ScreenState.reduce` in `TrackingReducer.kt`, and the
  `TrackingViewModel` that drives it: one state, one `onIntent`, effects on their own channel (D7).
  Depends on `:core` only.
- `:app`: `Application` (starts Koin), `MainActivity`, API key wiring. The only module that sees everything.

## Invariants: do not break these

- **The anchor is the last recorded point**, read from storage, never the previous GPS fix and never
  held in memory. See `docs/decisions.md` D10.
- **All recording goes through the single `RecordFixUseCase` instance.** Its mutex makes
  read-anchor → evaluate → write atomic. Keep it a Koin `single`.
- **Fixes with missing or poor accuracy are rejected before the anchor check** (D12).
- **Write an address only with `UPDATE route_points SET address = ? WHERE id = ?`.** A `Recorded` point may
  already have been deleted by a concurrent reset; an insert or upsert would bring it back (D14).
- `RouteDao.last()` is the production anchor query. Unit tests use a fake repository, so only the
  instrumented `RouteDaoTest` covers it (D10). Run that test after any change to `RouteDao`.
- **Starting tracking requires precise location; the service itself accepts either** (D16). After an
  approximate-only grant, a false rationale does not mean "blocked" until the upgrade was already asked (D18).
- **State transitions live in `ScreenState.reduce`; side effects live in the ViewModel** (D7). The
  reducer is pure - no Android, no coroutines, no repository - and everything it needs from the platform
  arrives as a `LocationSnapshot`, read once per intent. A new rule about when the screen changes goes
  there, with a JVM test that fails when the rule is broken; a new service call, lookup or effect goes in
  the ViewModel's `when`, after the reduction.
- **Kotlin stays at 2.4.20 or later.** Do not apply `org.jetbrains.kotlin.android`; AGP 9 has built-in Kotlin (D2).
- A Compose library module needs both the `kotlin.compose` plugin and `buildFeatures.compose` (D3).

## Tracking service rules (D17)

- Start with **`startService`**, then call `startForeground` inside the service. Never `startForegroundService`:
  it demands `startForeground` within seconds, which throws when the permission is gone.
- Order: **permission check → `startForeground` → request location updates.** On targetSdk 34+,
  `startForeground` with the location type throws when no location permission is held.
- Ask the platform directly with `ContextCompat.checkSelfPermission`. The fused provider reports a
  missing permission asynchronously, so a normal return proves nothing.
- The service's pre-`startForeground` check accepts either FINE or COARSE, as the platform does.
  This is a crash guard, not the product rule: the UI requires FINE to *start* tracking (D16).
- Wrap `startForeground` in try/catch and end the session cleanly on refusal, posting the "tracking stopped"
  notification. A restarted service *may* be refused location access. That was seen in the author's own
  app, but not reproduced in this project (D17), so do not claim it happens here.
- Commands go through one channel and end with `stopSelf(startId)`, so stop-then-start cannot interleave.
- A system kill continues the same session: `START_STICKY` restarts the service and it re-runs the start
  sequence. A session left marked active by a force-stop or reboot is ended at the next app start and the
  user is told; tracking never starts again by itself.
- Fixes still queued when a session ends are dropped (`acceptingFixes`, cleared first in `endSession`).
- With `START_STICKY` the restart `Intent` is `null`. Everything needed to resume is read from
  persistent storage, never from intent extras.
- In `onDestroy`, remove the location callback first. A leaked callback keeps GPS on.
- No time-based fallback points: the spec says every 100 m.

## How to work here

- Before a claim about builds or tests, follow the `verify-claim` skill. Before testing on a device or
  emulator, follow the `device-check` skill.
- **Media from a physical device only under all of these conditions,** otherwise from an emulator:
  **no real location reaches the screen** - either the permission is revoked so nothing can be drawn, or
  every fix the app receives comes from the `demo` build's own mock source, and which one it was is checked
  against the recorded coordinates; the app's data is cleared with `pm clear`, so no earlier real points
  remain; the route on screen is made-up and seeded; every frame is read in a contact sheet before anything
  is committed - **the first and last seconds too**, which are whatever was in front when the recorder
  started and stopped - and anything committed as a crop, a cut or a GIF is read in the form it is
  committed in; and the device
  is restored afterwards with the restore verified (mock sources removed, appops back to default, Do Not
  Disturb off, settings back). **Logs from a physical device are never committed.** Everything in
  `docs/media/` was recorded on the phone under those conditions; the emulator is used for the instrumented
  tests, not for media.
- **Measure, don't infer.** Read build and test logs, not exit codes. Delete old test results before
  quoting pass counts; stale XML has already produced a false result once.
- Label evidence in `docs/decisions.md` as Measured, Reasoned, or Device check pending, and add
  experiments to its verification log.
- A new rule in `:core` gets a test that fails when the rule is broken; check it with a deliberate mutation.
- Code, comments, commits and docs are in English. Conventional Commits, small and focused.
- **Commit, but do not push.** The author reviews every commit before pushing.
- Never commit `local.properties`, API keys, or `.claude/settings.local.json`.
