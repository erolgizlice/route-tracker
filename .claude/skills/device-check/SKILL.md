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
  location and addresses. Keep them in a scratch directory; never commit them. README media comes from an
  emulator on a made-up route.
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

Each point below cost a failed take (verification log, "Demo recording").

- **No `uiautomator` while `screenrecord` runs:** the dump fails with "null root node". Measure tap
  coordinates beforehand, then wait for readiness during the take. For the app, poll a pixel of a known
  button in a raw `adb exec-out screencap` (no PNG decoding needed). For a system dialog, poll
  `dumpsys window | grep mCurrentFocus`.
- **The first launch after install shows the splash screen for several seconds.** A fixed sleep is not
  enough; wait for readiness.
- **Match fixes to the location request,** or marker gaps grow past 150 m: fixes at least 6 s apart in the
  foreground (fastest interval 5 s) and 10 s apart in the background.
- **Set the start location with the app listening:** `geo fix` without an active listener does not update the
  platform's last location, and the first recorded point would be wherever the last run ended.
- **Clear other apps from recents first** (`am stack list`, then `am stack remove <taskId>`), and never open
  the app drawer in a take. Personal apps installed on the emulator would appear on screen.
- `screenrecord --size 540x1170 --bit-rate 1200000` produced about 3 MB per 90–120 s. Stop it with
  `pkill -INT screenrecord` so the file is finalized.
