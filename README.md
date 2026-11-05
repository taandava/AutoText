# AutoText — Enterprise Android Communication & Task Platform

> **Modern, offline-first Android application for unattended multi-channel message dispatch, cellular calling, and intelligent task execution.**  
> *Targeting Android 8.1 (API 27) through Android 16+ (API 37) • Zero-Warning Standard • 100% On-Device Sovereignty*

---

## 🚀 Key Highlights & Capabilities

- **Multi-Channel Dispatch & Task**:
  - **Direct Cellular SMS**: Multi-subscription SIM routing (SIM 1 / SIM 2) across Qualcomm, MediaTek, Exynos, and Unisoc chipsets, GSM-7/UCS-2 PDU calculation, and TRAI safety quota rate limiting.
  - **Universal Multi-Brand Cellular Calling**: Cross-vendor OEM dual-SIM handling (`OemBrandHelper.kt`) for Samsung, Vivo, iQOO, Oppo, Realme, Xiaomi, Motorola, Nokia, and Transsion. Enforces exact call duration (e.g. 10s with dial time included) and multi-tier hangup (`TelecomManager.endCall()`, in-call dialer foregrounding, 20+ multilingual OEM disconnect button IDs, and `KEYCODE_HEADSETHOOK`).
  - **Consumer WhatsApp Direct Chat**: Accessibility-driven direct chat navigation, text & media dispatch, semantic designated Send button targeting, and `VoiceNoteProtectionGuard` to prevent voice note (microphone) collisions.
  - **WhatsApp Business**: Dedicated customer messaging and catalog dispatch for `com.whatsapp.w4b`.
  - **WhatsApp VoIP Voice Calls**: Outbound voice calling with toolbar vs popup dropdown menu disambiguation ("Voice call" vs "Video call"), conversation timer inspection (`00:01`), and auto-hangup.
  - **WhatsApp Status Stories**: Background posting of text and media stories for personal and business accounts.
  - **Simulated Incoming Calls (Call Simulation)**: Realistic OEM call simulation with multi-tier ringtones, vibration motor pulsing, and interactive caller UI.
  - **System Reminders & Alarms**: Native Alarm Clock sync, full-screen heads-up display, and TTS voice alerts.
  - **Contact Sequences & Drip Campaigns**: Multi-step follow-up sequences across SMS and WhatsApp.
  - **Daily Contact Campaigns**: Dynamic pool scanning from phonebook, call logs, and SMS with batch pacing.
  - **Smart Auto-Reply Engine**: Notification listener and SMS/Call broadcast receiver with keyword matching and cooldown guards.
  - **Programmatic Feature API Layer**: Typed, suspendable endpoints (`TaskFeatureApi.kt`) for all app operations.
- **Biometric-Safe Hardware Auto-Lock**:
  - Emulates physical hardware Power button press (`GLOBAL_ACTION_LOCK_SCREEN`).
  - Zero third-party administrative lockouts; preserves fingerprint and face biometric wake on subsequent user unlock.
- **Dynamic Zero-Detection Stealth Mode (Banking App Shield)**:
  - Keeps accessibility completely detached (`ENABLED_ACCESSIBILITY_SERVICES` omitted and `ACCESSIBILITY_ENABLED = "0"`) during idle periods.
  - Directly activates for 2–3s only when a scheduled WhatsApp task triggers, then immediately detaches.
  - Third-party banking and UPI apps (Google Pay, PhonePe, Paytm, BHIM, YONO SBI, HDFC, ICICI, etc.) detect **zero** accessibility presence.
  - Instant retraction if an excluded or financial application is detected in the foreground.
- **Strict Display Orientation Invariant**:
  - Hard-locked to portrait orientation across all screens.
  - Actively audits and disables system auto-rotation (`accelerometer_rotation == 0`).
- **Cooperative Cancellation & Emergency Kill Switch**:
  - Immediate task abortion within <=250ms via sliced polling loops.
  - High-priority ADB broadcast triggers (`ACTION_EMERGENCY_STOP` & `ACTION_RESET_EMERGENCY_STOP`).
- **Unattended Execution & Auto-Unlock**:
  - Wakes screen, dismisses keyguard, inputs security PIN via touch simulation, executes task, returns to home screen, and locks hardware screen safely.
- **Stranded RUNNING Task Recovery**:
  - Directly reconciles orphaned tasks left in `RUNNING` status on app startup, alarm rescheduling, or engine pipeline exit.
- **Policy-Safe Terminology**:
  - Compliant with Google Play Store and OEM package policies; sanitized to 2-word naming conventions ("Task", "Smart Dispatch").
- **100% Offline Sovereignty**:
  - Zero external tracking, zero cloud dependencies, zero analytics. No `android.permission.INTERNET` requested. All data resides in local encrypted storage and SQLite (`autotext.db`).

---

## 🛡️ Top Header Real-Time Status Indicators

The header bar provides 7 live status indicator icons:
- **Active / Healthy**: Vivid Emerald Green (`#00E676`)
- **Inactive / Attention Needed**: Vivid Carmine Red (`#FF5252`)

1. **Stealth Guard (Banking Shield)**: Dynamic isolation from banking and UPI apps.
2. **Screen Orientation Guard**: Ensures system auto-rotate remains strictly disabled (`0`).
3. **Task Engine**: Live accessibility binder connection and touch simulation.
4. **Notification Quick-Reply Listener**: Notification listener for instant auto-replies.
5. **Device Admin & Screen Auto-Lock**: Device Admin privilege for hardware screen re-locking.
6. **Unrestricted Battery & Power**: Battery saver exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
7. **Task Scheduler Master Engine**: Master scheduler status; turns Red if paused or Emergency Stop active.

---

## 🛠️ Build & Installation

### Requirements
- **JDK**: Java 17 / 21
- **Android SDK**: Compile SDK 37, Min SDK 27, Target SDK 37
- **Build System**: Gradle 9.8.2 with Android Gradle Plugin 9.4.1

### Build Commands
```bash
# Compile and run all 107 unit tests (100% pass rate)
.\gradlew.bat testDebugUnitTest

# Run Android Lint static analysis (0 errors, 0 project code warnings)
.\gradlew.bat lintDebug

# Assemble signed production release APK and copy to root
.\gradlew.bat bundleProjectSource assembleRelease copyReleaseApkToRoot
```

---

## 📖 Comprehensive Documentation
For the complete, authoritative, all-in-one technical manual covering all 17 engineering phases, database schemas, and hardware verification, refer to:
- **Root Master Codebase Manual**: [`CODEBASE_MASTER.md`](./CODEBASE_MASTER.md)
- **Architectural Decision Records**: [`CODEBASE_MEMORY/docs/DECISIONS.md`](./CODEBASE_MEMORY/docs/DECISIONS.md)
- **Feature Acceptance Matrix**: [`CODEBASE_MEMORY/docs/FEATURE_MATRIX.md`](./CODEBASE_MEMORY/docs/FEATURE_MATRIX.md)
- **Codebase Memory Vault**: [`CODEBASE_MEMORY/docs/INDEX.md`](./CODEBASE_MEMORY/docs/INDEX.md)
- **In-App Interactive Manual**: [`app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md`](./app/src/main/assets/AUTOTEXT_FEATURES_AND_LOGIC.md) (viewable via the in-app Documentation screen).
