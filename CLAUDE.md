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
```

`local.properties` needs `sdk.dir` and `MAPS_API_KEY`. Both stay out of git.

## Modules and boundaries

```
:app ──► :feature:tracking ──► :core
  └────► :data ───────────────► :core
```

- `:core`: pure Kotlin/JVM. Domain model, repository contracts, `DistanceGate`, `RecordFixUseCase`.
  No Android imports, ever; the build enforces this.
- `:data`: Room, DataStore, fused location, geocoder, the tracking foreground service, `dataModule`.
- `:feature:tracking`: Compose UI and the MVI `TrackingViewModel`. Depends on `:core` only.
- `:app`: `Application` (starts Koin), `MainActivity`, API key wiring. The only module that sees everything.

## Invariants: do not break these

- **The anchor is the last recorded point**, read from storage, never the previous GPS fix and never
  held in memory. See `docs/decisions.md` D10.
- **All recording goes through the single `RecordFixUseCase` instance.** Its mutex makes
  read-anchor → evaluate → write atomic. Keep it a Koin `single`.
- **Fixes with missing or poor accuracy are rejected before the anchor check** (D12).
- **Kotlin stays at 2.4.20 or later.** Do not apply `org.jetbrains.kotlin.android`; AGP 9 has built-in Kotlin (D2).
- A Compose library module needs both the `kotlin.compose` plugin and `buildFeatures.compose` (D3).

## Tracking service rules

- Order: **permission check → `startForeground` → request location updates.** On targetSdk 34+,
  `startForeground` with the location type throws when no location permission is held.
- Ask the platform directly with `ContextCompat.checkSelfPermission`. The fused provider reports a
  missing permission asynchronously, so a normal return proves nothing.
- The service's pre-`startForeground` check accepts either FINE or COARSE, as the platform does.
  This is a crash guard, not the product rule: the UI requires FINE to *start* tracking (D16).
- Wrap `startForeground` in try/catch. A service restarted from the background cannot obtain location
  access; end the session cleanly instead of crash-looping.
- With `START_STICKY` the restart `Intent` is `null`. Everything needed to resume is read from
  persistent storage, never from intent extras.
- In `onDestroy`, remove the location callback first. A leaked callback keeps GPS on.
- No time-based fallback points: the spec says every 100 m.

## How to work here

- **Measure, don't infer.** Read build and test logs, not exit codes. Delete old test results before
  quoting pass counts; stale XML has already produced a false result once.
- Label evidence in `docs/decisions.md` as Measured, Reasoned, or Device check pending, and add
  experiments to its verification log.
- A new rule in `:core` gets a test that fails when the rule is broken; check it with a deliberate mutation.
- Code, comments, commits and docs are in English. Conventional Commits, small and focused.
- Never commit `local.properties`, API keys, or `.claude/settings.local.json`.
