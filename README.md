# Swipes

Personal Android app that records every finger touch system-wide, in the background, for analytics and replay.
Built for and tested on a Samsung Galaxy S25 Ultra (Android 16), unrooted.

## Features

- **Home**: recording status, today's taps/swipes/scrolls/typing, finger travel, touch time, most-touched apps,
  latest touches.
- **Analytics**: for the last 2 hours, today, yesterday, 7 days, this month, year to date, or a custom date range:
  touch-down or finger-path heatmaps, and touches per hour of day.
- **Replay**: video-like playback of finger paths for a time range, 1×–64×, with idle time skipped by default.
  Traces are colored by app (your 8 most-used apps keep fixed colors), a live per-app tally runs alongside, and the
  scrub bar shows touch volume over time.
- **Widgets**: *Today*, *Most touched today* and *Next milestone*. On One UI, long-press one on the home screen →
  *Create stack* and add the others to swipe between them. An alternative to the status notification, which can be
  turned off in Settings (as can milestone notifications).
- **Milestones**: notifications such as "You've scrolled 1,000 times today" or "Your finger has traveled 3 miles on
  the phone this month"; each fires at most once per day/week/month/year.

## What counts as what

- **Taps / long presses / swipes / scrolls** are touches on app content: a swipe moves more than 1.5 mm, a scroll is a
  mostly vertical swipe.
- **Typing** is any touch on the on-screen keyboard (key presses, glide typing), counted separately so taps aren't
  dominated by typing.
- Each touch is credited to the app whose window is under the finger (a notification pop-up only claims touches on
  the pop-up itself).

## Privacy

Everything stays on the phone (Room database in app-private storage). Nothing is uploaded.

- **Lock screen**: touches that start while the phone is locked (PIN, pattern, password, notification swipes) are
  stored without positions — only timing and path length. Unlock patterns can't be reconstructed.
- **Keyboard**: any touch that crosses the on-screen keyboard while it is visible is stored the same way, so typed
  text can't be reconstructed. Those areas stay blank in heatmaps and replay.
- When the app can't tell whether the phone is locked or the keyboard is up (e.g. right after a restart), it masks.
- The accessibility service is used only for the foreground app and keyboard bounds; it never reads text.

## How it works

Android doesn't let apps observe touches in other apps (the accessibility "observe" API is hidden, the public one
consumes touches). So the app starts a small helper with `adb`-level (shell) permissions that reads the touchscreen
input device read-only and streams samples to the app over a token-authenticated loopback socket.

```
adbd (Wireless debugging) ── pair once, launch ──▶ swipes-helper (shell uid, app_process)
                                                     reads /dev/input/eventN, buffers ~7 min of samples
app (kept alive by its accessibility service) ◀───── 127.0.0.1, token auth, resume by sequence number
  strokes → privacy masks → in-memory buffer → flush every 30 s / 200 strokes / screen off → Room
```

- `helper/`: runs in the shell-uid process (evdev decoding, ring buffer, loopback server).
- `adb/`: pairing with the phone's own Wireless debugging and launching the helper (libadb-android).
- `capture/`: stroke assembly, classification, privacy masks, buffering, flushing, status notification.
- `analytics/`, `replay/`, `milestones/`, `data/` (Room), `ui/` (Compose).

## Requirements on the phone

- **USB debugging on** (no cable needed). Keeps `adbd` alive; the helper lives in adbd's process group and would be
  killed when Wireless debugging turns off or Wi-Fi drops.
- **Wireless debugging** only to start the helper: first setup and after every reboot (needs Wi-Fi).
- Accessibility service on, notifications allowed; unrestricted battery recommended.

The in-app Setup screen walks through all of this, and "Check it's working" validates background recording and
position accuracy.

Trade-offs of leaving USB debugging on: some banking/payment apps refuse to run with debugging enabled, and a computer
plugged in can request debugging access (Android still requires accepting a prompt on the unlocked phone).

## Build

Requires JDK 17+ and the Android SDK (`ANDROID_HOME`).

```bash
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Connect a phone over Wireless debugging: `adb pair <ip>:<pairing-port> <code>`, then `adb connect <ip>:<port>`.

## Testing notes

- Pipeline logic is covered by JVM unit tests (`app/src/test`).
- On-device checks need real touches: `adb shell input` bypasses the touchscreen device, and SELinux denies shell
  writes to it, so touches can't be injected for the helper to see.
- Database snapshot for analysis: `adb exec-out run-as com.swipes cat databases/swipes.db > swipes.db`
  (also copy the `-wal` file, or open the app's Analytics first to flush).
