# Decisions

Every non-obvious choice in this project, the alternative it beat, and the evidence behind it.
AI tools made writing the code fast; they did not make verifying it any faster. So each entry
says how it was verified:

- **Measured**: confirmed by a build, a test run, or a controlled experiment. The evidence is stated.
- **Reasoned**: follows from documentation or analysis, but has not been measured in this project.
- **Device check pending**: must be confirmed on an emulator or device before submission.

---

## Build and stack

### D1. Google Maps through `maps-compose`

- **Decision:** Google Maps SDK with the `maps-compose` wrapper.
- **Rejected:** Mapbox. Its Android SDK is served from a Maven repository that requires a secret
  download token in Gradle, so a reviewer cloning the repo would hit an authentication failure
  before reaching any code. Google's SDK resolves from `google()` with no credentials; only the
  runtime API key is needed, and the app explains when it is missing (D5).
- **Evidence:** Reasoned, from Mapbox's installation requirements. Not measured here.

### D2. AGP 9.4 with built-in Kotlin, compiler at 2.4.20

- **Decision:** `org.jetbrains.kotlin.android` is not applied. AGP 9 compiles Kotlin itself; applying
  `org.jetbrains.kotlin.plugin.compose` 2.4.20 raises the compiler from AGP's default 2.2.10 to 2.4.20.
- **Rejected:** keeping the default 2.2.10 compiler.
- **Evidence:** Measured in a throwaway probe project with the same versions, before this repo existed.
  With `kotlin = "2.2.10"` the build failed on `maps-compose-8.6.0` and `maps-ktx-6.4.1`:
  *"Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is
  2.4.0, expected version is 2.2.0."* With 2.4.20 the same project built green, with kotlin-stdlib
  resolving to 2.4.20. **Do not lower the Kotlin version.**

### D3. Compose library modules need the plugin and the build feature

- **Decision:** `:feature:tracking` applies `kotlin.compose` and also sets `buildFeatures { compose = true }`.
- **Evidence:** Measured in the probe project. With both set, the compiled `TrackingScreen` signature
  gained the `Composer` and `int` parameters that the Compose compiler injects.

### D4. `MAPS_API_KEY` read through Gradle providers

- **Decision:** the key comes from the git-ignored `local.properties`, or the `MAPS_API_KEY` environment
  variable, through `providers.fileContents(...)`. It feeds a manifest placeholder and a
  `BuildConfig.HAS_MAPS_API_KEY` flag.
- **Rejected:** hard-coding the key; the Secrets Gradle Plugin (an extra plugin for one value, and its
  version could not be confirmed while setting up).
- **Evidence:** Measured in this repo. The concern was a stale configuration cache silently keeping an
  old key. The same task set was run five times:

  | Run | Configuration cache | Merged manifest key | `HAS_MAPS_API_KEY` |
  |---|---|---|---|
  | key empty | stored | `""` | false |
  | unchanged | reused | `""` | false |
  | key set | invalidated: *"local.properties has changed"* | `cc-probe-key-42` | true |
  | unchanged | reused | `cc-probe-key-42` | true |
  | key cleared | invalidated | `""` | false |

### D5. Missing key shows an explanation, not a grey map

- **Decision:** without a key, the map screen shows a card explaining how to add one. Tracking and
  recording still work.
- **Evidence:** Reasoned. Device check pending (screenshot for the README).

---

## Architecture

### D6. Four modules, with a pure JVM `:core`

```
:app ──► :feature:tracking ──► :core
  └────► :data ───────────────► :core
```

- **Decision:** `:core` holds the domain model, repository contracts, the distance rule and the
  record use case, built with `org.jetbrains.kotlin.jvm` only. `:feature:tracking` never sees `:data`;
  only `:app` wires them together through Koin.
- **Rejected:** a single module with packages. Layer boundaries would then be a convention; here an
  Android type in the domain is a compile error.
- **Evidence:** Measured in the probe project: the `:core` classpath contained no Android artifacts
  and produced Java 17 bytecode (major version 61).

### D7. MVI with one state and one entry point; Koin for injection

- **Decision:** the screen observes one `StateFlow<TrackingState>` and sends every user action through
  `onIntent(TrackingIntent)`. The selected marker is stored as an id and resolved against the current
  points, so a route reset closes the details card without any extra code.
- **Rationale:** the team's stack (MVI, Koin, Flow). One state object keeps rotation and process
  recreation to a single re-render.
- **Evidence:** Reasoned.

### D8. Koin starts in `Application`, not in an Activity

- **Decision:** `startKoin` runs in `RouteTrackerApp.onCreate`.
- **Rationale:** when the system restarts a sticky service, it creates the process without any
  Activity. The service still needs its dependencies.
- **Evidence:** Reasoned. Device check pending: `adb shell am kill` while tracking in the background.

### D9. The route is ordered by insertion id, not by timestamp

- **Rationale:** the device clock can be changed by the user or corrected by the network; the order
  points were recorded in cannot.
- **Evidence:** Reasoned.

---

## The 100 m rule

### D10. The anchor is the last recorded point, not the last GPS fix

- **Decision:** a fix becomes a marker only when it is at least 100 m from the newest persisted point.
- **Rejected:** measuring fix to fix. GPS jitter would add markers while standing still, and a slow
  walk made of sub-threshold steps would never add one. Also rejected: relying on the platform's
  `setMinUpdateDistanceMeters`, which is a battery hint, not a guarantee.
- **Why persisted:** the anchor is simply the last row in Room, so the rule survives process death and
  a route reset with no extra state to keep in sync.
- **Evidence:** Measured at the domain level. `DistanceGateTest` (11 tests) and `RecordFixUseCaseTest`
  (9 tests). The use-case tests feed fix sequences, because gate tests receive the anchor as input and
  cannot catch a caller choosing the wrong one. See the mutation checks in the verification log.
- **Known gap, the production query:** the use-case tests run against a fake repository, so the SQL
  that actually selects the anchor, `RouteDao.last()`, is not covered by any test. Measured in an
  independent review: changing `ORDER BY id DESC` to `ASC` compiled, and all 25 tests still passed.
  In the app, that bug would anchor every fix to the route's start, so once the user is 100 m away,
  every update would add a marker. Planned: an instrumented in-memory Room test for `last()` and
  `deleteAll()`, then the same mutation repeated to show that the test catches it.

### D11. The first accurate fix is the start marker

- **Decision:** the spec defines markers every 100 m but not the start. The first accurate fix is
  recorded, so the route has a visible starting point, and later markers are measured from it.
- **Evidence:** Product decision; covered by tests.

### D12. Fixes with accuracy worse than 50 m, or none, are rejected

- **Problem:** the first fix after starting is often a network fix with an accuracy of hundreds of meters.
  If it became the anchor, the next accurate GPS fix would look far away and drop a marker without
  any movement.
- **Decision:** reject fixes whose accuracy is missing, NaN, or above 50 m, before the anchor is even
  considered. Rejected fixes neither record nor move the anchor.
- **Trade-off:** with poor signal (tunnels, indoors, urban canyons) markers are delayed rather than wrong.
- **Rejected:** 100 m (as uncertain as the rule itself) and no filter.
- **Evidence:** the behaviour is measured by tests; **the 50 m threshold itself is reasoned, not measured.**
  Device check pending: emulator mock locations (`adb emu geo fix`, GPX routes) must carry an accuracy
  value, or nothing will be recorded during testing.

### D13. Haversine instead of `Location.distanceBetween`

- **Decision:** a spherical-Earth haversine in `:core`.
- **Rationale:** `distanceBetween` is an Android API and would pull the rule out of JVM tests. The sphere
  differs from the WGS84 ellipsoid by at most about 0.5%, half a meter at the threshold.
- **Test note:** the boundary tests use 99.5 m and 100.5 m. A point constructed exactly 100 m away computes
  to 99.99999999969069 m in floating point, so an exactly-100 test would test rounding, not the rule.
- **Evidence:** Measured (unit tests, including the antimeridian and antipodal NaN cases). The
  floating-point value was measured in an independent review session.

### D14. One writer: `RecordFixUseCase` with a mutex

- **Problem:** `LocationResult` can deliver several fixes at once. Two concurrent "read anchor, evaluate,
  write" sequences could read the same anchor and both write, leaving two markers under 100 m apart.
- **Decision:** the use case holds a `Mutex` around the whole sequence and is a Koin `single`.
- **Rejected:** a Room `@Transaction`. It would work, but it moves the rule into `:data`, where it can only
  be tested with a database.
- **Why reset does not take the lock:** reset is a single atomic delete. Whether it lands before, during
  or after a record call, the stored route matches one of the two serial orders.
- **Constraint this creates:** the *returned* value can be stale. In the order read → write → reset, the
  use case returns `Recorded(point)` for a row that no longer exists. Anything acting on that point later,
  such as writing its resolved address, must be a plain `UPDATE route_points SET address = ? WHERE id = ?`,
  which affects zero rows once the point is gone. An `@Insert` or `@Upsert` there would bring a reset
  point back to life.
- **Evidence:** Measured. `concurrent fixes cannot both pass against the same anchor` fails when the lock
  is removed (mutation M3). The fake repository yields at each read and write, so the coroutines genuinely
  interleave.

### D15. Resetting while tracking keeps tracking

- **Decision:** after confirmation the route is cleared; tracking continues and the next accurate fix
  starts a new route.
- **Rejected:** stopping tracking on reset (couples two independent actions); disabling reset while tracking.
- **Evidence:** Measured at the use-case level (`after a reset the next accurate fix starts a new route`).
  Device check pending.

### D16. Starting requires precise location; a running session accepts either

- **Decision:** approximate location snaps to a grid of roughly 2 km, which makes a 100 m rule meaningless, so
  starting requires `ACCESS_FINE_LOCATION`. A session that is already running only checks for either
  location permission before `startForeground`, because the platform accepts either for a location
  foreground service.
- **Evidence:** Decided, not implemented yet.

---

## Verification log

| Date | What | Result |
|---|---|---|
| 2026-09-16 | First `assembleDebug` | 100 tasks executed; Room schema exported; `BuildConfig` and merged manifest inspected |
| 2026-09-16 | Configuration cache and API key (D4) | First attempt invalid: each run used a different task set, so reuse was never tested, and the manifest was read from another task's output. Repeated with one fixed task set; results in D4 |
| 2026-09-16 | Mutation M1: accuracy limit `<=` → `<` | Failed exactly `accuracy exactly at the limit is accepted` |
| 2026-09-16 | Mutation M2: ignore distance | Failed 3 tests (just under 100 m, slow walk, GPS jitter) |
| 2026-09-16 | Independent review session | Found that the camera never centered on a route recorded after a fresh install; fixed in `1888b20`. Also found that gate tests could not catch a wrong anchor, which led to D14 |
| 2026-09-16 | Mutation M3: remove the write lock | Failed exactly the concurrency test |
| 2026-09-16 | Mutation M4: anchor on the first point | First attempt invalid: the mutation did not compile and the report showed stale test XML. Repeated after deleting old results: failed `anchor advances only when a point is recorded` and the slow-walk test. **Scope: use-case layer only.** It proves the use case asks for the last point, not that the Room query returns it; see the next review row |
| 2026-09-16 | `:core:test` after restoring | 25 / 25 passed, from freshly generated XML |
| 2026-09-16 | Second independent review | Confirmed 25 / 25 and the build. Removing the lock failed the concurrency test in 5 of 5 runs, so it is deterministic. Forcing the anchor to null failed 6 tests. **Found a gap:** `RouteDao.last()` changed from `DESC` to `ASC` still passed 25 / 25, because no test reaches the real DAO (D10). Also found that a `Recorded` result can point at a row already deleted by reset (D14) |
