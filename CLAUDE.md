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
- `:feature:tracking`: Compose UI and the MVI `TrackingViewModel`. Depends on `:core` only.
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
- A lost session is ended, never silently resumed; a stale active flag found at app start is cleared
  and the user is told.
- With `START_STICKY` the restart `Intent` is `null`. Everything needed to resume is read from
  persistent storage, never from intent extras.
- In `onDestroy`, remove the location callback first. A leaked callback keeps GPS on.
- No time-based fallback points: the spec says every 100 m.

## How to work here

- Before a claim about builds or tests, follow the `verify-claim` skill. Before testing on a device or
  emulator, follow the `device-check` skill.
- **Never commit screenshots, recordings or logs from a physical device:** they carry the tester's real
  location. README media comes from an emulator on a made-up route.
- **Measure, don't infer.** Read build and test logs, not exit codes. Delete old test results before
  quoting pass counts; stale XML has already produced a false result once.
- Label evidence in `docs/decisions.md` as Measured, Reasoned, or Device check pending, and add
  experiments to its verification log.
- A new rule in `:core` gets a test that fails when the rule is broken; check it with a deliberate mutation.
- Code, comments, commits and docs are in English. Conventional Commits, small and focused.
- **Commit, but do not push.** The author reviews every commit before pushing.
- Never commit `local.properties`, API keys, or `.claude/settings.local.json`.
