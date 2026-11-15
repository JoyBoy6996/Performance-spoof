[![Build APK](https://github.com/JoyBoy6996/Performance-spoof/actions/workflows/build.yml/badge.svg?branch=port/hyperos)](https://github.com/JoyBoy6996/Performance-spoof/actions/workflows/build.yml)

# Performance Spoof — HyperOS port

A port of [JoyBoy6996/Performance-spoof](https://github.com/JoyBoy6996/Performance-spoof)
to Xiaomi HyperOS (MIUI 13/14, HyperOS 1/2/3), keeping the spoofing behaviour
byte-for-byte identical while fixing the three things HyperOS does differently
from OriginOS 6.

---

## 1. What the app actually does

The app does **not** touch CPU frequencies, GPU clocks or vendor boost
sysfs nodes. It does something much more subtle, and that is why the ROM
matters so much:

| Element | Value |
|---|---|
| Package name | `com.antutu.ABenchMark` (impersonates AnTuTu Benchmark) |
| Activity | `com.antutu.ABenchMark.MainActivity` — transparent, alpha 0, no visible UI |
| Service | `com.antutu.ABenchMark.PerformanceService` — `foregroundServiceType="specialUse"` |
| Overlay | 2 x 2 px, `TYPE_APPLICATION_OVERLAY` (2038), flags `0x1000118`, alpha `0.02`, hardware accelerated, gravity `TOP\|START` |
| Heartbeat | 3000 ms, invalidates the overlay view |
| Notification | channel `iqoo_performance_service_channel`, id `1001` |
| Timings | 500 ms activity-to-background delay, 200 ms resume delay |

The mechanism is **identity + surface based**. The OEM's performance stack
looks at the running process list and at the SurfaceFlinger layer list. A
process that calls itself `com.antutu.ABenchMark` and owns a real,
hardware-accelerated, visible (if 2 x 2 px and 2 % opaque) layer looks exactly
like a benchmark that is currently running, so the OEM grants the performance
profile to the foreground game.

Three consequences follow, and they are the whole reason this app is fragile:

1. **The process must stay alive.** No process, no spoof.
2. **The overlay surface must stay alive.** HyperOS's memory freezer *destroys*
   the layers of a frozen background app. A frozen app has no layer, so the
   spoof silently stops working even though the notification is still there.
3. **The service must not be stopped.** When Android 14+ removes a foreground
   service that the app did not stop itself, the platform raises
   `ForegroundServiceDidNotStopInTimeException` **inside the app process**.
   That is the "crashing" symptom.

OriginOS 6 (vivo/iQOO) grants a foreground service much more latitude and does
not destroy layers of backgrounded apps, so the original works there.

---

## 2. Why HyperOS breaks it

HyperOS stacks five independent background-control layers on top of AOSP. Any
one of them is enough to stop the service or freeze the process:

| Layer | HyperOS mechanism | Effect on this app |
|---|---|---|
| AppOps | `RUN_ANY_IN_BACKGROUND`, `RUN_IN_BACKGROUND`, `START_FOREGROUND` | FGS start rejected, service dropped |
| Doze | `deviceidle whitelist` / `except-idle-whitelist` | Deep doze, no heartbeats |
| App standby | `am set-standby-bucket` → `restricted` | **Existing foreground services are removed** |
| Greezer | `settings put system cloud_greezer_enable false` | cgroup freezer freezes the process |
| Memory freezer | `settings put system cloud_memFreeze_control 0`, `cloud_memory_freeze_whitelist` | **Destroys the overlay surface** |

Plus three HyperOS-specific code-level faults in the original app:

1. **Promotion order.** The original attaches the overlay window in
   `onCreate()` and only calls `startForeground()` in `onStartCommand()`. On
   HyperOS the promotion window is tight, and an FGS that is stopped before it
   promotes is turned into a process crash.
2. **No restart path.** `START_STICKY` is subject to the same background
   foreground-service rules, so it does not bring the service back.
3. **Manifest entries pointing at missing classes.**
   `androidx.compose.ui.tooling.PreviewActivity` does not exist in a release
   build (the Compose tooling dependency is `debugImplementation` only) and
   `androidx.activity.ComponentActivity` is abstract. HyperOS builds that
   enumerate the activities of an installed package when they generate a
   recents thumbnail or an app info sheet will hit a `ClassNotFoundException`.

---

## 3. The recommended approach

Short version: **keep the app, give it an ADB-shell identity, and add an
alarm-based watchdog.** Do not rewrite the spoof, and do not hook HyperOS with
LSPosed.

There are three workable levels. Pick the highest one you can live with.

### Level 3 — Root + Magisk watchdog (recommended, exact behaviour)

The most robust option, and the one this port is built for.

* A Magisk module (`magisk-module/`) runs a root loop every 20 seconds that
  re-applies every exemption and restarts the service if the process died.
  Because the loop runs as root it uses the shell's
  `START_FOREGROUND_SERVICES_FROM_BACKGROUND`, so the Android 12+ background
  FGS restriction does not apply and the restart always succeeds.
* The app itself applies the same exemptions through `su -c` whenever it starts,
  so the protection works even without the module.
* The module honours an explicit user stop: the app writes a marker file and
  the watchdog does not restart the service while that marker exists.

### Level 2 — Shizuku (no root)

Shizuku gives the app the ADB shell identity without root. The app then applies
the AppOps, doze whitelist, standby bucket and freezer whitelist changes
itself.

What Shizuku **cannot** do by itself: restart the service when the app is in
the background. `startForegroundService()` from the background throws
`ForegroundServiceStartNotAllowedException` on Android 12+, and the app's
`AlarmManager` watchdog inherits the same restriction. The alarm still helps
(a) because the app usually has an activity in the recents back stack, which is
one of the documented exemptions, and (b) because it re-arms the service as
soon as any of the HyperOS exemptions are re-granted.

### Level 1 — No root, no Shizuku

Run the bundled script once over adb:

```bash
adb push setup-hyperos.sh /data/local/tmp/
adb shell sh /data/local/tmp/setup-hyperos.sh
```

The script applies everything that the shell identity allows and starts the
service. Re-run it after a reboot or a system update, because HyperOS restores
the defaults.

### What I would *not* do

* **LSPosed/Zygisk hooks on `PowerKeeperService`.** They break on every
  HyperOS update, and they do not help with the freezer destroying the
  overlay surface.
* **Trying to trigger the performance profile directly.** HyperOS exposes
  `persist.miui.migt.enable`, `persist.miui.migt.game_boost`,
  `persist.sys.miui_booster` and `debug.performance.tuning`. Setting them
  needs root and changes the device permanently, and it is not the same thing
  as the benchmark-detection behaviour this app reproduces.
* **Changing the package name.** The whole point is the identity
  `com.antutu.ABenchMark`. Renaming it removes the spoof.

---

## 4. What the port changed

### Preserved exactly (the spoof surface)

* Package name, namespace and every component name.
* Overlay geometry, type, flags, format, gravity, position, alpha and
  hardware-accelerated layer type.
* Heartbeat cadence of 3000 ms.
* Notification channel id, notification id, title and text.
* `ACTION_START` / `ACTION_STOP` action strings.
* Transparent activity, window flags `0x01000218`, immersive sticky mode.
* The 500 ms and 200 ms activity delays.
* The activation toast.

### Fixed / added

| Change | Why |
|---|---|
| `startForeground()` moved into `onCreate()`, before the overlay is attached | Removes the `ForegroundServiceDidNotStopInTimeException` crash and the "service stopped" path |
| `AlarmManager` watchdog every 20 s + restart on task removal | Brings the service back after HyperOS stops it |
| `BOOT_COMPLETED` receiver | Survives a reboot |
| `HyperOsGuard` applies AppOps, doze whitelist, standby bucket, Xiaomi cloud whitelists and the freeze switches | Removes the freezes and the layer destruction |
| Root (`su -c`) and Shizuku (`IShizukuService.newProcess`) backends | Gives the app the privileges it needs |
| `onTaskRemoved()` restart, with a marker file for explicit user stops | A swiped task no longer kills the spoof permanently |
| Notification small icon changed to a monochrome vector | A colour PNG renders as a white blob in the HyperOS shade |
| The two bogus activity declarations moved to a debug-only manifest | A release build no longer references a missing or abstract class |
| `RecoveryActivity` extended with "Apply HyperOS protections", "Authorize Shizuku" and "Open auto-start settings" | Gives the user a manual repair path when the automatic one fails |
| `onCreate()` of the service now clears the stop marker | The watchdog stops honouring an old stop |

---

## Shizuku

**Yes, Shizuku works, and it covers everything that has an adb surface.**
Shizuku's ADB mode runs commands as the shell user (uid 2000), which is
enough for all of it. No root required.

### What Shizuku gives you

* every AppOps write (`RUN_ANY_IN_BACKGROUND`, `RUN_IN_BACKGROUND`,
  `START_FOREGROUND`, `WAKE_LOCK`, `SYSTEM_ALERT_WINDOW`)
* both doze whitelists and the standby bucket
* the HyperOS freezer switches (`settings put global/system`)
* the performance game mode bridge
* **restarting the service from the background** - this is the big one, see
  below

### What Shizuku cannot do

The vendor UI toggles have no adb surface. App Quick Freeze, Sleep standby
optimisation, the per-app battery mode and Auto-launch live in the vendor's
own database (`com.oplus.battery`, `com.coloros.oppoguardelf`). The setup
console opens the right screen for each one, so it is one tap instead of
zero.

### Why the service is exported

Android 12+ forbids an app from calling `startForegroundService()` while it
is in the background. The shell is exempt, because it holds
`START_FOREGROUND_SERVICES_FROM_BACKGROUND`. So the watchdog restarts the
service with:

```bash
am start-foreground-service --user 0 -n com.antutu.ABenchMark/.PerformanceService \
    -a com.antutu.ABenchMark.ACTION_START
```

For the shell to be allowed to do that, the service has to be
`android:exported="true"` - the shell cannot start a non-exported component.
That does not open the app up:

* the stop action is **not** handled by the exported service. It goes through
  a non-exported `StopReceiver`;
* the service additionally requires a **per install secret token** on every
  stop request, so another app cannot stop the spoof even if it knows the
  action string.

### Setting it up

1. Install Shizuku and start it (wireless debugging pairing, or root mode).
2. Long-press the launcher icon -> *Setup and game boost* -> *Authorize
   Shizuku* -> accept in the Shizuku dialog.
3. Tap *Apply ROM protections*.

Verify the backend was picked up:

```bash
adb shell dumpsys package com.antutu.ABenchMark | grep -i shizuku
```

---

## 5. Install

### 5.0 Where the files live

| File | Where it is | When it runs |
|---|---|---|
| `setup-hyperos.sh` | **On your PC**, next to the APKs after unzipping the bundle | Manually, over adb |
| `setup-hyperos.sh` (pushed copy) | `/data/local/tmp/setup-hyperos.sh` on the phone, after `adb push` | Manually, over adb |
| `setup-hyperos.sh` (reference copy) | `/data/adb/modules/perfspoof-hyperos/setup-hyperos.sh` on the phone, after flashing the Magisk module | Never, it is documentation only |
| `service.sh` | `/data/adb/modules/perfspoof-hyperos/service.sh` | **Automatically**, every 20 s |
| `post-fs-data.sh` | `/data/adb/modules/perfspoof-hyperos/post-fs-data.sh` | **Automatically**, at every boot |
| `module.prop` | `/data/adb/modules/perfspoof-hyperos/module.prop` | Read by Magisk |

The app itself never needs the script: `HyperOsGuard` applies the same
exemptions from inside the app via root or Shizuku. The script is only for the
"no root, no Shizuku, one PC session" path.

To inspect the module folder on the phone:

```bash
adb shell su -c "ls -la /data/adb/modules/perfspoof-hyperos/"
```

### 5.1 The app

```bash
adb install -r PerformanceSpoof-HyperOS-release.apk
```

### 5.2 The manual HyperOS settings

On HyperOS, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` does not always
reach the vendor screen. Set these by hand:

1. Settings → Apps → JoyBoy Spoof → **Other permissions** → *Display over other
   apps* → allow.
2. Settings → Apps → JoyBoy Spoof → **Battery saver** → *No restrictions*.
3. Settings → Apps → JoyBoy Spoof → **Autostart** → on.
4. Settings → Apps → JoyBoy Spoof → Other permissions → *Start in background* →
   allow.

### 5.3 The protections (recommended)

Either flash `magisk-module.zip` in Magisk / KernelSU, or run
`setup-hyperos.sh` over adb, or start Shizuku and tap **Authorize Shizuku**
then **Apply HyperOS protections** in the recovery screen.

### 5.4 Verify

```bash
adb shell dumpsys activity services com.antutu.ABenchMark | grep -i isForeground
adb shell dumpsys window windows | grep -i antutu
adb shell cmd appops get com.antutu.ABenchMark | grep -Ei "RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND|START_FOREGROUND"
adb shell settings get system cloud_memory_freeze_whitelist
```

The overlay surface must be present in `dumpsys window windows`. If the
service is alive but the layer is missing, the memory freezer got it — check
`cloud_memory_freeze_whitelist` and `cloud_memFreeze_control`.

### 5.5 Diagnosing a crash

```bash
adb logcat -c
adb logcat | grep -Ei "antutu|ForegroundServiceDidNotStopInTime|FATAL EXCEPTION|PowerKeeper|freezer"
```

---

## 6. Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/path/to/android-sdk
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew assembleDebug assembleRelease
```

Outputs:

* `app/build/outputs/apk/debug/app-debug.apk`
* `app/build/outputs/apk/release/app-release.apk`

The release key is `app/perfs.poof-release.jks` (alias `perfspoof`,
password `perfspoof`). Replace it before publishing anything.

---

## 9. Build on GitHub Actions

Every push to this branch runs `.github/workflows/build.yml`, which needs no
secrets and no local setup:

| Step | What it does |
|---|---|
| Build | `assembleDebug` + `assembleRelease` on JDK 17 / SDK 36 |
| Verify | `aapt dump badging` must report `package: name='com.antutu.ABenchMark'`, and the manifest must contain `MainActivity` and `PerformanceService` |
| Artifact | the APKs are attached to the run as `PerformanceSpoof-port/<rom>` |
| Release | the same APKs are published on the release tag `ci-PerformanceSpoof-port/<rom>` and are downloadable without logging in |

Download the latest build from either place:

* Runs — <https://github.com/JoyBoy6996/Performance-spoof/actions>
* Releases — <https://github.com/JoyBoy6996/Performance-spoof/releases>

The signing key `app/perfs.poof-release.jks` is committed, so the CI-built APK
carries the same signature as a local build and installs over it in place.
