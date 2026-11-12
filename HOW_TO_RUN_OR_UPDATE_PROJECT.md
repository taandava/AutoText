# Helper — Full Source Codebase & Update Guide

This folder contains the complete, unabridged, and pristine source code of **Helper** (`com.jmd.helper`), extracted directly from the application APK.

With this directory, you have everything required to inspect, modify, enhance, build, and deploy the application from anywhere.

---

## 1. Project Structure

```
codebase/
├── app/
│   ├── build.gradle.kts               # Android application build configuration
│   ├── proguard-rules.pro             # R8 / ProGuard optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml    # App manifest (0% internet permission)
│       │   ├── java/com/jmd/helper/   # 142 Kotlin source files (Clean Architecture)
│       │   │   ├── api/               # Public Task Feature API
│       │   │   ├── compat/            # OS Compatibility Layer (API 27–37)
│       │   │   ├── core/              # Security, Safety, A11y & Privacy Managers
│       │   │   ├── data/              # SQLite, Room ORM, Repositories, DataStore
│       │   │   ├── engine/            # Sequential Execution & Dispatch Engines
│       │   │   ├── receiver/          # Broadcast Receivers (Boot, Alarm, Phone)
│       │   │   ├── scheduler/         # AlarmScheduler & WorkManager Workers
│       │   │   ├── service/           # Accessibility & Quick Tile Services
│       │   │   ├── ui/                # Material 3 Expressive UI & Screens
│       │   │   └── whatsapp/          # WhatsApp Automation Engine
│       │   ├── res/                   # Drawables, layouts, mipmaps, strings
│       │   └── assets/                # Documentation & Markdown assets
│       └── test/java/com/jmd/helper/  # Unit test suites (141 tests)
├── gradle/wrapper/                    # Gradle wrapper binaries
├── build.gradle.kts                   # Root build script
├── settings.gradle.kts                # Project settings
├── gradle.properties                  # JVM & AndroidX build parameters
├── gradlew & gradlew.bat              # Universal cross-platform build runners
├── README.md                          # Full architectural documentation
└── APP_COMPLETE_REQUIREMENTS_SPECIFICATION.md # ISO/IEEE requirements spec
```

---

## 2. How to Open in Android Studio

1. Launch **Android Studio** (Koala / Ladybug / Meerkat or newer).
2. Select **File > Open...** (or **Open an Existing Project**).
3. Navigate to this folder (the folder containing `build.gradle.kts` and `settings.gradle.kts`) and click **OK**.
4. Allow Gradle sync to complete.
5. Connect your Android device or start an emulator and click **Run 'app'**.

---

## 3. How to Build & Test from Terminal (CLI)

### Prerequisites
- **JDK 21** installed (e.g., Eclipse Adoptium Temurin 21 or Android Studio bundled JBR).
- Set `JAVA_HOME` pointing to JDK 21.

### Commands

#### Run Unit Tests
```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
.\gradlew.bat testDebugUnitTest

# macOS / Linux
export JAVA_HOME=/path/to/jdk-21
./gradlew testDebugUnitTest
```

#### Build Signed Release APK
```bash
# Windows PowerShell
.\gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```
The compiled signed release APK will be generated at:
- `app/build/outputs/apk/release/app-release.apk`
- Automatically copied to the project root as `app-release.apk`.

#### Install on Connected Device
```bash
adb install -r app-release.apk

# Grant Write Secure Settings for Bank-Safe Dynamic On-Demand Standby:
adb shell pm grant com.jmd.helper android.permission.WRITE_SECURE_SETTINGS
```

---

## 4. How to Update or Modify the App

1. **Add or Modify Task Types / Channels**:
   - Inspect [ChannelType.kt](app/src/main/java/com/jmd/helper/data/model/ChannelType.kt) and [TaskFeatureApi.kt](app/src/main/java/com/jmd/helper/api/TaskFeatureApi.kt).
   - Update UI sheets in [app/src/main/java/com/jmd/helper/ui/screen/compose/](app/src/main/java/com/jmd/helper/ui/screen/compose/).
2. **Modify UI Screens or Dialogs**:
   - All UI is built using 100% Jetpack Compose with Material 3 Expressive styling located in `app/src/main/java/com/jmd/helper/ui/`.
3. **Run Verification Before Release**:
   - Always run `./gradlew testDebugUnitTest` to verify that all 141 tests pass.
   - Run `./gradlew assembleRelease` to package your changes into a new signed APK containing the updated source code!

---

## 5. Architectural Invariants to Maintain
- **Zero Internet Permission**: Keep `android.permission.INTERNET` omitted from `AndroidManifest.xml` (100% on-device data sovereignty).
- **Zero `@Suppress` Annotations**: Write clean, idiomatic Kotlin code without suppression masks.
- **Material 3 Expressive UI**: Use standard 8-point compact layout tokens with minimum $48\text{dp}$ touch targets.

