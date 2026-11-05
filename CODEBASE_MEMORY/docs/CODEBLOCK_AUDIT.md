# AutoText — Codeblock Audit & Feature Utilization Assessment

This document provides a component-by-component, codeblock-by-codeblock analysis of the AutoText codebase (`com.jmd.autotext`), cataloging existing implementations, underutilized framework options, missing pieces, and completed logical enhancements in alignment with `instruction.md` and `agent.md`.

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
- **Audit Finding & Resolution:**
  - `TABLE_APP_PRIVACY_CONFIGS` is fully integrated into both `exportDatabaseToJson` and `importDatabaseFromJson`.
  - Backups preserve all user-configured banking app shields, package overrides, and privacy exemptions across all 15 database tables.

### 1.3 `PrivacyModels.kt` & `GlobalPrivacySettings.kt`
- **Current Codeblocks:**
  - `AppPrivacyConfig`: Data model representing per-app shield settings.
  - `GlobalPrivacySettings`: Represents system-wide banking shield and stealth settings.
- **Audit Finding & Resolution:**
  - Aligned the default `dynamicA11yOnDemand` in `GlobalPrivacySettings.kt` to `true` to ensure behavioral consistency with `PreferenceManager.kt` and ADR-002.

---

## 2. Core, OEM & Permissions Layer

### 2.1 `PermissionManager.kt` & `PermissionScreen.kt`
- **Current Codeblocks:**
  - `PermissionManager.kt`: Declares `PermissionSpecification` objects mapped to features, runtime checks (`hasPermission`), and rationale dialog builders.
  - `PermissionScreen.kt`: Compose UI showing category cards (Core Runtime, Telephony, Accessibility, Notification, Battery/Overlay) with 1-tap grant buttons and status badges.
- **Audit Finding & Resolution:**
  - Added `READ_CALL_LOG` to `PermissionManager.ALL_SPECIFICATIONS` and `coreRuntimePermissions`.
  - Added SDK-version-adaptive media permissions specification (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` for Android 13+, `READ_EXTERNAL_STORAGE` for legacy).
  - Added `ANSWER_PHONE_CALLS` runtime permission on Android 9+ for direct `TelecomManager.endCall()` call termination.
  - Cleaned up naming convention warnings in `PermissionScreen.kt`.

### 2.2 `OemBrandHelper.kt` (New Component)
- **Current Codeblocks:**
  - Dynamically inspects `Build.MANUFACTURER` and `Build.BRAND` across Samsung, Vivo, iQOO, Oppo, Realme, Xiaomi, OnePlus, Motorola, Nokia, and Transsion.
  - `applyDualSimExtras`: Injects cross-vendor dual-SIM intent extras (`simSlot`, `subscription_id`, `com.android.phone.extra.slot`, `sub_id`, `phone_id`, etc.) across Qualcomm, MediaTek, Exynos, and Unisoc chipsets.
  - In-call disconnect profiles: Provides multi-tier hangup strategies including `TelecomManager.showInCallScreen(false)` foregrounding and 20+ multilingual OEM disconnect button resource IDs.

### 2.3 `AccessibilityHealthManager.kt`
- **Current Codeblocks:**
  - `isAccessibilityServiceHealthy(context)`: Inspects `AccessibilityManager.getEnabledAccessibilityServiceList` for `AutoTextAccessibilityService`.
  - `openAutostartSettings(context)`: Dispatches OEM-specific intents for Xiaomi (MIUI/HyperOS), Huawei (EMUI), Samsung, Oppo (ColorOS), Vivo (Funtouch/OriginOS), and Transsion.
  - Vivo / iQOO specific autostart intents (`PurviewTabActivity`, `ExcessivePowerManagerActivity`) prioritized.

---

## 3. Task Dispatch Engine, Services & Programmatic API Layer

### 3.1 `TaskFeatureApi.kt` (New Component)
- **Current Codeblocks:**
  - Programmatic, typed, suspendable endpoints for all features: `sendWhatsAppMessage`, `postWhatsAppStatus`, `placeWhatsAppCall`, `sendSms`, `placePhoneCall`, `hangupActiveCall`, `triggerReminder`, `triggerCallSimulation`, `applySoundMode`.
  - Standardizes parameter validation, dual-SIM routing, and returns unified `TaskExecutionResult` objects.

### 3.2 `SequentialTaskEngine.kt`
- **Current Codeblocks:**
  - Mutex-guarded queue execution preventing overlapping accessibility gesture dispatches.
  - Dynamic keyguard unlock pre-condition (`KeyguardUnlockEngine`) and biometric-safe auto-lock postcondition.
  - Pre-action context validation with `ProtectedApplicationManager.verifyContextBeforeInteraction`.
  - Exact call duration holding with 250ms sliced polling and early hangup detection (`waitForCallDurationWithEarlyCheck`).
  - Stranded `RUNNING` task recovery upon pipeline completion and cancellation.

### 3.3 `AutoTextAccessibilityService.kt` & `AutoTextDeviceAdminReceiver.kt`
- **Current Codeblocks:**
  - Screen auto-lock strictly uses `GLOBAL_ACTION_LOCK_SCREEN` (mimicking a physical hardware Power button press).
  - All calls to `DevicePolicyManager.lockNow()` eliminated; `AutoTextDeviceAdminReceiver.lockNow()` delegates to `GLOBAL_ACTION_LOCK_SCREEN`, preserving user fingerprint and face biometric unlock.
  - Multi-tier phone call termination: `TelecomManager.endCall()`, in-call dialer foregrounding with `TelecomManager.showInCallScreen(false)`, 20+ multilingual OEM disconnect resource IDs, and `KEYCODE_HEADSETHOOK` fallback.
  - WhatsApp VoIP call termination with multi-window hierarchy traversal.

### 3.4 `VoiceNoteProtectionGuard.kt` & `WhatsAppCallEngine.kt`
- **Current Codeblocks:**
  - `WhatsAppCallEngine.kt`: Disambiguates header toolbar call button vs dropdown popup menu ("Voice call" vs "Video call").
  - `VoiceNoteProtectionGuard.kt`: Prevents misdirected coordinate taps on the voice note (microphone) button by verifying active text content before clicking designated Send buttons.

---

## 4. UI & Jetpack Compose Layer

### 4.1 Design System Alignment
- Material 3 Expressive styling with dark-mode primary surface tokens (`0xFF111318`), surface containers (`0xFF1D2024`), primary teal accents (`0xFF60D4D6`), and error containers (`0xFF93000A`).
- Standardized 46.dp touch targets on text fields, dropdowns, and inline action buttons.
- Fully accessible content descriptions on all interactive icons.
- Sanitized terminology across all UI composables: standardized on "Task", "Smart Dispatch", and clean 2-word naming conventions.

---

## 5. Verification & Test Suite

### 5.1 `AutoTextComprehensiveTest.kt`
- **Current Suite:** 107 comprehensive unit and integration tests passing (100% pass rate).
- **Coverage Areas:**
  - Phone number normalization & E.164 conversion
  - Message template parameter substitution (15 dynamic tokens)
  - Multi-SIM slot mapping & OEM dual-SIM extras
  - SMS quota counting and burst prevention
  - Task state machine transitions (`DRAFT` -> `VALIDATING` -> `READY` -> `SCHEDULED` -> `RUNNING` -> `SUCCESS`/`FAILED`/`CANCELLED`/`PAUSED`)
  - Stranded `RUNNING` task recovery invariants
  - Banking shield and protected package refusal
  - Keyguard PIN tap coordinate calculations
  - Sliced polling call termination detection
  - Terminology assertions ("Task" catalog completeness)
  - Backup and restore of all 15 tables including `TABLE_APP_PRIVACY_CONFIGS`
  - Runtime permissions catalog completeness (including `ANSWER_PHONE_CALLS`, Call Log, Media)
