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

## Emulator only (unverified)

Use a standard Google APIs x86_64 image. The API 37 image with 16 KB pages did not finish booting within
4 minutes on this Intel Mac.

```bash
$ADB -s emulator-5554 emu geo fix <longitude> <latitude>   # unverified; longitude comes first
$ADB -s emulator-5554 emu rotate                           # unverified
```

- Confirm that mock fixes carry an accuracy value; the gate rejects fixes without one (D12).
- A GPX route can be played from Extended Controls → Location → Routes.
- Test offline address lookup by turning off networking on the emulator, not on a personal phone.
