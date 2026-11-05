# AutoText — Architectural Blueprint & System Topology

## 1. High-Level Architectural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     Jetpack Compose UI                      │
│ (MainAppHost, ComposeTaskScreen, TasksScreen, Settings, etc)│
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│             Programmatic API Layer (Endpoints)              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │                  TaskFeatureApi                   │ │
│ │ (Typed, validated endpoints: WA, SMS, Calls, Reminders) │ │
│ └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Domain & Engine Layer                    │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │                  SequentialTaskEngine                   │ │
│ │ (Queue, State Machine, Concurrency Mutexes, Postconds)  │ │
│ └────────────────────────────┬────────────────────────────┘ │
│   │                          │                           │  │
│   ▼                          ▼                           ▼  │
│ SmsSender         WhatsAppSender         CallEngine│
└───┬──────────────────────────┬───────────────────────────┬──┘
    │                          │                           │
┌───▼──────────────────────────▼───────────────────────────▼──┐
│                   Core Infrastructure                       │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │             ProtectedApplicationManager                 │ │
│ │ (Default-deny, Financial Isolation, Context Verification) │
│ └─────────────────────────────────────────────────────────┘ │
│ ┌───────────────────────┐         ┌───────────────────────┐ │
│ │   PermissionManager   │         │    OemBrandHelper     │ │
│ └───────────────────────┘         └───────────────────────┘ │
│ ┌───────────────────────┐         ┌───────────────────────┐ │
│ │  AccessibilityHealth  │         │ TaskSafetyMgr   │ │
│ └───────────────────────┘         └───────────────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                  Persistence & Data Layer                   │
│   DatabaseHelper (SQLite WAL) • 15 Relational Tables        │
│   TaskRepository • PrivacyRepository • AppRepository        │
└─────────────────────────────────────────────────────────────┘
```

## 2. Component Directory Responsibilities

### 2.1 UI Layer (`com.jmd.helper.ui`)
- `activity`: Host and standalone activities (`MainActivity`, `CallSimulationActivity`, `ReminderAlarmActivity`, `TaskLauncherActivity`).
- `components`: Reusable Material 3 Expressive glassmorphism elements (`GlassComponents.kt`, `AppStatusIndicators.kt`, `Pickers.kt`).
- `screen`: Screen composables grouped by domain (`compose`, `tasks`, `contacts`, `campaign`, `autoreply`, `logs`, `privacy`, `sequence`, `settings`).

### 2.2 Programmatic API Layer (`com.jmd.helper.api`)
- `TaskFeatureApi`: Unified entrypoint providing typed, suspendable, validated programmatic endpoints for all operations (`sendWhatsAppMessage`, `postWhatsAppStatus`, `placeWhatsAppCall`, `sendSms`, `placePhoneCall`, `hangupActiveCall`, `triggerReminder`, `triggerCallSimulation`, `applySoundMode`). Standardizes parameters, error mapping, and result envelopes (`TaskExecutionResult`).

### 2.3 Task Execution Engine (`com.jmd.helper.engine`)
- `SequentialTaskEngine`: Central serial orchestrator with channel-based queue, state machine enforcement (`DRAFT` → `VALIDATING` → `READY` → `SCHEDULED` → `RUNNING` → `SUCCESS`/`FAILED`/`CANCELLED`), mutex isolation (`uiTaskMutex`, `smsMutex`), pre-flight human idle checks, exact call duration holding with early termination detection, and biometric-safe device auto-lock postconditions.
- `SmsSender`: Direct cellular dispatch with multi-SIM slot indexing, multi-part SMS concatenation, and real-time carrier delivery callback listener.
- `WhatsAppSender`: Bridges task parameters to intent dispatchers and accessibility preparation.
- `ContactIngestionEngine`: Background ingestion from system contacts, call logs, SMS, and WhatsApp chats into the dynamic outreach pool.

### 2.4 Core Security, Privacy & OEM Layer (`com.jmd.helper.core`)
- `OemBrandHelper`: Universal multi-brand OEM compatibility engine providing dual-SIM intent extras and in-call disconnect profiles for Samsung, Vivo, iQOO, Oppo, Realme, Xiaomi, OnePlus, Motorola, Nokia, and Transsion devices across Qualcomm, MediaTek, Exynos, and Unisoc chipsets.
- `ProtectedApplicationManager`: Strict default-deny policy for UI interactions, active foreground context verification, known financial package catalog (60+ banking/UPI apps), and instant event abandonment.
- `PermissionManager`: Centralized permission-to-feature mapping, verified runtime checks (including `ANSWER_PHONE_CALLS`), transparent user-directed settings navigation, and graceful degradation rules.
- `PrivacyManager`: App exclusion management, smart accessibility scoping, dynamic zero-detection activation, and audit log masking.
- `AccessibilityHealthManager`: Service operational state tracking, dynamic on-demand lifecycle control, and system auto-rotate invariant enforcement.
- `TaskSafetyManager`: Circuit breakers, failure counters, emergency stop flags, and human touch interruption detection.

### 2.5 Data Persistence Layer (`com.jmd.helper.data`)
- `DatabaseHelper`: Thread-safe SQLite OpenHelper with WAL enabled, managing 15 relational tables across schema migrations.
- `Repositories`: Clean data access abstractions (`TaskRepository`, `PrivacyRepository`, `AppRepository`, `DailyCampaignRepository`, `AutoReplyRepository`, `LogRepository`, `SequenceRepository`, `TemplateRepository`, `WhatsAppRepository`).
- `PreferenceManager`: SharedPreferences backing for application configuration, standby shield options, and auto-unlock credentials.

### 2.6 Services & Receivers (`com.jmd.helper.service`, `com.jmd.helper.receiver`)
- `TaskAccessibilityService`: Background accessibility service for physical UI interaction, window inspection, keyguard unlock, biometric-safe hardware power button screen locking (`GLOBAL_ACTION_LOCK_SCREEN`), and multi-tier call disconnection.
- `TaskNotificationListenerService`: Listens for incoming chat notifications to drive scheduled reply rules.
- `TaskTileService`: Quick Settings tile for 1-tap pause/resume of accessibility for Google Pay compatibility.
- `TaskForegroundService`: Foreground service lifecycle manager.
- `TaskAlarmReceiver`: Exact AlarmManager intent target executing scheduled tasks, campaigns, and diagnostics.
- `BootReceiver`: Boot completed receiver restoring active scheduled alarms.
- `TaskReconciliationReceiver`: Startup and boot reconciliation receiver healing orphaned or stranded running tasks.
- `PhoneCallReceiver` & `SmsReceiver`: Telephony broadcast listeners for incoming triggers and auto-replies.
