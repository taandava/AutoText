# AutoText — Architectural Blueprint & System Topology

## 1. High-Level Architectural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     Jetpack Compose UI                      │
│ (MainAppHost, ComposeTaskScreen, TasksScreen, Settings, etc)│
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
│ SmsSender         WhatsAppAutomationSender         CallEngine│
└───┬──────────────────────────┬───────────────────────────┬──┘
    │                          │                           │
┌───▼──────────────────────────▼───────────────────────────▼──┐
│                   Core Infrastructure                       │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │             ProtectedApplicationManager                 │ │
│ │ (Default-deny, Financial Isolation, Context Verification) │
│ └─────────────────────────────────────────────────────────┘ │
│ ┌───────────────────────┐         ┌───────────────────────┐ │
│ │   PermissionManager   │         │  AccessibilityHealth  │ │
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

### 2.1 UI Layer (`com.jmd.autotext.ui`)
- `activity`: Host and standalone activities (`MainActivity`, `FakeCallActivity`, `ReminderAlarmActivity`, `AutomationLauncherActivity`).
- `components`: Reusable Material 3 Expressive glassmorphism elements (`GlassComponents.kt`, `AppStatusIndicators.kt`, `Pickers.kt`).
- `screen`: Screen composables grouped by domain (`compose`, `tasks`, `contacts`, `campaign`, `autoreply`, `logs`, `privacy`, `sequence`, `settings`).

### 2.2 Task Execution Engine (`com.jmd.autotext.engine`)
- `SequentialTaskEngine`: Central serial orchestrator with channel-based queue, state machine enforcement (`DRAFT` → `VALIDATING` → `READY` → `SCHEDULED` → `RUNNING` → `SUCCESS`/`FAILED`/`CANCELLED`), mutex isolation (`uiAutomationMutex`, `smsMutex`), pre-flight human idle checks, and device auto-lock postconditions.
- `SmsSender`: Direct cellular dispatch with multi-SIM slot indexing, multi-part SMS concatenation, and real-time carrier delivery callback listener.
- `WhatsAppAutomationSender`: Bridges task parameters to intent dispatchers and accessibility preparation.
- `ContactIngestionEngine`: Background ingestion from system contacts, call logs, SMS, and WhatsApp chats into the dynamic outreach pool.

### 2.3 Core Security, Privacy & Health (`com.jmd.autotext.core`)
- `ProtectedApplicationManager`: Strict default-deny policy for UI interactions, active foreground context verification, known financial package catalog, and instant event abandonment.
- `PermissionManager`: Centralized permission-to-feature mapping, verified runtime checks, transparent user-directed settings navigation, and graceful degradation rules.
- `PrivacyManager`: App exclusion management, smart accessibility scoping, dynamic zero-detection activation, and audit log masking.
- `AccessibilityHealthManager`: Service operational state tracking, dynamic on-demand lifecycle control, and system auto-rotate invariant enforcement.
- `AutomationSafetyManager`: Circuit breakers, failure counters, emergency stop flags, and human touch interruption detection.

### 2.4 Data Persistence Layer (`com.jmd.autotext.data`)
- `DatabaseHelper`: Thread-safe SQLite OpenHelper with WAL enabled, managing 15 relational tables across schema migrations.
- `Repositories`: Clean data access abstractions (`TaskRepository`, `PrivacyRepository`, `AppRepository`, `DailyCampaignRepository`, `AutoReplyRepository`, `LogRepository`, `SequenceRepository`, `TemplateRepository`, `WhatsAppRepository`).
- `PreferenceManager`: SharedPreferences backing for application configuration, stealth options, and auto-unlock credentials.

### 2.5 Services & Receivers (`com.jmd.autotext.service`, `com.jmd.autotext.receiver`)
- `AutoTextAccessibilityService`: Background accessibility service for physical UI interaction, window inspection, and keyguard unlock.
- `AutoTextNotificationListenerService`: Listens for incoming chat notifications to drive automated reply rules.
- `AutoTextA11yTileService`: Quick Settings tile for 1-tap pause/resume of accessibility for Google Pay compatibility.
- `TaskForegroundService`: Foreground service lifecycle manager.
- `TaskAlarmReceiver`: Exact AlarmManager intent target executing scheduled tasks, campaigns, and diagnostics.
- `BootReceiver`: Boot completed receiver restoring active scheduled alarms.
- `PhoneCallReceiver` & `SmsReceiver`: Telephony broadcast listeners for incoming triggers and auto-replies.

