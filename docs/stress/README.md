# A thousand markers on the map

The 100 m rule has no upper bound: a 100 km walk stores a thousand points, and every one of them is a
marker. This is what that costs, measured, and what the measurement does not say.

The markers themselves are described in [D23](../decisions.md#d23-route-markers-are-four-bitmaps-shared-by-every-marker):
each of the four looks is one bitmap, drawn once and shared by every marker that uses it.

- **Preview:** [`docs/media/clip3-stress-preview.gif`](../media/clip3-stress-preview.gif) - eight seconds of
  the panning and zooming, cut from a 15 s take on a Galaxy S23 that began with a cold start: the app opened
  with a thousand markers already stored. The take was recorded with the location permission revoked, so
  there was no my-location dot and no real position on screen; the route is a made-up one written into the
  database by the `demo` build (D26). The numbers in the tables below come from that same run.

## Method

### Putting a thousand points in the database

Two ways, because a release build is not debuggable. **The `demo` build type** (D26) carries a receiver
that writes the points itself, which is how the recorded run and the two phone rows below were seeded:

```bash
PKG=com.erolgizlice.routetracker
$ADB -s $S shell am broadcast -n $PKG/com.erolgizlice.routetracker.demo.MockLocationReceiver \
  --es cmd seed --ei count 1000 --ed spacing 20
```

It logs `seeded 1000 points, 20.0 m apart` and takes about 14 s. Nothing in `release` or `debug` can do
this; the dex of each APK was scanned to prove it (D26).

**For a debuggable build** - the debug and benchmark rows below - the database file is built on the host
and copied into the app's data directory with `run-as`:

```bash
PKG=com.erolgizlice.routetracker
S=<serial>                      # every adb call is pinned to one device
ADB=~/Library/Android/sdk/platform-tools/adb

# 1. Build route.db on the host: the schema comes from data/schemas/...RouteDatabase/1.json, and Room
#    checks both user_version and its identity hash when it opens the file.
python3 - <<'PY'
import math, sqlite3, time
db = sqlite3.connect("/tmp/route.db")
db.executescript("""
    PRAGMA user_version = 1;
    CREATE TABLE IF NOT EXISTS `route_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `recorded_at` INTEGER NOT NULL, `address` TEXT);
    CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
    INSERT OR REPLACE INTO room_master_table (id, identity_hash)
        VALUES (42, '72c52e56a6d6a2fb99ebb7087726d445');
""")
# An Archimedean spiral around Taksim Square: 1000 points about 20 m apart, in a 700 x 690 m box.
lat0, lng0, spacing = 41.03630, 28.98490, 20.0
mlat, mlng = 111320.0, 111320.0 * math.cos(math.radians(lat0))
b, theta, pts = spacing / (2 * math.pi), 0.0, []
for _ in range(1000):
    r = b * theta
    pts.append((lat0 + r * math.sin(theta) / mlat, lng0 + r * math.cos(theta) / mlng))
    theta += spacing / max(r, b)
pts.reverse()   # outside in, so the last point - the one the camera opens on - is the middle of the spiral
t0 = int(time.time() * 1000) - len(pts) * 10_000
db.executemany("INSERT INTO route_points (latitude, longitude, recorded_at, address) VALUES (?, ?, ?, NULL)",
               [(lat, lng, t0 + i * 10_000) for i, (lat, lng) in enumerate(pts)])
db.commit()
PY

# 2. The app must have run once, so that its databases/ directory exists.
$ADB -s $S shell am force-stop $PKG
$ADB -s $S push /tmp/route.db /data/local/tmp/route.db
$ADB -s $S shell chmod 644 /data/local/tmp/route.db
$ADB -s $S shell run-as $PKG sh -c \
  'rm -f databases/route.db-wal databases/route.db-shm && cp /data/local/tmp/route.db databases/route.db'
$ADB -s $S shell rm -f /data/local/tmp/route.db
```

Verified from the app, not from the file: the control bar says "1000 markers" after the next launch.

**Why 20 m and not 100 m.** At the zoom the app opens on, 100 m is about 154 px, so a thousand points
100 m apart would be a 100 km line and only a handful of markers would ever be on screen. At 20 m the
whole spiral fits on one screen, and **all thousand markers are drawn at once** - denser than the 100 m
rule can ever produce, which is the point.

To get a debug-seeded route into the benchmark build, install the debug build, seed it, then `install -r`
the benchmark build: the data survives because both are signed with the debug key. That trick does not
work for `release` or `demo`, which are signed with a different certificate - hence the receiver.

### The gestures and the frame statistics

```bash
$ADB -s $S shell am force-stop $PKG
$ADB -s $S shell am start -W -n $PKG/.MainActivity      # then wait for the map to finish rendering
$ADB -s $S shell dumpsys gfxinfo $PKG reset
# ~10 s of panning and zooming: eight `input swipe` pans across the middle of the screen, and
# `input tap x y; input tap x y` twice for a double-tap zoom, all away from the bottom card.
$ADB -s $S shell dumpsys gfxinfo $PKG
```

The same gesture list, in the same order, for every row below.

## Results

Ten seconds of panning and zooming with 1000 markers on screen:

| Device | Build | Markers | Frames | Janky | p50 | p90 | p99 | GPU p99 |
|---|---|---|---|---|---|---|---|---|
| Galaxy S23 (Android 16) | debug | default pin | 583 | 1 (0.17 %) | 5 ms | 5 ms | 6 ms | - |
| Galaxy S23 (Android 16) | debug | four bitmaps | 582 | 1 (0.17 %) | 5 ms | 5 ms | 6 ms | - |
| Galaxy S23 (Android 16) | benchmark | default pin | 594 | 0 (0.00 %) | 5 ms | 5 ms | 6 ms | 4 ms |
| Galaxy S23 (Android 16) | benchmark | four bitmaps | 568 | 0 (0.00 %) | 5 ms | 5 ms | 5 ms | 3 ms |
| Galaxy S23 (Android 16) | demo (= release) | four bitmaps, **the recorded clip** | 497 | 0 (0.00 %) | 5 ms | 5 ms | 5 ms | 3 ms |
| API 36 emulator | debug | default pin | 107 | 94 (87.9 %) | 16 ms | 65 ms | 81 ms | - |
| API 36 emulator | debug | four bitmaps | 106 | 91 (85.9 %) | 16 ms | 65 ms | 77 ms | - |

The demo row is the run that was recorded as the video. Its output, verbatim, with the pid replaced and
the all-zero tail of each histogram cut:

```
** Graphics info for pid <pid> [com.erolgizlice.routetracker] **

Stats since: 165001782519972ns
Total frames rendered: 497
Janky frames: 0 (0.00%)
Janky frames (legacy): 0 (0.00%)
50th percentile: 5ms
90th percentile: 5ms
95th percentile: 5ms
99th percentile: 5ms
Number Missed Vsync: 0
Number High input latency: 0
Number Slow UI thread: 0
Number Slow bitmap uploads: 0
Number Slow issue draw commands: 0
Number Frame deadline missed: 0
Number Frame deadline missed (legacy): 0
HISTOGRAM: 5ms=495 6ms=1 7ms=1 8ms=0 ... (every remaining bucket 0)
50th gpu percentile: 2ms
90th gpu percentile: 2ms
95th gpu percentile: 2ms
99th gpu percentile: 3ms
GPU HISTOGRAM: 1ms=167 2ms=313 3ms=14 4ms=2 5ms=1 6ms=0 ... (every remaining bucket 0)

Pipeline=Skia (OpenGL)
```

### Cold start with a thousand points

`am start -W` for the first frame, `reportFullyDrawn` for the route and the map on screen, medians of
three cold starts, debug builds:

| Device | Build | Markers | First frame | Fully drawn |
|---|---|---|---|---|
| Galaxy S23 | demo (= release) | four bitmaps, **the recorded run** | 135 ms | 731 ms |
| Galaxy S23 | debug | default pin, before the startup work | 860 ms | 3370 ms |
| Galaxy S23 | debug | four bitmaps, after it | 549 ms | 3250 ms |
| API 36 emulator | debug | default pin, before | 2217 ms | 8303 ms |
| API 36 emulator | debug | four bitmaps, after | 1635 ms | 8036 ms |

The first row is what the video shows: three cold starts measured in the same session as the recording,
medians of 135 ms to the first frame and 731 ms to a thousand markers on screen. The debug rows below it
are the pessimistic case and the only ones that exist for "before", because the startup comparison was
made before there was a release-like build to compare in.

With a seven-point route the same builds are 850 / 2285 ms and 559 / 2237 ms on the S23, so a thousand
points cost about a second of the time to "fully drawn" in a debug build - reading them from Room and
handing them to the map, not drawing them.

## What this shows, and what it does not

- **It is not a performance claim.** Four shared bitmaps are not faster than the default pin; the numbers
  are the same to within the run-to-run spread. What the measurement says is that the four looks and the
  highlighted selection **cost nothing measurable**, which is why they are worth having.
- **`dumpsys gfxinfo` counts the frames the app's own render thread produced.** The map draws into its own
  surface, so these numbers are the cost the markers put on the app, not the cost of the map's rendering.
  During these takes the app produced frames at the display's rate (568 frames in ~10 s) with a 99th
  percentile of 5 ms, which is what "no cost on the app's side" looks like.
- **The emulator's numbers are bound by its GPU emulation**, not by the markers: 86-88 % janky frames with
  either kind of marker, on a machine where the map itself takes about 3 s to render. They are in the table
  because both builds were measured the same way, and they say nothing about a phone. **The deciding
  numbers come from the phone.**
- **The 20 m spacing is deliberately harsher than the app can produce.** A real route keeps 100 m between
  points, so a real thousand-point route would never have all of them on screen at once.
- A thousand markers do have a visible cost at startup, in reading the route and handing it to the map
  (the table above). The app does not page or cluster them; that is listed as a limitation in the README.
