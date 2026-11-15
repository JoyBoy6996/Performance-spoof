[![Build APK](https://github.com/JoyBoy6996/Performance-spoof/actions/workflows/build.yml/badge.svg?branch=port/coloros)](https://github.com/JoyBoy6996/Performance-spoof/actions/workflows/build.yml)

# Performance Spoof — ColorOS / OxygenOS / realme UI port

A multi-ROM port of [JoyBoy6996/Performance-spoof](https://github.com/JoyBoy6996/Performance-spoof).
The spoofing behaviour is unchanged and identical to the HyperOS port; what
changes is the set of ROM-specific protections and the way the performance
boost is obtained on OPPO-family devices.

---

## 1. The short version

On OriginOS the app works because OriginOS grants a performance profile when
it sees a benchmark running. The app fakes that: package name
`com.antutu.ABenchMark`, a `specialUse` foreground service, and a 2 × 2 px
hardware-accelerated `TYPE_APPLICATION_OVERLAY` that heartbeats every 3000 ms.

**ColorOS has no equivalent auto-trigger.** Its boost is a *power mode*
(Battery mode → High performance mode) or a *per game mode* (Game Center →
Pro Gamer mode). So on ColorOS the port has to do two things:

1. **Keep the spoof alive** — same layers as on HyperOS, because ColorOS has
   its own equivalent of the freezer.
2. **Produce the boost explicitly** — through the AOSP performance game mode,
   which is what ColorOS builds "Pro Gamer mode" on top of.

Everything about the spoof itself is untouched.

---

## 2. What is preserved exactly

| Element | Value |
|---|---|
| Package name | `com.antutu.ABenchMark` |
| Activity | `com.antutu.ABenchMark.MainActivity` — transparent, alpha 0 |
| Service | `com.antutu.ABenchMark.PerformanceService` — `foregroundServiceType="specialUse"` |
| Overlay | 2 × 2 px, type `2038`, flags `0x1000118`, format `TRANSLUCENT`, alpha `0.02`, hardware accelerated, gravity `TOP\|START`, x/y = 0 |
| Heartbeat | 3000 ms |
| Notification | channel `iqoo_performance_service_channel`, id `1001` |
| Action strings | `com.antutu.ABenchMark.ACTION_START` / `ACTION_STOP` |
| Timings | 500 ms activity-to-background, 200 ms resume |
| Window flags | `0x01000218`, immersive sticky mode |
| Launch behaviour | The launcher icon still opens the transparent activity and nothing else |

The launcher icon now has a **long-press shortcut** ("Setup and game boost")
that opens the new `SetupActivity`. A normal tap is unchanged.

---

## 3. What ColorOS does to you

| Layer | ColorOS mechanism | Effect |
|---|---|---|
| App Quick Freeze | Battery → Power saving settings → App Quick Freeze | "Cannot receive or send notifications and cannot run in the background" |
| Sleep standby optimisation | Battery → Power saver options | Pauses the app after the screen has been off a while |
| Smart / Custom Power Saver | Battery | "Intelligently restrict background running" |
| App battery management | App management → Battery usage | "Allow background activity", "Allow foreground activity", "Optimize battery use" |
| Auto-launch | Apps → Auto-launch (ColorOS 12+), Startup Manager (older) | Decides who may start by themselves |
| Recents clear | Clear all | Kills the task and the service with it |

Most of these live in the vendor's own database (`com.oplus.battery`,
`com.coloros.oppoguardelf`) and are **not reachable from adb**. The guard
therefore applies everything that *is* scriptable, and the setup console opens
the exact vendor screen for the rest.

### Scriptable layer (applied by the app, the script and the Magisk module)

```bash
cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow
cmd appops set <pkg> RUN_IN_BACKGROUND allow
cmd appops set <pkg> START_FOREGROUND allow
cmd appops set <pkg> WAKE_LOCK allow
cmd appops set <pkg> SYSTEM_ALERT_WINDOW allow
dumpsys deviceidle whitelist +<pkg>
cmd deviceidle except-idle-whitelist +<pkg>
am set-standby-bucket <pkg> active
am set-inactive <pkg> false
cmd package unstop --user 0 <pkg>
```

### Manual layer (one tap from the setup console)

1. Settings → Apps → **Auto-launch** → this app → ON
2. Settings → Apps → App management → this app → **Battery usage** →
   *Allow background activity* ON, *Allow foreground activity* ON,
   *Optimize battery use* OFF
3. Settings → Battery → Power saving settings → **App quick freeze** → remove
   this app
4. Settings → Battery → Power saver options → **Sleep standby optimisation**
   OFF
5. Pull the card down in Recents to **lock** it

---

## 4. The boost bridge

ColorOS's per-game performance treatment is built on the AOSP game mode. The
platform command is documented and works on any Android 12+ device:

```bash
adb shell device_config put game_overlay <pkg> mode=2
adb shell cmd game mode performance <pkg>
```

The `device_config` entry makes performance mode available for a package that
does not declare itself a game, and the second command activates it. Relaunch
the game afterwards.

The app does this for you:

* **Use the app on screen** — resolves the foreground package and fills it in
* **Turn on performance mode** — applies the two commands to that package
* **Performance mode for the spoof itself** — puts
  `com.antutu.ABenchMark` into performance mode too, so the platform sees a
  running benchmark that is already in performance mode
* **Reset performance mode** — puts it back to standard

The same code path works on HyperOS, OriginOS and stock Android, so the port
is not ColorOS-only in its boost logic.

For the global variant, use **Open battery mode / High performance mode**
(Settings → Battery → Battery mode → High performance mode) or **Open Game
Center** for the per-game "Pro Gamer mode".

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
| `setup-coloros.sh` | **On your PC**, next to the APKs after unzipping | Manually over adb |
| `setup-coloros.sh` (pushed copy) | `/data/local/tmp/setup-coloros.sh` | Manually over adb |
| `setup-coloros.sh` (reference copy) | `/data/adb/modules/perfspoof-romguard/setup-coloros.sh` | Never, documentation only |
| `service.sh` | `/data/adb/modules/perfspoof-romguard/service.sh` | **Automatically**, every 20 s |
| `post-fs-data.sh` | `/data/adb/modules/perfspoof-romguard/post-fs-data.sh` | **Automatically**, at every boot |

### 5.1 The app

```bash
adb install -r PerformanceSpoof-ColorOS-release.apk
```

If the original `com.antutu.ABenchMark` was installed, uninstall it first —
the signing key differs.

### 5.2 The protections

Pick one:

* **Root** — flash `magisk-module.zip` in Magisk / KernelSU. The root loop
  re-applies the exemptions every 20 s and restarts the service when ColorOS
  stops it. Because it runs as root it uses the shell's
  `START_FOREGROUND_SERVICES_FROM_BACKGROUND`, so the restart always succeeds.
* **Shizuku** — start Shizuku, then in the app long-press the launcher icon →
  *Setup and game boost* → *Authorize Shizuku* → *Apply ROM protections*.
* **No root** — run the script over adb:

```bash
adb push setup-coloros.sh /data/local/tmp/
adb shell sh /data/local/tmp/setup-coloros.sh
adb shell sh /data/local/tmp/setup-coloros.sh com.tencent.tmgp.pubgmhd
```

### 5.3 Verify

```bash
adb shell dumpsys activity services com.antutu.ABenchMark | grep -i isForeground
adb shell dumpsys window windows | grep -i antutu
adb shell cmd game mode performance com.tencent.tmgp.pubgmhd
adb shell cmd appops get com.antutu.ABenchMark | grep -Ei "RUN_IN_BACKGROUND|START_FOREGROUND"
adb shell dumpsys deviceidle whitelist | grep -i antutu
```

The overlay surface must be present in `dumpsys window windows`. If the
service is alive but the layer is missing, the app was frozen or killed — check
App Quick Freeze and the auto-launch list.

### 5.4 Diagnosing a crash

```bash
adb logcat -c
adb logcat | grep -Ei "antutu|ForegroundServiceDidNotStopInTime|FATAL EXCEPTION|fuelgaue|guardelf|freeze"
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

The release key is `app/perfs.poof-release.jks` (alias and password
`perfspoof`). Replace it before publishing anything.

---

## 7. Files

| File | Role |
|---|---|
| `RomGuard.java` | Shared AOSP layer plus the HyperOS and ColorOS vendor layers |
| `GameBoostController.java` | Performance game mode bridge, foreground package detection |
| `SetupActivity.java` | Setup console: protections, vendor screens, game boost |
| `ShellBridge.java` | Root (`su`) and Shizuku (`IShizukuService.newProcess`) backends |
| `ServiceWatchdog.java` / `WatchdogReceiver.java` | Alarm watchdog and boot start |
| `PerformanceService.java` | The spoof service, promoted to the foreground before the overlay is attached |
| `RomCompatibility.java` | ROM family detection and per-family setup instructions |
| `setup-coloros.sh` | Host-side script |
| `magisk-module/` | Root watchdog module |

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
