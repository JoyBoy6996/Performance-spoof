[![Build APK](https://github.com/JoyBoy6996/Performance-spoof/actions/workflows/build.yml/badge.svg?branch=port/master)](https://github.com/JoyBoy6996/Performance-spoof/actions/workflows/build.yml)

# Performance Spoof — MASTER build

One application for **OriginOS / Funtouch OS**, **ColorOS / OxygenOS / realme UI**
and **HyperOS / MIUI**. A single core engine, a per-ROM adapter for each
family, and a proper dashboard instead of the invisible trick.

---

## 1. What the core does (identical on every ROM)

The core is ROM independent and is preserved byte for byte from the original
reconstruction:

| Element | Value |
|---|---|
| Package name | `com.antutu.ABenchMark` |
| Service | `com.antutu.ABenchMark.PerformanceService`, `foregroundServiceType="specialUse"` |
| Overlay | 2 × 2 px, `TYPE_APPLICATION_OVERLAY` (2038), flags `0x1000118`, format `TRANSLUCENT`, alpha `0.02`, hardware accelerated, gravity `TOP\|START`, x/y = 0 |
| Heartbeat | 3000 ms, invalidates the overlay view |
| Notification | channel `iqoo_performance_service_channel`, id `1001` |
| Actions | `com.antutu.ABenchMark.ACTION_START` / `ACTION_STOP` |
| Activity trampoline | `com.antutu.ABenchMark.MainActivity`, transparent, window flags `0x01000218`, 500 ms / 200 ms delays |

The mechanism is identity based: the process calls itself AnTuTu Benchmark and
owns a real, visible SurfaceFlinger layer, so the OEM performance stack reads
it as a running benchmark.

The core is **not** ROM aware. Everything that differs between the three
families lives in the adapter layer.

---

## 2. Architecture

```
com.antutu.ABenchMark
├── PerformanceService      the core: FGS + overlay + heartbeat
├── MainActivity            the core: transparent activation trampoline
├── StopReceiver            in-app only stop path
├── WatchdogReceiver        alarm watchdog + boot start
├── ServiceWatchdog         AlarmManager scheduling
├── ServiceLauncher         privileged or in-app service start
├── ShellBridge             root (su) and Shizuku backends
├── RomGuard                AOSP layer + one vendor layer per family
├── GameBoostController     performance game mode bridge
├── RomCompatibility        family detection + per-family instructions
└── ui/
    ├── MasterActivity      the dashboard (Jetpack Compose, Material 3)
    ├── MasterScreens       Home / Boost / Setup / Logs
    ├── MasterBridge        state + actions for the UI
    └── Theme.kt            Material You with a brand fallback
```

---

## 3. The three adapters

### Shared AOSP layer (every ROM)

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

### OriginOS / Funtouch OS (vivo, iQOO)

* **Scriptable**: the *Background power consumption management* list is the
  secure setting `high_background_power_consumption_apps`, a semicolon
  separated package list. Adding the package there stops the Application
  Behavior Engine from pausing it.
* **Vendor screens**: i Manager whitelist, background start manager,
  Excessive Power Manager.
* **The boost is the spoof itself.** OriginOS grants the performance profile
  when it sees a running benchmark, so nothing extra is needed once the
  service and the overlay are alive.

### ColorOS / OxygenOS / realme UI (OPPO, OnePlus, realme)

* **Scriptable**: the shared AOSP layer plus `set-inactive false`.
* **Vendor screens**: Auto-launch, App battery management (*Allow background
  activity*, *Allow foreground activity*, *Optimize battery use OFF*), App
  Quick Freeze, Sleep standby optimisation, and the recents lock.
* **The boost** is a per game mode. The app applies the AOSP performance game
  mode, which is what ColorOS builds *Pro Gamer mode* on top of:

  ```bash
  device_config put game_overlay <pkg> mode=2
  cmd game mode performance <pkg>
  ```

  Plus the global *Battery mode → High performance mode*.

### HyperOS / MIUI (Xiaomi, Redmi, POCO)

* **Scriptable**: the shared AOSP layer, the Greezer and memory freezer
  switches, and the three Xiaomi cloud whitelists. The memory freezer is the
  one that matters: it **destroys the overlay surface** of a frozen app, so
  the spoof silently stops working even though the notification is still
  there.
* **Vendor screens**: Autostart, Battery saver *No restrictions*, *Start in
  background*.
* **The boost**: performance game mode for the game, plus Game Turbo.

---

## 4. The dashboard

| Tab | What it does |
|---|---|
| **Home** | Live status, one tap activate, the four status tiles, the permission list |
| **Boost** | Pick a game from the installed apps or by package name, apply or reset the performance mode, jump to Game Center and Battery mode |
| **Setup** | The checklist for the detected ROM, the privileged backend state, one tap access to every vendor screen |
| **Logs** | Live diagnostics and the session log |

Material You on Android 12+, with a hand tuned brand fallback elsewhere. The
status refreshes every 1.5 s, so the Home tab shows the real state of the
service, the overlay and the protections.

**A normal tap on the launcher icon opens the dashboard.** The original
transparent behaviour is still one long-press away: *Quick activate* runs the
preserved `MainActivity`, which asks for the permissions, starts the service
and moves itself to the back in one go.

---

## 5. Install

```bash
adb install -r PerformanceSpoof-Master-release.apk
```

If the original `com.antutu.ABenchMark` is installed, uninstall it first, or
install over it directly — the master build uses the same signing key as the
earlier ports, so it upgrades in place.

### Privileged access, pick one

* **Root** — flash `magisk-module.zip` in Magisk / KernelSU. The root loop
  re-applies the exemptions every 20 s and restarts the service when the ROM
  stops it.
* **Shizuku** — start Shizuku, then in the app: *Setup → Authorize Shizuku*,
  then *Apply protections*.
* **Neither** — run the script once over adb:

```bash
adb push setup-master.sh /data/local/tmp/
adb shell sh /data/local/tmp/setup-master.sh
adb shell sh /data/local/tmp/setup-master.sh com.tencent.tmgp.pubgmhd
```

The script detects the ROM family itself and prints the vendor switches that
have to be set by hand.

---

## 6. Verify

```bash
adb shell dumpsys activity services com.antutu.ABenchMark | grep -i isForeground
adb shell dumpsys window windows | grep -i antutu
adb shell cmd game mode performance com.tencent.tmgp.pubgmhd
adb shell cmd appops get com.antutu.ABenchMark | grep -Ei "RUN_IN_BACKGROUND|START_FOREGROUND"
adb shell dumpsys deviceidle whitelist | grep -i antutu
adb shell settings get system cloud_memory_freeze_whitelist      # HyperOS
adb shell settings get secure high_background_power_consumption_apps   # OriginOS
```

The overlay surface **must** be present in `dumpsys window windows`. If the
service is alive but the layer is missing, the app was frozen — check App
Quick Freeze (ColorOS) or the memory freezer (HyperOS).

### Crash triage

```bash
adb logcat -c
adb logcat | grep -Ei "antutu|ForegroundServiceDidNotStopInTime|FATAL EXCEPTION|fuelgaue|guardelf|freeze"
```

---

## 7. Design notes worth knowing

**The service is exported.** Android 12+ forbids an app from calling
`startForegroundService()` while it is in the background, but the shell is
exempt because it holds `START_FOREGROUND_SERVICES_FROM_BACKGROUND`. For the
shell to be allowed to do that, the service has to be `exported="true"` — the
shell cannot start a non-exported component. That does not open the app up:
the stop action is handled by a non-exported `StopReceiver`, and the service
additionally demands a per install secret token on every stop request, so no
other app can stop the spoof.

**Promotion happens before the overlay.** `startForeground()` is called in
`onCreate()`, not `onStartCommand()`. When Android 14+ removes a foreground
service the app did not stop itself it raises
`ForegroundServiceDidNotStopInTimeException` inside the app process, which is
the "the app crashes" symptom.

**The package name is the feature.** The spoof only works while the package
is `com.antutu.ABenchMark`, because that is what the OEM performance stack
keys on. Renaming it removes the mechanism.

---

## 8. Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/path/to/android-sdk
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew assembleDebug assembleRelease
```

The UI is Kotlin with Jetpack Compose. AGP 9 ships built-in Kotlin support, so
the Kotlin Android plugin is **not** applied on top of it; only the Compose
compiler plugin is.

Outputs:

* `app/build/outputs/apk/debug/app-debug.apk`
* `app/build/outputs/apk/release/app-release.apk`

The release key is `app/perfs.poof-release.jks` (alias and password
`perfspoof`). Replace it before publishing anything.

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
