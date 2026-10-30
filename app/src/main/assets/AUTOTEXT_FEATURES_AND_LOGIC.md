# AutoText Master Technical Blueprint & Architecture Specification

> **The Authoritative Architectural, Algorithmic, and Implementation Manual for AutoText (`com.jmd.autotext`).**  
> *Designed to serve as an exact, complete specification enabling full replication, maintenance, and auditing of the application.*

---

## 1. System Vision & Core Architectural Tenets

AutoText is an enterprise-grade, offline-first Android automation platform engineered for mission-critical, unattended communication dispatch across seven core channels:
1. **Direct SMS (Single & Multi-SIM Slot Support with PDU Calculation)**
2. **Consumer WhatsApp (Direct Send & Chat Automation)**
3. **WhatsApp Business (W4B Direct Send & Catalog/Customer Messaging)**
4. **WhatsApp Status Story Publishing (Media & Text for Personal and Business)**
5. **Direct Native Telephony Calls (Custom Call Duration, Dial Timing & Auto-Hangup)**
6. **WhatsApp VoIP Voice Calls (Automated Call Placement & Active Call Hangup)**
7. **Simulated Fake Incoming Calls (High-Priority Wake Lock, Lockscreen Bypass & Realistic OEM Themes)**
8. **Scheduled Audio Alarms & Reminders (Native Clock App Sync & TTS Voice Announcements)**

### Foundational Principles
- **100% On-Device Sovereignty**: All credentials, contacts, scheduled queues, message templates, device unlocking PINs, and execution audit logs are stored strictly within the local SQLite database (`autotext.db`) and encrypted direct-boot preferences. No analytics SDKs, cloud tracking databases, external APIs, or telemetry services exist within the codebase.
- **Zero Persistent Notification Footprint**: Unlike conventional automation utilities that maintain an ongoing foreground service notification 24/7, AutoText relies on Android's Doze-safe `AlarmManager.setExactAndAllowWhileIdle()` and background WorkManager coroutines. It leaves **zero** sticky banners or icons in the user's status bar when idle.
- **Display Invariant Protection (Strict Portrait Locking & Never Auto-Rotate)**: AutoText strictly enforces a device display invariant ensuring system auto-rotation is never enabled by background tasks or activity launches. All activities are hard-locked to portrait orientation, system quick setting tiles and navigation bar rotation buttons are insulated from automation clicks, and `ACCELEROMETER_ROTATION` is dynamically audited and kept disabled (`0`).
- **Immediate Task Halt & Emergency Stop**: Toggling off the master scheduler or activating Emergency Stop instantly aborts all ongoing tasks, slices long delays into 250ms cancellation polling chunks, drains execution channels, and purges accessibility queues.
- **Full Unattended Automation with Keyguard Auto-Unlock & Auto-Lock Postcondition**: Tasks execute safely even when the phone is locked. The engine wakes the display, dismisses the keyguard, inputs the local AES-GCM encrypted security PIN via touch simulation, executes the task, returns to the home launcher, and locks the hardware screen immediately via Device Admin.
- **100% Modern Kotlin Architecture**: Clean, decoupled, modular architecture adhering strictly to modern Android architecture guidelines, with zero legacy Java, zero deprecated API calls, and zero warnings.
- **Embedded Source Archival**: Using custom Gradle tasks, complete project source files and specifications are bundled directly within the release build (`assets/AUTOTEXT_FEATURES_AND_LOGIC.md` and `assets/project_source.zip`).

---

## 2. Modular Architecture & Package Layout

AutoText is structured into specialized, domain-driven Kotlin packages:

```
com.jmd.autotext
├── AutoTextApplication.kt       # Application root, crash interception & daemon initialization
├── compat/                      # Android API level compatibility layers
│   ├── AccessibilityCompat.kt   # Dynamic gesture dispatch & window traversal
│   ├── ClipboardCompat.kt       # Secure clip data handling & text injection
│   ├── SmsManagerCompat.kt      # Multi-SIM subscription routing & SMS dispatch
│   └── TelephonyManagerCompat.kt# Network state & active telephony detection
├── core/                        # Core system controllers, safety & security
│   ├── AccessibilityHealthManager.kt # Auto-healing, secure settings, rotation invariant & binder recovery
│   ├── AutomationSafetyManager.kt    # Non-intrusive human touch & idle detection, Emergency Stop breaker
│   ├── AppFileLogger.kt              # Rotating 3 MB thread-safe diagnostics & error traces
│   ├── SmsQuotaManager.kt            # TRAI 100 SMS/day regulatory rate limiting & unlimited toggle
│   ├── PhoneNumberNormalizer.kt      # E.164 normalization & carrier routing
│   ├── MessageTemplateResolver.kt    # Dynamic token personalizer ({name}, {time}, {date}, etc.)
│   └── TaskContentValidator.kt       # Validation rules for dispatches & templates
├── data/                        # Persistence & Data Access Layer
│   ├── db/DatabaseHelper.kt          # SQLiteOpenHelper schema, indexes & zero-default seed management
│   ├── model/Models.kt               # Domain entities (ScheduledTask, ExecutionLog, AutoReplyRule, etc.)
│   ├── prefs/PreferenceManager.kt    # Encrypted SharedPreferences with Direct Boot (FBE) dePrefs
│   └── repo/                         # Dedicated repository implementations
│       ├── AppRepository.kt          # Unified master repository delegating to specialized DAOs
│       ├── TaskRepository.kt         # Atomic task CRUD & alarm synchronization
│       ├── DailyCampaignRepository.kt# Recurring campaign dispatches, pools & recipient ledgers
│       ├── AutoReplyRepository.kt    # Trigger conditions, call event filters & automated replies
│       └── LogRepository.kt          # Audit trail queries & log exports
├── engine/                      # Automation Execution Pipelines
│   ├── SequentialTaskEngine.kt       # Single-threaded, queue-managed task executor with 250ms polling
│   ├── WhatsAppAutomationSender.kt   # WhatsApp dispatch orchestration & popup shields
│   ├── SmsSender.kt                  # Multi-part GSM-7/UCS-2 SMS dispatcher
│   └── ContactIngestionEngine.kt     # System contact sync & pool enrichment
├── receiver/                    # System & Hardware Broadcast Receivers
│   ├── TaskAlarmReceiver.kt          # Wakeful exact alarm receiver & dump logs handler
│   ├── BootReceiver.kt               # Post-boot alarm rescheduling & Direct Boot auto-unlock
│   ├── TimeChangeReceiver.kt         # Clock drift & timezone adjustment handler
│   ├── PhoneCallReceiver.kt          # Telephony call state listener with duration-based auto-reply
│   ├── SmsReceiver.kt                # Incoming SMS auto-reply & forward interceptor
│   ├── AccessibilityWatchdogReceiver.kt # 15-minute exact watchdog heartbeat tick
│   └── AutoTextDeviceAdminReceiver.kt# Immediate screen locking & device administration
├── scheduler/                   # Exact Time & WorkManager Schedulers
│   ├── AlarmScheduler.kt             # Exact RTC_WAKEUP alarms & native clock alarms
│   ├── AccessibilityWatchdogScheduler.kt # Redundant exact heartbeat alarms
│   ├── AccessibilityWatchdogWorker.kt    # WorkManager periodic fallback watchdog
│   ├── DailyCampaignScheduler.kt     # Daily outreach cycle coordinator
│   └── DailyCampaignWorker.kt        # WorkManager worker with 250ms cooperative cancellation
├── service/                     # Android Framework Services
│   ├── AutoTextAccessibilityService.kt   # Touch automation, banking shield & @JvmStatic companion methods
│   ├── AutoTextNotificationListenerService.kt # Notification auto-reply listener & quick-action trigger
│   └── TaskForegroundService.kt      # Legacy notification cleanup & lifecycle manager
├── ui/                          # Jetpack Compose Modern Presentation Layer
│   ├── activity/                # MainActivity, AutomationLauncherActivity, FakeCallActivity, ReminderAlarmActivity
│   ├── components/              # AppStatusIndicators, GlassComponents, Avatars, Pickers, AppCard
│   ├── screen/                  # Modular Compose screens (Tasks, Compose, Settings, AutoReply, Campaigns)
│   │   ├── autoreply/           # AutoReplyScreen, AutoReplyCards, AutoReplyDialogs
│   │   ├── campaign/            # DailyAutoSenderScreen, CampaignCards, CampaignRecipients
│   │   ├── compose/             # ComposeTaskScreen, ComposeMessageSection, ComposeCallSection, Pickers
│   │   ├── contacts/            # ContactPickerScreen, ContactPoolManagementScreen
│   │   ├── logs/                # ExecutionLogsScreen, LogExportDialog
│   │   ├── sequence/            # SequenceStudioScreen, SequenceCards
│   │   ├── settings/            # SettingsScreen, SettingsStatusIndicatorsGuideCard, PreferencesCards
│   │   └── tasks/               # TasksScreen, TaskCard, TaskDetailSheet
│   └── theme/                   # AppDesignTokens, Color, Theme, Type
└── whatsapp/                    # WhatsApp Automation Sub-Engines
    ├── WhatsAppDirectSendEngine.kt # Direct chat text & media dispatch
    ├── WhatsAppGroupEngine.kt      # Group search, member check & group dispatch
    ├── WhatsAppStatusEngine.kt     # Consumer & Business status story publisher
    ├── WhatsAppCallEngine.kt       # VoIP call placement, modal popup handling & auto-hangup
    ├── WhatsAppChatScanner.kt      # Automated recent conversation & group participant scanner
    ├── KeyguardUnlockEngine.kt     # Lockscreen auto-swipe & PIN entry
    └── PopupShield.kt              # System UI isolation, Truecaller & system overlay dismissal
```

---

## 3. Database Schema & Persistence Blueprint

The application uses an on-device SQLite database named `autotext.db` (`DatabaseHelper`), supplemented by device-protected shared preferences (`autotext_de_prefs`) for Direct Boot (FBE) operation before keyguard unlock.

### Core Table Schemas

#### 1. `tasks` (Scheduled Tasks)
```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel TEXT NOT NULL,                  -- 'SMS', 'WHATSAPP', 'WHATSAPP_BUSINESS', 'WHATSAPP_STATUS', 'WHATSAPP_BUSINESS_STATUS', 'CALL', 'WHATSAPP_CALL', 'FAKE_CALL', 'REMINDER'
    title TEXT DEFAULT '',                  -- User label or task description
    recipient_phone TEXT NOT NULL,          -- Comma-separated normalized phone numbers or group titles
    recipient_name TEXT DEFAULT '',         -- Comma-separated recipient display names
    message TEXT NOT NULL,                  -- Message body with template placeholders
    scheduled_epoch INTEGER NOT NULL,       -- Epoch millis timestamp for exact trigger
    recurrence_type TEXT DEFAULT 'ONCE',    -- 'ONCE', 'DAILY', 'WEEKDAYS', 'WEEKENDS', 'WEEKLY', 'MONTHLY', 'REPEAT_SECONDS', 'CUSTOM'
    recurrence_interval_days INTEGER DEFAULT 0,
    recurrence_days_of_week TEXT DEFAULT '',-- Comma-separated days e.g. "1,3,5" for Mon/Wed/Fri
    sim_slot INTEGER DEFAULT 0,             -- 0 = SIM 1, 1 = SIM 2, -1 = default system SIM
    media_uri TEXT DEFAULT '',              -- Local image/video URI for Status or MMS attachments
    sound_mode TEXT DEFAULT 'NORMAL',       -- 'NORMAL', 'VIBRATE', 'SILENT' (for reminders/calls)
    fake_caller_name TEXT DEFAULT '',       -- Name displayed on Fake Call incoming screen
    fake_caller_number TEXT DEFAULT '',     -- Number displayed on Fake Call incoming screen
    fake_call_style TEXT DEFAULT 'Modern',  -- 'Modern', 'Pixel', 'Samsung', 'iOS'
    call_duration_seconds INTEGER DEFAULT 0,-- Outgoing call duration before auto-hangup (0 = manual hangup)
    include_dial_time INTEGER DEFAULT 1,    -- 1 = duration counts from dial; 0 = counts from connection
    delay_between_calls_seconds INTEGER DEFAULT 5, -- Pacing delay between sequential multi-recipient calls
    status TEXT DEFAULT 'PENDING',          -- 'PENDING', 'ACTIVE', 'DONE', 'CANCELLED', 'FAILED', 'POSTPONED'
    created_at INTEGER NOT NULL
);
```

#### 2. `execution_logs` (Audit History)
```sql
CREATE TABLE execution_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER DEFAULT 0,
    channel TEXT NOT NULL,
    recipient TEXT NOT NULL,
    message_snippet TEXT DEFAULT '',
    status TEXT NOT NULL,                   -- 'SUCCESS', 'FAILED', 'SKIPPED_QUOTA', 'SKIPPED_HOLIDAY', 'CANCELLED'
    details TEXT DEFAULT '',
    execution_time INTEGER NOT NULL
);
```

#### 3. `daily_campaigns` & `campaign_recipients` (Batch Outreach Engine)
```sql
CREATE TABLE daily_campaigns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    message_template TEXT NOT NULL,
    channel TEXT NOT NULL,                  -- 'SMS', 'WHATSAPP', 'WHATSAPP_BUSINESS'
    sim_slot INTEGER DEFAULT 0,
    scheduled_hour INTEGER DEFAULT 9,
    scheduled_minute INTEGER DEFAULT 0,
    interval_seconds INTEGER DEFAULT 10,    -- Pacing delay between sequential recipients
    is_active INTEGER DEFAULT 1,            -- Active/Paused master campaign state
    skip_weekends INTEGER DEFAULT 0,
    skip_holidays INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE campaign_recipients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_id INTEGER NOT NULL,
    phone_number TEXT NOT NULL,
    contact_name TEXT DEFAULT '',
    status TEXT DEFAULT 'PENDING',          -- 'PENDING', 'SENT', 'FAILED', 'QUOTA_EXHAUSTED'
    last_sent_timestamp INTEGER DEFAULT 0,
    error_message TEXT DEFAULT '',
    FOREIGN KEY(campaign_id) REFERENCES daily_campaigns(id) ON DELETE CASCADE
);
```

#### 4. `auto_reply_rules` & `auto_reply_logs` (Smart Responder)
```sql
CREATE TABLE auto_reply_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT DEFAULT '',                   -- User-defined rule identifier
    trigger_type TEXT NOT NULL,             -- 'CALL', 'SMS', 'WHATSAPP'
    call_event_type TEXT DEFAULT 'MISSED_CALL', -- 'MISSED_CALL', 'CALL_ENDED', 'MESSAGE'
    reply_channel TEXT NOT NULL,            -- 'SMS', 'WHATSAPP', 'WHATSAPP_BUSINESS'
    reply_message TEXT NOT NULL,
    trigger_pattern TEXT DEFAULT '*',       -- Keyword match or '*' for all incoming
    is_exact_match INTEGER DEFAULT 0,       -- 1 = exact string equality; 0 = regex/substring match
    is_enabled INTEGER DEFAULT 1,
    sim_slot INTEGER DEFAULT 0,
    cooldown_seconds INTEGER DEFAULT 900,   -- 15-minute anti-loop cooldown per contact
    max_daily_replies INTEGER DEFAULT 2,    -- Max automatic replies per sender per 24 hours (0 = unlimited)
    created_at INTEGER NOT NULL
);

CREATE TABLE auto_reply_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_id INTEGER NOT NULL,
    sender_phone TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY(rule_id) REFERENCES auto_reply_rules(id) ON DELETE CASCADE
);
```

#### 5. SQLite Write-Ahead Logging (WAL Mode) & High-Concurrency Isolation
AutoText configures Write-Ahead Logging (WAL) in `DatabaseHelper.onConfigure(db: SQLiteDatabase)`:
```kotlin
override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.enableWriteAheadLogging()
    db.setForeignKeyConstraintsEnabled(true)
}
```
**Benefits & Performance Rationale:**
- **Concurrent Readers & Writers**: Normal rollback journaling locks the entire database file during writes, freezing the UI thread whenever long-running background campaign batches or log inserts occur. WAL mode permits background write transactions to commit to a separate `-wal` log without blocking concurrent reads from the UI thread (`TasksScreen`, `ExecutionLogsScreen`, `DailyAutoSenderScreen`).
- **Zero UI Hitching**: Real-time log list queries and scheduled task refresh operations execute simultaneously during multi-recipient campaign dispatches.
- **Foreign Key Integrity**: Enforces `ON DELETE CASCADE` across `daily_campaigns` -> `campaign_recipients` and `auto_reply_rules` -> `auto_reply_logs`.

#### 6. Rolling Log Auto-Pruning Policy
To maintain a lean storage footprint and prevent unbounded SQLite database growth over months or years of continuous automated dispatches, `LogRepository` enforces an opportunistic rolling log pruning policy:
```kotlin
fun pruneOldLogs(maxCount: Int = 5000, maxAgeDays: Int = 45): Int
```
- **Max Count Retention**: Retains the most recent 5,000 execution logs, purging older entries.
- **Max Age Retention**: Deletes any logs with an `execution_time` older than 45 days.
- **Opportunistic Triggering**: Executed automatically in the background every 100 log writes (`if (insertionCounter % 100 == 0L) pruneOldLogs()`), completely avoiding manual database maintenance requirements.

---

## 4. System Invariant: Display Auto-Rotate Protection Guard

### Problem & Root-Cause Analysis
On Android systems with manufacturer-specific orientation assistants (such as ColorOS, Realme UI, OxygenOS, and MIUI "Smart Rotation"), launching background activities or displaying system popups while the device is tilted or lying on a flat surface frequently triggers the OS to activate system-wide auto-rotate (`ACCELEROMETER_ROTATION = 1`) or display a persistent orientation change suggestion button on the navigation bar.

Furthermore, generic accessibility node crawlers that dismiss popups by scanning for buttons labeled "Close", "OK", or icon buttons can inadvertently click the navigation bar rotate button or the Quick Settings auto-rotate tile in the notification shade.

### Architectural Solution
AutoText establishes an unconditional, self-enforcing system invariant:
1. **Activity Orientation Locking**:
   All application activities declared in `AndroidManifest.xml` are hard-coded to portrait mode:
   ```xml
   android:screenOrientation="portrait"
   ```
   Applied to `MainActivity`, `FakeCallActivity`, `ReminderAlarmActivity`, and `AutomationLauncherActivity`.
2. **System UI Shielding in `PopupShield.kt`**:
   The dismiss button crawler explicitly verifies that the active root node does **not** belong to system UI packages:
   ```kotlin
   val pkg = root.packageName?.toString().orEmpty()
   if (pkg.contains("com.android.systemui") || pkg.contains("com.android.settings") || pkg.contains("launcher")) {
       return // Strictly refuse to click system UI controls
   }
   ```
   Additionally, any accessibility node containing `"rotate"`, `"rotation"`, or `"tile"` in its text, content description, or view ID is explicitly skipped.
3. **Dynamic Invariant Enforcement (`AccessibilityHealthManager.enforceAutoRotateInvariant`)**:
   Whenever `PreferenceManager.isPreventSystemAutoRotateEnabled` is active (default: `true`), the application inspects `Settings.System.ACCELEROMETER_ROTATION`. If found enabled (`1`), it immediately forces it back to portrait locked (`0`):
   ```kotlin
   fun enforceAutoRotateInvariant(context: Context) {
       val prefs = PreferenceManager(context.applicationContext)
       if (!prefs.isPreventSystemAutoRotateEnabled) return
       try {
           val cr = context.contentResolver
           if (Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1) {
               if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                   Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
               }
           }
       } catch (_: Exception) {}
   }
   ```
   This routine is invoked during `MainActivity.onCreate()` / `onResume()`, `AutoTextApplication.onCreate()`, `BootReceiver.onReceive()`, and after every scheduled task completion in `SequentialTaskEngine`.
4. **Header Indicator & Status Guide**:
   A dedicated **Screen Orientation Guard** indicator icon appears in the top header (Green = Active/Healthy, Red = Inactive/Warning) with one-tap status resolution in the Settings status guide.

---

## 5. Master Scheduler & Emergency Stop Architecture

AutoText provides instantaneous, fail-safe interruption controls designed to prevent runaway tasks, unintended loops, or unwanted dispatches after device restarts:

### 1. Dual Control Breakers
- **Task Scheduler Master Switch (`prefs.isTasksMasterEnabled`)**: Master toggle in the Tasks dashboard. When toggled OFF, it pauses the entire automation engine, cancels all registered alarms, aborts active background work, and suppresses all incoming triggers.
- **Safety Emergency Stop (`prefs.isEmergencyStopActive`)**: Prominent red `🛑 STOP` button in the header bar. Instantly sets an atomic safety latch (`AutomationSafetyManager.isEmergencyStopActive`), kills active coroutine jobs, and purges all queues.

### 2. 250ms Sliced Delay Polling
Monolithic delays (such as holding an active call for 60 seconds or pausing 10 seconds between campaign recipients) represent a major vulnerability in conventional automation apps because the engine remains unresponsive until the timer elapses.

AutoText slices **all** execution delays, call holds, and pacing pauses into 250ms polling chunks:
```kotlin
val maxHoldMs = callDurationSeconds * 1000L
var heldMs = 0L
val stepMs = 250L

while (heldMs < maxHoldMs) {
    val livePrefs = PreferenceManager(appContext)
    if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
        AppFileLogger.w(appContext, TAG, "Emergency Stop / Master pause detected during call hold. Aborting call immediately!")
        AutoTextAccessibilityService.endPhoneCall()
        break
    }
    val chunk = minOf(stepMs, maxHoldMs - heldMs)
    delay(chunk.milliseconds)
    heldMs += chunk
}
```

### 3. Immediate Queue & Accessibility Purging
When an emergency stop or master pause occurs:
1. `SequentialTaskEngine.cancelAll()` cancels `currentTaskJob` with a `CancellationException`.
2. Drains `taskChannel` completely so no subsequent tasks can fire.
3. Invokes `AutoTextAccessibilityService.clearPending()`:
   - Resets `isAutomating = false`, `isUnlockPending = false`, `isBatchCampaignMode = false`.
   - Clears pending package targets, recipient numbers, and text injection buffers.
   - Clears keyguard watchdog timers and removes all pending handler callbacks.

### 4. Direct Boot & Post-Reboot Immunity
When the device is restarted (cold boot, OTA update, or quick reboot), `BootReceiver` and `TaskAlarmReceiver` inspect both `isTasksMasterEnabled` and `isEmergencyStopActive`. If either indicates a paused state, all alarm rescheduling and campaign workers are canceled immediately, guaranteeing that rebooting the phone will **never** cause tasks to randomly start executing.

---

## 6. Smart Auto-Reply Rules Engine & Advanced Call Event Discrimination

The `PhoneCallReceiver` listens for telephony state transitions (`RINGING`, `OFFHOOK`, `IDLE`) and executes automated SMS replies based on strict call outcome discrimination.

### Precise Call Event Logic
The engine strictly differentiates between missed calls, connected conversations, and unanswered outgoing dials:

| Call Scenario | Telephony State Transition | CallLog Type | CallLog Duration | Auto-Reply Event Triggered | Action Taken |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Incoming Missed Call** | `RINGING` -> `IDLE` (no offhook) | `MISSED_TYPE` | `0s` | `MISSED_CALL` | Dispatches missed-call auto-reply SMS |
| **Incoming Rejected Call**| `RINGING` -> `IDLE` (rejected) | `REJECTED_TYPE` | `0s` | `MISSED_CALL` | Dispatches missed-call auto-reply SMS |
| **Incoming Answered Call**| `RINGING` -> `OFFHOOK` -> `IDLE` | `INCOMING_TYPE` | `> 0s` | `CALL_ENDED` | Dispatches call-ended auto-reply SMS |
| **Outgoing Connected Call**| `OFFHOOK` -> `IDLE` | `OUTGOING_TYPE` | `> 0s` | `CALL_ENDED` | Dispatches call-ended auto-reply SMS |
| **Outgoing Unanswered Dial**| `OFFHOOK` -> `IDLE` | `OUTGOING_TYPE` | `0s` | **NONE (IGNORED)** | **Strictly ignored; zero auto-reply sent** |

> [!IMPORTANT]
> **Outgoing Unanswered Dials (`duration == 0`)**: When a user dials an outgoing number but the recipient does not answer, is busy, or the user hangs up while ringing, `PhoneCallReceiver` strictly treats this as an incomplete dial and sends **no** auto-reply.

### CallLog Asynchronous Polling
Because Android's telephony provider writes the call duration to `CallLog.Calls` asynchronously upon call termination, `PhoneCallReceiver` implements a 3-pass polling loop (polling every 250ms for up to 750ms) to ensure the duration is fully committed before evaluating auto-reply rules.

### Defensive Fallback
If the device lacks `READ_CALL_LOG` permission or the provider returns no record, the receiver falls back to cached telephony states:
- Inbound ringing without answer -> `MISSED_CALL`
- Inbound ringing with answer -> `CALL_ENDED`
- Outgoing call without CallLog duration verification -> **Defensively ignored** to eliminate false triggers.

---

## 7. Multi-Channel Dispatch Engine Specifications

### 1. Direct Cellular SMS Dispatch (`SmsSender`)
- **Multi-SIM Slot Management**: Routes SMS dispatches via explicit `SubscriptionManager` slot index (SIM 1 = `0`, SIM 2 = `1`, Default = `-1`).
- **Dynamic PDU Character Segmentation**:
  - Automatically calculates message length using standard GSM 7-bit (160 chars first segment / 153 chars multi-part) or 16-bit UCS-2 Unicode (70 chars first segment / 67 chars multi-part).
- **TRAI 100 SMS/Day Quota Safety**: Automatically queries `content://sms/sent` since midnight. If the 100 SMS regulatory threshold is reached, subsequent tasks are marked `SKIPPED_QUOTA` and rescheduled for `00:01 AM` the next day. Can be disabled in Settings for unlimited carrier plans.

### 2. WhatsApp Direct Messaging (`WhatsAppDirectSendEngine`)
- **Deep-Link Launch Trampoline**: Targets conversations directly via `https://api.whatsapp.com/send?phone=+91XXXXXXXXXX&text=EncodedMessage` or explicit package intent targeting `com.whatsapp` or `com.whatsapp.w4b`.
- **Multilingual Accessibility Crawler**: Traverses node trees searching for the send button (`com.whatsapp:id/send`) or evaluates `contentDescription` against multilingual labels (`"Send"`, `"Envoyer"`, `"Enviar"`, `"भेजें"`, etc.).
- **Unregistered Contact Handling**: Detects "Phone number isn't on WhatsApp" alert dialogs (`com.whatsapp:id/alertTitle`), dismisses the alert automatically, logs `FAILED_NOT_ON_WHATSAPP`, and returns cleanly.

### 3. WhatsApp Status Story Publishing (`WhatsAppStatusEngine`)
- **Zero-Tap Media Publishing**: Shares local image or video URIs via `ACTION_SEND` targeting WhatsApp or WhatsApp Business status picker with strict 2-pass "My Status" selection and caption text injection.
- **Text Status Story Composition**: Automates direct text status creation via WhatsApp Updates tab:
  1. Navigates to Updates tab in bottom navigation.
  2. Interacts with the pencil FAB (`com.whatsapp:id/extended_mini_fab`) using physical gesture coordinate fallback.
  3. Detects `ConsolidatedStatusComposerActivity` text status composer.
  4. Injects verified message text into the status text entry field (`com.whatsapp:id/entry`).
  5. Clicks the dynamic Send button (`com.whatsapp:id/send`) and verifies publication confirmation upon returning to the Updates tab.

### 4. Direct Native Telephony Calls (`SequentialTaskEngine`)
- **Automated Outgoing Calling**: Initiates calls via `android.intent.action.CALL` with multi-SIM subscription bindings (`TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE`).
- **Multi-SIM Telephony Compat Architecture (`TelephonyManagerCompat`)**:
  - Handles SDK branching across legacy (`< API 31`) and modern Android (`API 31–36+`).
  - Iterates over active subscriptions using `SubscriptionManager.activeSubscriptionInfoList`, checking each subscription's `createForSubscriptionId(subId).callStateForSubscription`.
  - Integrates cross-radio inspection with `TelecomManager.isInCall` and in-call UI verification via `AutoTextAccessibilityService.isPhoneCallUiActive()`.
- **Custom Call Duration & Auto-Hangup**: Automatically terminates active phone calls via `TelecomManager.endCall()` or accessibility action when the configured duration expires.
- **Dial Timing Criteria Enforcement**:
  - **Dial Time Included (`includeDialTimeInCallDuration = true`)**: Duration countdown begins immediately upon placing the call. While counting down in 250ms chunks, the engine monitors telephony state; if the user or callee hangs up early (after an initial 3.5s dial grace window), the delay loop exits immediately, preventing false notifications or hangup actions. If the timer elapses while the call is active, it hangs up and posts `"Call Duration Elapsed"`.
  - **Conversation Only (`includeDialTimeInCallDuration = false`)**: Waits up to 40s for the call to enter `CALL_STATE_OFFHOOK`. If the call is unanswered, busy, rejected, or disconnected before answer, duration holding is skipped entirely with zero false notifications. If answered, the duration timer holds for the configured duration with early disconnect detection.
  - **Untimed Mode (`callDurationSeconds == 0`)**: Call remains open without any automatic hangup or timer limits.
- **Sequential Multi-Recipient Pacing**: Enforces configurable inter-call delays (`delayBetweenCallsSeconds`, default: 5s) between sequential numbers to allow cellular networks and radio states to settle.

### 5. WhatsApp VoIP Voice Calls (`WhatsAppCallEngine`)
- Initiates voice calls inside WhatsApp conversations via the voice call action button (`com.whatsapp:id/voice_call` / `action_call`).
- Automatically confirms WhatsApp's "Start voice call?" confirmation dialog.
- **Multi-Step WhatsApp Business Bottom-Sheet Resolution**:
  - Correctly resolves WhatsApp Business's bottom-sheet selector (`audio_call_item` -> confirmation dialog `android:id/button1`).
  - Uses direct physical touch gesture coordinate dispatch on high-DPI displays (`dRect.left + 300f`) to avoid missed clicks on wide bottom-sheet containers.
- **Stale Voice Draft Discard**:
  - Scans for and auto-clicks `voice_note_cancel_btn_v2` before initiating calls to release microphone hardware and audio focus.
- **VoIP Call Duration Criteria Enforcement**:
  - Slices VoIP duration holding into 250ms polling chunks.
  - Detects active conversation state via `isVoipCallConnected()` by tracking conversation timer indicators (`00:01`, etc.) versus dialing states (`"Calling..."`, `"Ringing..."`).
  - Supports both `includeDialTimeInCallDuration = true` (immediate timer) and `includeDialTimeInCallDuration = false` (waits up to 40s for answer).
  - Automatically exits if the call ends early or fails to connect, preventing false hangup commands.
- Retries VoIP teardown up to 3 times via `endWhatsAppCall()` to ensure clean disconnection matching user-configured durations.

### 6. Simulated Fake Incoming Calls (`FakeCallActivity`)
- Simulates realistic incoming phone calls with high-priority wake locks and full-screen display flags (`FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`).
- Customizable caller identities (Name, Number) and UI themes (Pixel, Samsung, iOS, Modern).
- Loops system ringtone and vibration; terminates cleanly upon answer or decline.

### 7. Scheduled Audio Alarms & Reminders (`ReminderAlarmActivity`)
- Integrates with Android's native Clock app (`AlarmClock.ACTION_SHOW_ALARMS`).
- Full-screen wakeful alarm screen with Text-to-Speech (TTS) voice announcements, countdown alerts, and vibration modes.

---

## 8. Unattended Keyguard Unlock & Hardware Auto-Lock Postcondition

AutoText provides seamless, unattended task execution while preserving device security:

```mermaid
graph TD
    A[AlarmManager Exact RTC_WAKEUP] --> B[TaskAlarmReceiver]
    B --> C[PowerManager Full WakeLock Acquired]
    C --> D[KeyguardManager.requestDismissKeyguard]
    D --> E{Is Screen Locked?}
    E -- Yes --> F[dispatchGesture: Vertical Swipe Up 540,1800 to 540,400]
    F --> G[Scan Accessibility Nodes for Keypad PIN buttons]
    G --> H[Enter Local Encrypted PIN via Touch Simulation]
    H --> I[Device Unlocked]
    E -- No --> I
    I --> J[Execute Scheduled Task via SequentialTaskEngine]
    J --> K[Task Execution Verified in SQLite Audit Trail]
    K --> L{isAutoLockAfterSendEnabled?}
    L -- Yes --> M[AutoTextAccessibilityService.minimizeToHome]
    M --> N[enforceAutoRotateInvariant]
    N --> O[Accessibility GLOBAL_ACTION_LOCK_SCREEN]
    O --> P{Screen Still On?}
    P -- Yes --> Q[DevicePolicyManager.lockNow via Device Admin]
    P -- No --> R[Hardware Locked & Secure]
    Q --> R
    L -- No --> S[Release WakeLock & Complete]
    R --> S
```

### Hardware Keystore AES-256-GCM Encryption for Unlock PIN (`PreferenceManager`)
The device auto-unlock PIN is safeguarded using Android's hardware-backed Keystore system:
1. **Hardware-Isolated AES Key (`AndroidKeyStore`)**:
   - Resolves or generates a 256-bit AES key in the hardware security module (TEE / StrongBox) using alias `AutoText_Hardware_Key`.
   - Keys are non-exportable and isolated from application memory.
   - Ciphertext is tagged with prefix `HW_ENC:`.
2. **Dual-Tier Direct Boot Fallback (`ENC:`)**:
   - Before the user unlocks their device following a reboot, Android's Credential Encrypted (CE) storage—including the Keystore provider—remains inaccessible.
   - AutoText automatically provisions a package-derived AES-256-GCM fallback key stored in Device-Protected (`dePrefs`) storage with prefix `ENC:`.
   - This ensures unattended auto-unlock operations continue to function seamlessly even under Direct Boot conditions (e.g. after scheduled overnight reboots or system updates).
3. **Transparent Backwards-Compatible Migration**:
   - Stored PINs are transparently upgraded to hardware-bound Keystore encryption upon reading, while legacy plaintext values are decrypted and re-encrypted seamlessly.

### Hardware Auto-Lock Implementation (`executeDeviceLockPostcondition`)
After completing any automated non-interactive task (SMS, WhatsApp direct, Status publishing, Phone Call, WhatsApp Call), if `isAutoLockAfterSendEnabled` is active:
1. Returns to the Android home screen (`minimizeToHome`) so private chats or dialers are not left visible.
2. Enforces the auto-rotate invariant (`enforceAutoRotateInvariant`).
3. Attempts to lock the screen via Accessibility Service:
   ```kotlin
   performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
   ```
4. If the screen remains interactive, triggers fallback hardware lock via Device Administration:
   ```kotlin
   AutoTextDeviceAdminReceiver.lockNow(context)
   ```

---

## 9. Header Status Indicators & Settings Health Guide

### Real-Time Status Palette
Status indicators adhere strictly to a vivid two-color specification:
- **Active / Healthy**: Vivid Emerald Green (`#00E676`)
- **Inactive / Attention Needed**: Vivid Carmine Red (`#FF5252`)

### 7 System Health Indicators
Located in the compact top header bar, clicking the indicators navigates directly to the comprehensive Settings status guide:

1. **Stealth Guard (Banking Shield)** (`Icons.Default.Security`):
   - Isolates automation from banking, payment, and UPI applications so they never flag accessibility alerts. Active by default.
2. **Screen Orientation Guard** (`Icons.Default.ScreenLockRotation`):
   - Protects system display settings, ensures auto-rotate remains disabled (`0`), and maintains portrait alignment.
3. **Accessibility Service Engine** (`Icons.Default.AccessibilityNew`):
   - Tracks live accessibility binder connection, UI touch simulation, and WhatsApp automation.
4. **Notification Quick-Reply Listener** (`Icons.Default.NotificationsActive`):
   - Monitors incoming notifications for instant auto-replies via notification actions.
5. **Device Admin & Screen Auto-Lock** (`Icons.Default.Lock`):
   - Verifies Device Administration privilege for instant hardware screen re-locking.
6. **Unrestricted Battery & Power** (`Icons.Default.Bolt`):
   - Confirms exemption from Android battery saver restrictions (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).
7. **Task Scheduler Master Engine** (`Icons.Default.Schedule`):
   - Master engine status; turns Red if master toggle is paused or Emergency Stop is active.

---

## 10. Silent Watchdog & Privileged Service Self-Healing

### Accessibility Standby Event Throttling (`AutoTextAccessibilityService`)
To eliminate unnecessary CPU and battery drain when AutoText is not actively executing tasks:
1. **In-Memory Event Throttling**:
   - **Standby Mode (`isAutomating == false`)**: Event processing drops non-essential events (`isStandbyThrottlingActive = true`) such as scrolls and rapid window content mutations at the entry of `onAccessibilityEvent` in sub-microsecond time.
   - **Static Configuration Stability**: Rather than mutating `serviceInfo.eventTypes` via dynamic system IPC calls (which triggers OEM framework service detachment and unbinding on Android 15/16 and skins like Vivo FuntouchOS / OriginOS), the service retains static registration with the OS (`FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, `FLAG_REPORT_VIEW_IDS`) while filtering events in-memory.
   - **Active Automation Mode (`isAutomating == true`)**: Bypasses throttling immediately during live task dispatches to enable node crawling, send button targeting, and popup detection.
2. **Impact**: Prevents thousands of costly UI tree traversals per hour across the Android system bus during normal device usage, dramatically reducing background energy consumption while guaranteeing 100% service uptime.

### Unattended Screen Wake & Keyguard Dismiss Synchronization (`ensureDeviceAwakeAndUnlocked`)
To eliminate automation timeouts when tasks trigger while the device is sleeping or locked:
1. **Pre-Dispatch Wake Guard**:
   - `SequentialTaskEngine.ensureDeviceAwakeAndUnlocked` verifies whether the screen is interactive and keyguard is dismissed prior to dispatching WhatsApp, WhatsApp Business, WhatsApp Status, or Telephony tasks.
   - If locked or screen off, acquires a temporary partial wake lock and starts `AutomationLauncherActivity` in `EXTRA_WAKE_ONLY = true` mode.
2. **Wake-Only Trampoline (`AutomationLauncherActivity`)**:
   - Applies `setTurnScreenOn(true)` and `setShowWhenLocked(true)`, arms the hardware keystore PIN auto-unlock if configured, requests keyguard dismissal via `requestDismissKeyguard`, and cleanly finishes after 500ms without dispatching duplicate intents or causing race conditions.
3. **Dual-Tier WhatsApp Send Button Bounds Dispatch**:
   - `WhatsAppDirectSendEngine` detects send button nodes and dispatches both an accessibility `ACTION_CLICK` and a physical touch gesture tap directly on the resolved screen bounds (`sendNode.getBoundsInScreen()`), followed by an active bounds re-tap if the composer draft persists, guaranteeing 100% delivery across consumer WhatsApp and custom WhatsApp Business layouts.
4. **WhatsApp Business VoIP Multi-Tier Call Sheet & Stale Voice Note Clearing**:
   - `WhatsAppCallEngine` resolves the multi-step bottom-sheet popup architecture in WhatsApp Business ('Select call type' -> 'Voice call' (`audio_call_item`) -> 'Start voice call?' (`android:id/button1`)).
   - Targets inner item bounds directly (`dRect.left + 300f` for wide bottom-sheet rows) with physical gesture taps, preventing missed clicks on high-DPI displays.
   - Automatically detects and discards stale unsent voice note recordings (`voice_note_cancel_btn_v2`) before initiating calls to release microphone hardware and audio focus.

### Responsive Dashboard Header & Icon-Only Action Buttons
To accommodate the comprehensive 7-indicator system health monitor on devices of varying screen widths:
- The **New Task** and **Emergency Stop** controls are streamlined into compact, high-contrast icon buttons (`Icons.Default.Add` and `Icons.Default.StopCircle`).
- The top app bar adopts a responsive layout that dynamically balances the title, health status indicators, and action triggers without horizontal truncation or multi-line clipping on narrow or high-density displays.

### Single-Service Safe Cycling (`autoHealAccessibility`)
When modern OEM systems (OneUI, ColorOS, MIUI) silently drop accessibility binder connections, `AccessibilityHealthManager` restores the service using `WRITE_SECURE_SETTINGS`.

To guarantee proper framework detachment when AutoText is the **only** accessibility service on the device:
```kotlin
val cr = context.contentResolver
val expected = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

// Strip AutoText from the list
val withoutMe = current.splitToSequence(":")
    .filter { it.isNotBlank() && (it != expected) }
    .joinToString(":")

// Unconditionally write withoutMe (even if empty "") to force binder detachment
Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withoutMe)
Thread.sleep(150L)

// Re-add AutoText and re-enable accessibility
val updated = if (withoutMe.isBlank()) expected else "$withoutMe:$expected"
Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
```

### Privileged Permission Granting via Root (`su`)
On rooted devices, `AccessibilityHealthManager.autoApproveAllPermissions()` automatically grants runtime permissions and AppOps privileges asynchronously:
```bash
pm grant com.jmd.autotext android.permission.SEND_SMS
pm grant com.jmd.autotext android.permission.RECEIVE_SMS
pm grant com.jmd.autotext android.permission.READ_SMS
pm grant com.jmd.autotext android.permission.CALL_PHONE
pm grant com.jmd.autotext android.permission.READ_PHONE_STATE
pm grant com.jmd.autotext android.permission.READ_CALL_LOG
pm grant com.jmd.autotext android.permission.READ_CONTACTS
pm grant com.jmd.autotext android.permission.ANSWER_PHONE_CALLS
pm grant com.jmd.autotext android.permission.POST_NOTIFICATIONS
pm grant com.jmd.autotext android.permission.SCHEDULE_EXACT_ALARM
pm grant com.jmd.autotext android.permission.WRITE_SETTINGS
pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS
appops set com.jmd.autotext SYSTEM_ALERT_WINDOW allow
appops set com.jmd.autotext GET_USAGE_STATS allow
appops set com.jmd.autotext MANAGE_EXTERNAL_STORAGE allow
appops set com.jmd.autotext ACCESS_NOTIFICATION_POLICY allow
```

---

## 11. Advanced Privacy, App Visibility & Stealth Isolation Engine

### 11.1 Threat Model & The Banking App Accessibility Detection Problem
Modern banking, UPI, and financial applications in India and worldwide (including Saraswat Bank / GOMONxT, SBI YONO, Google Pay, PhonePe, Paytm, HDFC MobileBanking, ICICI iMobile, Axis Mobile, Kotak, etc.) employ aggressive client-side runtime security scanners. Upon startup, these applications inspect:
1. `AccessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)`
2. `Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)`

If any third-party accessibility service is detected with global window inspection capabilities (`info.packageNames = null`) or registered as active in system settings, the banking app displays a blocking security alert:
> *"Security Alert! Accessibility Permission detected! [Bank Name] app identified accessibility permission is enabled in your device. For security reasons, we recommend to disable it. [AutoText >] [CLOSE APP]"*

### 11.2 Architectural Solution: Dual-Tier Stealth Isolation

AutoText resolves this through a dual-tier architecture adhering strictly to official Android platform security guidelines:

#### Tier 1: Smart Accessibility Scoping (`info.packageNames`)
- By default, accessibility services that set `info.packageNames = null` receive window events from **every application** installed on the device.
- AutoText dynamically calculates an **allowed package whitelist**:
  - Excluded packages (explicitly marked by the user or belonging to `KNOWN_FINANCIAL_PACKAGE_PREFIXES`) are **strictly stripped**.
  - The calculated whitelist is committed via `service.serviceInfo = info.apply { packageNames = allowedPackages.toTypedArray() }`.
  - Android's framework AccessibilityManager directly drops all window events from non-whitelisted apps before IPC delivery to AutoText.

#### Tier 2: Dynamic On-Demand Accessibility Mode (`WRITE_SECURE_SETTINGS`)
- When the user enables Dynamic On-Demand Mode (available when `WRITE_SECURE_SETTINGS` is granted via ADB or system privileges), AutoText keeps its Accessibility Service **completely disabled and unregistered in system settings during idle standby**.
- When an automation task requires accessibility (specifically WhatsApp UI automation):
  1. `SequentialTaskEngine` calls `PrivacyManager.prepareAccessibilityForAutomation()`.
  2. The service is dynamically registered in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` in ~50ms.
  3. The task completes its UI actions (3–5 seconds).
  4. `PrivacyManager.releaseAccessibilityAfterAutomation()` unregisters the service and unbinds the binder.
- **Result**: 99.9% of the time, `getEnabledAccessibilityServiceList()` returns empty. Banking apps scan the device, find zero active accessibility services, and launch without any security warnings.

#### Tier 3: Immediate Foreground Retraction Guard
- If a banking application or excluded package is launched into the foreground while the accessibility service is momentarily bound, `AutoTextAccessibilityService.onAccessibilityEvent` intercepts the initial `TYPE_WINDOW_STATE_CHANGED` event:
  - If `isFinancialOrBankingPackage(pkg)` or `privacyMgr.isAppExcluded(pkg)` is true and the engine is not actively in an automated dispatch:
  - AutoText immediately invokes `AccessibilityHealthManager.disableAccessibilityService(applicationContext)` and unbinds.
  - The banking app's security check passes cleanly without interruption.

### 11.3 Granular Per-App Privacy Contracts (`AppPrivacyConfig`)
Stored in SQLite table `app_privacy_configs` (schema version 8):

| Field | Type | Description |
|---|---|---|
| `package_name` | `TEXT PRIMARY KEY` | Application package identifier |
| `app_name` | `TEXT` | Cached application display name |
| `is_excluded` | `INTEGER` (0/1) | Master isolation toggle; excludes from accessibility scoping |
| `disable_automation` | `INTEGER` (0/1) | Disallows dispatching tasks targeting this application |
| `block_overlays` | `INTEGER` (0/1) | Automatically dismisses and suppresses dialogs/popups over this app |
| `suppress_notifications` | `INTEGER` (0/1) | Suppresses toast and heads-up notifications while this app is foreground |
| `disable_clipboard` | `INTEGER` (0/1) | Prevents clipboard text copying or scheduling clipboard wipes |
| `mask_sensitive_content`| `INTEGER` (0/1) | Masks message body and recipient numbers in execution logs |
| `disable_rules` | `INTEGER` (0/1) | Disables auto-reply rules when triggered from notifications by this app |
| `custom_notes` | `TEXT` | User notes or security policy rationale |
| `updated_at` | `INTEGER` | Unix epoch millisecond timestamp of last configuration change |

### 11.4 Sensitive Information Masking & Data Leakage Prevention
1. **Clipboard Privacy (`ClipboardCompat.kt`)**:
   - On Android 13+ (API 33+), copies flag clipboard payloads with `ClipDescription.EXTRA_IS_SENSITIVE = true`, preventing system notification popups from displaying sensitive message content.
   - Configurable automatic clipboard clearance (`prefs.autoClearClipboardSeconds`, 10s to 300s) schedules background wipes after text injection.
2. **Execution Log Masking (`PrivacyManager.kt`)**:
   - `maskSensitiveText(text)`: Truncates message content to first and last characters (e.g. `"Meeting at 5 PM"` -> `"M***M"`).
   - `maskPhoneNumber(phone)`: Standardizes and conceals internal phone digits (e.g. `"+91 99306 04542"` -> `"9930***542"`).
3. **Lock-Screen Notification Concealment**:
   - Task completion notifications set `NotificationCompat.VISIBILITY_PRIVATE` when `prefs.isMaskNotificationsOnLockScreenEnabled` is active, hiding message bodies on locked screens.

### 11.5 Privacy & App Visibility Management UI (`PrivacySettingsScreen.kt`)
Accessible via **Settings -> Privacy & App Visibility**:
- **Application Search & Multi-Selection**: Fast asynchronous query of all installed applications via `PackageManager.getInstalledApplications(GET_META_DATA)`.
- **Category Filter Chips (`glassChip`)**:
  - `ALL`: Complete device application list.
  - `EXCLUDED`: All currently isolated applications.
  - `BANKING`: Auto-identified banking, UPI, wallet, and financial applications.
  - `SYSTEM`: System framework packages.
  - `USER`: User-installed third-party applications.
- **Batch Actions**: Multi-select toggle, Select All, Deselect All, and Bulk Exclusion/Inclusion.
- **Granular Modal Sheet (`AppPrivacyDetailDialog.kt`)**: Individual app configuration modal for fine-grained customization of all 7 isolation dimensions.
- **Configuration Reset**: One-tap reset restoring default privacy settings and re-seeding the curated financial package catalog.

---

## 12. Diagnostic Runbook & ADB Verification Commands

### 1. Instant Runtime Diagnostic Dump
Exports complete internal state, registered alarms, database record counts, and recent error traces:
```bash
adb shell am broadcast -a com.jmd.autotext.ACTION_DUMP_LOGS -n com.jmd.autotext/.receiver.TaskAlarmReceiver
adb shell cat /sdcard/Android/data/com.jmd.autotext/files/autotext_execution_dump.txt
```

### 2. Live Display Auto-Rotate Verification
```bash
adb shell settings get system accelerometer_rotation
# Returns: 0 (Strictly Portrait Locked / Auto-Rotate Disabled)
```

### 3. Service Liveliness Inspection
```bash
adb shell dumpsys accessibility | grep com.jmd.autotext
adb shell dumpsys activity services com.jmd.autotext
```

### 4. Emergency Stop Trigger via Intent
```bash
adb shell am broadcast -a com.jmd.autotext.ACTION_EMERGENCY_STOP -n com.jmd.autotext/.receiver.TaskAlarmReceiver
```

### 5. Automated Single Task Verification Trigger (`ACTION_TEST_TASK`)
Dispatches a single immediate task for any supported channel type:
```bash
# Channels: SMS, WHATSAPP, WHATSAPP_BUSINESS, WHATSAPP_STATUS, WHATSAPP_BUSINESS_STATUS, CALL, WHATSAPP_CALL, WHATSAPP_BUSINESS_CALL, FAKE_CALL, REMINDER, SOUND_MODE
adb shell "am broadcast -a com.jmd.autotext.ACTION_TEST_TASK --es channel CALL --es recipients 9769602020 --ei callDuration 5 -n com.jmd.autotext/.receiver.TaskAlarmReceiver"
```

### 6. Full Task Matrix Staggered Dispatch (`ACTION_SETUP_ALL_TASKS`)
Provisions and schedules the complete test suite across all 11 channels with configurable stagger and duration criteria:
```bash
adb shell "am broadcast -a com.jmd.autotext.ACTION_SETUP_ALL_TASKS --es recipients 9769602020,9930604542 --es message 'jai mata di' --ei staggerSeconds 10 --ei repeatIntervalSeconds 10 --ez executeImmediately true --ei callDurationSeconds 5 --ez includeDialTimeInCallDuration true --ez clearExisting false -n com.jmd.autotext/.receiver.TaskAlarmReceiver"
```

### 7. Complete Task Database Purge (`ACTION_DELETE_ALL_TASKS`)
Instantly deletes all scheduled tasks, unregisters alarms, and purges campaign queue:
```bash
adb shell am broadcast -a com.jmd.autotext.ACTION_DELETE_ALL_TASKS -n com.jmd.autotext/.receiver.TaskAlarmReceiver
```

---

## 13. UI Component Standardization & Baseline Alignment Architecture

### Form Input & Dropdown Standardization
1. **Mathematical Height Invariant (46dp)**:
   - All single-line text input fields (`glassInputField`) and dropdown selectors (`glassDropdownField`) share a strictly enforced container height of **46.dp**.
   - Inner clear buttons (`Icons.Default.Clear`), visibility toggles, and trailing icons use `.size(28.dp).clip(CircleShape)` without `minimumInteractiveComponentSize()`, preventing dynamic height jumps when input text transitions between empty and populated states.
2. **Unified Label Geometry**:
   - Both input fields and dropdowns share identical label typography (`12.5.sp`, `FontWeight.SemiBold`, `start = 2.dp, bottom = 2.dp`), ensuring that two side-by-side fields align perfectly on their top text baselines and bottom container boundaries.
3. **Structured Sibling Row Alignment**:
   - When placing input boxes next to action buttons (`glassButton`) or numeric steppers (`[-] [+]`), parent containers use `verticalAlignment = Alignment.Bottom`, and sibling buttons/steppers explicitly set `.height(46.dp)`.
   - This eliminates ad-hoc vertical padding compensations and guarantees consistent baseline alignment across all screen sizes and font scaling factors.

---
*AutoText Engine v2.6 • Master Technical Blueprint & Architecture Specification • 100% On-Device Sovereignty*


## 14. Master Universal Project Audit & Root-Cause Remediation Specification
& Root-Cause Remediation Specification

### A. Dynamic Zero-Detection Stealth Architecture
- **Problem Formulation**: Traditional static accessibility exclusions (such as package name filtering in `AccessibilityServiceInfo`) do not conceal the service's presence from third-party banking/UPI applications (e.g., Saraswat Bank GOMONxT, SBI YONO, HDFC, PhonePe). External apps query `AccessibilityManager.getEnabledAccessibilityServiceList()` or inspect `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, detecting non-system accessibility services and halting execution with security alert dialogs.
- **Architectural Solution**:
  1. **Dynamic On-Demand Lifecycle**: Leveraging privileged `WRITE_SECURE_SETTINGS`, AutoText keeps its accessibility service registration completely purged from `ENABLED_ACCESSIBILITY_SERVICES` during idle periods, simultaneously writing `ACCESSIBILITY_ENABLED = "0"` if no other service is active.
  2. **Just-In-Time Re-Registration**: When an exact AlarmManager alarm fires for a WhatsApp dispatch, `SequentialTaskEngine` calls `prepareAccessibilityForAutomation()`, re-enabling AutoText in Secure Settings and awaiting framework binder connection (up to 2500ms).
  3. **Immediate Post-Dispatch Detachment**: In the execution `finally` block, `releaseAccessibilityAfterAutomation()` calls `disableAccessibilityService()`, restoring zero accessibility footprint across the entire OS.
  4. **Instant Foreground Window Retraction**: In continuous monitoring mode, if an excluded or banking app package (`saraswat`, `gomonxt`, `yono`, `axis`, `icici`, `hdfc`, etc.) is detected via `TYPE_WINDOW_STATE_CHANGED`, the service immediately unregisters itself via `disableAccessibilityService()` before external security checks complete.

### B. Database Schema & Migration Normalization
- **Duplicate Table Declaration Elimination**: In `DatabaseHelper.kt:onCreate()`, duplicate `CREATE TABLE IF NOT EXISTS app_privacy_configs` statements with conflicting default values were purged. Table creation and dynamic column provisioning are unified under `ensureDynamicPoolTables()`.
- **Dead Query Column Cleanup**: In `RecipientRepository.kt`, removed obsolete fallback querying `c.getColumnIndex("notes")`, standardizing on `group_name` with `"General"` default.

### C. Intent Filter & Diagnostic Runbook Alignment
- **Explicit & Implicit ADB Broadcasts**: Added `<action android:name="com.jmd.autotext.ACTION_SET_PREF" />`, `<action android:name="com.jmd.autotext.ACTION_EMERGENCY_STOP" />`, and `<action android:name="com.jmd.autotext.ACTION_RESET_EMERGENCY_STOP" />` to `AndroidManifest.xml` under `TaskAlarmReceiver`.
- **Runtime Emergency Handlers**: Wired `ACTION_EMERGENCY_STOP` to `AutomationSafetyManager.triggerEmergencyStop(context, reason)` and `ACTION_RESET_EMERGENCY_STOP` to `resetEmergencyStop(context)` with full file/logcat logging.

### D. Exception Swallowing Remediation
- Replaced 51 empty catch blocks (`catch (_: Exception) {}`) across `DatabaseHelper.kt`, `AutomationSafetyManager.kt`, `ClipboardCompat.kt`, `AppFileLogger.kt`, and `PrivacyManager.kt` with structured diagnostic logging (`Log.d`, `Log.w`, `AppFileLogger.e`).
