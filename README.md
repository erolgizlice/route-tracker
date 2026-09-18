# Route Tracker

An Android app that tracks the user's location in the foreground and background and adds a map marker
each time the user has moved 100 m from the last marker. Tapping a marker shows its address. Tracking can
be started and stopped, and the route stays on the map across app restarts until it is reset. Kotlin,
Jetpack Compose, Google Maps, Room, DataStore, Koin, and a location foreground service.

Every design decision, the alternative it beat, and how it was verified is in
[`docs/decisions.md`](docs/decisions.md). The numbers below cite those entries as D1–D24.

## Demo

Three clips, all recorded on a **Samsung Galaxy S23** (Android 16) with the `demo` build type: `release`
with the same certificate and the same R8 output, plus one source set that release does not have - it can
feed the fused location provider with mock fixes and seed a route (D26). The route on screen is made-up:
707 m along İstiklal Caddesi, Istanbul, from Taksim to Galatasaray, fed one fix per second at about
11 m per second with an accuracy of 8 m. All three play in real time at a constant 30 frames per second.

- [Clip 1: fresh install, precise location, start, walk, another app in front, back to the route](docs/media/clip1-tracking.mp4) (95 s)
- [Clip 2: the address of a tapped marker, stop, the app killed and reopened with the route kept, reset](docs/media/clip2-address-stop-reset.mp4) (33 s)
- [Clip 3: a thousand markers, panned and zoomed](docs/media/clip3-stress.mp4) (11 s). Recorded with the
  location permission revoked, so no real position can be on screen; the numbers behind it are in
  [`docs/stress/README.md`](docs/stress/README.md).

While the app is in the background in clip 1, a calculator is in front: the phone's home screen, its
recents and its notification shade stay out of frame, because they carry the tester's own data. The system
permission dialog is in Turkish, the phone's language; the app itself ships English strings only.

| Seven markers, the last three recorded in the background | The foreground notification | Address of a tapped marker |
|---|---|---|
| <img src="docs/media/route.png" width="240" alt="Route along İstiklal Caddesi with seven markers: a teal pin at the start, five teal dots, and a red pin at the newest point"> | <img src="docs/media/notification.png" width="240" alt="Notification reading Tracking your route, six markers, a new one every 100 m"> | <img src="docs/media/address.png" width="240" alt="Details card with the address of the tapped marker, which is drawn as a blue pin"> |

In clip 1 the markers are 110 m apart, which is what a fix every 5 s at 11 m per second gives against a
100 m rule. Three of the seven are recorded while the app is in the background.

## Requirements and where they are implemented

| Requirement | Implementation | Decisions |
|---|---|---|
| A marker for every 100 m of movement | `DistanceGate` and `RecordFixUseCase` in `:core`; anchor query `RouteDao.last()` | D10–D14 |
| Track in the foreground and in the background, for as long as possible | `TrackingService`, a location foreground service | D17, D19 |
| Tapping a marker shows its address | Details card in `TrackingScreen`; `RoomAddressLookup`, `GeocoderAddressResolver` | D21, D14 |
| Start and stop tracking | `TrackingViewModel` → `ServiceTrackingController` → `TrackingService`; permission flow | D16–D18 |
| Reset the route | Confirmation dialog → `RouteRepository.reset()` | D15, D22 |
| The route is shown again when the app is reopened, until reset | Room table `route_points`, observed by `TrackingViewModel` | D9, D10 |

## Setup

**Tooling used for this project:** Android Studio 2026.1 (build AI-261.26222.65.2614.16204760), Android SDK
platform 37, Gradle 9.6.0 through the wrapper, AGP 9.4.0, Kotlin 2.4.20. `minSdk` is 26 and `targetSdk` 37.

**JDK:** `gradle/gradle-daemon-jvm.properties` asks for a JDK 25 to run the Gradle daemon. In a fresh clone
on the author's machine, Gradle found the installed JDK 25.0.1 and used it. On a machine without JDK 25,
Gradle can download one from the URLs in that file; that path was not tested here.

### Google Maps API key

The key is not in the repository. To see the map, create your own:

1. In the Google Cloud console, enable **Maps SDK for Android** and create an API key.
2. Restrict it to Android apps, with package name `com.erolgizlice.routetracker` and **your own** debug
   SHA-1. Restrict its APIs to Maps SDK for Android. Get the SHA-1 with:

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```

3. Add it to `local.properties` (git-ignored) and rebuild:

   ```properties
   MAPS_API_KEY=your-key
   ```

   A `MAPS_API_KEY` environment variable works too. Changing the key invalidates Gradle's configuration
   cache, so the next build picks it up (D4).

Because the key is tied to a debug signing certificate, use a debug build from the machine whose SHA-1 you
registered.

**Without a key**, the build still succeeds, tracking and recording still work, and the map area shows an
explanation instead of a blank map (D5):

<img src="docs/media/missing-key.png" width="240" alt="Map unavailable card shown when MAPS_API_KEY is missing">

## Build and test

```bash
./gradlew assembleDebug
./gradlew :core:test :feature:tracking:testDebugUnitTest               # 32 JVM tests: the 100 m rule, marker styles
ANDROID_SERIAL=<device-serial> ./gradlew :data:connectedDebugAndroidTest   # 6 instrumented tests: Room SQL
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleBenchmark        # release-like build for measurements
./gradlew assembleRelease          # unsigned unless the signing properties are set (see below)
```

On 2026-09-17 all 38 tests passed from freshly generated results (32 JVM, 6 on an API 36 emulator).
Connected tests run on every attached device unless `ANDROID_SERIAL` names one. To move an emulator, use
`adb emu geo fix <longitude> <latitude>` (longitude first).

**The `benchmark` build type** exists because startup numbers from a debuggable build say little about what
a user gets: no ahead-of-time compilation, no minification, and debug-only checks. It is `release` plus one
line - the debug signing config - so the Maps key restricted to that certificate still works; everything
else, including minification and resource shrinking, is inherited with `initWith`, and the two APKs come out
the same size to the byte (D26). It is a measurement tool, not a shipping build.

**The `demo` build type** is `release` with the same certificate and the same R8 output, plus
`app/src/demo/`: a receiver that feeds the fused provider with mock locations and can seed a route. It
exists so the demo can be recorded on a real phone without walking a real route, and none of it is in
`release` or `debug` - the dex of each APK was scanned to prove that (D26).

**Signing a release build** needs four properties in `~/.gradle/gradle.properties`, never in the repository:
`RT_RELEASE_STORE_FILE` (an absolute path), `RT_RELEASE_STORE_PASSWORD`, `RT_RELEASE_KEY_ALIAS` and
`RT_RELEASE_KEY_PASSWORD`. Without them `assembleRelease` still works and produces an unsigned APK. A signed
release build needs its own certificate registered on the Maps key, or the map stays grey and logcat says
`Authorization failure`.

## Architecture

```
:app ──► :feature:tracking ──► :core
  └────► :data ───────────────► :core
```

- **`:core`** is pure Kotlin/JVM: the domain model, the 100 m rule, `RecordFixUseCase`, and the contracts the
  UI needs. Any Android import there is a compile error (D6).
- **`:data`**: Room for the route, DataStore for the "session active" flag, the foreground service with the
  fused location provider, notifications, the Geocoder, and the Koin module.
- **`:feature:tracking`**: the Compose map screen and `TrackingViewModel`. It depends on `:core` only.
- **`:app`**: `Application` (starts Koin, so a restarted service has its dependencies, D8), `MainActivity`,
  and the API key wiring.

**MVI:** the screen renders one `StateFlow<TrackingState>` and sends every action through
`onIntent(TrackingIntent)`. One-off actions, like the permission request, travel as effects (D7).

**Room and DataStore:** the route is a list with queries, so it goes in Room, in insertion order (D9). The
session is a single flag, so it goes in DataStore. A sticky restart reads it, because its `Intent` is null.

**Foreground service:** started with `startService` from the foreground app, then promoted with a
location-typed `startForeground`. The order is permission check → `startForeground` → location updates, and
a refusal is caught and ends the session cleanly (D17).

## The 100 m rule

- **The anchor is the last recorded point, not the last GPS fix.** Measuring fix to fix would let GPS jitter
  add markers while standing still, and a slow walk would never add one. The anchor is the newest row in
  Room, so the rule survives process death (D10).
- **Fixes with no accuracy, or accuracy worse than 50 m, are rejected** before the anchor is considered. On a
  Samsung Galaxy S23, the first fix after starting had an accuracy of 100 m and would otherwise have become
  the start of the route. 50 m is a judgment, not a tuned value (D12).
- **Distance is haversine on a spherical Earth,** which keeps the rule testable on the JVM. The difference
  from the WGS84 ellipsoid is at most about 0.5% (D13).
- **One writer:** read anchor → evaluate → write runs under a mutex in the single `RecordFixUseCase`, so
  batched fixes cannot both pass against the same anchor (D14).
- The location request is high accuracy, every 10 s, at most every 5 s, with no platform distance filter (D19).

## Markers on the map

Four looks, so that one glance answers where the route began, where it is now, and which point the card
belongs to:

| Look | Point | Why |
|---|---|---|
| Teal pin | the start | Where tracking began: a pin, because it marks a place rather than a step along the way |
| Teal dot | the points in between | Small enough that neighbours 100 m apart never touch at the zoom the app opens on |
| Red pin | the newest point | Where the user is now, and the only marker that moves |
| Larger blue pin | the point whose address card is open | The map and the card agree on what is selected |

The map runs all the way to the bottom edge: the system's navigation-bar scrim is off, the app draws its
own in the theme's background colour between the map and a translucent control card, and the card pads its
own content by the navigation bar inset, so its buttons stay above the bar and the Google attribution stays
above the card (D25).

Each look is **one bitmap**, drawn once with a `DrawScope` and shared by every marker that uses it, so a
route of a thousand points holds four bitmaps. The pins are anchored at their tip and carry transparent
padding, which keeps the newest point's marker clear of the my-location dot underneath it: with the default
pin, a tap there often went to the dot instead of the marker. What the four bitmaps cost is measured in
[`docs/stress/README.md`](docs/stress/README.md) - nothing, within the run-to-run spread (D23).

## Opening the app, as measured

Two moments are worth measuring: the **first frame**, which `am start -W` reports as `TotalTime`, and the
moment the **route and the map are on screen**, which the app reports itself through `ReportDrawnWhen` and
`am start` logs as "Fully drawn". Medians of five cold starts with a seven-point route, no instrumentation
in the build:

| Device | Build | First frame | Route and map on screen |
|---|---|---|---|
| Samsung Galaxy S23, Android 16 | benchmark | 131 ms | 503 ms |
| Samsung Galaxy S23, Android 16 | debug | 550 ms | 2209 ms |
| API 36 emulator | benchmark | 526 ms | 2365 ms |
| API 36 emulator | debug | 1538 ms | 4008 ms |

A debug build is three to four times slower than the release-like one throughout, which is why the two are
never mixed. On the first launch after a clean install, where the map has no cached tiles, the same
measurement gives 146 / 1876 ms on the S23 and 503 / 3834 ms on the emulator in the benchmark build.

**What the startup work changed** (D24). The map is now composed after the stored route has been read, so
its camera opens on the route instead of opening on a default view of the city and moving there a moment
later. Measured with temporary startup logs in the build - which add about 150 ms to the last figure, so the
pairs are comparable with each other and not with the table above:

| Measurement | Before | After |
|---|---|---|
| S23 benchmark, cold start, first frame | 246 ms | 123 ms |
| S23 benchmark, cold start, route and map on screen | 735 ms | 666 ms |
| S23 benchmark, clean install, route and map on screen | 1394 ms | 1588 ms |
| API 36 emulator, benchmark, cold start, first frame | 859 ms | 476 ms |
| API 36 emulator, benchmark, cold start, route and map on screen | 2896 ms | 3148 ms |

So the first frame is about half of what it was, and the honest other half: because the map starts loading
its tiles about 70 ms later, the moment everything is on screen moved the wrong way on the emulator and on
a clean install. The tile bytes did not change at all - a clean install received 777 KB either way - so the
"loading the same tiles twice" theory behind the change turned out to be wrong.

An independent measurement by the reviewer session, on the emulator with a debug build and a seven-point
route, two rounds of five cold starts: the median `am start -W` TotalTime went from 2152 ms to 1626 ms.

**Where the time goes** on the S23's benchmark build: process start to `Application.onCreate` 14 ms,
`startKoin` 1 ms, first frame 145 ms, route read from Room 216 ms, map object ready 230 ms, map finished
rendering 615 ms. The map's tiles dominate, and the app's own code is a small part of it. StrictMode found
no disk reads on the main thread from app code. While the route is being read there is neither a map nor a
placeholder on screen - both are inside the same condition - and that window shows the window background,
the colour the placeholder uses, so the launch screen hands over to one flat surface.

## Background behaviour, as measured

| Scenario | Result | Device | Ref |
|---|---|---|---|
| Start tracking | Foreground service with type location | Samsung Galaxy S23, Android 16 | D17 |
| HOME, 45 s in the background | Fixes kept arriving; the platform recorded location access through the foreground service, with only while-in-use permission | Samsung Galaxy S23, Android 16 | D17 |
| Process killed by the system, 4 times | The service restarted within 1–16 s each time, regained foreground location, and tracking continued | Samsung Galaxy S23, Android 16 | D17, D8 |
| Location permission revoked while tracking | The service restarted after the platform's 64 s backoff and ended the session 0.4 s later, without a crash, posting "Tracking stopped" | Samsung Galaxy S23, Android 16 | D17 |
| Force-stop while tracking | No restart within 30 s. On reopening, the app said tracking had stopped, and the route was kept | Samsung Galaxy S23, Android 16 | D17 |
| Stop from the notification | Session ended; no "Tracking stopped" notification | Samsung Galaxy S23, Android 16 | D17 |
| 4 rotations while tracking | Same process, no new start command, marker count unchanged | API 33 emulator | D17 |
| Task swiped away from recents | Tracking continued and the next point was recorded | API 33 emulator | D17 |
| Offline while opening an address | "Address unavailable" with Retry; the address appeared after reconnecting | API 33 emulator | D21 |

## Known limitations

- **After a force-stop or a reboot, tracking does not start again by itself.** The route is kept, and the app
  says tracking stopped. This is deliberate: location tracking restarts only when the user asks (D17).
- **Not tested:** Doze and long periods in the background, OEM battery management, swiping away on OEM
  launchers (measured on stock Android only), and Android 8–12 (API 26–32). Tested devices: a Samsung
  Galaxy S23 with Android 16, an API 36 emulator (Android 16), and an API 33 emulator.
- **No Baseline Profile is generated for this app.** That needs a macrobenchmark module and a separate
  device run, which is outside the scope here. The libraries' own profiles are installed:
  `androidx.profileinstaller` 1.4.0 is on the runtime classpath and the build compiles an ART profile into
  the APK (D24).
- **A thousand markers are all drawn at once.** The app does not cluster or page them; what that costs is
  measured in [`docs/stress/README.md`](docs/stress/README.md).
- **A refused service restart** was seen in another app on the same device model but never triggered here.
  The path is implemented (the session ends with a notification) and unverified in this project (D17).
- **Stop and Start continue the same route.** Distance covered while stopped is joined by a straight line
  (D22).
- **Inaccurate fixes are dropped on purpose,** so indoors or in tunnels markers can be delayed and a route
  can have gaps (D12).
- **Addresses depend on the platform Geocoder and the network.** Offline, a marker shows no address until a
  retry succeeds (D21).
- **Fixes still queued when a session ends are dropped.** A fix already being recorded at that moment still
  completes, so at most one point can land milliseconds after Stop (D17).
- **`ACCESS_BACKGROUND_LOCATION` is not requested.** A location foreground service started from the
  foreground keeps receiving location in the background with while-in-use permission. The measurements in
  D17 show exactly that, and it avoids the separate background-location permission flow.
- The emulator's map renderer, from older Google Play services, needs the Apache HTTP legacy library, which
  is declared (D20).

## AI usage

This project was built with **Claude Code**, using the model **Claude Opus 5**. Every commit carries a
`Co-Authored-By: Claude Opus 5` trailer.

**Workflow:** prompt → plan → implementation → verification with the repository's skills
(`.claude/skills/verify-claim` for builds, tests and mutation checks; `.claude/skills/device-check` for
devices and emulators) → commit. At each milestone, a second, independent Claude Code session re-measured the
claims (test counts, mutations, on-device behaviour), and its findings were fixed in follow-up commits.
Attempts that turned out to be invalid are kept in the verification log of `docs/decisions.md`.

**What the author did:** answered the assistant's ten decision questions (module layout, commit attribution
(`Co-Authored-By`), repository visibility, repository name and location, what the first fix means, the
accuracy threshold, approximate-only permission, reset while tracking, which emulator to use, and what happens
to a lost session); provided and supervised the test phone; and created the Maps API key. The brief that
opened the session, the review messages and the delivery brief were prepared in the separate reviewer session,
and the author decided what to send ([`docs/ai/prompts.md`](docs/ai/prompts.md)). Until the morning of
2026-09-17 the assistant also pushed; from then on it only committed, and the author reviewed each commit
locally and pushed. The on-device scenarios were run through `adb` by the assistant, on the author's phone and
on an emulator.

**Helper files in the repository:**

- [`CLAUDE.md`](CLAUDE.md): architecture, invariants and working rules for AI assistants.
- [`.claude/skills/`](.claude/skills/): `verify-claim` and `device-check` procedures, with small scripts.
- [`docs/decisions.md`](docs/decisions.md): decisions, rejected alternatives, evidence and the verification log.
- [`docs/ai/`](docs/ai/): how the AI workflow ran, and the prompts that drove it.
