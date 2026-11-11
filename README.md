
# ⚡ Performance Spoof

An Android application focused on recreating the app-owned components and background service behavior of ABenchMark through source-level reconstruction.

The project contains reconstructed Java source code, Android resources, Gradle configuration, and an automated GitHub Actions workflow for building the APK.

<p align="center">
  <b>Android Source Reconstruction</b>
</p>

---

## ✨ Features

- Reconstructed Android Activity and Service classes.
- Original application package and component names.
- Transparent, background-oriented Activity behavior.
- Foreground service implementation.
- Invisible 2 × 2 pixel hardware-accelerated overlay.
- Periodic service heartbeat mechanism.
- Android permission handling.
- Original background timing behavior.
- Notification channel and foreground notification.
- Automated APK builds using GitHub Actions.
- No embedded original APK or binary payload.

## ⚙️ Technical Details

| Property | Value |
|---|---|
| Project Name | Performance Spoof |
| Application ID | `com.antutu.ABenchMark` |
| Language | Java |
| Build System | Gradle |
| Android Gradle Plugin | 9.4.1 |
| Minimum Android | Android 8.0 (API 26) |
| Compile SDK | 36 |
| Target SDK | 36 |
| Java Version | 17 |
| Architecture | Android Application |

## 🧩 Reconstructed Components

### MainActivity

- Transparent Activity implementation.
- Handles overlay permission.
- Requests overlay permission when needed; battery and notification permissions are optional.
- Starts the foreground service.
- Uses the original 500 ms background delay.
- Uses the original 200 ms resume background delay.
- Supports immersive system UI behavior.

### PerformanceService

- Foreground service implementation.
- Notification channel management.
- Invisible application overlay.
- Hardware-accelerated View.
- Periodic heartbeat mechanism.
- Service lifecycle management.

### Timing Configuration

| Operation | Interval |
|---|---:|
| Activity background delay | 500 ms |
| Resume background delay | 200 ms |
| Service heartbeat | 3000 ms |

## 🏗️ Project Structure

```text
Performance-spoof/
│
├── .github/
│   └── workflows/
│       └── build.yml
│
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/antutu/ABenchMark/
│   │       ├── res/
│   │       └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── gradle/
│   └── wrapper/
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── RECONSTRUCTION.md
└── README.md
```

## 🚀 Build Instructions

### Option 1: GitHub Actions (Recommended)

No local Android Studio installation is required.

1. Fork or clone this repository.
2. Push the project to GitHub.
3. Open the repository's **Actions** tab.
4. Select **Build ABenchMark source reconstruction**.
5. Wait for the workflow to complete.
6. Open the completed workflow run.
7. Download the `ABenchMark-source-build` artifact.

The workflow automatically:

- Sets up JDK 17.
- Installs the required Android SDK.
- Configures Gradle.
- Builds the debug APK.
- Verifies the application package and components.
- Uploads the APK as a downloadable artifact.

### Option 2: Build Locally

**Requirements:**
- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0

Clone the repository:

```bash
git clone https://github.com/JoyBoy6996/Performance-spoof.git

cd Performance-spoof
```

Build the debug APK:

```bash
chmod +x gradlew

./gradlew clean assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Android Compatibility

- Minimum supported version: Android 8.0.
- Target Android version: API 36.
- Uses Android foreground service APIs.
- Requires appropriate permissions for overlay functionality.
- Notification permission and battery optimization exemption are not activation requirements.
- HyperOS, MIUI, ColorOS, OxygenOS, realme UI, OriginOS, and Funtouch OS are identified when their public build identifiers expose the ROM name. Otherwise diagnostics report an unidentified vendor variant rather than guessing.

The service enters foreground before attaching the overlay. OEM background management can still stop services, so the recovery screen offers version-dependent guidance without relying on private APIs. Build-time checks do not verify behavior on physical OEM devices.

## ⚠️ Important Notes

This repository is a source-level reconstruction, not the original Kotlin source code.

The reconstructed application components preserve the identified app-owned behavior and timing characteristics.

However:

- Original source formatting and comments cannot be recovered from DEX.
- Some compiler-generated Kotlin callbacks are represented using Java equivalents.
- The project does not introduce CPU frequency modifications, OEM performance commands, or undocumented hardware boost mechanisms.
- A foreground service and application package identity do not guarantee activation of an OEM performance profile.
- The generated APK uses a new signing key.

See [RECONSTRUCTION.md](RECONSTRUCTION.md) for detailed technical findings and reconstruction limitations.

## 🛠️ Technologies Used

- Java
- Android SDK
- AndroidX
- Gradle
- GitHub Actions

## 📄 License

Add your preferred open-source license here before distributing the project.

---

<p align="center">
  Made with ⚡ by <a href="https://github.com/JoyBoy6996">JoyBoy</a>
</p>
