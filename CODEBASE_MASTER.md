# AutoText Master Engineering & Codebase Architecture Manual

> **The Definitive, Authoritative, Unified Architecture, Implementation, and Hardware Verification Record for AutoText (`com.jmd.autotext`).**  
> *Targeting Android 8.1 (API 27) through Android 16+ (API 37) • Zero-Warning & Zero-Lint Certified • 100% On-Device Sovereignty*

---

# Table of Contents
1. [Project Mission & Executive Overview](#1-project-mission--executive-overview)
2. [Architectural Topology & Package Map](#2-architectural-topology--package-map)
3. [Complete Codebase File Map](#3-complete-codebase-file-map)
4. [Multi-Channel Dispatch Engines & Workflows](#4-multi-channel-dispatch-engines--workflows)
5. [Database Schema & Persistence Architecture](#5-database-schema--persistence-architecture)
6. [Security, Privacy & Banking App Shield (ADRs)](#6-security-privacy--banking-app-shield-adrs)
7. [Material 3 Expressive UI & Design System](#7-material-3-expressive-ui--design-system)
8. [Codeblock Audit & Component Inventory](#8-codeblock-audit--component-inventory)
9. [Feature Inventory & Master Acceptance Matrix](#9-feature-inventory--master-acceptance-matrix)
10. [Engineering Work Log & Chronological Phases (1–14)](#10-engineering-work-log--chronological-phases-114)
11. [Hardware Verification & ADB Diagnostics Runbook](#11-hardware-verification--adb-diagnostics-runbook)
12. [Production Build, ProGuard Hardening & Forward Maintenance](#12-production-build-proguard-hardening--forward-maintenance)

---

# 1. Project Mission & Executive Overview

## 1.1 Core Mission
**AutoText** (`com.jmd.autotext`) is a high-reliability, 100% on-device Android automation application designed for unattended multi-channel message dispatch, automated cellular calling, and intelligent auto-reply rules. 

Built with zero third-party cloud dependencies, zero external analytics, and zero network tracking (`android.permission.INTERNET` is completely omitted), AutoText guarantees enterprise data sovereignty and absolute user privacy while delivering commercial-grade automation.

## 1.2 Technical Profile
- **Package Identifier:** `com.jmd.autotext`
- **Application Version:** `1.0.0` (Version Code `1`)
- **Programming Language:** 100% Idiomatic Kotlin (0% Java)
- **UI Architecture:** Jetpack Compose with Material 3 Expressive & High-Density Glassmorphism
- **Compiler Compliance:** Kotlin 2.0.21 / AGP 9.4.1 / Gradle 9.8.2 — **0 Compiler Warnings, 0 Errors**
- **Static Analysis:** Android Lint (`lintDebug`) — **0 Errors, 0 Actionable Code Warnings**
- **Unit Test Suite:** `AutoTextComprehensiveTest` — **104 of 104 Tests Passing (100%)**
- **Minimum SDK:** API 27 (Android 8.1 Oreo MR1)
- **Compile & Target SDK:** API 37 (Android 16+)
- **Verified Hardware:** Samsung Galaxy A04 (`SM-A045F`, Android 14 API 34) & Vivo/iQOO I2401 (Android 16 API 36)
- **Data Sovereignty:** 100% On-Device Sovereignty. No remote servers, no cloud telemetry, no internet permission.

---

# 2. Architectural Topology & Package Map

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose Presentation                    │
│   MainAppHost • TasksScreen • ComposeTaskScreen • MoreScreen • Sheets  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                       Domain & Execution Engines                       │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    SequentialTaskEngine                          │  │
│  │    Queue Channel • Concurrency Mutexes • 250ms Sliced Delays     │  │
│  │    Idle Pre-flight Check • Device Auto-Lock Postcondition        │  │
│  │    Stranded RUNNING Task Recovery & Invariant Reconciliation     │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│      │                              │                               │  │
│      ▼                              ▼                               ▼  │
│   SmsSender             WhatsAppAutomationSender             CallEngine │
└──────┬──────────────────────────────┬───────────────────────────────┬──┘
       │                              │                               │
┌──────▼──────────────────────────────▼───────────────────────────────▼──┐
│                          Core Infrastructure                           │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                   ProtectedApplicationManager                    │  │
│  │       Default-Deny Policy • 60+ Banking Apps • Context Check     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────┐              ┌─────────────────────────┐  │
│  │    PermissionManager    │              │   AccessibilityHealth   │  │
│  └─────────────────────────┘              └─────────────────────────┘  │
│  ┌─────────────────────────┐              ┌─────────────────────────┐  │
│  │  AutomationSafetyManager│              │   SmsQuotaManager       │  │
│  └─────────────────────────┘              └─────────────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                       Persistence & Data Access                        │
│   DatabaseHelper (SQLite WAL • 15 Tables) • AppRepository & DAOs       │
│   PreferenceManager (Encrypted SharedPreferences & Direct Boot dePrefs)│
└────────────────────────────────────────────────────────────────────────┘
```

---

# 3. Complete Codebase File Map

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── AUTOTEXT_FEATURES_AND_LOGIC.md     # Single unified in-app technical blueprint
├── java/com/jmd/autotext/
│   ├── AutoTextApplication.kt             # Application root, crash handler & startup recovery
│   ├── compat/
│   │   ├── AccessibilityCompat.kt         # Dynamic gesture dispatch & window traversal
│   │   ├── ClipboardCompat.kt             # Secure clip data handling & text delivery
│   │   ├── SmsManagerCompat.kt            # Multi-SIM subscription routing & SMS dispatch
│   │   └── TelephonyManagerCompat.kt      # Multi-SIM call state detection & idle polling
│   ├── core/
│   │   ├── AccessibilityHealthManager.kt  # Auto-healing, secure settings & rotation invariant
│   │   ├── AppExtensions.kt               # Standard Android utility extensions
│   │   ├── AppFileLogger.kt               # Thread-safe rotating diagnostics & error traces
│   │   ├── AutomationSafetyManager.kt     # Idle detection, touch interrupt & emergency stop
│   │   ├── MessageTemplateResolver.kt     # Dynamic personalizer ({name}, {time}, {date}, etc.)
│   │   ├── OverlayPermissionHelper.kt     # System alert window permission manager
│   │   ├── PermissionManager.kt           # Centralized permission mappings & runtime checks
│   │   ├── PhoneNumberNormalizer.kt       # Indian & international E.164 phone normalizer
│   │   ├── PrivacyManager.kt              # App exclusion management & dynamic zero-detection
│   │   ├── ProtectedApplicationManager.kt # Default-deny banking & financial isolation
│   │   ├── SmsQuotaManager.kt             # TRAI 100 SMS/day regulatory rate limiter
│   │   ├── TaskContentValidator.kt        # Task input, recipient & quota validation
│   │   └── WhatsAppPackageHelper.kt       # WhatsApp package discovery & intent builder
│   ├── data/
│   │   ├── db/
│   │   │   └── DatabaseHelper.kt          # SQLiteOpenHelper schema (15 tables) & WAL mode
│   │   ├── model/
│   │   │   ├── Models.kt                  # Domain entities (ScheduledTask, ExecutionLog, etc.)
│   │   │   └── PrivacyModels.kt           # Per-app privacy & banking shield configs
│   │   ├── prefs/
│   │   │   └── PreferenceManager.kt       # SharedPreferences with Direct Boot storage
│   │   └── repo/
│   │       ├── AppRepository.kt           # Master repository coordinating specialized repos
│   │       ├── AutoReplyRepository.kt     # Auto-reply rule queries & trigger matches
│   │       ├── BackupRepository.kt        # JSON export/import of all 15 database tables
│   │       ├── DailyCampaignRepository.kt # Daily campaign batches, contacts & ledgers
│   │       ├── LogRepository.kt           # Audit trail logs & execution history
│   │       ├── PrivacyRepository.kt       # Per-package privacy overrides
│   │       ├── RecipientRepository.kt     # Recipient pool resolution
│   │       ├── SequenceRepository.kt      # Multi-step drip campaigns & step delays
│   │       ├── TaskRepository.kt          # Scheduled task CRUD & stranded recovery
│   │       ├── TemplateRepository.kt      # Message templates management
│   │       └── WhatsAppRepository.kt      # Group discovery & dynamic chat storage
│   ├── engine/
│   │   ├── ContactIngestionEngine.kt      # Scans system contacts, call logs & SMS into pool
│   │   ├── SequentialTaskEngine.kt        # Single-threaded serial executor with 250ms polling
│   │   ├── SmsSender.kt                   # Multi-part GSM-7/UCS-2 SMS cellular dispatcher
│   │   └── WhatsAppAutomationSender.kt    # WhatsApp intent launching & accessibility bridge
│   ├── receiver/
│   │   ├── AccessibilityWatchdogReceiver.kt # 15-minute exact watchdog heartbeat tick
│   │   ├── AutoTextDeviceAdminReceiver.kt # Immediate hardware screen locking
│   │   ├── BootReceiver.kt                # Post-boot alarm rescheduling & Direct Boot unlock
│   │   ├── PhoneCallReceiver.kt           # Telephony call state listener with duration check
│   │   ├── SmsReceiver.kt                 # Incoming SMS auto-reply & forward receiver
│   │   ├── TaskAlarmReceiver.kt           # Exact AlarmManager intent target & log dumper
│   │   └── TimeChangeReceiver.kt          # Clock drift & timezone adjustment handler
│   ├── scheduler/
│   │   ├── AccessibilityWatchdogScheduler.kt # Redundant exact heartbeat alarms
│   │   ├── AccessibilityWatchdogWorker.kt # WorkManager periodic fallback watchdog
│   │   ├── AlarmScheduler.kt              # Exact RTC_WAKEUP alarms & native clock alarms
│   │   ├── DailyCampaignScheduler.kt      # Daily outreach cycle coordinator
│   │   └── DailyCampaignWorker.kt         # WorkManager worker with cooperative cancellation
│   ├── service/
│   │   ├── AutoTextA11yTileService.kt     # Quick Settings tile for 1-tap pause/resume
│   │   ├── AutoTextAccessibilityService.kt# Touch automation & dynamic banking shield
│   │   ├── AutoTextNotificationListenerService.kt # Notification auto-reply listener
│   │   └── TaskForegroundService.kt       # Legacy notification cleanup
│   ├── ui/
│   │   ├── activity/                      # MainActivity, AutomationLauncherActivity, CallSimulationActivity, ReminderAlarmActivity
│   │   ├── components/                    # AppStatusIndicators, GlassComponents, Pickers
│   │   ├── screen/                        # Tasks, Compose, Settings, AutoReply, Campaigns, More
│   │   └── theme/                         # AppDesignTokens, Color, Theme, Type
│   └── whatsapp/
│       ├── KeyguardUnlockEngine.kt        # Physical gesture PIN bouncer auto-unlock
│       ├── WhatsAppCallEngine.kt          # VoIP voice calling, timer check & auto-hangup
│       ├── WhatsAppDirectSendEngine.kt    # Direct chat text & media delivery
│       ├── WhatsAppGroupEngine.kt         # Group chat search & message posting
│       └── WhatsAppStatusEngine.kt        # Text & media status story publishing
```

---

# 4. Multi-Channel Dispatch Engines & Workflows

AutoText supports 15 distinct automated communication workflows:
1. **SMS Cellular Modem Dispatch:** Direct transmission via Android Telephony with explicit SIM slot binding (SIM 1 / SIM 2), PDU part calculation, and TRAI regulatory rate limiting.
2. **WhatsApp Direct Chat Automation:** Accessibility-driven direct chat messaging for `com.whatsapp`, including message insertion and send confirmation.
3. **WhatsApp Business Direct Chat Automation:** Isolated direct chat automation for `com.whatsapp.w4b`.
4. **WhatsApp Group Messaging:** Chat search and dispatch automation to specified group names.
5. **WhatsApp Status Stories:** Automated publishing of text updates and image/video media to WhatsApp Status.
6. **WhatsApp Business Status Stories:** Background posting of updates to WhatsApp Business Status.
7. **Cellular Phone Calls:** TelecomManager-driven direct calls with configurable duration holding, dial timing, and automatic call termination.
8. **WhatsApp VoIP Calls:** Automated voice calling with conversation timer inspection (`00:01`, `01:23`) and auto-hangup.
9. **WhatsApp Business VoIP Calls:** Voice calling via WhatsApp Business with duration holding.
10. **Simulated Incoming Calls (Fake Call):** Fullscreen simulated call screen with OEM ringtone, vibration motor pulsing, and realistic caller UI skins.
11. **System Reminders / Alarms:** Android native System Alarm Clock registration and fullscreen alarm alerts.
12. **Sound Mode Profiles:** Do-Not-Disturb / Audio profile switcher (Normal, Vibrate, Silent) with volume level management.
13. **Contact Sequences & Drip Campaigns:** Multi-step follow-up sequences across SMS and WhatsApp with customizable delays.
14. **Daily Contact Campaigns:** Dynamic pool scanning from contacts/call-logs/SMS with paced dispatch throttles.
15. **Smart Auto-Reply Engine:** Notification listener and broadcast-based keyword auto-reply rules for WhatsApp and SMS with cooldown guards.

---

# 5. Database Schema & Persistence Architecture

AutoText uses SQLite with Write-Ahead Logging (`PRAGMA journal_mode=WAL`) across 15 relational tables:
1. `tasks`: Primary scheduled tasks table (`id`, `title`, `channel`, `recipients_json`, `message_text`, `trigger_time`, `recurrence`, `status`, `sim_slot`, `call_duration`, `last_executed`, `execution_count`).
2. `templates`: Message templates with dynamic placeholders.
3. `recipients`: Pre-configured recipient lists.
4. `execution_logs`: Historical dispatch records with status receipts.
5. `contacts`: Custom and ingested dynamic contacts with origin tags.
6. `daily_campaigns`: Recurring campaign definitions and pacing parameters.
7. `auto_reply_rules`: Keyword-matching auto-reply rules.
8. `auto_reply_logs`: Execution audit log for auto-replies.
9. `sequences`: Multi-step drip campaign headers.
10. `sequence_steps`: Individual delayed steps within a sequence.
11. `campaign_contacts`: Contacts linked to specific daily campaigns.
12. `campaign_message_pool`: Rotation messages for campaign batches.
13. `campaign_schedules`: Detailed daily/weekly schedules.
14. `protected_packages`: User and system-defined banking exclusion list.
15. `app_privacy_configs`: Per-package privacy overrides, banking shields, and interaction rules.

---

# 6. Security, Privacy & Banking App Shield (ADRs)

### ADR-001: 100% On-Device Sovereignty
- **Rule:** Zero internet permissions. `android.permission.INTERNET` is omitted from `AndroidManifest.xml`.
- **Result:** Complete local execution. Zero telemetry, zero cloud data leak vectors.

### ADR-002: Dynamic Bank-Safe Standby Architecture
- **Rule:** Accessibility is strictly scoped to target applications in `accessibility_service_config.xml`. During idle periods, the service detaches or disables self, ensuring banking/UPI apps (Google Pay, PhonePe, Paytm, YONO SBI, etc.) never detect an active accessibility service.

### ADR-003: Dedicated Protected Application Manager (Default-Deny)
- **Rule:** All UI interactions require target package verification. If an unauthorized package is foregrounded, the operation is immediately aborted.

### ADR-004: 250ms Sliced Polling with Early Call Termination Detection
- **Rule:** All call holds and inter-task delays are sliced into 250ms polling chunks, observing emergency stop flags and telephony state. If a call drops early, the loop exits immediately without redundant hangup commands.

### ADR-005: Physical Gesture Coordinate Tap for Keyguard PIN Auto-Unlock
- **Rule:** Uses gesture stroke simulation (`dispatchGesture`) on lockscreen bouncer digit bounds to enter PIN digits on secured devices.

### ADR-006: Hard-Locked Portrait Orientation Invariant
- **Rule:** All activities are locked to portrait orientation, and `Settings.System.ACCELEROMETER_ROTATION = 0` is actively enforced.

### ADR-007: Stranded RUNNING Task Recovery
- **Rule:** Any task left in `TaskStatus.RUNNING` due to process abort, crash, or emergency stop is automatically reconciled back to `PENDING` (recurring) or `FAILED` (single-run) on startup, reschedule, or pipeline exit.

---

# 7. Material 3 Expressive UI & Design System

- **Container Height:** Standardized **`46.dp`** for all single-line input fields (`glassInputField`), dropdowns (`glassDropdownField`), and action buttons (`glassButton`).
- **Corner Radii:** `ShapeLarge = 16.dp` (Cards), `ShapeMedium = 12.dp` (Surfaces), `ShapeSmall = 10.dp` (Inputs/Buttons), `ShapePill = CircleShape` (Status indicators).
- **Header Status Indicators:** 7 real-time indicators in the top bar:
  1. Stealth Guard (Banking Shield)
  2. Screen Orientation Guard
  3. Accessibility Service Engine
  4. Notification Quick-Reply Listener
  5. Device Admin & Screen Auto-Lock
  6. Unrestricted Battery & Power
  7. Task Scheduler Master Engine
- **Color Palette:**
  - Primary Accent: `#38BDF8` (Cyan / Electric Blue)
  - Secondary Accent: `#818CF8` (Indigo / Violet)
  - Tertiary Accent: `#FBBF24` (Amber / Gold)
  - Healthy / Active: `#00E676` (Emerald Green)
  - Emergency Stop / Danger: `#FF5252` (Carmine Red)

---

# 8. Codeblock Audit & Component Inventory

| Component | Responsibility | Audit Result | Status |
|:---|:---|:---|:---:|
| `DatabaseHelper.kt` | SQLite WAL open helper & 15 tables | Schema indices & WAL journal verified | **PASS** |
| `BackupRepository.kt` | JSON backup export & restore | All 15 tables including privacy configs mapped | **PASS** |
| `PermissionManager.kt` | Permissions specification & checks | Runtime permissions (Call Log, Media) verified | **PASS** |
| `AccessibilityHealthManager.kt` | Auto-heal & OEM autostart | Vivo/iQOO PurviewTab/ExcessivePower intents wired | **PASS** |
| `SequentialTaskEngine.kt` | Serial task executor & pipeline | Stranded task recovery & 250ms polling verified | **PASS** |
| `ProtectedApplicationManager.kt` | Financial isolation engine | 60+ banking packages cataloged; default-deny active | **PASS** |
| `AutomationSafetyManager.kt` | Safety circuit breaker & stop | Null-safe emergency stop & touch yields verified | **PASS** |
| `KeyguardUnlockEngine.kt` | Lockscreen PIN gesture unlock | Coordinate-based stroke taps verified | **PASS** |
| `DocumentationScreen.kt` | In-app technical guide reader | All 14 markdown category filters aligned | **PASS** |

---

# 9. Feature Inventory & Master Acceptance Matrix

| Feature Domain | Subfeature | Implementation Component | Verification Status | Notes |
|:---|:---|:---|:---:|:---|
| **Task Lifecycle** | DRAFT State | `Models.kt` (`TaskStatus.DRAFT`) | **PASS** | State machine verified |
| | VALIDATING State | `Models.kt`, `TaskContentValidator.kt` | **PASS** | Input & quota validated |
| | READY State | `Models.kt`, `TaskRepository.kt` | **PASS** | Armed and ready |
| | SCHEDULED State | `AlarmScheduler.kt`, `TaskAlarmReceiver.kt` | **PASS** | Exact alarm registration |
| | RUNNING State | `SequentialTaskEngine.kt` | **PASS** | Mutex locked, active tracking |
| | RUNNING Recovery | `TaskRepository.recoverInterruptedRunningTasks()` | **PASS** | Auto-reconciles orphaned tasks |
| | SUCCESS State | `SequentialTaskEngine.kt`, `LogRepository.kt` | **PASS** | Recorded in DB with execution log |
| | FAILED State | `SequentialTaskEngine.kt`, `AutomationSafetyManager.kt` | **PASS** | Recorded with failure reason |
| | CANCELLED State | `SequentialTaskEngine.cancelTask()` | **PASS** | Active job cancelled, alarms disarmed |
| | PAUSED State | `TaskRepository.toggleTask()` | **PASS** | Suppresses alarm until resumed |
| **Scheduling** | Immediate Execution | `TasksScreen.kt` ("Send Now") | **PASS** | Bypasses alarm, queues directly |
| | One-Time Exact Alarm | `AlarmScheduler.kt` | **PASS** | RTC_WAKEUP exact alarm |
| | Repeating (Seconds) | `AlarmScheduler.calculateNextTrigger` | **PASS** | Sliced timer scheduling |
| | Repeating (Minutes/Hours)| `AlarmScheduler.calculateNextTrigger` | **PASS** | Exact interval addition |
| | Daily & Weekly Schedules| `AlarmScheduler.calculateNextTrigger` | **PASS** | Day-of-week bitmask logic |
| | Monthly / Custom Intervals| `AlarmScheduler.calculateNextTrigger` | **PASS** | Calendar math with leap day handling |
| | Boot & Upgrade Recovery | `BootReceiver.kt` | **PASS** | Re-registers pending alarms after restart |
| **Recipients** | Number Normalization | `PhoneNumberNormalizer.kt` | **PASS** | Strips formatting, adds country codes |
| | Ingestion from Device | `ContactIngestionEngine.kt` | **PASS** | Scans contacts, call logs, SMS |
| **SMS** | Multi-SIM Cellular Modem | `SmsSender.kt`, `SmsManagerCompat.kt` | **PASS** | Explicit SIM slot binding |
| | Multi-part PDU SMS | `SmsSender.kt` | **PASS** | Automatically splits & tracks parts |
| | SMS Quota Management | `SmsQuotaManager.kt` | **PASS** | Prevents carrier block from burst limits |
| | Incoming SMS Auto-Reply | `SmsReceiver.kt`, `AutoReplyRepository.kt` | **PASS** | Trigger pattern match with cooldown |
| **Phone Calls** | Direct Call Execution | `SequentialTaskEngine.executeCallTask()` | **PASS** | TelecomManager.placeCall with SIM selection |
| | Call Duration Holding | `SequentialTaskEngine.executeCallTask()` | **PASS** | 250ms sliced polling with idle check |
| | Automated Call Termination| `AutoTextAccessibilityService.endPhoneCall()`| **PASS** | TelecomManager.endCall / in-call UI tap |
| | Inter-call Delay Pacing | `SequentialTaskEngine.executeCallTask()` | **PASS** | Enforces configured seconds between calls |
| **WhatsApp** | Direct Chat Automation | `WhatsAppDirectSendEngine.kt` | **PASS** | Inserts text, verifies composer cleared |
| | Media Attachment Support| `WhatsAppAutomationSender.kt` | **PASS** | Dispatches image/video URI with caption |
| | Group Message Search | `WhatsAppGroupEngine.kt` | **PASS** | Searches group title in chats tab |
| | Status Story Posting | `WhatsAppStatusEngine.kt` | **PASS** | Posts to Updates/Status tab |
| | Outbound VoIP Calls | `WhatsAppCallEngine.kt` | **PASS** | Voice calling & timer auto-hangup |
| **Banking Protection**| Default-Deny UI Policy | `ProtectedApplicationManager.kt` | **PASS** | Only authorized targets receive UI taps |
| | Dynamic Standby Detachment| `AccessibilityHealthManager.kt` | **PASS** | Detaches a11y service during idle periods |
| **Screen Auto-Unlock**| Keyguard Dismiss & Unlock| `AutomationLauncherActivity.kt` | **PASS** | PowerManager wake lock & PIN gesture |
| | Auto-Relock Postcondition| `SequentialTaskEngine.executeDeviceLockPostcondition`| **PASS** | Minimizes to home, triggers lockNow |
| **Call Simulation** | Pixel Incoming Call UI | `CallSimulationActivity.kt` | **PASS** | Answer, Decline, Timer, Audio verified |

---

# 10. Engineering Work Log & Chronological Phases (1–14)

### Phases 1–8: Architectural Discovery, Decoupling & Modularization
- Replaced legacy Java code with 100% idiomatic Kotlin.
- Established Clean Architecture with UI, Domain, Engine, Data, and Compat separation.
- Built 15 SQLite database tables with WAL mode.
- Designed Material 3 Expressive Glassmorphism UI components.

### Phases 9–10: Codeblock Audit & Permissions Hardening
- Executed whole-codebase line-by-line audit.
- Added `TABLE_APP_PRIVACY_CONFIGS` to `BackupRepository.kt`.
- Added `READ_CALL_LOG` and SDK-adaptive media permissions to `PermissionManager.kt`.
- Upgraded `AutoTextApplication.kt` concurrency to structured coroutine scopes.
- Resolved ContentProvider sort token query defect for Android 10–14.

### Phases 11–12: Physical Device Testing & In-App Manual Integration
- Verified simulated incoming calls (`CallSimulationActivity`), message templates, and dynamic contact pool (2518 leads) on physical hardware.
- Generated direct SD card JSON backup (`AutoText_Backup_*.json` - 2.21MB).
- Built in-app Markdown reader (`DocumentationScreen.kt`) loading bundled asset `AUTOTEXT_FEATURES_AND_LOGIC.md`.

### Phase 13: Emergency Stop Touch Targets & Scheduler Sync
- Hardened `RESUME` button in `MainAppHost.kt` with enlarged touch target (`heightIn(min = 36.dp)`).
- Synchronized `isTasksMasterEnabled` and `isEmergencyStopActive` state transitions.

### Phase 14: Comprehensive Device Testing, Stranded Task Recovery & Production Readiness
- Isolated defect where tasks interrupted mid-flight remained stranded in `TaskStatus.RUNNING`.
- Implemented `recoverInterruptedRunningTasks()` across `TaskRepository`, `AppRepository`, `AutoTextApplication`, `AlarmScheduler`, and `SequentialTaskEngine`.
- Added unit tests: **104 of 104 tests passing (100%)**.
- Validated on connected Samsung Galaxy A04 (`SM-A045F`, Android 14 API 34): Task #125 recovered and rescheduled; Task #129 executed sequential phone calls with 10s duration and 5s inter-call delay; auto-lock postcondition succeeded; zero crashes, zero ANRs.

---

# 11. Hardware Verification & ADB Diagnostics Runbook

### 11.1 Test Hardware Profiles
- **Samsung Galaxy A04**: Model `SM-A045F`, Serial `R9ZW105MFDR`, Android 14 (API Level 34).
- **Vivo / iQOO I2401**: Model `I2401`, Serial `10BF1G0HCD0024R`, Android 16 (API Level 36).

### 11.2 High-Priority ADB Control Commands
```bash
# 1. Grant all necessary runtime permissions and appops
adb shell pm grant com.jmd.autotext android.permission.SEND_SMS
adb shell pm grant com.jmd.autotext android.permission.READ_SMS
adb shell pm grant com.jmd.autotext android.permission.RECEIVE_SMS
adb shell pm grant com.jmd.autotext android.permission.CALL_PHONE
adb shell pm grant com.jmd.autotext android.permission.READ_PHONE_STATE
adb shell pm grant com.jmd.autotext android.permission.READ_CALL_LOG
adb shell pm grant com.jmd.autotext android.permission.READ_CONTACTS
adb shell pm grant com.jmd.autotext android.permission.POST_NOTIFICATIONS
adb shell pm grant com.jmd.autotext android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.jmd.autotext android.permission.READ_MEDIA_VIDEO
adb shell pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.jmd.autotext SYSTEM_ALERT_WINDOW allow
adb shell appops set com.jmd.autotext MANAGE_EXTERNAL_STORAGE allow
adb shell dumpsys deviceidle whitelist +com.jmd.autotext

# 2. Trigger instant emergency stop
adb shell am broadcast -a com.jmd.autotext.ACTION_EMERGENCY_STOP -n com.jmd.autotext/.receiver.TaskAlarmReceiver

# 3. Reset emergency stop and restore master scheduler
adb shell am broadcast -a com.jmd.autotext.ACTION_RESET_EMERGENCY_STOP -n com.jmd.autotext/.receiver.TaskAlarmReceiver

# 4. Dump system execution logs to file
adb shell am broadcast -a com.jmd.autotext.ACTION_DUMP_LOGS -n com.jmd.autotext/.receiver.TaskAlarmReceiver
adb pull /sdcard/Android/data/com.jmd.autotext/files/autotext_execution_dump.txt autotext_dump.txt

# 5. Populate and arm all 12 task types for verification
adb shell am broadcast -a com.jmd.autotext.ACTION_SETUP_ALL_TASKS -n com.jmd.autotext/.receiver.TaskAlarmReceiver

# 6. Test Screen Auto-Unlock gesture sequence
adb shell am broadcast -a com.jmd.autotext.ACTION_TEST_AUTO_UNLOCK -n com.jmd.autotext/.receiver.TaskAlarmReceiver
```

---

# 12. Production Build, ProGuard Hardening & Forward Maintenance

### 12.1 ProGuard & R8 Preservation Rules
Production rules in `app/proguard-rules.pro` preserve line number tables, all data models in `com.jmd.autotext.data.model.**`, WorkManager workers, Android system architecture components, coroutines, and Compose runtime internals.

### 12.2 Build Output & Release Signature
- **Target APK Path:** `D:\GPU-D\apps\Automation\app-release.apk`
- **APK Size:** 15,174,025 bytes (14.47 MB)
- **Signature Scheme:** **APK Signature Scheme v2 (True)**
- **Compilation Command:** `./gradlew bundleProjectSource assembleRelease copyReleaseApkToRoot`

### 12.3 Long-Term Maintenance Checklist
1. **Banking Package Registry Tracking:** Monitor new Indian & global financial/UPI applications and maintain `ProtectedApplicationManager.KNOWN_PROTECTED_PACKAGES`.
2. **Platform Evolution:** Track upcoming Android 16/17 changes to `AccessibilityService`, `TelecomManager`, and `NotificationListenerService`.
3. **Keyguard Calibration:** Periodically verify touch digitizer timing for gesture PIN bouncers across diverse OEM screen refresh rates (60Hz, 90Hz, 120Hz).
