# AutoText — Codeblock Audit & Feature Utilization Assessment

This document provides a component-by-component, codeblock-by-codeblock analysis of the AutoText codebase (`com.jmd.autotext`), cataloging existing implementations, underutilized framework options, missing pieces, and recommended logical enhancements in alignment with `instruction.md` and `agent.md`.

---

## 1. Data & Persistence Layer

### 1.1 `DatabaseHelper.kt`
- **Current Codeblocks:**
  - `onCreate(db)`: Creates 15 relational tables with WAL mode, indices on `scheduled_time`, `status`, `task_id`, `package_name`, and transactional migration paths up to DB Version 15.
  - `TABLE_APP_PRIVACY_CONFIGS`: Stores per-package privacy exclusions, banking shields, and custom interaction policies (`package_name`, `app_name`, `is_banking_app`, `is_shielded`, `stealth_mode_enabled`, `allow_automation`, `updated_at`).
- **Feature Utilization:**
  - WAL journal mode (`PRAGMA journal_mode=WAL`) maximizes concurrency between background alarm triggers and foreground Compose UI queries.
  - Foreign key constraints with cascade deletes clean up child steps and campaign contacts cleanly.
- **Audit Findings & Enhancements:**
  - The schema is complete and robust. All 15 tables are properly indexed.

### 1.2 `BackupRepository.kt`
- **Current Codeblocks:**
  - `exportDatabaseToJson(context)`: Queries tables, converts cursor rows to JSON arrays, and streams to a gzip/json backup file.
  - `importDatabaseFromJson(context, jsonString)`: Decodes JSON, opens a database transaction, clears existing table rows, and batch-inserts restored data.
- **Audit Finding (Missing Piece - High Priority):**
  - Lines 43–59 (`exportDatabaseToJson`) iterate over 14 tables: `TABLE_TASKS`, `TABLE_TEMPLATES`, `TABLE_RECIPIENTS`, `TABLE_LOGS`, `TABLE_CONTACTS`, `TABLE_DAILY_CAMPAIGNS`, `TABLE_AUTO_REPLY_RULES`, `TABLE_AUTO_REPLY_LOGS`, `TABLE_SEQUENCES`, `TABLE_SEQUENCE_STEPS`, `TABLE_CAMPAIGN_CONTACTS`, `TABLE_CAMPAIGN_MESSAGE_POOL`, `TABLE_CAMPAIGN_SCHEDULES`, `TABLE_PROTECTED_PACKAGES`.
  - **`TABLE_APP_PRIVACY_CONFIGS` was omitted** from both `exportDatabaseToJson` and `importDatabaseFromJson` (lines 99–115).
  - *Impact:* Backups silently drop custom user-configured banking app shields, package overrides, and privacy exemptions.
  - *Enhancement:* Add `TABLE_APP_PRIVACY_CONFIGS` to both export and import table loops with explicit column mapping.

### 1.3 `PrivacyModels.kt` & `GlobalPrivacySettings.kt`
- **Current Codeblocks:**
  - `AppPrivacyConfig`: Data model representing per-app shield settings.
  - `GlobalPrivacySettings`: Represents system-wide banking shield and stealth settings.
- **Audit Finding (Consistency Defect):**
  - In `GlobalPrivacySettings.kt`, `val dynamicA11yOnDemand: Boolean = false`.
  - However, in `PreferenceManager.kt`, `isDynamicA11yOnDemandEnabled` defaults to `true` (which is required by ADR-002 and `instruction.md` Part 8 to prevent banking apps from detecting active accessibility services).
  - *Enhancement:* Align the default in `GlobalPrivacySettings.kt` to `true` to ensure behavioral consistency across repositories and UI models.

---

## 2. Core & Permissions Layer

### 2.1 `PermissionManager.kt` & `PermissionScreen.kt`
- **Current Codeblocks:**
  - `PermissionManager.kt`: Declares `PermissionSpecification` objects mapped to features, runtime checks (`hasPermission`), and rationale dialog builders.
  - `PermissionScreen.kt`: Compose UI showing category cards (Core Runtime, Telephony, Accessibility, Notification, Battery/Overlay) with 1-tap grant buttons and status badges.
- **Audit Findings (Missing Pieces - High Priority):**
  - `Manifest.permission.READ_CALL_LOG` is declared in `AndroidManifest.xml` and actively utilized by `PhoneCallReceiver.kt` to determine call completion vs missed calls. However, it was missing from `coreRuntimePermissions` in `PermissionScreen.kt`. Users granting permissions in 1-tap onboarding were not prompted for Call Log, leaving `PhoneCallReceiver.kt` degraded unless manually enabled in system settings.
  - Media permissions: `ComposeTaskScreen.kt` supports attaching images and videos to WhatsApp and MMS tasks. Modern Android (API 33+) requires `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (or `READ_EXTERNAL_STORAGE` on API < 33). These permissions were absent from `PermissionManager.ALL_SPECIFICATIONS` and `PermissionScreen.kt`.
  - *Enhancement:* Add `READ_CALL_LOG` to `PermissionManager.ALL_SPECIFICATIONS` and `coreRuntimePermissions`. Add SDK-version-adaptive media permissions specification (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` for Android 13+, `READ_EXTERNAL_STORAGE` for legacy) to provide a seamless media attachment permission flow.

### 2.2 `AccessibilityHealthManager.kt`
- **Current Codeblocks:**
  - `isAccessibilityServiceHealthy(context)`: Inspects `AccessibilityManager.getEnabledAccessibilityServiceList` for `AutoTextAccessibilityService`.
  - `openAutostartSettings(context)`: Dispatches OEM-specific intents for Xiaomi (MIUI), Huawei (EMUI), Samsung, Oppo (ColorOS), Vivo (Funtouch/OriginOS), and Transsion.
- **Audit Finding & Underutilized Option:**
  - Vivo / iQOO devices (such as Vivo I2401 running OriginOS / FuntouchOS on Android 16) utilize two specific autostart intent targets that are more direct than the generic settings:
    - Component: `com.vivo.permissionmanager/.activity.PurviewTabActivity`
    - Component: `com.vivo.abe/.ui.ExcessivePowerManagerActivity`
  - *Enhancement:* Include these high-priority intents in the Vivo autostart intent cascade before generic fallbacks to ensure zero-effort OEM battery optimization whitelisting.

### 2.3 `AutoTextApplication.kt`
- **Current Codeblocks:**
  - `onCreate()`: Initializes timber/file logging, database helper, notification channels, watchdog scheduler, and background state reconciliation.
- **Audit Finding & Underutilized Option:**
  - Background initialization currently spawns a raw unmanaged Java thread: `Thread { ... }.start()`.
  - *Defects of raw thread:* Uncaught exceptions can cause silent thread termination; no lifecycle containment or structured cancellation; uncoordinated background dispatch.
  - *Enhancement:* Migrate background initialization to a structured `CoroutineScope(Dispatchers.IO + SupervisorJob())` with an explicit `CoroutineExceptionHandler`.

---

## 3. Automation Engine & Services Layer

### 3.1 `SequentialTaskEngine.kt`
- **Current Codeblocks:**
  - Mutex-guarded queue execution preventing overlapping accessibility gesture dispatches.
  - Dynamic keyguard unlock pre-condition (`KeyguardUnlockEngine`) and auto-relock postcondition.
  - Pre-action context validation with `ProtectedApplicationManager.verifyContextBeforeInteraction`.
  - Sliced polling wait loop for phone calls observing SIM state and emergency stop flags.
- **Audit Findings:**
  - Feature utilization is exemplary: handles WhatsApp personal/business dispatch, SMS PDU multi-part delivery, multi-SIM voice calls, contact sequence steps, and automated hangup.

### 3.2 `AutoTextAccessibilityService.kt` & `ProtectedApplicationManager.kt`
- **Current Codeblocks:**
  - Scoped accessibility events (`accessibility_service_config.xml` targeting WhatsApp, SystemUI, and Phone dialers).
  - Banking shield: `ProtectedApplicationManager.KNOWN_PROTECTED_PACKAGES` contains 60+ Indian and global banking/UPI apps (Google Pay, PhonePe, Paytm, YONO, HDFC, ICICI, etc.).
  - Default-deny UI action filter refusing gesture or click dispatch if foreground package is protected.
  - Human touch interrupt detector yielding control if user touches screen during automation.
- **Audit Findings:**
  - Fully implements ADR-001 through ADR-006.

---

## 4. UI & Jetpack Compose Layer

### 4.1 Design System Alignment
- Material 3 Expressive styling with dark-mode primary surface tokens (`0xFF111318`), surface containers (`0xFF1D2024`), primary teal accents (`0xFF60D4D6`), and error containers (`0xFF93000A`).
- 46.dp touch targets on text fields and icon buttons.
- Fully accessible content descriptions on all interactive icons.

---

## 5. Verification & Test Suite

### 5.1 `AutoTextComprehensiveTest.kt`
- Currently contains 76 comprehensive unit and integration tests covering:
  - Phone number normalization
  - Message template parameter substitution
  - Multi-SIM slot mapping
  - SMS quota counting and burst prevention
  - Task state machine transitions (`DRAFT` -> `VALIDATING` -> `READY` -> `SCHEDULED` -> `RUNNING` -> `SUCCESS`/`FAILED`/`CANCELLED`/`PAUSED`)
  - Banking shield and protected package refusal
  - Keyguard PIN tap coordinate calculations
  - Sliced polling call termination detection
- **Audit Finding (Test Expansion):**
  - Add test cases verifying:
    1. Backup and restore of `TABLE_APP_PRIVACY_CONFIGS`.
    2. Permissions resolution for `READ_CALL_LOG` and media permissions across SDK levels.

