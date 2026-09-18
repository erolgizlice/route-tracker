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

### D7. MVI with one state, one entry point and a pure reducer; Koin for injection

- **Decision:** the screen observes one `StateFlow<TrackingState>` and sends every user action through
  `onIntent(TrackingIntent)`; the one-off actions only the UI can perform travel back on their own effect
  channel. Every transition an intent causes is one pure function - `ScreenState.reduce(intent, location)`
  in `TrackingReducer.kt` - with no Android, no coroutines and no repository in it. What the platform says
  about location is read once per intent and handed in as a `LocationSnapshot(hasPrecise, hasAny,
  isEnabled)`. The side effects that belong to a transition - starting or stopping the service, resetting
  the route, resolving an address, sending an effect - stay in the ViewModel and run after the reduction.
  The selected marker is stored as an id and resolved against the current points, so a route reset closes
  the details card without any extra code.
- **Rationale:** the team's stack (MVI, Koin, Flow). One state object keeps rotation and process
  recreation to a single re-render. And the transitions are where the decisions are: the permission ladder
  of D18 - when a false rationale really means "blocked" - is a rule, not a call to the platform, and while
  it lived between `locationAccess` calls inside the ViewModel the only way to check it was on a device.
  As a pure function it is eight lines of JVM test.
- **Rejected:** leaving the transitions inside the `when` in `onIntent`, each branch doing
  `screen.update { copy(...) }` next to its own side effect. That is what this was until now. It works and
  it is short, but the rules in it can only be reached through a ViewModel, a scope and four fakes.
- **Rejected:** a sealed `Command` type returned beside the new state, so that the effects were data too.
  The ViewModel's `when` already says what each intent does; a second hierarchy would have to be kept in
  step with the first, and no test becomes possible that is not possible now.
- **Not a reduction:** two state changes do not answer an intent and stay in the ViewModel - the address
  lookup's result, which depends on the route the screen does not own, and the stale session found once at
  start (D17).
- **Behaviour:** unchanged, deliberately. Two things about it are new and neither is visible: the snapshot
  is read once per intent, so up to three cheap platform reads happen on intents that did not need them,
  and a permission result now moves the state once instead of twice.
- **Evidence:** Measured, on the JVM and on the phone. 18 JVM tests in `TrackingReducerTest` cover the ladder, the device-wide switch,
  resume, reset, selection and the intents that must not move the state. Two mutations: swapping
  `PreciseLocationDenied` and `PreciseLocationBlocked` failed exactly the four tests about the approximate
  ladder, and dropping the `!upgradeAlreadyRequested` term - the D18 subtlety - failed exactly the one test
  that is about it. Restored with matching md5 and 18 / 18 again. The wiring from an intent to its effect
  is what no test covers, so it was checked on the phone as well: the permission dialog, the "location is
  off" card, a session recorded from mock fixes, an address, Stop, a restart that kept the route, and Reset
  all behaved as before (verification log).

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
- **Where the policy lives:** the policy itself - return the stored address if there is one, otherwise
  resolve, write only with `UPDATE`, and return null when it cannot be resolved - sits in `:data`, in
  `RoomAddressLookup`. It is really a use case, and it could have gone to `:core` as
  `EnsureAddress(repository, resolver)`, where fakes would drive it on the JVM in milliseconds. It was left
  in `:data` because its only dependencies are the DAO and the geocoder, and because the UPDATE-only
  restriction (D14) belongs next to the DAO that has to honour it; the price is that this policy is covered
  by the device measurements below rather than by a JVM test.
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

## Startup and the look of the map

### D23. Route markers are four bitmaps, shared by every marker

- **Decision:** a marker is drawn in one of four looks - the start, the points in between, the newest
  point, and the point whose address card is open. Each look is one bitmap, drawn once with a `DrawScope`
  and shared by every marker that uses it, so a route of a thousand points holds four bitmaps. Which look
  a marker gets is a pure function of the index, the number of points and the selected id
  (`routeMarkerStyle`), so it is covered by JVM unit tests instead of a screenshot.
- **The pins are anchored at their tip and carry transparent padding.** The newest point is where the user
  is standing, so its marker sits under the my-location dot; an icon anchored at its tip keeps its head
  clear of the dot, and the padding makes the tap target larger than the 21 dp dot it draws.
- **Rejected:** `MarkerComposable`, which composes Compose content for every marker; numbered markers,
  which need one bitmap per number; and the default pin, which says nothing about which end of the route
  it is.
- **Evidence:** Measured (verification log, "Markers with 1000 points" and "Tapping the newest marker").
  - 1000 points on screen, 10 s of panning, Galaxy S23, debug build: the default markers produced 583
    frames with 1 janky frame (0.17 %) and a 99th percentile of 6 ms; the four bitmaps produced 582
    frames, 1 janky frame, 99th percentile 6 ms. Cold start with the same 1000 points went from 3370 ms
    to 3250 ms fully drawn, which is within the run-to-run spread.
  - **So this is not a performance claim.** It costs nothing measurable and it adds the four looks and the
    selected state. `dumpsys gfxinfo` counts the frames the app's own render thread produced; the map
    draws into its own surface, so these numbers are the cost the markers put on the app, not the cost of
    the map's own rendering.
  - With the my-location dot exactly on the newest point, five independent trials (open the app, one tap)
    opened that point's card five times out of five.

### D24. What makes the app slow to open, measured

- **Decision:** three changes, each measured on a release-like build (see the `benchmark` build type):
  1. the map is composed only after the stored route has been read, and its camera opens on the last
     point instead of moving there afterwards;
  2. the map area is covered by the app's own background colour until the map reports itself loaded,
     capped at 3 s so that a keyless or offline map still shows the route;
  3. `ReportDrawnWhen` marks the screen fully drawn when the route and the map are both on screen, so
     `am start -W` and startup profilers stop at something the user can use.
- **Rejected:** `MapsInitializer.initialize` in the Activity before `setContent`. Measured on both
  devices: no improvement, and the first frame was equal or slightly later (S23 benchmark cold start:
  246 ms first frame and 735 ms fully drawn without it, 241 ms and 791 ms with it; the emulator was 859 /
  2896 against 842 / 2912). The renderer already loads inside the first composition, which is why moving
  it a few milliseconds earlier changes nothing. Also rejected: calling it from `Application`, where a
  sticky service restart would pay for a map that has no Activity to show it (D8).
- **Rejected:** generating a Baseline Profile for this app, which needs a macrobenchmark module and a
  second device run; out of scope for the case. The libraries' own profiles are installed:
  `androidx.profileinstaller` 1.4.0 is on the runtime classpath and the build compiles an ART profile
  into the APK.
- **Where the time goes** (S23, benchmark build, cold start with a 7-point route, medians of 5 runs):
  process start to `Application.onCreate` 14 ms, `startKoin` 1 ms, first frame 145 ms, route read from
  Room 216 ms, map object ready 230 ms, map finished rendering 615 ms, fully drawn 677 ms. The map's
  tiles dominate; the app's own code is a small part of it. A debug build is about three times slower
  throughout, which is why the numbers in the README say which build they come from.
- **While the route is being read there is neither a map nor a placeholder on screen**, because both of
  them live inside the same condition. The window is short - 216 ms on the S23 in the benchmark build -
  and it shows the window background, the same colour as the placeholder, so there is nothing to see
  happening: the launch screen hands over to a surface of one colour and the map appears inside it.
- **The 3 s cap was measured, not guessed.** Without a Maps key the map still reports itself loaded
  (3.8 s on the API 36 emulator) and draws the route on its empty grid, so the placeholder goes away by
  itself. Offline with an empty tile cache it never reports itself loaded and draws nothing at all: no
  tiles, no markers, no my-location dot. The cap is what keeps the placeholder from covering that state,
  and the Google logo, for good.
- **"Fully drawn" reports at the cap as well** (`isMapLoaded || placeholderTimedOut`). Offline with an
  empty tile cache the map never reports itself loaded, so the screen used to never be reported fully
  drawn at all; it now reports 5.3 s after launch on the emulator, which is the 2.2 s route read plus the
  3 s cap. The online numbers are unchanged: S23 benchmark cold start 127 ms to the first frame and
  510 ms fully drawn before the change, 131 / 503 ms after, and a clean install 156 / 2038 ms before,
  146 / 1876 ms after (medians of 5 and 3 runs, no instrumentation in the build).
- **The marker count is not the stored one until the route is read.** The control bar is drawn before the
  route arrives, so it says "0 markers" for that window - measured frame by frame in clip 2, where the card
  appears 0.65 s after the launch and the count changes from 0 to 7 half a second later, at 0.55 s. The flat
  placeholder over the map area makes it easier to notice than the old grid did. Hiding the count until the
  route loads would move the card's height mid-launch, so it stays as it is and is written down instead.
- **No disk reads on the main thread from app code:** with StrictMode's thread policy on, the API 36
  emulator logged no violations, and the S23 logged two platform font reads (`Typeface.getFullFlipFont`,
  15 ms each) plus four that Play services suppresses in its own code.
- **The tile bytes did not change.** Opening the camera on the route was expected to save a tile load at
  the default zoom; on a clean install the first launch received 777 KB before the change and 777 KB
  after it, so no tiles were saved. What changed is the first frame (246 ms to 145 ms on the S23, 859 ms
  to 455 ms on the emulator) and that the city view is never on screen.

### D25. The map runs to the bottom edge, behind a translucent card

- **Decision:** the bottom overlay reaches the bottom edge of the screen, so the map is not cut off by a
  strip above the navigation bar. Four parts:
  1. `enableEdgeToEdge` with a transparent style for both bars, and, on API 29 and above,
     `window.isNavigationBarContrastEnforced = false`, so the system stops painting its own scrim behind
     the navigation bar. `minSdk` is 26, so the flag is behind a version check.
  2. The app draws that scrim itself, **between the map and the card**: a box as tall as the navigation
     bar inset plus 32 dp, filled with a vertical gradient of the theme's `background` colour at alpha
     0, then 0.4, then 0.8 at the bottom edge. It is in the app's own colour, so it lightens the map
     rather than darkening it, and it never darkens the card, which sits on top of it. The three
     numbers - 0.8 at the edge, half of it in the middle, 32 dp above the inset - are the proportions
     that sat right in the author's own app, and they live next to the card's alpha in one place so the
     two cannot drift apart.
  3. The scrim is decorative: `clearAndSetSemantics {}` leaves it out of what a screen reader traverses.
  4. The overlay takes only the horizontal and top insets, and the card pads its **own content** by the
     navigation bar inset, which keeps the buttons above the gesture bar or the three buttons. The card's
     background is the surface colour at 85 %, so the map shows through it.
- **Rejected: a real blur behind the card.** The map renders into its own surface, which a Compose
  `Modifier.blur` cannot sample - it would blur an empty composable. The only way to get a blurred map
  would be to blur the whole map view, which would blur the route and the markers with it.
- **Rejected: moving the Google logo.** The SDK pins it to the bottom edge of the map view. The map's
  bottom `contentPadding` lifts it, which is what this screen does, but padding also shrinks the area the
  camera can use, and the Maps Platform terms require the attribution to stay visible. So the logo stays
  where the SDK puts it, above the card.
- **Evidence:** Measured on both devices (verification log, "Edge-to-edge bottom card").
  - **Before:** on the API 36 emulator the map already ran behind the gesture bar - the rows from y=2240 to
    the bottom vary across the width (mean deviation 11-21 of 255). On the Galaxy S23, with three-button
    navigation, the same rows were a flat near-white layer, RGB (252, 253, 252) with a deviation of 1-9:
    the system's contrast scrim.
  - **After:** both devices show map pixels in that region (deviation 5-12), and nothing else moved.
  - **Text contrast**, measured inside the bounds `uiautomator` reports for the two text lines: 19.2 : 1
    against the card's background. Where a dark map label shows through the translucent card, the darkest
    background found is RGB (202, 196, 208), which is 10.0 : 1 against `onSurface` #1D1B20 - above the
    4.5 : 1 target with room to spare.
  - **The Google logo stays visible above the card and the scrim:** its lowest pixel is y=1884 on the S23
    and y=1894 on the emulator, while the card starts at about y=2023 and y=2064 and the scrim only covers
    the last 154 px. The map's bottom `contentPadding` is measured from the card's height, which is the
    taller of the two, so no extra padding was needed for the scrim. The Maps SDK has no slot for moving
    the logo; `contentPadding` is the only lever, and it also shrinks the area the camera can use.
  - **Tap targets, with the scrim in place:** on the S23, Start reacted to five of five taps and Reset
    opened its dialog five of five times, with the buttons ending at y=2131 and the bar's inset starting
    167 px below them. On the emulator, Start, Stop and Reset each answered five of five. Stop was not
    tapped on the phone: tracking there would record the tester's real location.
  - **The scrim is not in a screen reader's path:** `uiautomator` still lists a node at [0,2186][1080,2340],
    but with no text, no content description, not focusable and not clickable, so the six nodes it does
    traverse are the map, the my-location button, the two text lines and the two buttons.
  - The screen is not locked to one orientation. In landscape the card spans the width, the buttons sit at
    y=877-932 of 1080 and the gesture bar draws over the scrim.
  - The keyless build (the "Map unavailable" card and the route count are still there, no crash) and the
    placeholder path were re-checked after the change: the card is drawn over the placeholder from the
    first frame, with the count showing 0 until the route is read.

### D26. Release signing, and which build each clip comes from

- **Decision:** a real `release` build type - minified, resources shrunk, not debuggable - signed from four
  Gradle properties that live only in `~/.gradle/gradle.properties`: `RT_RELEASE_STORE_FILE`,
  `RT_RELEASE_STORE_PASSWORD`, `RT_RELEASE_KEY_ALIAS`, `RT_RELEASE_KEY_PASSWORD`. If any of them is missing
  or blank the signing config is **not created at all**, and `assembleRelease` still produces an unsigned
  APK, so a reviewer's clone builds. `*.jks` and `*.keystore` are git-ignored; the keystore and its
  passwords never enter the repository, and the passwords are never printed.
- **`benchmark` is `release` plus one line:** `initWith(getByName("release"))` and the debug signing
  config. Nothing else is set twice, so the two cannot drift apart - which is what makes the startup and
  stress numbers, all measured on `benchmark`, worth anything for a release build.
- **Rejected: signing the release build with the debug key.** The Maps key is restricted to a certificate,
  and reusing the debug one would have avoided a Cloud Console change. But then "release" would not be a
  release: the debug keystore ships with the SDK, with the same password for everyone, so anyone could sign
  an update for this package.
- **Rejected: recording the thousand-marker clip with the release build.** Those points are written
  straight into the app's database, which needs `run-as`, which needs a debuggable build. Seeding with the
  debug build and then updating to the release build does not work either: the signatures differ and the
  install is refused. So clip 3 stays on the `benchmark` build - the same code and the same R8 output - and
  the README says which clip came from which build.
- **A third build type, `demo`, for the recordings.** It is `initWith(release)`: the same signature, the
  same R8, plus one source set `release` does not have. `app/src/demo/` holds a broadcast receiver that
  turns on `FusedLocationProviderClient.setMockMode` and feeds fixes along a made-up route, and that seeds
  a thousand points straight into storage for the stress clip. The library itself is in every variant
  through `:data` - that is where the fused provider comes from - so what makes this demo-only is the
  receiver and `:app`'s own compile dependency on it, declared as `"demoImplementation"`. No other variant
  contains code that calls `setMockMode`.
- **Rejected: the platform's test providers.** Measured twice on the phone, with and without internet: with
  a mock `fused` provider, and again with a mock `gps` one, `dumpsys location` showed the mock fix while the
  app kept receiving real ones (accuracy 12-43 m, points 1-6 m apart, i.e. standing still). Play services'
  fused client ignores them. The app's own `setMockMode` is the path it does honour.
- **Rejected: putting the mock source in the production path behind a flag.** A release build that can fake
  the user's location is a release build that can fake the user's location.
- **Evidence:** Measured (verification log, "Release build on the emulator").
  - `assembleRelease` with the four properties present produces `app-release.apk`, signed with certificate
    `B9:E3:24:73:94:3C:19:D0:F5:17:1E:D1:55:2F:78:AB:78:05:2D:AA`; with them absent, the same task produces
    `app-release-unsigned.apk` and still succeeds. That was checked with a Gradle home that has no such
    properties, not by editing the author's own.
  - `release` and `benchmark` produce APKs of exactly the same size, 1 433 090 bytes, with the same dex
    size, 1 954 484 bytes. Only the certificate differs: the debug one is
    `A0:96:6F:AE:45:69:C0:71:FB:F1:AF:E8:02:20:FC:D3:AD:05:A6:7C`.
  - **R8 broke nothing**, checked on the signed release APK on the API 36 emulator: Start put the service
    in the foreground; a 330 m scripted walk recorded 3 points; addresses were resolved and stored (for
    example "Cihangir, Sıraselviler Cd. No:9-2"); Stop ended the session; a force-stop and reopen kept the
    route; Reset emptied it. The log holds no `ClassNotFoundException`, `NoSuchMethodError`,
    `NoSuchFieldError`, `NoClassDefFoundError` or Koin failure, and no `FATAL` line. No extra ProGuard rule
    was needed; Room and Koin bring their own.
  - **The demo hooks are in the demo build only.** Scanning the dex and the merged manifest of each APK
    for `MockLocationReceiver`, `routetracker/demo` and `setMockMode`: release 0, 0, 0 and no receiver;
    demo 1, 1, 2 and the receiver; debug 0, 0, 1 - that one hit is the library's own method name, which is
    in the debug build because `play-services-location` is unminified there and reaches it through `:data`.
    The release build is minified, so even that name is gone.
  - **The mock path works and records the made-up route.** On the phone: `setMockMode(true)` returned
    `ok=true` with no `SecurityException`, and a scripted walk recorded points with an accuracy of exactly
    8.0 m and gaps of exactly 110.0 m, all of them on the İstiklal route, with their addresses resolved.
    Seeding wrote a thousand points in 14 s, and the control bar then read "1000 markers".
  - The map is grey in that build until the release certificate is registered, and the log says so exactly:
    `Authorization failure` followed by `Android Application (<cert_fingerprint>;<package_name>):` with the
    fingerprint above and `com.erolgizlice.routetracker`.

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
| 2026-09-17 | Demo re-recorded | The shortened clips still stuttered and jumped, so both clips were recorded again and replace those described in the rows above. New AVD `RouteTracker_API36`: Android 16, Google APIs x86_64, Play services 25.26.35, 6 cores, 6 GB RAM, host GPU. Recorded with the emulator's own recorder (`adb emu screenrecord start --fps 30 FILE.webm`). It ignored `--bit-rate`, producing 111 MB of VP9 for 34 s, so the clips were converted to H.264 at a constant 30 fps (`ffmpeg -i IN.webm -vf fps=30 -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart -an OUT.mp4`). A 34 s trial had a median of 39 ms and a maximum of 112 ms between captured frames. Movement: one `geo fix` per second at 11 m/s along the 707 m route. **Clip 1, four takes:** (1) gaps 108, 112, 196, 112 and 110 m, because the first delivery after HOME came 18 s late; (2) paused before HOME, gaps 110–132 m, but the background part was 35 s of a still home screen, so the shade now stays open during it; (3) gaps 108, 112, 187, 110 and 110 m, with two deliveries missed while full-size screenshots were being taken during the take; (4) valid, with no screenshots during the take: gaps 110, 110, 110, 143, 110 and 110 m, the last 3 recorded in the background, 105.4 s, 3162 frames, 7.4 MB. **Clip 2, three takes:** (1) invalid: a tap on the last marker, which sits under the my-location dot, did not open the card, so the "Close" tap landed on Stop and the "Stop" tap on Start; (2) invalid: the same marker needed a second tap; (3) valid on a marker away from the dot, whose first tap had opened the card 3 of 3 times beforehand: 37.5 s, 1126 frames, 4.9 MB. In both final clips the longest gap between frames is 33 ms. The README frames were extracted from the final clips |
| 2026-09-17 | Startup baseline | Measured with a temporary log patch (process start to each step) and the new `benchmark` build type, medians of 5 cold starts with a 7-point route. S23: 246 ms to the first frame and 735 ms fully drawn in the benchmark build, 850 / 2285 ms in the debug build. API 36 emulator: 859 / 2896 ms benchmark, 2120 / 4725 ms debug. Clean-install first launch (3 runs): S23 302 / 1394 ms benchmark. Where the time goes is in D24 |
| 2026-09-17 | Maps renderer preloaded early (rejected) | `MapsInitializer.initialize` in `MainActivity` before `setContent`: S23 benchmark cold start 241 ms first frame and 791 ms fully drawn against 246 / 735 without it; emulator 842 / 2912 against 859 / 2896. No gain, first frame equal or slightly later, so it was reverted (D24) |
| 2026-09-17 | Camera opens on the route | The map is composed after the route is read. S23 benchmark cold start: first frame 246 → 145 ms, fully drawn 735 → 677 ms; emulator 859 → 455 ms and 2896 → 3106 ms. The camera log now holds one position only, the route at zoom 16, where it used to hold the default city view at zoom 11 first. A recording of a cold start confirms no city view in any frame. The expected saving in tile bytes did not happen: a clean-install first launch received 777 KB both before and after |
| 2026-09-17 | Map placeholder | Visual change only, and the numbers agree: S23 benchmark, 145 → 151 ms to the first frame on a clean install, 1435 → 1437 ms fully drawn. The cap was checked in the two cases where the map never finishes loading; see D24 |
| 2026-09-17 | Markers with 1000 points | 1000 points 20 m apart (denser than the 100 m rule allows, so that all of them are on screen), 10 s of panning, debug build. S23: default markers 583 frames, 1 janky (0.17 %), p99 6 ms; the four shared bitmaps 582 frames, 1 janky, p99 6 ms. API 36 emulator: 87.9 % janky, p99 81 ms against 85.9 % and 77 ms - its own GPU emulation bounds both. Cold start with 1000 points, S23 debug: 3370 → 3250 ms fully drawn. No cost, and no performance claim |
| 2026-09-17 | Tapping the newest marker | With the my-location dot exactly on the newest point, five independent trials (open the app, one tap on the pin, located from a screenshot) opened that point's card 5 / 5. Two earlier attempts were invalid: the test route had been seeded with a 66 m gap between its last two points, closer than the 100 m rule can produce, and taps at the identical pixel are dropped by `adb shell input tap` - the same 1 / 4 pattern appears with the default markers, and a few pixels of jitter gives 4 / 5 on both |
| 2026-09-17 | Tests after the marker and startup work | `verify-claim`: 32 JVM tests (11 + 5 + 9 + 7) and 6 instrumented tests on the API 36 emulator, 38 / 38 from fresh XML, 73 of 73 tasks executed, 0 from cache. Mutation M5: letting a marker's position win over the selection failed exactly the two tests about a selected start and a selected end, compiled cleanly, and the file was restored to the same md5 |
| 2026-09-17 | Keyless and offline build after the changes | Debug build with `MAPS_API_KEY` removed (`local.properties` restored afterwards with the same sha256): `HAS_MAPS_API_KEY = false`, the "Map unavailable" card, the route drawn on the empty grid, no crash. `BitmapDescriptorFactory` is called inside the map's content, so the marker bitmaps are built after the SDK has started. Offline with an empty tile cache: the map draws nothing at all and the placeholder goes at its 3 s cap |
| 2026-09-17 | Fully drawn also at the placeholder's cap | Offline with an empty tile cache the screen was never reported fully drawn; with `isMapLoaded || placeholderTimedOut` it reports 5.3 s after launch (2.2 s route read plus the 3 s cap). Online is untouched: S23 benchmark cold start 127 ms first frame and 510 ms fully drawn before, 131 / 503 ms after; clean install 156 / 2038 ms before, 146 / 1876 ms after |
| 2026-09-17 | Startup numbers for the README, without instrumentation | The log patch itself costs about 150 ms of the "fully drawn" figure, so the README numbers were re-measured with it removed. Cold start with a 7-point route, medians of 5: S23 131 / 503 ms benchmark and 550 / 2209 ms debug; API 36 emulator 526 / 2365 ms benchmark and 1538 / 4008 ms debug. Clean install, medians of 3: S23 146 / 1876 ms benchmark, emulator 503 / 3834 ms |
| 2026-09-17 | Markers with 1000 points in a release-like build | Same gesture list on the S23 in the benchmark build: the default pin gave 594 frames, 0 janky, 99th percentile 6 ms and GPU 4 ms; the four shared bitmaps gave 568 frames, 0 janky, 5 ms and 3 ms. Written up with every run in `docs/stress/README.md` |
| 2026-09-17 | Mock location on the phone | Tried for the demo clips, so that the phone would show a made-up route. `appops set 2000 android:mock_location allow` plus a test provider was accepted by the platform (`dumpsys location` showed the mock fix), but Play services' fused client ignored it: with a `fused` test provider and again with a `gps` one, the app kept recording real fixes (accuracy 36, 43, 31, 16, 13 m, points 1-2 m apart, i.e. standing still). The app's data was wiped immediately, the test providers removed, the appop set back to default, and clips 1 and 2 stayed on the emulator |
| 2026-09-17 | Demo re-recorded with the marker and startup changes | All three clips come from the `benchmark` build. Clip 1 (emulator, 103.8 s, 6.9 MB): 7 points 110, 110, 110, 143, 110 and 110 m apart, the 143 m one across the switch to the background, no crash. A variant with the 47.6 s notification-shade stretch played at 4× is kept for the author to choose (68.1 s, 6.0 MB). Clip 2 (emulator, 35.6 s, 4.5 MB): the address card opened on the first tap of a marker tested 3 / 3 beforehand. Clip 3 (Galaxy S23, 11.3 s, 4.4 MB): 1000 seeded points, location permission revoked so no my-location dot and no real position, `dumpsys gfxinfo` from the same run reporting 568 frames and 0 janky. Every clip is constant 30 fps with a largest frame gap of 33 ms |
| 2026-09-17 | Privacy gate on the phone clip | A contact sheet at 1 frame per second was read before anything was committed: every frame inside the app, no my-location dot, no account name, nothing identifying in the status bar, and only public map labels. The phone was then restored and checked: 0 active mock providers, `mock_location` appop default, Do Not Disturb off, screen timeout back to 30 s, and the app's data cleared |
| 2026-09-17 | Keyless screenshot retaken | The first attempt was invalid: the app still had cached tiles from the keyed build, so the "missing key" screenshot showed a working map. After `pm clear` the keyless build draws nothing at all - no tiles, no markers - with the "Map unavailable" card over it and the route still counted in the control bar. `local.properties` came back with the same sha256 |
| 2026-09-17 | prompts.md checked against the transcript | 17 messages, 6 674 of 59 000 characters removed, every removal marked. The check splits each published message on its markers and matches the pieces against the original in order; a positive control (one letter changed, and 60 characters silently dropped) fails it. Messages 12, 13, 16 and 17 quote a `[removed: …]` marker themselves, which the check reports separately |
| 2026-09-17 | Sensitive-term scan, valid on the third attempt | The first run reported zero for all 25 terms, and its positive control then found nothing in the file scope. Two faults: on macOS `grep -i` does not fold the Turkish "İ" to "i", and the file scope was running without `-i` at all. Rewritten as a scan that folds case and Turkish diacritics over three scopes - 71 text files in the working tree, every commit message, every commit diff. Positive controls written in a different case and without diacritics (ISTIKLAL, GALATASARAY, ROUTE MARKER, co-authored-by) hit in every scope; all 25 terms: 0 hits. Nine binary files (eight media files and the Gradle wrapper jar) cannot be scanned and were read by eye |
| 2026-09-17 | Tests after the final round | `verify-claim`: 32 JVM tests (11 + 5 + 9 + 7) and 6 instrumented tests on the API 36 emulator, 38 / 38 from freshly generated XML, 73 of 73 tasks executed, 0 from cache |
| 2026-09-17 | Clean clone without a key | A clone of `polish/markers-startup` at 56979c8 with only `sdk.dir` in `local.properties`: BUILD SUCCESSFUL, 107 of 107 tasks executed, `HAS_MAPS_API_KEY = false`, 32 JVM tests passed, and `git status --porcelain` empty. `docs/media` is absent from the clone, because the media is not committed yet |
| 2026-09-17 | The marker count while the route is read | Measured frame by frame in clip 2 rather than estimated: the crop of the count line matches "0 markers" from 21.75 s to 22.30 s and "7 markers" from 22.30 s, so the wrong count is on screen for 0.55 s, after 0.65 s of launch screen. A review estimate of about 2 s covered the whole reopen, from the tap to the route on screen (D24) |
| 2026-09-18 | Edge-to-edge bottom card | Before: emulator rows y=2240-2339 varied across the width (deviation 11-21), so the map already ran behind the gesture bar; the S23's same rows were a flat RGB (252, 253, 252) contrast scrim (deviation 1-9). After turning the scrim off and extending the overlay: both devices show map pixels there. Contrast 19.2 : 1 on the card's text and 10.0 : 1 at the darkest point where the map shows through; the Google logo's lowest pixel stays about 200 px above the card; Start and Reset 5 / 5 on the S23, Start, Stop and Reset 5 / 5 each on the emulator; rotation, the keyless build and the placeholder path unchanged (D25) |
| 2026-09-18 | The app draws the navigation-bar scrim itself | Replaced the 24 dp fade above the card with a scrim between the map and the card: as tall as the navigation bar inset plus 32 dp, the theme's background colour at alpha 0 / 0.4 / 0.8 downwards, `clearAndSetSemantics {}`. Re-measured after the change: Google logo's lowest pixel y=1884 (S23) and y=1894 (emulator) against a card starting at y=2023 / y=2064 and a 154 px scrim; card text contrast 19.0-19.2 : 1 and 10.0 : 1 where the map shows through; Start, Stop and Reset 5 / 5 each on the emulator and Start and Reset 5 / 5 on the S23; the scrim's node carries no text, description, focus or click; landscape and the keyless build unchanged; 38 / 38 tests from fresh XML (D25) |
| 2026-09-18 | Release build on the emulator | `assembleRelease` signed from properties that live outside the repository; without them the same task produces an unsigned APK (checked with a Gradle home that has none). `release` and `benchmark` APKs are the same size to the byte, 1 433 090, with the same 1 954 484 byte dex; only the certificate differs. On the signed release APK: foreground service after Start, 3 points from a 330 m walk, addresses resolved and stored, Stop ended the session, force-stop and reopen kept the route, Reset emptied it, and the log had no ClassNotFound / NoSuchMethod / NoSuchField / NoClassDefFound / Koin error and no FATAL. An earlier reading of "no addresses" was invalid: the emulator had just booted and its network was not up yet; repeated with the network confirmed, the addresses were there (D26) |
| 2026-09-18 | Mock location on the phone, second attempt | Repeated after the phone's internet was restored, in case that was the reason: it was not. With a `fused` test provider the app again recorded real fixes (accuracy 25, 16, 16, 12, 12 m, 2-6 m apart). The app's data was wiped immediately, the provider removed and the appop set back to default (D26) |
| 2026-09-18 | The app's own mock source | `appops set <package> android:mock_location allow`, then `FusedLocationProviderClient.setMockMode(true)` from a receiver in the demo source set: `ok=true`, no `SecurityException`. A scripted walk at 11 m/s recorded 4 points with accuracy 8.0 m and gaps of exactly 110.0 m, all on the made-up İstiklal route, with addresses resolved ("Katip Mustafa Çelebi, İstiklal Cd. No:61"). Seeding a thousand points took 14 s and the control bar read "1000 markers" (D26) |
| 2026-09-18 | Demo hooks are not in release | Dex and manifest scan of the three APKs for `MockLocationReceiver`, `routetracker/demo` and `setMockMode`: release 0 / 0 / 0 with no receiver in the manifest; demo 1 / 1 / 2 with the receiver; debug 0 / 0 / 1, the library's own method name. Release and demo APKs differ by 48 bytes (D26) |
| 2026-09-18 | The demo recorded on the phone | All three clips come from the Galaxy S23 with the demo build. Clip 1 (95.4 s): fresh install, the Turkish system permission dialog, 7 points recorded from mock fixes at 8.0 m accuracy, the calculator in front while tracking continued, then back to the app. Clip 2 (33.0 s): the address card, Stop, the app killed while the calculator was in front, reopened with the route kept, Reset to zero. Clip 3 (11.3 s): a thousand seeded points, location permission revoked, `dumpsys gfxinfo` from the same run reporting 573 frames with 2 janky (0.35 %) and a 99th percentile of 5 ms. Every clip is constant 30 fps with a largest frame gap of 33 ms |
| 2026-09-18 | Privacy gate on the phone clips | Contact sheets at one frame per second or slower were read before anything was committed. The first clip 1 take was rejected: the Clock app, used as the background scene, showed the tester's own alarms. Re-recorded with the calculator, which shows only "0". No home screen, recents or notification shade in any frame, no account names, nothing identifying in the status bar. Afterwards the phone was restored and the restore verified: mock_location appop back to default, location permissions revoked, app data cleared, Do Not Disturb off, screen timeout back to 30 s, no active mock providers |
| 2026-09-18 | The screen's transitions as a pure function (D7) | `ScreenState.reduce` was lifted out of the ViewModel with the behaviour unchanged, and 18 JVM tests written for it. Mutation: `PreciseLocationDenied` and `PreciseLocationBlocked` swapped in the approximate branch - compiled, and failed exactly the four tests about that ladder, each with the expected message. Mutation: the `!upgradeAlreadyRequested` term dropped from the same condition, which is the D18 subtlety - compiled, and failed exactly the one test that is about it. Restored with matching md5 and 18 / 18 again |
| 2026-09-18 | Full suite after the reducer | Old results deleted and 0 remained. `:core:test :feature:tracking:testDebugUnitTest :data:connectedDebugAndroidTest --rerun-tasks --no-build-cache`: 73 actionable tasks, 73 executed, 0 FROM-CACHE, 56 / 56 from fresh XML with `stale_files=0` - 50 JVM tests and the 6 instrumented ones on the Galaxy S23 |
| 2026-09-18 | The refactored screen on the phone (D7) | Demo build carrying the reducer, every fix from its own mock source. Start with no permission opened the system dialog (`GrantPermissionsActivity`), and cancelling it left "Location permission needed"; with precise granted and the device-wide switch off, Start showed "Location is off" and started no service, and switching the location back on and returning to the screen cleared the card; a session on mock fixes recorded 3 points over 230 m, a marker's card showed its address, Close kept the session running, Stop ended it (`Ending session: stopped by the user`, 0 foreground services), force-stop and reopen kept the route, and Reset emptied it. No FATAL, no ClassNotFound / NoSuchMethod / Koin error. Restored afterwards and verified one item at a time: mock appop `default`, both location permissions revoked, app data cleared, the location switch back on |
| 2026-09-18 | Test counts re-measured for the README | Old results deleted, 0 remained, then the same three tasks with `--rerun-tasks --no-build-cache`: 73 of 73 executed, 0 FROM-CACHE, 56 / 56 from fresh XML with `stale_files=0` - 25 in `:core`, 25 in `:feature:tracking` and 6 instrumented on the Galaxy S23. The README had been left at the numbers of 2026-09-17 |
| 2026-09-18 | The privacy gate missed the opening of clip 1 | Every frame of the committed media was read again before the GIF previews were cut, and both clip 1 files turned out to begin with 2.0 s of the phone's Clock app, the tester's own alarm labels and times legible. The gate row above is about the middle of the clip, where the calculator is in front; the opening was never covered, because `clip1_s23.sh` starts the recorder and then launches the app, so the take opens on whatever was already on screen. Both files were re-encoded from 2.5 s (libx264, crf 22, constant 30 fps): 93 s and 63 s, first frame now the app's own placeholder, checked frame by frame afterwards. Clip 2, clip 3 and the four screenshots were re-read in full and are clean. The rule in `CLAUDE.md`, the `device-check` skill and `docs/ai/README.md` now says to start at the first frame and finish at the last |
| 2026-09-18 | GIF previews for the README | GitHub does not play a repository video inline, so each clip has a GIF: clip 1, seconds 7-47 of the shortened take at 3.3×, 8 fps, 280 px, 128 colours, 12.0 s, 0.88 MB; clip 2, seconds 1-9 in real time, 10 fps, 300 px, 128 colours, 8.0 s, 0.32 MB; clip 3, seconds 6-14 in real time, 6 fps, 220 px, 24 colours, 8.0 s, 1.69 MB. 1 MB was not reachable for clip 3 and the limit is the content: panning changes every pixel, so consecutive frames share nothing. Measured on the same 8 s window - 8 fps / 300 px / 128 colours 8.57 MB, 8 fps / 260 px / 48 colours 3.50 MB, 6 fps / 240 px / 16 colours 1.71 MB, 5 fps / 240 px / 24 colours 1.62 MB, which is where the panning starts to look choppy. All 224 GIF frames were read in contact sheets, in the form they are committed |
| 2026-09-18 | The clips themselves left the repository | The three GIF previews stay and the four MP4 files were removed, so the README plays without them and a clone is a few megabytes rather than twenty-one. The earlier blobs were removed from the history as well, not only from the tip: `git filter-repo --invert-paths` over the four paths, then `main`, the branch and the `v1.0` tag force-pushed, and a fresh clone checked for the old object ids. What each take showed, and the measurements from it, stay in the README and in the rows above |
