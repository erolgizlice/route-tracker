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
- **Evidence:** Measured on the API 33 emulator (`docs/media/missing-key.png`), with the `MAPS_API_KEY` line
  removed from `local.properties`. The configuration cache was invalidated, `HAS_MAPS_API_KEY = false`, the
  merged manifest key was empty and the build succeeded. The card was shown; after Start the service was in
  the foreground and fixes 100.8 m apart were recorded (the marker count is visible under the card). With
  the file restored (same sha256) and the keyed build installed, the card disappeared and the map loaded.
  The first render took longer than 8 s.

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
- **Evidence:** Measured on a Galaxy S23 (Android 16). The process was killed with
  `adb shell run-as <package> kill -9 <pid>`. That needs no root on a debuggable build, and ActivityManager
  logs it as `has died: prcp FGS`. The system restarted the service in a new process with no Activity,
  and the service ran with its injected dependencies. `adb shell am kill` is not a valid test: it only
  kills processes that are safe to kill, which excludes a process running a foreground service.
  What the restarted service could do is recorded in D17.

### D9. The route is ordered by insertion id, not by timestamp

- **Rationale:** the device clock can be changed by the user or corrected by the network; the order
  points were recorded in cannot.
- **Evidence:** Measured by the instrumented test `routeIsOrderedByRecordingEvenWhenTheClockWentBackwards`:
  a point stamped earlier but inserted later stays last, and `last()` returns it.

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
- **The production query, a gap that is now closed:** the use-case tests run against a fake repository, so
  at first nothing covered `RouteDao.last()`, the SQL that actually selects the anchor. An independent review
  measured it: changing `ORDER BY id DESC` to `ASC` compiled, and all 25 tests still passed. In the app,
  that bug would anchor every fix to the route's start, so once the user is 100 m away, every update would
  add a marker. `RouteDaoTest` (6 instrumented tests, in-memory Room) now covers `last()`, insertion order,
  `deleteAll()` and the address update. The same mutation now fails exactly the two tests that read the
  last point (M5 in the verification log).

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
- **Evidence:** the behaviour is measured by tests. On a Galaxy S23, indoors, the problem occurred
  exactly as described. The first fix after starting had an accuracy of 100 m and was rejected; 4 s
  later a 14 m fix became the start of the route. Later a 173 m fix arrived in the background and was
  also rejected. In that session good fixes were 6–27 m and coarse ones 100–173 m, so 50 m separated
  them cleanly. **That is one session on one device: the threshold remains a judgment, not a tuned value.**
  Mock locations were checked too, because a fix without accuracy would make emulator testing record
  nothing. On an API 33 emulator, `adb emu geo fix` fixes arrive with an accuracy of 5.0 m and are
  recorded. GPX route playback has not been checked.

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
- **Evidence:** Measured at the use-case level (`after a reset the next accurate fix starts a new route`)
  and on a Galaxy S23: while tracking, confirming the dialog took the route from 1 marker to 0 and
  tracking stayed on. Within 20 s the next accurate fix was recorded as the start of the new route.

### D16. Starting requires precise location; a running session accepts either

- **Decision:** approximate location snaps to a grid of roughly 2 km, which makes a 100 m rule meaningless, so
  starting requires `ACCESS_FINE_LOCATION`. A session that is already running only checks for either
  location permission before `startForeground`, because the platform accepts either for a location
  foreground service.
- **Evidence:** Measured on a Galaxy S23. With only approximate location granted, tracking did not start,
  no service ran, and the screen asked for precise location. The upgrade prompt is covered in D18.

### D17. Starting, running and losing the tracking service

- **Start with `startService`, then `startForeground` inside the service.**
  - **Rejected: `startForegroundService`.** It obliges the service to call `startForeground` within seconds.
    On targetSdk 34+, a location-typed `startForeground` throws without a location permission. A session
    that has to end for lack of permission would then have to break one rule or the other. With
    `startService`, ending without ever calling `startForeground` breaks nothing. Sessions are started
    from the foreground app, where `startService` is allowed. The notification's Stop action sends its command
    through a `PendingIntent` while the service is already running. That was measured only with the app
    directly behind the notification shade, not from a fully backgrounded app.
- **Order inside the service: permission → `startForeground` → location updates.**
  - **Permission first,** asked through `ContextCompat.checkSelfPermission`. The fused provider reports a
    missing permission only asynchronously, so a normal return would prove nothing.
  - **Foreground second:** for a user-started session the app is in the foreground, so promotion is
    allowed. Location is never requested by a service that is not yet foreground.
  - **Rejected: permission → updates → foreground,** the order used in the author's own app. It works too,
    but when the platform refuses `startForeground` the location updates already registered have to be
    removed again. Here a refusal leaves nothing to undo.
- **Refusals end the session cleanly.** `SecurityException` (no permission) and
  `ForegroundServiceStartNotAllowedException` (started from the background) are caught. An exception
  thrown out of `onStartCommand` crashes the app, after which `START_STICKY` restarts the service into
  the same throw. When the session ends without the user stopping it, a "Tracking stopped, your route
  is saved" notification is posted.
- **What happens to a session the user did not stop depends on whether the service survives:**
  - **The system kills the process:** `START_STICKY` restarts the service and **the same session continues**.
    The session is still marked active, so the restarted service runs the same permission → foreground →
    updates sequence. This does not start a new session without the user; the user never stopped this one.
    Measured 4 out of 4 times on the S23 (table below).
  - **The restart is refused, or the permission is gone:** the service ends the session and posts "Tracking
    stopped, your route is saved". The permission case was triggered here. A refused `startForeground` was
    only seen in the author's own app.
  - **Force-stop or reboot:** nothing restarts and no app code runs, so the session stays marked active. At
    the next app start, an active session with no service running in the process is ended and the screen
    says so. **Tracking does not start again by itself** (the author's decision): once the app has been
    closed that way, location tracking restarts only when the user asks.
- **Fixes still queued when a session ends are dropped.** Ending a session first clears an in-memory flag
  that the recorder checks, before anything that can suspend. Otherwise a fix already in the channel could
  be recorded just after Stop. The flag narrows the window but does not close it: a fix already inside
  `RecordFixUseCase` when Stop arrives still completes. Closing that too would mean checking the session
  inside the use case's lock, which is not worth it for at most one point, milliseconds apart. The window
  was not triggered on a device, so this part is reasoned. On the Android 13 emulator, recording was checked across start → stop → start: points were
  recorded while tracking, none while stopped, and recording resumed after restarting.
- **Commands are processed one at a time,** and ending uses `stopSelf(startId)`. A quick stop-then-start
  cannot interleave, and a stop does not kill a start that arrived after it.
- **Evidence:** Measured on a Galaxy S23 (Android 16):

  | Scenario | Result |
  |---|---|
  | Start | `isForeground=true types=0x00000008` (location) |
  | HOME, 45 s in the background | Fixes kept arriving. appops: `FINE_LOCATION mode=foreground` (no background permission), last access state `fgsvc`. **`ACCESS_BACKGROUND_LOCATION` is not needed** |
  | Process killed in the background, four times | Restarted after about 6, 1, 4 and 16 s. Each time the service regained foreground location and fixes continued; 0 refusals, 0 crashes |
  | Both location permissions revoked while tracking | System killed the process ("permissions revoked") and restarted the service 64 s later. The service ended the session 0.4 s after starting, with "no location permission", without reaching `startForeground`. 0 crashes; "Tracking stopped" notification posted |
  | `am force-stop` while tracking | No restart scheduled within 30 s. On reopening, the notice was shown, tracking was off and the route was kept |
  | Stop from the notification | Session ended "stopped by the user"; no "Tracking stopped" notification |

  On an Android 13 emulator (API 33, Google APIs):

  | Scenario | Result |
  |---|---|
  | 480 m walk from `geo fix` in 40 m steps, one third of it in the background | 5 points, spaced 108, 111, 125 and 126 m apart; a point was recorded while in the background; 0 crashes |
  | Four rotations (orientation forced through system settings, confirmed by screenshot size) | 4 Activity relaunches, same process, `lastStartId` unchanged (no new start command), marker count unchanged |
  | Task swiped away from recents | Task removed from recents; same process, still a foreground service, and the next point was recorded |

- **Not reproduced here:** in the author's own app, on the same device model, a restarted service was
  refused location foreground access. In this project it was never refused at restart delays up to
  16 s, and the condition that triggers the refusal is unknown; a candidate is the much longer delay
  after repeated deaths. The catch path is therefore implemented but has **not been observed in this
  project**.
- **Device check pending:** long idle periods (Doze), OEM battery management and OEM behaviour when a task
  is swiped away (measured on stock Android only), and Android 8–12 (API 26–32).

### D18. Approximate location: when precise location is really "blocked"

- **Problem:** to choose between "Allow" (show the system dialog again) and "Open settings", the usual
  signal is `shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION)`. After the user picks
  "approximate", it does not mean what it appears to mean.
- **Evidence:** Measured on a Galaxy S23 (Android 16), from a clean permission state:

  | Step | Rationale for FINE | Does a new request show a dialog? |
  |---|---|---|
  | First dialog: user picks approximate | false | **Yes**: the "change to precise location" upgrade dialog |
  | Upgrade dialog: user keeps approximate | false | No |

  The same `false` answers both situations, so it cannot distinguish them. The first implementation read it
  as "blocked" and showed "Open settings" when the upgrade dialog was still available.
- **Decision:** after an approximate-only result, "blocked" is concluded only if this result answers a
  request that had already asked for the upgrade, meaning the previous issue was precise-location denied
  or blocked. Otherwise the screen offers "Allow". After a process restart the screen may offer "Allow"
  once more; that tap returns immediately and switches to "Open settings".
- **Evidence for the fix:** measured on the same device. Approximate showed "Allow"; the upgrade dialog
  appeared; keeping approximate showed "Open settings"; pressing Start twice more showed no dialog and
  kept "Open settings". The first fix attempt flipped the card back to "Allow" at that last step, which is
  why a previously blocked state now counts as "upgrade already requested".

### D19. Location request: high accuracy, no platform distance filter

- **Decision:** `PRIORITY_HIGH_ACCURACY`, 10 s interval, 5 s fastest interval. No `setMinUpdateDistanceMeters`.
- **Rationale:** balanced power is accurate to about 100 m, as coarse as the rule itself. The interval is
  a battery bound, not a schedule; a marker still requires 100 m. A platform distance filter would
  measure from the last *delivered* fix, which may be one the gate rejected, while the rule measures
  from the last *recorded* point (D10).
- **Rejected:** a time-based fallback point while standing still. The spec asks for a marker every 100 m.
- **Evidence:** Reasoned. Observed on a Galaxy S23: fixes about every 5 s in the foreground and every
  10 s in the background.

### D20. Declare the Apache HTTP legacy library

- **Problem:** the map renderer is loaded from the device's Google Play services, not from the app. On an
  Android 13 emulator with Play services 23.18.18, the app crashed as the map loaded:
  `NoClassDefFoundError: org/apache/http/ProtocolVersion`. Apps targeting API 28+ must request that library
  explicitly. The Galaxy S23, with Play services 26.33.32, never showed the crash, but a reviewer's older
  emulator would crash on first launch.
- **Decision:** `<uses-library android:name="org.apache.http.legacy" android:required="false"/>` in the
  `:feature:tracking` manifest, next to the map dependency.
- **Evidence:** Measured by mutation on that emulator. With the line removed from the merged manifest the
  same crash returned; with it restored, the app ran with 0 crashes.

### D21. When addresses are resolved

- **Decision:** resolve a point's address in the background right after it is recorded and store it; if that
  fails, try again when the marker is tapped. Recording never waits for the network, and a stored address
  keeps working offline. The write is a plain `UPDATE` by id (D14).
- **Rejected:** resolving only on tap (every tap would need the network, and the first look at an old route
  would be slow). Also rejected: blocking the recording on the geocoder.
- **Failure is an expected result:** no geocoder, a timeout (10 s), offline, or a rate limit returns null.
  The card then says the address is unavailable and offers Retry.
- **Evidence:** Measured.
  - Galaxy S23: a marker recorded before geocoding existed showed its address about 0.3 s after the tap.
  - API 33 emulator: 2 of 5 points received their address when recorded, while the other 3 stayed empty.
    The reason was not captured, because the logs had been cleared between tests. Tapping one of them
    showed "Looking up address…" and the address about 6 s later, stored in the database.
  - Offline (Wi-Fi and data off, 0 ping replies): the card showed "Address unavailable" with Retry. The
    resolver logged `UNAVAILABLE: Unable to resolve host` and nothing crashed. After reconnecting, Retry
    showed the address within 8 s, and tracking stayed on throughout.

### D22. Stop and Start continue the same route

- **Decision:** stopping tracking does not end the route; only Reset does. After Start, the next accurate fix
  is measured against the last point of the existing route. If the user moved while tracking was stopped,
  that fix is recorded, and the polyline joins the two points with a straight line across the gap.
- **Rejected:** splitting the route into segments per session. That needs a session id on every point, a
  Room schema migration, and per-segment polylines, which is outside the case's scope.
- **Evidence:** Measured on the Android 13 emulator (verification log, "Recording across stop and restart").
  The emulator moved 144 m while tracking was stopped, and the first point after Start was recorded there.
- **README:** listed as a known limitation. The demo recording either explains it or does not move while
  tracking is stopped.

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
| 2026-09-16 | `verify-claim` skill, first run on this repo | Old results deleted, then confirmed 0 remained. `4 actionable tasks: 4 executed`, 0 FROM-CACHE, 25 / 25 from fresh XML. Positive controls: with no result files the script reports none; with a start time after the run it marks all 3 files stale and exits 1. Mutation M1 repeated: compiled, failed exactly the limit test, restored with matching md5, 25 / 25 again |
| 2026-09-17 | Emulator plan | The installed API 37 image (16 KB pages) was still offline after 4 minutes with hardware acceleration available; stopped. A standard API 36 Google APIs image is needed for mock-location tests |
| 2026-09-17 | Killing an app without root | `run-as <package> kill -9 <pid>` on a Galaxy S23 user build: ActivityManager logged the death. It replaces `adb root` for this test |
| 2026-09-17 | Permission flow on device (D16, D18) | First implementation showed "Open settings" after approximate location although the upgrade dialog was still available. Fixed, then found flipping back to "Allow" when Start was pressed while blocked; fixed again. The full sequence was re-measured from a reset permission state after each fix, with the APK timestamp checked to be newer than the source |
| 2026-09-17 | Service lifecycle on device (D17) | Foreground type, background fixes and appops, four kills, revocation, force-stop and the notification Stop action; results in D17. First notification-Stop attempt did not happen: the collapsed notification hid its action button, so the tap never occurred. Repeated after expanding the notification |
| 2026-09-17 | Reset while tracking (D15) | Passed on device |
| 2026-09-17 | Address on marker tap | A marker recorded before geocoding existed showed its address about 0.3 s after the tap |
| 2026-09-17 | API 33 emulator crash (D20) | The first launch crashed in the Play services map renderer. Diagnosed from the crash buffer, fixed, then confirmed by mutation: the crash returns without the manifest line and disappears with it |
| 2026-09-17 | Mock location accuracy (D12) | `geo fix` fixes carry 5.0 m accuracy and are recorded |
| 2026-09-17 | Walk simulation (D17) | 5 points spaced 108–126 m apart, including in the background. **The rotation part of this run was invalid:** `emu rotate` and `dumpsys` readings did not match a screenshot taken afterwards. Rotation was repeated by forcing orientation through system settings, with each rotation confirmed by screenshot size |
| 2026-09-17 | Address retry and offline (D21) | **First attempt invalid:** the old map renderer does not expose markers to accessibility, so every tap failed, and the helper's "NOT FOUND" output had been discarded. Repeated by tapping coordinates read from a screenshot, checking each opened card's coordinates against the database |
| 2026-09-17 | Swipe away from recents (D17) | **First attempt invalid:** the swipe missed and the task stayed in recents. Repeated with the recents screen verified by screenshot; the task was removed and tracking continued |
| 2026-09-17 | Instrumented result location | Connected test XML lands in `build/outputs/androidTest-results/connected/<variant>/`. `verify-claim` had reported 25 tests and ignored those 6; the skill now covers both locations |
| 2026-09-17 | Mutation M5: `last()` DESC → ASC | Previously passed all 25 tests (D10 gap). Now: compiled, 58 of 58 tasks executed, 0 from cache, and exactly `lastReturnsTheMostRecentlyRecordedPoint` and `routeIsOrderedByRecordingEvenWhenTheClockWentBackwards` failed. Restored with matching md5: 31 / 31 |
| 2026-09-17 | Recording across stop and restart | After adding the dropped-fix flag (D17): on the emulator, 2 points were recorded while tracking, none after Stop, and 2 after Start again. The first of those is the location the emulator had moved to while stopped, 144 m from the last point |
| 2026-09-17 | Missing key (D5) | Measured as described in D5. The first point recorded after Start was the emulator's stale location from an earlier test, 5.8 km away: the fused provider hands out its last known location first. Scripted demos must set the start location before starting |
| 2026-09-17 | Demo route coordinates | The first candidate line for İstiklal Caddesi (Taksim → Galatasaray) reverse-geocoded only to side streets south of it. It was shifted about 45 m north and re-checked: addresses on İstiklal Cd. (No:7, No:131), and on the map the line runs past Aya Triada and Çiçek Pasajı. 660 m in 55 m steps |
| 2026-09-17 | Demo recording, clip 1 | Five attempts, four invalid. (1) Taps located with `uiautomator` made the script run 146 s against a 118 s recording limit. (2) The Start tap landed on the first-launch splash screen, so nothing was recorded. (3) `uiautomator dump` fails while `screenrecord` runs ("null root node"), so nothing was recorded. (4) Marker gaps were 203, 163, 114 and 153 m, because fixes every 4 s were faster than the location request delivers (fastest 5 s in the foreground, 10 s in the background). (5) Valid: taps wait for readiness (a pixel on the Start button, window focus on the permission dialog), 55 m steps every 6 s in the foreground and 110 m steps every 10 s in the background. Gaps 145, 109, 112, 107 and 110 m; 106.7 s of video (read from the MP4 header); the last 2 points recorded in the background; 0 crashes |
| 2026-09-17 | Demo recording, clip 2 | Valid on the first attempt, each step checked: service record gone after Stop, task gone from recents, route kept after reopening, 0 points after Reset. 73.0 s of video, 0 crashes |
| 2026-09-17 | Stale emulator location | `geo fix` updates the platform's last location only while some app is listening. Without a warm-up, the first recorded point was the previous run's end point. Before each recording, the start point was sent while tracking, then the app was reinstalled |
| 2026-09-17 | Tests for the README | `verify-claim`: 25 JVM tests (11 + 5 + 9) and 6 instrumented tests (API 33 emulator). 31 / 31 from fresh XML; 58 of 58 tasks executed, 0 from cache |
| 2026-09-17 | JDK for a fresh clone | In a clone of the repository, Gradle 9.6.0 scanned the installed JDKs and started the daemon with JDK 25.0.1, matching `toolchainVersion=25` in `gradle/gradle-daemon-jvm.properties`. Downloading JDK 25 on a machine without one was not tested |
| 2026-09-17 | Demo clips shortened | `screenrecord` writes a frame only when the screen changes, so 72.2 s of clip 1 and 45.1 s of clip 2 were frames held longer than 1 s while waiting for location updates. Re-encoded with `ffmpeg -i IN.mp4 -vf "setpts='if(eq(N,0),0,PREV_OUTPTS+min(PTS-PREV_INPTS,1.0/TB))'" -fps_mode vfr -enc_time_base:v demux -c:v libx264 -crf 23 -pix_fmt yuv420p -movflags +faststart -an OUT.mp4`. **First attempt invalid:** without `-enc_time_base:v demux` the encoder used a time base derived from the clips' average of about 4 fps and dropped frames (148 of 432, 53 of 405). With the demuxer's 1/90000 time base: 432 and 405 frames kept; clip 1 106.7 → 54.2 s and 3.04 → 1.75 MB; clip 2 73.0 → 40.0 s and 3.31 → 1.60 MB. The longest gap between frames is 1.000 s; the final frame of clip 2 stays 1.16 s. Time on screen, original → 1.0 s cap: permission dialog 2.08 → 2.08 s, reset dialog 2.97 → 2.97 s, address card 6.29 → 1.73 s. A 1.5 s cap was also made for comparison: 63.5 s and 45.0 s, address card 2.23 s. The address-card times were confirmed on a contact sheet of the frames, after a frame dump taken with `-ss` had shown the frame after the card instead |
