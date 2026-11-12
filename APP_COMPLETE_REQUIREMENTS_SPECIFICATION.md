# Helper — Software Requirements & Architectural Specification (SRS)

> **Document Identifier:** `SRS-HELPER-V1.3.0`  
> **Package Identifier:** `com.jmd.helper`  
> **Platform Target:** Android 8.1 (API 27) through Android 16+ (API 37)  
> **Standard:** ISO/IEC/IEEE 29148:2018 Engineering Specification  
> **Compliance:** 100% On-Device Sovereignty • Zero Internet Permissions • Banking App Protection Shield

---

## 1. Executive Overview & System Purpose

### 1.1 Purpose
Helper (`com.jmd.helper`) is a commercial-grade, on-device autonomous hardware and task scheduling automation platform. The system operates entirely on-device without third-party cloud servers, external telemetry, or network permissions (`android.permission.INTERNET` is omitted). It is engineered for unattended multi-channel message dispatch, scheduled carrier and VoIP calling, contact sequence drip messaging, dynamic contact pool campaigns, and smart auto-reply automation.

### 1.2 Target Hardware & Platform Matrix
- **Minimum Supported OS:** Android 8.1 Oreo MR1 (API 27)
- **Target & Compile SDK:** Android 16 / 17 (API 36–37)
- **Primary Test Hardware:** Vivo / iQOO I2401 (`10BF1G0HCD0024R`, Android 16 API 36) & Samsung Galaxy A04 (`SM-A045F`, Android 14 API 34)
- **UI Framework:** 100% Jetpack Compose with Material 3 Expressive Design System
- **Language Stack:** 100% Idiomatic Kotlin (2.0.21+)

---

## 2. Universal Non-Negotiable System Constraints

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       UNIVERSAL SYSTEM INVARIANTS                           │
├───────────────────────────────┬─────────────────────────────────────────────┤
│ 1. Zero Network Access        │ android.permission.INTERNET is strictly     │
│                               │ omitted. Zero outbound data transmission.   │
├───────────────────────────────┼─────────────────────────────────────────────┤
│ 2. Bank-Safe Shield           │ Instant disableSelf() triggered upon any    │
│                               │ banking, UPI, or financial app entering FG. │
├───────────────────────────────┼─────────────────────────────────────────────┤
│ 3. Zero Sticky Notifications  │ Zero persistent notification banners when   │
│                               │ idle. Battery-friendly Doze-safe alarms.    │
├───────────────────────────────┼─────────────────────────────────────────────┤
│ 4. Biometric-Safe Lock        │ Dispatches GLOBAL_ACTION_LOCK_SCREEN on     │
│                               │ API 28+; never forces strong auth PIN lock. │
├───────────────────────────────┼─────────────────────────────────────────────┤
│ 5. Display Invariant          │ Screen hard-locked to portrait orientation; │
│                               │ ACCELEROMETER_ROTATION audited and kept 0.  │
├───────────────────────────────┼─────────────────────────────────────────────┤
│ 6. Zero @Suppress Invariant   │ Zero warning suppressions in handwritten    │
│                               │ Kotlin code. Idiomatic APIs throughout.     │
└───────────────────────────────┴─────────────────────────────────────────────┘
```

### 2.1 Constraint 1: Absolute Data Sovereignty (Offline-First)
The manifest must never request `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`. All databases, credentials, execution histories, and communication records must remain strictly inside device-encrypted internal storage (`/data/user/0/com.jmd.helper`).

### 2.2 Constraint 2: Banking & Financial App Protection Shield & NPCI UPI Isolation
The system must maintain an active list of 60+ Indian and global financial package identifiers (including Google Pay, PhonePe, Paytm, BHIM, YONO SBI, iMobile, HDFC, Axis, Cred, etc.). Whenever any monitored package gains window focus or Accessibility event generation:
1. `TaskAccessibilityService` must instantly call `disableSelf()`.
2. All tree inspection and node traversal must immediately cease.
3. The event must not be logged or stored.
4. **NPCI UPI Common Library Compliance Invariants**:
   - `accessibility_service_config.xml` must never list financial/banking packages in `android:packageNames`. Scoping must strictly remain constrained to WhatsApp, dialer, and SystemUI.
   - Flags must strictly be `flagDefault|flagReportViewIds`. `flagRetrieveInteractiveWindows` is prohibited.
   - `serviceInfo.packageNames` in `PrivacyManager.kt` must enforce a strict non-null invariant (`info.packageNames = allowedPackages.toTypedArray()`).
   - Dynamic On-Demand Standby: When granted `WRITE_SECURE_SETTINGS`, Helper remains **100% detached** from `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` during idle periods, ensuring Google Pay and UPI apps see 0 running accessibility services.
   - Quick Settings Tile (`TaskTileService`): Provides a 1-tap manual pause/resume option for environments without ADB setup.

### 2.3 Constraint 3: Biometric-Safe Screen Locking
Post-task execution screen locking must never execute `DevicePolicyManager.lockNow()` as a primary mechanism, because `lockNow()` sets `strongAuthRequired=0x2`, invalidating biometric trust (fingerprint and face unlock). The system must dispatch `GLOBAL_ACTION_LOCK_SCREEN` via AccessibilityService, which simulates the physical Power button, preserving biometrics.

### 2.4 Constraint 4: Display & Rotation Invariant
Tasks must never cause the screen to rotate to landscape orientation. Background execution must verify and enforce `Settings.System.ACCELEROMETER_ROTATION = 0` (Portrait locked).

### 2.5 Constraint 5: TRAI 100 SMS/Day Regulatory Guard
To prevent carrier SIM blacklisting under Telecom Regulatory Authority of India (TRAI) regulations, the system must enforce a daily dispatch counter (`SmsQuotaManager`). When 100 SMS dispatches have occurred in a calendar day, further SMS execution must be blocked with an actionable regulatory warning.

---

## 3. Functional Requirements (18 Autonomous Channels)

### 3.1 Messaging Channels

#### FR-01: Carrier SMS Dispatch (`SMS`)
- **Execution:** Dispatches direct cellular SMS via `SmsManager.getDefault()` or `SmsManager.getSmsManagerForSubscriptionId(simSlot)`.
- **Formatting:** Supports GSM-7 (160 chars/part) and Unicode/Emoji (70 chars/part) multipart splitting via `divideMessage()`.
- **Quota:** Enforces 100 SMS/day cap per SIM slot.

#### FR-02: WhatsApp Consumer Direct Chat (`WHATSAPP`)
- **Execution:** Launches WhatsApp (`com.whatsapp`) via direct URI `https://api.whatsapp.com/send?phone=...` or contact chat traversal.
- **Media:** Attaches images and videos via `Intent.ACTION_SEND` with media `content://` URI grants.
- **Delivery:** AccessibilityService verifies input field population and dispatches send action.

#### FR-03: WhatsApp Business Direct Chat (`WHATSAPP_BUSINESS`)
- **Execution:** Directs dispatch specifically to WhatsApp Business (`com.whatsapp.w4b`) package.
- **Parity:** Supports all text, dynamic variables, and media capabilities of FR-02.

#### FR-04: WhatsApp Consumer Group Messaging (`WHATSAPP_GROUP`)
- **Targeting:** Locates groups using virtual identifiers `group:GroupName`.
- **Search:** Navigates to WhatsApp, taps search bar, types group title, verifies exact title match, and delivers payload.
- **Validation:** Enforces non-blank content verification prior to scheduling.

#### FR-05: WhatsApp Business Group Messaging (`WHATSAPP_BUSINESS_GROUP`)
- **Targeting:** Targets client and team groups within WhatsApp Business (`com.whatsapp.w4b`).
- **Isolation:** Maintains separate dynamic contact pool categorization (`WA_BUSINESS_GROUP`).

#### FR-06: WhatsApp Consumer Status Story (`WHATSAPP_STATUS`)
- **Publishing:** Automatically posts text captions, photos, or videos to WhatsApp Status.
- **Targeting:** Selects "My Status" from the WhatsApp share target picker.

#### FR-07: WhatsApp Business Status Story (`WHATSAPP_BUSINESS_STATUS`)
- **Publishing:** Directs Status publishing specifically to WhatsApp Business account.

---

### 3.2 Voice Calling Channels

#### FR-08: Direct Carrier Phone Call (`CALL`)
- **Dispatch:** Initiates direct outgoing call via `TelecomManager` / `Intent.ACTION_CALL`.
- **Duration Tracking:** Monitors call state changes (`CALL_STATE_OFFHOOK`, `CALL_STATE_IDLE`). Automatically terminates call when configured duration expires.
- **Safety:** Filters out toll-free, emergency, and non-mobile numbers.

#### FR-09: WhatsApp Consumer Voice Call (`WHATSAPP_CALL`)
- **Dispatch:** Launches WhatsApp contact chat and triggers the voice call action button via accessibility tree traversal.
- **Active State:** Monitors VoIP call presence and manages auto-hangup.

#### FR-10: WhatsApp Business Voice Call (`WHATSAPP_BUSINESS_CALL`)
- **Dispatch:** Executes VoIP calling through WhatsApp Business client.

---

### 3.3 Simulation & System Channels

#### FR-11: Call Simulation Alert (`CALL_SIMULATION`)
- **Display:** Displays full-screen realistic incoming call screen (`CallSimulationActivity`) over locked screen via `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
- **Customization:** Configurable caller name, phone number, and UI theme style (Google Pixel, Samsung One UI, WhatsApp Call).
- **Ringtone:** Audio playback with custom vibration patterns.

#### FR-12: Local Reminder Alarm (`REMINDER`)
- **Display:** Doze-bypassing audio and visual reminder dialog with dynamic token replacement.

#### FR-13: Sound Mode Profile Automation (`SOUND_MODE`)
- **Control:** Programmatically adjusts system sound profile (Normal, Vibrate, Silent) via `AudioManager` respecting Do Not Disturb policies.

#### FR-14: Camera Snapshot (`CAMERA`)
- **Capture:** Background snapshot capture using CameraX via front or rear hardware sensor.

#### FR-15: Flashlight Strobe (`TORCH`)
- **Signaling:** Hardware camera LED torch pulsing at user-defined frequency or SOS emergency sequence.

#### FR-16: Audio Sound Recorder (`RECORDER`)
- **Capture:** Private audio recording saved to sandboxed internal storage directory.

#### FR-17: Screen Brightness Control (`BRIGHTNESS`)
- **Adjustment:** Programmatically modifies screen brightness percentage via `Settings.System.SCREEN_BRIGHTNESS`.

#### FR-18: On-Device Text-To-Speech (`TTS`)
- **Speech:** Speaks scheduled message aloud using Android `TextToSpeech` engine without network connection.

---

## 4. WhatsApp Chat & Group Harvesting Engine Specification

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   WHATSAPP HARVESTING STATE MACHINE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. ENSURE_CHATS_TAB     │ Locates and clicks bottom "Chats" navigation tab. │
│                         │ Realigns view if WhatsApp opened on Updates/Calls.│
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 2. ENSURE_GROUPS_FILTER │ Executes 3-pass backward scroll to top of list.   │
│                         │ Activates "Groups" filter chip or validates       │
│                         │ search bar indicates "Search group chats".        │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 3. HARVEST_ROWS         │ Binds strictly to android:id/list. Rejects footer │
│                         │ items ("Manage Groups"), newsletter & updates.    │
│                         │ Ingests clean titles into dynamic_contacts.       │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 4. SCROLL_DOWN          │ Scrolls chat list forward via node action or      │
│                         │ vertical swipe. Triggers cycle loop until 4       │
│                         │ consecutive stagnant cycles are detected.         │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 5. COMPLETE             │ Scopes pool wipe by package (WA_GROUP vs          │
│                         │ WA_BUSINESS_GROUP) and saves verified batch.      │
└─────────────────────────┴───────────────────────────────────────────────────┘
```

### 4.1 Strict List Hierarchy Invariant
`WhatsAppChatScanner` must exclusively inspect `RecyclerView` containers matching `id == "android:id/list" || id.endsWith("/list")`. It must explicitly reject sibling lists containing `updates_list`, `status`, `channels`, or `community` to prevent WhatsApp Status updates from contaminating contact and group records.

### 4.2 Top-Scroll Guarantee
Before harvesting begins in `ENSURE_GROUPS_FILTER`, the engine must execute 3 backward scroll gestures (`topScrollAttempts < 3`). This guarantees the scanner starts at the first item of the list regardless of prior user scrolling.

### 4.3 Group Criteria Heuristics
A conversation row is classified as a group chat if any of the following evaluate to true:
1. The WhatsApp "Groups" top filter chip is active (`isGroupsFilterActive == true`).
2. The search bar displays `Search group chats`.
3. The row hierarchy contains an author label (`msg_from_tv` or `sender_name`).
4. The subtitle text contains group event markers ("created group", "added you", "left group", "colon prefix").

---

## 5. Security & Keyguard Auto-Unlock Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        HARDWARE UNLOCK LIFECYCLE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. Alarm Triggered      │ AlarmManager wakes device via PARTIAL_WAKE_LOCK   │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 2. Lock Evaluation      │ KeyguardManager evaluates if screen is locked     │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 3. Turn Screen On       │ TaskLauncherActivity sets setTurnScreenOn(true)   │
│                         │ and setShowWhenLocked(true)                       │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 4. Keyguard Assist      │ Bouncer revealed via fast-flick upward swipe.     │
│                         │ PIN digits entered via accessibility touch.       │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 5. Task Dispatch        │ Payload delivered through targeted channel        │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 6. Home Minimization    │ System navigates to Home launcher via             │
│                         │ GLOBAL_ACTION_HOME                                │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 7. Fingerprint-Safe Lock│ Dispatches GLOBAL_ACTION_LOCK_SCREEN              │
│                         │ (Preserves biometric fingerprint trust)           │
├─────────────────────────┼───────────────────────────────────────────────────┤
│ 8. Dynamic Standby      │ After 5s idle, service detaches for banking safety│
└─────────────────────────┴───────────────────────────────────────────────────┘
```

---

## 6. Local SQLite Database Schema

The SQLite database (`helper.db`) maintains relational persistence across 16 normalized tables:

```sql
-- 1. Scheduled Tasks Table
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    channel TEXT NOT NULL,
    recipients TEXT NOT NULL,          -- JSON Array of phone numbers / group IDs
    recipient_names TEXT NOT NULL,     -- JSON Array of display names
    message_text TEXT NOT NULL,
    trigger_time INTEGER NOT NULL,
    recurrence TEXT NOT NULL DEFAULT 'ONCE',
    custom_interval_minutes INTEGER DEFAULT 0,
    days_of_week TEXT DEFAULT '',
    status TEXT NOT NULL DEFAULT 'SCHEDULED',
    sim_slot INTEGER DEFAULT -1,
    ask_before_send INTEGER DEFAULT 0,
    sound_mode TEXT DEFAULT '',
    simulated_caller_name TEXT DEFAULT '',
    simulated_caller_number TEXT DEFAULT '',
    simulated_call_style TEXT DEFAULT 'Modern',
    created_at INTEGER NOT NULL,
    last_executed INTEGER DEFAULT 0,
    execution_count INTEGER DEFAULT 0,
    media_uri TEXT DEFAULT '',
    call_duration_seconds INTEGER DEFAULT 0,
    include_dial_time INTEGER DEFAULT 0,
    call_sequence_mode TEXT DEFAULT 'PARALLEL',
    delay_between_calls_seconds INTEGER DEFAULT 0,
    call_failure_policy TEXT DEFAULT 'CONTINUE'
);

-- 2. Dynamic Contact & Group Pool Table
CREATE TABLE dynamic_contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,               -- Cellular phone or virtual group tag ("group:Family")
    normalized_number TEXT NOT NULL,   -- E.164 normalized or "group:family"
    source TEXT NOT NULL,              -- CONTACT, SMS, CALL, WA_CHAT, WA_GROUP, WA_BUSINESS_GROUP
    photo_uri TEXT DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 3. Execution Audit Logs Table
CREATE TABLE execution_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL,
    channel TEXT NOT NULL,
    recipient TEXT NOT NULL,
    message_snippet TEXT NOT NULL,
    status TEXT NOT NULL,              -- SUCCESS, FAILED, SKIPPED, CANCELLED
    details TEXT DEFAULT '',
    segments INTEGER DEFAULT 1,
    timestamp_millis INTEGER NOT NULL
);

-- 4. Reusable Message Templates Table
CREATE TABLE templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'General',
    created_at INTEGER NOT NULL
);

-- 5. Smart Auto-Reply Rules Table
CREATE TABLE auto_reply_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trigger_type TEXT NOT NULL,        -- SMS, MISSED_CALL, WHATSAPP
    matching_rule TEXT NOT NULL,       -- EXACT, CONTAINS, REGEX, ALL
    keywords TEXT NOT NULL,
    reply_message TEXT NOT NULL,
    channel TEXT NOT NULL,
    sim_slot INTEGER DEFAULT -1,
    cooldown_seconds INTEGER DEFAULT 60,
    is_active INTEGER NOT NULL DEFAULT 1
);
```

---

## 7. Quality Gates & Acceptance Verification

Every release candidate must satisfy all of the following quality gates:

1. **Compilation:** Compiles cleanly with zero Kotlin compilation errors across debug and release build variants (`compileDebugKotlin`, `compileReleaseKotlin`).
2. **Lint & Security:** Release lint passes with zero fatal errors (`lintVitalRelease`).
3. **Unit Tests:** 100% of unit tests pass (`.\gradlew :app:testDebugUnitTest`, 141/141 tests green).
4. **Hardware Verification:** Verified on physical test device `10BF1G0HCD0024R` (Vivo / iQOO 13, Android 16 API 36) including live Google Pay payment success and WhatsApp group harvesting.
5. **Privacy:** Zero network permissions in `AndroidManifest.xml` verified via `aapt dump permissions`.
6. **Code Standards:** Zero `@Suppress` annotations in handwritten Kotlin code.
7. **Production Artifact:** Signed `app-release.apk` compiled with full R8 shrinking and minification.
8. **In-APK Codebase Distribution:** Release APK contains complete pristine source code in `assets/codebase/` and `assets/project_source.zip`.
9. **Architectural Documentation:** All 144 files and 1,328 code blocks feature structured "What it is", "What it does", and "Linked with" specifications.
