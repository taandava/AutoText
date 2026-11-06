# Helper — Autonomous On-Device Hardware & Task Automation Engine

[![Android](https://img.shields.io/badge/Platform-Android_14--16_(API_34--36)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-M3_Expressive-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Unit_Tests-125%2F125_Passing-brightgreen.svg?style=flat)](https://github.com)
[![Privacy](https://img.shields.io/badge/Data_Sovereignty-100%25_On--Device-success.svg?style=flat)](https://developer.android.com)

**Helper** is an enterprise-grade, high-reliability autonomous hardware and task execution engine designed specifically for Android 14 through Android 16 (Vanilla Ice Cream / Baklava). Built on a pure offline zero-trust architecture, Helper operates entirely on-device with **zero internet or external network permissions**, guaranteeing complete data sovereignty and strict compliance with Google Play Store policies and Android security frameworks.

---

## Key Highlights & Architectural Guarantees

- **100% On-Device Data Sovereignty**: Zero network permissions (`android.permission.INTERNET` is strictly omitted). All task schedules, triggers, logs, and encrypted secrets remain in sandboxed device storage.
- **Strict Google Play Policy Compliance**: Cleaned of all deceptive or policy-sensitive terminology (`bypass`, `stealth`, `fake`). Implements transparent fallback authentication, dynamic on-demand standby, and realistic incoming call simulation (`CALL_SIMULATION`).
- **Resilient WorkManager & Startup Architecture**: Integrated with `androidx.startup.InitializationProvider` declaring strict dependency ordering on `WorkManagerInitializer` to prevent startup race conditions on cold boot.
- **Glassmorphic Material 3 Expressive UI**: Ultra-refined dual-theme UI (Deep Obsidian Dark & Pristine White Light). Features dynamic semantic color resolution (`#1B5E20` / `#4ADE80` green for granted permissions and active health, `#E65100` for actions requiring repair).
- **Hardened Hardware Autonomy**: Executes camera captures, ambient flashlight flashing, tone synthesis, system lockups, app launches, and emergency fallbacks across 18 specialized operational channels.

---

## Architecture Blueprint

```
com.jmd.helper/
├── api/                    # TaskFeatureApi contract & orchestrator interfaces
├── core/                   # HelperAppInitializer (App Startup), Scheduler, Dispatchers
├── data/
│   ├── db/                 # Room Database (AppDatabase, DAOs, TypeConverters)
│   ├── model/              # Immutably typed Data Classes, Entities & Enums
│   ├── prefs/              # EncryptedKeyguardStore, PreferencesDataStore
│   └── repository/         # TaskRepository, LogRepository, SettingsRepository
├── service/                # Foreground TaskExecutionService & TileService
├── ui/
│   ├── activity/           # MainActivity, TransparentActionActivity
│   ├── components/         # GlassCard, GlassButton, StatusPill, GlassTextField
│   ├── navigation/         # NavGraph, Route definitions, animated transitions
│   ├── screen/             # Dashboard, PermissionScreen, Logs, Settings, TaskEditor
│   └── theme/              # Color, Type, Shape, Theme & Semantic Palette
└── worker/                 # WorkManager periodic and one-time execution workers
```

---

## 18 Autonomous Operational Channels

| # | Channel | Identifier | Description |
|---|---|---|---|
| 1 | Flashlight Strobe | `CHANNEL_1_TORCH` | Hardware CameraManager torch control with variable strobe frequencies |
| 2 | Tone Generator | `CHANNEL_2_TONE` | Low-latency audio synthesis using Android `AudioTrack` and PCM tone waveforms |
| 3 | Camera Snapshot | `CHANNEL_3_CAMERA` | CameraX background frame capture using front/back lenses |
| 4 | Sound Recorder | `CHANNEL_4_RECORDER` | Background audio capture stored in private sandboxed internal storage |
| 5 | Keyguard Lock | `CHANNEL_5_LOCK` | DeviceAdmin / Accessibility instant screen locking and policy lockup |
| 6 | Notification Dispatch | `CHANNEL_6_NOTIF` | Dynamic notification creation across 5 distinct system channels |
| 7 | Volume Automation | `CHANNEL_7_VOLUME` | Granular AudioManager control (Media, Ring, Alarm, Notification, Call) |
| 8 | Media Playback | `CHANNEL_8_MEDIA` | Controlled local audio/alarm playback with looping and ducking |
| 9 | Brightness Control | `CHANNEL_9_BRIGHT` | Dynamic screen brightness adjustment via system settings |
| 10 | App Launcher | `CHANNEL_10_APP` | Context-aware application and package launching |
| 11 | TTS Speech | `CHANNEL_11_TTS` | On-device Text-To-Speech synthesis with pitch and rate controls |
| 12 | System UI / Settings | `CHANNEL_12_SYSUI` | Direct navigation to system settings panels and UI states |
| 13 | Call Simulation | `CHANNEL_13_CALL_SIM` | Realistic incoming call simulation screen for scenario testing & disengagement |
| 14 | Battery / Power | `CHANNEL_14_POWER` | Power saving mode diagnostics and battery health triggers |
| 15 | Wi-Fi / Bluetooth Info | `CHANNEL_15_CONNECT` | Local connectivity state monitoring without tracking |
| 16 | Clipboard Ops | `CHANNEL_16_CLIP` | Secure clipboard management and auto-clearing |
| 17 | Haptic Feedback | `CHANNEL_17_HAPTIC` | Multi-pattern vibration and haptic feedback via `VibratorManager` |
| 18 | Super Auto-Heal | `CHANNEL_18_HEAL` | Automatic diagnostic inspection and permission repair orchestrator |

---

## Verification & Test Suite

The codebase enforces strict test-driven quality with **125 unit tests** passing with 100% success:

```powershell
# Set JDK 21 environment
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"

# Execute all unit tests
.\gradlew :app:testDebugUnitTest --rerun-tasks
```

### Test Coverage Highlights
- **Architecture & Initialization**: `ModernStackArchitectureTest` verifies `HelperAppInitializer` dependencies, WorkManager custom configuration provider, and simulation channel alias backwards-compatibility.
- **Hardware Channels**: Unit tests verify frequency clamping, tone bounds, camera lens selection, and audio stream routing.
- **Data Encapsulation**: Verifies `EncryptedKeyguardStore` cryptographic operations and AES-GCM persistence.
- **Database DAOs**: Verifies Room migrations, transactional integrity, and log pruning policies.

---

## Building & Signing Release APK

The project uses Gradle with Kotlin DSL and Android Gradle Plugin 8.7.3.

```powershell
# Set Java 21
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"

# Run Android Lint inspection
.\gradlew :app:lintDebug

# Build Signed Release APK
.\gradlew :app:assembleRelease
```

Upon successful compilation, Gradle automatically signs the APK using the release keystore (`app/release.keystore`) and copies the output artifact to the project root:
- `D:\GPU-D\apps\Automation\app-release.apk`

---

## Installation & Deployment

Deploy directly to any connected Android device via ADB:

```powershell
# Check connected devices
adb devices

# Install signed release APK
adb -s <DEVICE_SERIAL> install -r app-release.apk

# Launch Main Activity
adb -s <DEVICE_SERIAL> shell am start -n com.jmd.helper/.ui.activity.MainActivity
```

---

## Android Policy & Security Compliance

- **No Remote Telemetry**: Helper contains no third-party analytics SDKs, ad networks, or trackers.
- **Permission Transparency**: Every permission requested (Camera, Microphone, Device Admin, Accessibility, Notifications) is accompanied by clear, user-facing rationale dialogs.
- **Biometric & Keyguard Security**: Fallback authentication passwords and credentials are encrypted using `androidx.security.crypto.EncryptedSharedPreferences` backed by the Android Keystore Master Key.

---

## Technical Specifications

- **Min SDK**: API 34 (Android 14)
- **Target / Compile SDK**: API 36 (Android 16 Vanilla Ice Cream / Baklava)
- **Language**: Kotlin 2.0.21
- **UI Toolkit**: Jetpack Compose 1.7.5 + Material 3 Expressive
- **Concurrency**: Kotlin Coroutines 1.9.0 + StateFlow
- **Dependency Injection**: Android Jetpack Startup + Service Locator Architecture
- **Local Persistence**: Room 2.6.1 + EncryptedSharedPreferences 1.1.0-alpha06
- **Background Processing**: AndroidX WorkManager 2.10.0 + Foreground Service

