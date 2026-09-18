---
name: device-check
description: Procedure for verifying tracking behaviour on a device or emulator (permissions, foreground service, background location, process death, force-stop, revocation, notification actions, addresses). Use before running or claiming any on-device result.
---

# device-check

Each command below was run in this project, and the note next to it says what its output showed.
Commands marked **unverified** have not been run here yet; verify them first, then remove the mark.
Record every result in the verification log of `docs/decisions.md`.

## Ground rules

- **Privacy:** screenshots, screen recordings and logs from a physical device show the tester's real
  location and addresses. Keep them in a scratch directory. **Logs from a physical device are never
  committed,** and a recording or screenshot from one is committed only when all of these hold, otherwise
  record on an emulator:
  1. **no real location reaches the screen.** Two ways, and the take says which one it uses: revoke the
     permission (`pm revoke` both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`), so the my-location
     dot cannot be drawn - confirm with a screenshot - or let every fix the app receives come from the
     `demo` build's own mock source, and check the recorded coordinates afterwards: they have to be the
     made-up route, and the accuracy the mock sends (8 m here) rather than a real reading;
  2. the app's data is cleared (`pm clear`), so points recorded at real places are gone;
  3. the route on screen is made-up and seeded into the database (see `docs/stress/README.md`);
  4. every frame is read in a contact sheet (`ffmpeg -vf "fps=1,tile=8x2"`) and checked for real
     addresses, account names and anything identifying in the status bar, before anything is committed.
     **Start at the first frame and finish at the last one:** a take begins and ends with whatever app was
     in front when the recorder started and stopped, and 1 frame per second over a 95 s clip is easy to
     skim past. That is how two seconds of the Clock app, with the tester's own alarms, reached the
     repository (verification log, 2026-09-18). What is committed as a crop, a cut or a GIF is read in the
     form it is committed in: that is what ships, so that is what gets scanned;
  5. the device is restored right afterwards and the restore is verified one item at a time: mock sources
     removed, `appops android:mock_location` back to `default`, Do Not Disturb off, screen timeout back to
     its old value, app data cleared.

  Everything in `docs/media/` was recorded this way, on the phone. The emulator is used for the
  instrumented tests; it is not a source of media any more, and its own recorder needed a host-side
  workaround that the phone does not (see "Recording a demo").
- **Always pass `-s <serial>`.** A phone and an emulator are often connected at the same time.
- **Install the debug build** (`./gradlew :app:installDebug`). The Maps key is restricted to the package name
  and this machine's debug signing certificate, so other builds show a grey map.
- **Before trusting any behaviour, confirm the installed APK is newer than your last source change**, e.g.
  `stat` on the source file and `app/build/outputs/apk/debug/app-debug.apk`.
- Clear logs before each scenario (`adb -s $S logcat -c`), then read them afterwards. A scenario that did not
  run is not a pass. One tap here silently failed because a collapsed notification hid its button.

```bash
ADB=~/Library/Android/sdk/platform-tools/adb; S=<serial>; P=com.erolgizlice.routetracker
```

## Driving the UI

`python3 .claude/skills/device-check/droid.py` dumps the screen with `uiautomator` and taps nodes by their
text or content description. Markers carry the content description "Route marker N". Set the device with
`SERIAL=<serial>`.

- `droid.py texts` lists the visible labels and where they are.
- `droid.py tap "Start"` taps a label, or prints `NOT FOUND` and exits 1.
- `droid.py shot out.png` saves a screenshot.

In zsh, call it through a function, e.g. `d() { python3 .../droid.py "$@"; }`. A command stored in a
variable, like `D="python3 droid.py"`, is not split into words and fails.

## Permissions

```bash
$ADB -s $S shell dumpsys package $P | grep -E "permission.(ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|POST_NOTIFICATIONS): granted"
# Reset to a never-asked state. Revoking alone keeps the "user fixed" flags, so dialogs stay suppressed.
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  $ADB -s $S shell pm revoke $P android.permission.$p
  $ADB -s $S shell pm clear-permission-flags $P android.permission.$p user-set user-fixed
done
$ADB -s $S shell pm grant $P android.permission.ACCESS_FINE_LOCATION
```

Revoking a permission from a running app kills its process ("permissions revoked"). That is the revocation test.

## Foreground service and background location

```bash
$ADB -s $S shell dumpsys activity services $P | grep -E "ServiceRecord|isForeground|restartCount"
#   expect isForeground=true ... types=0x00000008 (location)
$ADB -s $S shell dumpsys appops --package $P | grep -A3 FINE_LOCATION
#   mode=foreground means no background permission; an access marked fgsvc came through the foreground service
$ADB -s $S logcat -d -s TrackingService:V      # "Recorded point", "Skipped fix: <reason> (accuracy …)", "Ending session: …"
$ADB -s $S logcat -d -b crash | grep -c FATAL
```

Background test: `input keyevent KEYCODE_HOME`, wait at least 30 s, then check that fixes kept arriving.

## Process death, force-stop, revocation

| Scenario | Command | What happened here |
|---|---|---|
| System kill | `$ADB -s $S shell run-as $P kill -9 $($ADB -s $S shell pidof $P)` | Works without root on a debuggable build. The restart delay grows with each death: about 1, 4, 16, then 64 s. Read `Scheduling restart of crashed service … in Nms` from `logcat -b system` and wait at least that long |
| Not a system kill | `am kill` | Invalid: it skips a process running a foreground service |
| Force-stop | `$ADB -s $S shell am force-stop $P` | No restart is scheduled. Reopen the app and expect the "stopped while the app was closed" notice |
| Revocation | `pm revoke` both location permissions while tracking | Process killed and service restarted after the backoff; expect "Ending session: no location permission" and the "Tracking stopped" notification |

## Notifications

```bash
# Current notifications only. Without the sed range, dumpsys also prints the archive of old ones.
$ADB -s $S shell dumpsys notification --noredact | sed -n '/Notification List:/,/Snoozed/p' | grep -A30 "pkg=$P" | grep -E "id=|android.title="
$ADB -s $S shell cmd statusbar expand-notifications
```

Action buttons on a collapsed notification are hidden (seen on Samsung). Swipe down on the notification
title to expand it (`input swipe x y x y+400 300`) before tapping "Stop". Collapse the shade afterwards
with `cmd statusbar collapse`.

## Emulator

Use a standard 4 KB-page Google APIs x86_64 image. The `Pixel_5` AVD (API 33) booted in about 10 s; the API 37
image with 16 KB pages did not finish booting within 4 minutes on this Intel Mac. Its old Play services
(23.18.18) is also what exposed the crash in D20, which makes it a useful test.

| Need | Command | What happened here |
|---|---|---|
| Move | `$ADB -s $E emu geo fix <longitude> <latitude>` (longitude first) | Fixes arrived with 5.0 m accuracy and were recorded. For a walk, step latitude by 0.00035972 per 40 m |
| Rotate | `settings put system accelerometer_rotation 0`, then `settings put system user_rotation 1` / `0` | Confirm each rotation by the screenshot's width and height. `emu rotate` was unreliable, and a `dumpsys` reading disagreed with the screenshot. Restore `accelerometer_rotation 1` afterwards |
| Offline | `svc wifi disable; svc data disable` (and `enable`) | Confirm with `ping -c 2 -W 2 8.8.8.8` returning 0 replies. Do this on the emulator, never on a personal phone |
| Read the route | `$ADB -s $E root`, then `$ADB -s $E shell "sqlite3 /data/data/$P/databases/route.db 'SELECT id, latitude, longitude, address FROM route_points'"` | Works on Google APIs images; Play Store images cannot be rooted |
| Tap a marker | Coordinates from a screenshot | This image's map renderer does not expose markers to accessibility, so `droid.py tap "Route marker N"` fails here, unlike on the S23. Check that the card's coordinates match the database row |
| Swipe away | `input keyevent KEYCODE_APP_SWITCH`, screenshot, then `input swipe 540 1300 540 80 120` on the card | Confirm with `dumpsys activity recents`: the package must no longer be listed. The first attempt missed silently |
| Instrumented tests | `ANDROID_SERIAL=$E ./gradlew :data:connectedDebugAndroidTest` | Without `ANDROID_SERIAL` the tests also run on the phone. Results land in `data/build/outputs/androidTest-results/connected/debug/` |

GPX route playback (Extended Controls → Location → Routes) is **unverified**.

## Recording a demo

Each point below cost a failed take (verification log, "Demo recording" and "Demo re-recorded").

- **Put a neutral app in front before the recorder starts, not after.** The take opens on whatever is on
  screen at that moment: `am start` the calculator, wait for it, and only then start `screenrecord`. The
  clip 1 takes launched the app first and so opened on the phone's Clock app.
- **Record on the host, not in the guest.** `adb shell screenrecord` writes a frame only when the screen changes
  and loads the emulator while it encodes, which made the clips stutter and jump. Use
  `adb -s $E emu screenrecord start --fps 30 /abs/path/take.webm`, then `adb -s $E emu screenrecord stop`. It ignores
  `--bit-rate`, so convert to H.264 at a constant frame rate:
  `ffmpeg -i take.webm -vf fps=30 -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart -an clip.mp4`.
- **Use a current system image with enough resources.** The API 33 image's old Play services (23.18.18) needed the
  legacy HTTP library (D20). An API 36 Google APIs image (Play services 25.26.35) with 6 cores, 6 GB RAM and
  `-gpu host` recorded at a steady ~26 frames per second (median 39 ms between frames).
- **Move smoothly and match the location request:** one `geo fix` per second, at about 11 m/s, gives a marker
  every ~110 m with the 5 s (foreground) and 10 s (background) delivery intervals. Keep full-size screenshots
  out of a take: extract README frames from the final video afterwards (`ffmpeg -ss T -i clip.mp4 -frames:v 1`).
- **Stand still across the switch to the background.** The first delivery after HOME can arrive late; if the
  location keeps moving, the next marker lands far past 150 m. Keep the notification shade open during the
  background part, so the rising marker count shows that tracking continues.
- **No `uiautomator` during a take** (the guest recorder made it fail with "null root node"). Measure tap
  coordinates beforehand. During the take, confirm each tap had its effect before the next one: sample a pixel
  of a button with a raw `adb exec-out screencap` (16-byte header), away from its label. The Start button's label
  sits on (883, 2120); its background at (960, 2120) reads purple for Start and red for Stop. For system dialogs,
  wait for `dumpsys window | grep mCurrentFocus`.
- **Do not tap a marker that overlaps the my-location dot** (the last recorded point, while standing still). The
  tap sometimes goes to the dot, the card does not open, and the next blind tap lands on Stop or Start. Test
  the chosen marker three times before recording.
- **The first launch after install shows the splash screen for several seconds;** wait for readiness, not a
  fixed sleep. Set the start location with the app listening, because `geo fix` without a listener does not
  update the last known location. Clear other apps from recents (`am stack list`, `am stack remove <taskId>`),
  and never open the app drawer in a take.
