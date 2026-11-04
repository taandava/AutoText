# AutoText — Enterprise Android Automation Platform

> **Modern, offline-first Android automation platform for unattended multi-channel message dispatch, cellular calling, and voice automation.**  
> *Targeting Android 8.1 (API 27) through Android 16+ (API 37) • Zero-Warning Standard • 100% On-Device Sovereignty*

---

## 🚀 Key Highlights & Capabilities

- **Multi-Channel Automation**:
  - **Direct Cellular SMS**: Multi-subscription SIM routing (SIM 1 / SIM 2), GSM-7/UCS-2 PDU calculation, and TRAI safety quota rate limiting.
  - **Consumer WhatsApp**: Direct chat navigation, text & media dispatch, and auto-send confirmation.
  - **WhatsApp Business**: Dedicated customer messaging and catalog dispatch for `com.whatsapp.w4b`.
  - **WhatsApp Status Stories**: Background posting of text and media stories for personal and business accounts.
  - **Cellular Telephony Calls**: Configurable call duration, early hangup detection, dial timing, and automatic call termination.
  - **WhatsApp VoIP Voice Calls**: Outbound voice calling with conversation timer inspection and duration auto-hangup.
  - **Simulated Incoming Calls**: Realistic OEM call simulation with multi-tier ringtones and haptic motor pulsing.
  - **System Reminders & Alarms**: Native Alarm Clock sync, full-screen heads-up display, and TTS voice alerts.
  - **Contact Sequences & Drip Campaigns**: Multi-step follow-up sequences across SMS and WhatsApp.
  - **Daily Contact Campaigns**: Dynamic pool scanning from phonebook, call logs, and SMS with batch pacing.
  - **Smart Auto-Reply Engine**: Notification listener and SMS/Call broadcast receiver with keyword matching and cooldown guards.
- **Dynamic Zero-Detection Stealth Mode (Banking App Shield)**:
  - Keeps accessibility completely detached (`ENABLED_ACCESSIBILITY_SERVICES` omitted and `ACCESSIBILITY_ENABLED = "0"`) during idle periods.
  - Automatically activates for 2–3s only when a scheduled WhatsApp task triggers, then immediately detaches.
  - Third-party banking and UPI apps (Google Pay, PhonePe, Paytm, BHIM, YONO SBI, HDFC, ICICI, etc.) detect **zero** accessibility presence.
  - Instant retraction if an excluded or financial application is detected in the foreground.
- **Strict Display Orientation Invariant**:
  - Hard-locked to portrait orientation across all screens.
  - Actively audits and disables system auto-rotation (`accelerometer_rotation == 0`).
- **Cooperative Cancellation & Emergency Kill Switch**:
  - Immediate task abortion within <=250ms via sliced polling loops.
  - High-priority ADB broadcast triggers (`ACTION_EMERGENCY_STOP` & `ACTION_RESET_EMERGENCY_STOP`).
- **Unattended Execution & Auto-Unlock**:
  - Wakes screen, dismisses keyguard, inputs security PIN via touch simulation, executes task, and locks hardware screen via Device Admin.
- **Stranded RUNNING Task Recovery**:
  - Automatically reconciles orphaned tasks left in `RUNNING` status on app startup, alarm rescheduling, or engine pipeline exit.
- **100% Offline Sovereignty**:
  - Zero external tracking, zero cloud dependencies, zero analytics. No `android.permission.INTERNET` requested. All data resides in local encrypted storage and SQLite (`autotext.db`).

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
- **Android SDK**: Compile SDK 37, Min SDK 27, Target SDK 37
- **Build System**: Gradle 9.8.2 with Android Gradle Plugin 8.7.2 / 9.4.1

### Build Commands
```bash
# Compile and run all 104 unit tests (100% pass rate)
.\gradlew.bat testDebugUnitTest

# Run Android Lint static analysis (0 errors, 0 project code warnings)
.\gradlew.bat lintDebug

# Assemble signed production release APK and copy to root
.\gradlew.bat bundleProjectSource assembleRelease copyReleaseApkToRoot
```

---

## 📖 Comprehensive Documentation
For the complete, authoritative, all-in-one technical manual covering all 14 engineering phases, database schemas, and hardware verification, refer to:
- **Root Master Codebase Manual**: [`CODEBASE_MASTER.md`](./CODEBASE_MASTER.md)
- **In-App Interactive Manual**: [`app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md`](./app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md) (viewable via the in-app Documentation screen).
