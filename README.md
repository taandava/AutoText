# AutoText — Enterprise Android Automation Platform

> **Modern, offline-first Android automation platform for unattended multi-channel message dispatch and voice call execution.**  
> *Targeting Android 8.0 (API 26) through Android 16+ (API 36/37) • Zero-Warning Standard • 100% On-Device Sovereignty*

---

## 🚀 Key Highlights & Capabilities

- **Multi-Channel Automation**:
  - Direct SMS (Single & Dual-SIM multi-subscription support with PDU calculation)
  - Consumer WhatsApp (Direct message, chat navigation, and contact dispatch)
  - WhatsApp Business (Direct customer messaging and catalog dispatch)
  - WhatsApp Status Stories (Media and text publishing for personal & business)
  - Cellular Telephony Calls (Configurable call duration, dial timing & auto-hangup)
  - WhatsApp VoIP Voice Calls (Automated call placement & active duration hangup)
  - Simulated Fake Incoming Calls (Realistic OEM themes with multi-tier ringtone & haptic pulse)
  - Scheduled Audio Alarms & Reminders (Android Alarm Clock sync & voice alerts)
- **Dynamic Zero-Detection Stealth Mode (Banking App Shield)**:
  - Keeps accessibility completely detached (`ENABLED_ACCESSIBILITY_SERVICES` omitted and `ACCESSIBILITY_ENABLED = "0"`) during idle periods.
  - Automatically activates for 2–3s only when a scheduled WhatsApp task triggers, then immediately detaches.
  - Third-party banking and UPI apps (Saraswat Bank GOMONxT, SBI YONO, HDFC, PhonePe, Paytm, etc.) detect **zero** accessibility presence.
  - Instant retraction if an excluded application is detected in the foreground.
- **Strict Display Orientation Invariant**:
  - Hard-locked to portrait orientation across all screens.
  - Actively audits and disables system auto-rotation (`accelerometer_rotation == 0`).
- **Cooperative Cancellation & Emergency Kill Switch**:
  - Immediate task abortion within <=250ms via sliced polling loops.
  - High-priority ADB broadcast triggers (`ACTION_EMERGENCY_STOP` & `ACTION_RESET_EMERGENCY_STOP`).
- **Unattended Execution & Auto-Unlock**:
  - Wakes screen, dismisses keyguard, inputs security PIN via touch simulation, executes task, and locks hardware screen via Device Admin.
- **100% Offline Sovereignty**:
  - Zero external tracking, zero cloud dependencies, zero analytics. All data resides in local encrypted storage and SQLite (`autotext.db`).

---

## 🛡️ Top Header Real-Time Status Indicators

The header bar provides 7 live status indicator icons:
- **Active / Healthy**: Vivid Emerald Green (`#00E676`)
- **Inactive / Attention Needed**: Vivid Carmine Red (`#FF5252`)

1. **Stealth Guard (Banking Shield)**: Dynamic isolation from banking and UPI apps.
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
- **Android SDK**: Compile SDK 36, Min SDK 27, Target SDK 37
- **Build System**: Gradle 9.8.2 with Android Gradle Plugin 8.7.2

### Build Commands
```bash
# Compile and run all 68 unit tests
.\gradlew.bat testDebugUnitTest

# Run Android Lint static analysis (0 errors, 0 warnings required)
.\gradlew.bat lintDebug

# Assemble signed production release APK and copy to root
.\gradlew.bat bundleProjectSource assembleRelease copyReleaseApkToRoot

# Install on connected Android device via ADB
adb -s <DEVICE_ID> install -r app-release.apk
```

---

## 📋 Documentation & Specifications

- [`app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md`](file:///d:/New%20folder/apps/Automation/app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md): Master Technical Blueprint & Architecture Specification *(also rendered offline inside the app under **Settings -> Technical Manual & Logic Guide**)*.
- [`design.md`](file:///d:/New%20folder/apps/Automation/design.md): Material 3 Expressive Design System, UI Specifications & Component Inventory.
- [`agent.md`](file:///d:/New%20folder/apps/Automation/agent.md): Master Engineering Directive, Defect Remediation Log, Hardware Verification Matrix & Operational Runbook.
