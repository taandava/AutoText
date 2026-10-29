# AutoText (`com.jmd.autotext`)

> **Enterprise-Grade Offline-First Android Multi-Channel Automation Platform**  
> *Engineered for mission-critical, unattended communication dispatch, regulatory compliance, and 100% on-device privacy.*

---

## 📱 Overview

AutoText is a modern Android application written entirely in Kotlin with Jetpack Compose that automates communication across seven core channels:
1. **Direct Cellular SMS**: Multi-SIM slot targeting with dynamic GSM-7 vs. UCS-2 character calculation and TRAI regulatory rate limiting.
2. **Consumer WhatsApp**: Direct chat deep-linking, touch simulation, and send button automation.
3. **WhatsApp Business (W4B)**: Customer outreach and marketing dispatches via `com.whatsapp.w4b`.
4. **WhatsApp Status Stories**: Automated publishing of images, videos, and text captions to "My Status" without user taps.
5. **Direct Native Telephony Calls**: Automated outgoing phone calls with custom call duration limits, auto-hangup timing, and inter-call pacing.
6. **WhatsApp VoIP Voice Calls**: Automated WhatsApp voice calling, in-call status monitoring, and auto-hangup.
7. **Simulated Fake Incoming Calls**: Full-screen lockscreen bypass with customizable caller identities, vibration, and ringtones.
8. **Audio Alarms & Reminders**: System clock app integration, TTS voice announcements, and countdown alerts.

---

## 🛡️ Core System Invariants & Safety

### 1. Display Auto-Rotate Protection Guard
- All 4 application activities (`MainActivity`, `FakeCallActivity`, `ReminderAlarmActivity`, `AutomationLauncherActivity`) are hard-locked to portrait mode (`android:screenOrientation="portrait"`).
- `PopupShield.kt` shields system UI packages (`com.android.systemui`, `com.android.settings`, `com.android.launcher`) and strictly avoids clicking rotation tiles or navigation bar rotation buttons.
- `AccessibilityHealthManager.enforceAutoRotateInvariant()` actively monitors and resets `Settings.System.ACCELEROMETER_ROTATION` to `0` across activity lifecycles and background task postconditions.
- Screen Orientation Guard status indicator in header and settings guide shows live orientation health.

### 2. Immediate Task Halt & Emergency Stop
- **Master Scheduler Switch (`prefs.isTasksMasterEnabled`)**: Instantly pauses all background engines, cancels registered alarms, and suppresses incoming triggers.
- **Safety Emergency Stop (`prefs.isEmergencyStopActive`)**: Prominent red `🛑 STOP` button in the header bar.
- **250ms Sliced Delay Polling**: All execution delays, call holds, and pacing pauses poll cancellation state every 250ms for instantaneous stopping.
- Complete queue draining and accessibility state reset via `AutoTextAccessibilityService.clearPending()`.

### 3. Outgoing Call vs. Missed Call Auto-Reply Discrimination
- **Outgoing Unanswered Dials (`duration == 0`)**: Strictly ignored; never triggers an auto-reply.
- **Outgoing Connected Calls (`duration > 0`)**: Triggers `CALL_ENDED` auto-reply upon conversation conclusion.
- **Incoming Unanswered Calls**: Triggers `MISSED_CALL` auto-reply.
- **Incoming Answered Calls (`duration > 0`)**: Triggers `CALL_ENDED` auto-reply.
- Multi-attempt polling queries Android's `CallLog.Calls` to let telephony providers commit accurate call durations.

### 4. Zero-Default Clean Text Authoring
- All text inputs across Task Composer, Sequence Studio, Campaign Builder, and Auto-Reply rules initialize strictly to empty strings (`""`) without prebuilt entries or fallback text injection.

### 5. Unattended Screen Unlock & Auto-Lock Postcondition
- Automated Keyguard unlock via touch simulation using hardware-backed or Direct Boot encrypted PIN.
- Automatic hardware screen lock postcondition executed via Accessibility Service and Device Administration (`AutoTextDeviceAdminReceiver.lockNow`).

### 6. SQLite Write-Ahead Logging (WAL Mode)
- Configured via `DatabaseHelper.onConfigure()`.
- Unlocks concurrent read and write transactions: background batch campaign dispatches and log writes commit to `-wal` without stalling UI queries in `TasksScreen`, `ExecutionLogsScreen`, or `DailyAutoSenderScreen`.
- Foreign key constraints enabled with `ON DELETE CASCADE` integrity.

### 7. Rolling Log Auto-Pruning Policy
- Auto-pruning in `LogRepository` caps execution logs to a maximum of 5,000 entries and a 45-day retention window.
- Opportunistically invoked in the background every 100 log writes, preventing database bloat over long-term operation.

### 8. Accessibility Standby Event Throttling
- When idle (`isAutomating == false`), `AutoTextAccessibilityService` throttles its event mask to touch and window state changes only (`STANDBY_EVENT_MASK`).
- Discards scroll, window content, and text change events from other apps, drastically slashing background CPU and battery consumption.
- Switches dynamically to `TYPES_ALL_MASK` only during active automation sequences.

### 9. Hardware Keystore AES-256-GCM Encryption
- Auto-unlock PIN stored with 256-bit AES encryption managed by `AndroidKeyStore` (`HW_ENC:`).
- Dual-tier Direct Boot fallback (`ENC:`) stored in device-protected storage (`dePrefs`) ensures keyguard auto-unlock functions reliably even across cold reboots before first user unlock.

### 10. Responsive Dashboard Header Layout
- Compact, icon-only action buttons for **New Task** (`+`) and **Emergency Stop** (`🛑`).
- Responsive header grid gracefully accommodates all 7 real-time system health indicators without clipping across varying screen sizes.

---

## 🏗️ Architecture & Package Layout

```
com.jmd.autotext
├── AutoTextApplication.kt       # Application entry point, crash dump interception & daemon init
├── compat/                      # Android API backward-compatibility bridges
├── core/                        # System health, safety, SMS quotas, rotation invariant & logging
├── data/                        # SQLite persistence, Direct Boot prefs, repositories & models
├── engine/                      # Sequential task execution, SMS & WhatsApp senders
├── receiver/                    # Alarms, boot receivers, call listeners & device admin
├── scheduler/                   # Exact AlarmManager & WorkManager campaign coordinators
├── service/                     # Accessibility touch automation & notification listeners
├── ui/                          # Jetpack Compose UI (Screens, Dialogs, Theme, Status Indicators)
└── whatsapp/                    # WhatsApp direct, group, status, and VoIP engines
```

---

## 🚦 System Health Indicators

The top header bar displays 7 real-time status indicator icons:
- **Active / Healthy**: Vivid Emerald Green (`#00E676`)
- **Inactive / Attention Needed**: Vivid Carmine Red (`#FF5252`)

1. **Stealth Guard (Banking Shield)**: Isolates automation from banking and UPI apps.
2. **Screen Orientation Guard**: Ensures system auto-rotate remains strictly disabled (`0`).
3. **Accessibility Service Engine**: Live accessibility binder connection and touch simulation.
4. **Notification Quick-Reply Listener**: Notification listener for instant auto-replies.
5. **Device Admin & Screen Auto-Lock**: Device Admin privilege for hardware screen re-locking.
6. **Unrestricted Battery & Power**: Battery saver exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
7. **Task Scheduler Master Engine**: Master scheduler status; turns Red if paused or Emergency Stop active.

---

## 🛠️ Build & Installation

### Requirements
- **JDK**: Java 17
- **Android SDK**: Compile SDK 37, Min SDK 26, Target SDK 37
- **Build System**: Gradle 9.8.2 with Android Gradle Plugin 8.9.2

### Build Commands
```bash
# Compile and run all 60 unit tests
.\gradlew.bat testDebugUnitTest

# Assemble signed production release APK and copy to root
.\gradlew.bat assembleRelease copyReleaseApkToRoot

# Install on connected Android device via ADB
adb -s <DEVICE_ID> install -r app-release.apk
```

---

## 📋 Comprehensive Technical Blueprint

For full architectural diagrams, algorithmic state machines, SQL schemas, and diagnostic commands, see the authoritative internal specification:
- [`app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md`](file:///d:/New%20folder/apps/Automation/app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md)
*(Also rendered directly in the app under **Settings -> Technical Manual & Logic Guide**).*
