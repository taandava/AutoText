# Super — File & Code Reference

Auto-generated map of every Kotlin source file: what it is, what it does, and the main
declarations it contributes.

- Kotlin files: **136**
- Kotlin lines: **57,719**

> No `src/test` or `src/androidTest` tree: the project ships without test code by request.

## `com.jmd.superapp` — 1 file(s)

### `app/src/main/java/com/jmd/superapp/SuperApplication.kt`
*226 lines*
**What it is:** Central Android Application entry point and process lifecycle orchestrator for Super.
**Key blocks:**
- `class SuperApplication` — Coordinates system-wide services, registers user-facing notification categories,

## `com.jmd.superapp.api` — 1 file(s)

### `app/src/main/java/com/jmd/superapp/api/TaskFeatureApi.kt`
*745 lines*
**What it is:** Architecture definition and implementation for [TaskFeatureApi].
**What it does:** Exposes standardized, callable endpoints for all core task features: Each endpoint enforces pre-condition validation, semantic designated element actions, accurate result verification, and execution logging with verified real-device execution outcome.
**Key blocks:**
- `class FeatureExecutionResult` — Encapsulates state, business contracts, and operations for [FeatureExecutionResult]
- `class WhatsAppMessageRequest` — Encapsulates state, business contracts, and operations for [WhatsAppMessageRequest]
- `class WhatsAppStatusRequest` — Encapsulates state, business contracts, and operations for [WhatsAppStatusRequest]
- `class WhatsAppCallRequest` — Encapsulates state, business contracts, and operations for [WhatsAppCallRequest]
- `class SmsRequest` — Encapsulates state, business contracts, and operations for [SmsRequest]
- `class PhoneCallRequest` — Encapsulates state, business contracts, and operations for [PhoneCallRequest]
- `class CallSimulationRequest` — Encapsulates state, business contracts, and operations for [CallSimulationRequest]
- `object TaskFeatureApi` — Encapsulates state, business contracts, and operations for [TaskFeatureApi]

## `com.jmd.superapp.compat` — 6 file(s)

### `app/src/main/java/com/jmd/superapp/compat/AccessibilityCompat.kt`
*58 lines*
**What it is:** Architecture definition and implementation for [AccessibilityCompat].
**What it does:** Provides safe accessibility node state extraction across evolving Android accessibility APIs.
**Key blocks:**
- `object AccessibilityCompat` — Safely accesses toggle and checkbox checked states across legacy and modern Android releases. On Android 16+ (API 36+), uses tri-state ch…

### `app/src/main/java/com/jmd/superapp/compat/ActivityWindowCompat.kt`
*80 lines*
**What it is:** Backward-compatible activity window and lockscreen helper for Android 8.0 (API 26) through Android 15/16 (API 35+).
**What it does:** Provides safe execution of [Activity.setShowWhenLocked] and [Activity.setTurnScreenOn], which were introduced.
**Key blocks:**
- `object ActivityWindowCompat` — Provides safe execution of [Activity.setShowWhenLocked] and [Activity.setTurnScreenOn], which were introduced
- `fun Activity`

### `app/src/main/java/com/jmd/superapp/compat/ClipboardCompat.kt`
*173 lines*
**What it is:** Architecture definition and implementation for [ClipboardCompat].
**What it does:** Provides safe clipboard synchronization, clip attachment, sensitive content protection, and scheduled clipboard wipe across Android versions.
**Key blocks:**
- `object ClipboardCompat` — Executes ClipboardCompat operations conforming to application constraints

### `app/src/main/java/com/jmd/superapp/compat/PackageManagerCompat.kt`
*56 lines*
**What it is:** Architecture definition and implementation for [PackageManagerCompat].
**What it does:** Provides safe, backward-compatible [PackageManager] query operations across Android versions.
**Key blocks:**
- `object PackageManagerCompat` — Executes PackageManagerCompat operations conforming to application constraints

### `app/src/main/java/com/jmd/superapp/compat/SmsManagerCompat.kt`
*128 lines*
**What it is:** Architecture definition and implementation for [SmsManagerCompat].
**What it does:** Provides safe SMS framework abstractions across legacy Android versions and modern subscription-based system services (API 31+).
**Key blocks:**
- `object SmsManagerCompat` — Branches between [Context.getSystemService] on Android 12+ (API 31+) and [SmsManager.getDefault] on legacy platforms

### `app/src/main/java/com/jmd/superapp/compat/TelephonyManagerCompat.kt`
*178 lines*
**What it is:** Architecture definition and implementation for [TelephonyManagerCompat].
**What it does:** Provides backward-compatible framework adaptations across Android SDK versions (API 26 through API 36+), strictly avoiding lint suppressions through explicit permission verification and runtime SDK branching.
**Key blocks:**
- `object TelephonyManagerCompat` — Handles SDK branching between legacy [TelephonyManager.getCallState] (< API 31) and [TelephonyManager.getCallStateForSubscription] (>= AP…

## `com.jmd.superapp.core` — 25 file(s)

### `app/src/main/java/com/jmd/superapp/core/AccessibilityHealthManager.kt`
*662 lines*
**What it is:** Architecture definition and implementation for [AccessibilityHealthManager].
**What it does:** Provides watchdog monitoring, connection heartbeat tracking, privileged service healing, and manufacturer-specific battery optimization management.
**Key blocks:**
- `object AccessibilityHealthManager` — Provides continuous background verification and lifecycle maintenance for accessibility services

### `app/src/main/java/com/jmd/superapp/core/AppDataEvents.kt`
*46 lines*
**Key blocks:**
- `object AppDataEvents`

### `app/src/main/java/com/jmd/superapp/core/AutoReplyMatcher.kt`
*96 lines*
**What it is:** Pure deterministic rule matching engine for auto-replies.
**What it does:** Evaluates whether an incoming message or call event qualifies for auto-reply based on.
**Key blocks:**
- `object AutoReplyMatcher` — Evaluates whether an incoming message or call event qualifies for auto-reply based on

### `app/src/main/java/com/jmd/superapp/core/BackupCipher.kt`
*238 lines*
**What it is:** Cryptographic backup vault providing cross-device portable encryption (AES-256-GCM + PBKDF2).
**Key blocks:**
- `class InvalidPassphraseException`
- `object BackupCipher`

### `app/src/main/java/com/jmd/superapp/core/ContentValidator.kt`
*313 lines*
**What it is:** Architecture definition and implementation for [ContentValidator].
**What it does:** Provides pre-flight media URI accessibility inspection, MIME resolution, and channel-specific message content validation (SMS text-only vs WhatsApp multimedia).
**Key blocks:**
- `class AttachmentValidationResult` — Encapsulates state, business contracts, and operations for [AttachmentValidationResult]
- `class ContentValidationResult` — Encapsulates state, business contracts, and operations for [ContentValidationResult]
- `object TaskContentValidator` — Encapsulates state, business contracts, and operations for [TaskContentValidator]

### `app/src/main/java/com/jmd/superapp/core/ContextExtensions.kt`
*83 lines*
**What it is:** Architecture definition and implementation for [ContextExtensions].
**What it does:** Provides convenient context extensions for Toast messaging and native dialog interactions.
**Key blocks:**
- `fun Context` — Thread-safe: can be called from any background or worker thread
- `fun showAppTimePicker` — Executes [showAppTimePicker] logic in accordance with system and security invariants

### `app/src/main/java/com/jmd/superapp/core/DispatchJournal.kt`
*311 lines*
**What it is:** Durable exactly-once dispatch journal for scheduled task occurrences.
**What it does:** Records every task occurrence (`task_id` + scheduled trigger time) in SQLite before dispatch and.
**Key blocks:**
- `object DispatchJournal` — Uses one row per `(task_id, occurrence_key)` inside transactions, with an in-flight timeout so a

### `app/src/main/java/com/jmd/superapp/core/FileLogger.kt`
*562 lines*
**What it is:** Architecture definition and implementation for [FileLogger].
**What it does:** Provides persistent on-device diagnostic logging, campaign dispatch CSV ledgers, error tracking, scheduled file rotation (3 MB threshold), and log export.
**Key blocks:**
- `object AppFileLogger` — Persists log entries directly to private application internal storage (`files/logs/`)

### `app/src/main/java/com/jmd/superapp/core/LifecycleObserver.kt`
*83 lines*
**What it is:** Architecture definition and implementation for [LifecycleObserver].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for LifecycleObserver.
**Key blocks:**
- `object AppLifecycleObserver` — Allows background dispatch engines and accessibility services to observe app presence without manual activity counting

### `app/src/main/java/com/jmd/superapp/core/MessageVariables.kt`
*57 lines*
**What it is:** Canonical catalog of the dynamic variables a message may contain.
**What it does:** Defines the exact token set understood by [MessageTemplateResolver] so that every.
**Key blocks:**
- `class MessageVariable` — Defines the exact token set understood by [MessageTemplateResolver] so that every
- `object MessageVariables` — Serves composer chips ([MESSAGE], [STATUS]) and the settings reference ([REFERENCE])

### `app/src/main/java/com/jmd/superapp/core/NotificationIconHelper.kt`
*60 lines*
**Key blocks:**
- `object NotificationIconHelper`

### `app/src/main/java/com/jmd/superapp/core/OemBrandHelper.kt`
*712 lines*
**What it is:** Architecture definition and implementation for [OemBrandHelper].
**What it does:** Provides comprehensive, unified device manufacturer detection, autostart intents, OEM-specific battery optimization settings, floating window/overlay routing, safe system overlay filtering, and brand-tailored user guidance across:.
**Key blocks:**
- `class OemBrand` — Encapsulates state, business contracts, and operations for [OemBrand]
- `object OemBrandHelper` — Encapsulates state, business contracts, and operations for [OemBrandHelper]

### `app/src/main/java/com/jmd/superapp/core/OverlayPermissionUtils.kt`
*147 lines*
**What it is:** Architecture definition and implementation for [OverlayPermissionUtils].
**What it does:** Provides system overlay window permission verification and system settings intent routing.
**Key blocks:**
- `typealias OverlayPermissionUtils` — Provides system overlay window permission verification and system settings intent routing
- `object OverlayPermissionHelper` — Provides domain logic and lifecycle management for OverlayPermissionHelper

### `app/src/main/java/com/jmd/superapp/core/PermissionManager.kt`
*648 lines*
**What it is:** Architecture definition and implementation for [PermissionManager].
**What it does:** Provides comprehensive, verified permission-to-feature mapping, live runtime state verification, graceful degradation rules, and transparent user-directed settings intent routing. Adheres strictly to Part 9 Invariants: 1. Zero root 'su' commands or artificial elevation. 2. Transparent user-directed system settings navigation. 3. Never claims a permission is granted without verifying live system state via official APIs. 4. Application remains completely stable when optional permissions are unavailable.
**Key blocks:**
- `class PermissionFeatureInfo` — Encapsulates state, business contracts, and operations for [PermissionFeatureInfo]
- `class PermissionCategory` — Provides domain logic and lifecycle management for PermissionCategory
- `object PermissionManager` — Encapsulates state, business contracts, and operations for [PermissionManager]

### `app/src/main/java/com/jmd/superapp/core/PhoneNumberUtils.kt`
*408 lines*
**What it is:** Architecture definition and implementation for [PhoneNumberUtils].
**What it does:** Provides international and national phone number standardization, emergency service rejection, contact verification, duplicate detection, and multi-delimiter input ingestion.
**Key blocks:**
- `object PhoneNumberNormalizer` — Encapsulates state, business contracts, and operations for [PhoneNumberNormalizer]

### `app/src/main/java/com/jmd/superapp/core/PrivacyManager.kt`
*665 lines*
**What it is:** Architecture definition and implementation for [PrivacyManager].
**What it does:** Centralized, thread-safe privacy engine governing application exclusions, accessibility target scoping, sensitive content masking, overlay suppression, and clipboard protection.
**Key blocks:**
- `class PrivacyManager` — Encapsulates state, business contracts, and operations for [PrivacyManager]

### `app/src/main/java/com/jmd/superapp/core/ProtectedApplicationManager.kt`
*487 lines*
**What it is:** Architecture definition and implementation for [ProtectedApplicationManager].
**What it does:** Dedicated Protected Application Manager enforcing strict isolation between Super and banking, UPI, payment, financial authentication, and designated sensitive applications. Enforces Part 8 Invariants: 1. Default-deny policy for all external UI interactions. 2. Only explicitly authorized target applications (WhatsApp, WhatsApp Business, Phone/Dialer, Keyguard) may receive UI actions. 3. Immediate pre-action context verification before every physical tap, click, or text entry. 4. Zero inspection or traversal of protected applications' UI trees or window nodes. 5. Instant cancellation of pending task callbacks when a protected or non-authorized application is encountered. 6. Zero collection of credentials, PINs, OTPs, card details, or screen contents. 7. Zero overlay displays or notification heads-up over protected applications.
**Key blocks:**
- `object ProtectedApplicationManager` — Encapsulates state, business contracts, and operations for [ProtectedApplicationManager]

### `app/src/main/java/com/jmd/superapp/core/ReplyLoopGuard.kt`
*167 lines*
**What it is:** Loop protection for dynamic replies, split into a pure decision core ([ReplyLoopGuard]) and a.
**Key blocks:**
- `object ReplyLoopGuard` — Normalizes reply content into a fingerprint and decides whether a repeated reply inside the
- `class ReplySafetyGuard` — Reads the device's own subscriber numbers and keeps a small per-recipient ledger of the last

### `app/src/main/java/com/jmd/superapp/core/SafetyManager.kt`
*386 lines*
**What it is:** Architecture definition and implementation for [SafetyManager].
**What it does:** Provides fail-safe protections and channel-level circuit breakers preventing task execution loops and unintended dispatches.
**Key blocks:**
- `typealias SafetyManager`
- `object TaskSafetyManager` — Provides domain logic and lifecycle management for TaskSafetyManager

### `app/src/main/java/com/jmd/superapp/core/SmsQuotaManager.kt`
*612 lines*
**What it is:** Architecture definition and implementation for [SmsQuotaManager].
**What it does:** Provides cellular SMS quota tracking, TRAI/carrier daily limit enforcement (100 SMS/day safeguard), multi-part PDU segment calculation, provider reconciliation, and in-flight reservation management.
**Key blocks:**
- `object SmsQuotaManager` — Integrates internal execution logs with device-wide `content://sms/sent` records to track both scheduled and user-sent SMS segments again…

### `app/src/main/java/com/jmd/superapp/core/SuperAppInitializer.kt`
*60 lines*
**What it is:** Architecture definition and implementation for [SuperAppInitializer].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SuperAppInitializer.
**Key blocks:**
- `class SuperAppInitializer` — Flattens application initialization into a unified startup sequence, optimizing cold-start latency

### `app/src/main/java/com/jmd/superapp/core/SuperNotificationManager.kt`
*228 lines*
**Key blocks:**
- `object SuperNotificationManager`

### `app/src/main/java/com/jmd/superapp/core/TemplateResolver.kt`
*159 lines*
**What it is:** Architecture definition and implementation for [TemplateResolver].
**What it does:** Provides dynamic placeholder substitution for scheduled messages, auto-replies and campaigns.
**Key blocks:**
- `object MessageTemplateResolver` — Replaces every placeholder listed in [MessageVariables] with live date, time,

### `app/src/main/java/com/jmd/superapp/core/TraceUtils.kt`
*36 lines*
**What it is:** Architecture definition and implementation for [TraceUtils].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for TraceUtils.
**Key blocks:**
- `object AppTrace` — Enables zero-overhead section profiling for critical execution paths: keyguard dismissal, accessibility traversal, database maintenance, …

### `app/src/main/java/com/jmd/superapp/core/WhatsappPackageUtils.kt`
*144 lines*
**What it is:** Architecture definition and implementation for [WhatsappPackageUtils].
**What it does:** Provides package identification, installation resolution, and intent targeting for consumer WhatsApp and WhatsApp Business editions.
**Key blocks:**
- `object WhatsAppPackageHelper` — Encapsulates state, business contracts, and operations for [WhatsAppPackageHelper]

## `com.jmd.superapp.data.db` — 1 file(s)

### `app/src/main/java/com/jmd/superapp/data/db/DatabaseHelper.kt`
*667 lines*
**What it is:** Architecture definition and implementation for [DatabaseHelper].
**What it does:** Provides thread-safe SQLite database initialization, schema versioning, transaction isolation, and scheduled schema migrations across database versions 1 through 6. [com.jmd.superapp.data.repo.DailyCampaignRepository], [com.jmd.superapp.data.repo.LogRepository], [com.jmd.superapp.data.repo.AutoReplyRepository], [com.jmd.superapp.data.repo.WhatsAppRepository].
**Key blocks:**
- `class DatabaseHelper` — Manages 15 relational tables:

## `com.jmd.superapp.data.model` — 3 file(s)

### `app/src/main/java/com/jmd/superapp/data/model/BackupDataDto.kt`
*131 lines*
**What it is:** Architecture definition and implementation for [BackupDataDto].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for BackupDataDto.
**Key blocks:**
- `class BackupDataDto` — Encapsulates state, business contracts, and operations for [BackupDataDto]
- `class TaskBackupDto` — Provides domain logic and lifecycle management for TaskBackupDto
- `class AutoReplyBackupDto` — Provides domain logic and lifecycle management for AutoReplyBackupDto
- `class CampaignBackupDto` — Provides domain logic and lifecycle management for CampaignBackupDto
- `class ContactBackupDto` — Provides domain logic and lifecycle management for ContactBackupDto

### `app/src/main/java/com/jmd/superapp/data/model/Models.kt`
*623 lines*
**What it is:** Architecture definition and implementation for [Models].
**What it does:** Defines core immutable data contracts and domain models across the entire Super ecosystem. Encompasses scheduled tasks, dynamic contact items, campaign configurations, auto-reply rules, execution logs, channel types, and recurrence rules. `DailyCampaignScreen`.
**Key blocks:**
- `class DynamicContactItem` — Tracks individual contact outreach status, last sent timestamps, and dispatch counts across both SMS and WhatsApp channels
- `class DailyCampaignConfig` — Provides domain logic and lifecycle management for DailyCampaignConfig
- `class IngestionSummary` — Provides domain logic and lifecycle management for IngestionSummary
- `class DynamicPoolStats` — Provides domain logic and lifecycle management for DynamicPoolStats
- `class ChannelType` — Provides domain logic and lifecycle management for ChannelType
- `class ExecutionLog` — Provides domain logic and lifecycle management for ExecutionLog
- `class RecipientItem` — Provides domain logic and lifecycle management for RecipientItem
- `class WhatsAppContactItem` — Provides domain logic and lifecycle management for WhatsAppContactItem
- `class WhatsAppFilterMode` — Provides domain logic and lifecycle management for WhatsAppContactItem
- `class WhatsAppGroupItem` — Provides domain logic and lifecycle management for WhatsAppGroupItem
- `class AutoReplyRule` — Provides domain logic and lifecycle management for AutoReplyRule
- `class RepeatUnit` — Provides domain logic and lifecycle management for RepeatUnit
- …and 5 more

### `app/src/main/java/com/jmd/superapp/data/model/PrivacyModels.kt`
*106 lines*
**What it is:** Architecture definition and implementation for [PrivacyModels].
**What it does:** Defines data models and immutable configuration contracts for per-application privacy, system visibility controls, content masking, and accessibility scoping.
**Key blocks:**
- `class AppPrivacyConfig` — Encapsulates state, business contracts, and operations for [AppPrivacyConfig]
- `class AppInfoItem` — Encapsulates state, business contracts, and operations for [AppInfoItem]
- `class GlobalPrivacySettings` — Encapsulates state, business contracts, and operations for [GlobalPrivacySettings]

## `com.jmd.superapp.data.prefs` — 2 file(s)

### `app/src/main/java/com/jmd/superapp/data/prefs/AppDataStore.kt`
*255 lines*
**What it is:** Architecture definition and implementation for [AppDataStore].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for AppDataStore.
**Key blocks:**
- `val Context` — Provides core logic, component interfaces, and subsystem bindings for AppDataStore
- `class AppDataStore` — Asynchronous, fully transactional, and coroutine-native key-value persistence. Prevents main thread disk I/O and ANRs during high-frequen…

### `app/src/main/java/com/jmd/superapp/data/prefs/PreferenceManager.kt`
*866 lines*
**What it is:** Architecture definition and implementation for [PreferenceManager].
**What it does:** Provides typed configuration storage for app preferences, default SIM bindings, security PIN settings, keyguard unlock options, UI branding, and Direct Boot (FBE) device-protected storage.
**Key blocks:**
- `class PreferenceManager` — Encapsulates state, business contracts, and operations for [PreferenceManager]

## `com.jmd.superapp.data.repo` — 9 file(s)

### `app/src/main/java/com/jmd/superapp/data/repo/AppRepository.kt`
*1378 lines*
**What it is:** Architecture definition and implementation for [AppRepository].
**What it does:** Exposes a unified, transaction-safe facade aggregating access across all domain repositories: tasks, dynamic contact pool, daily campaigns, execution logs, auto-reply rules, and WhatsApp contacts. [com.jmd.superapp.data.repo.LogRepository], [com.jmd.superapp.data.repo.RecipientRepository], [com.jmd.superapp.data.repo.AutoReplyRepository], [com.jmd.superapp.data.repo.WhatsAppRepository] `ComposeTaskScreen`, `DailyCampaignScreen`.
**Key blocks:**
- `class AppRepository` — Encapsulates state, business contracts, and operations for [AppRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/AutoReplyRepository.kt`
*320 lines*
**What it is:** Architecture definition and implementation for [AutoReplyRepository].
**What it does:** Governs auto-reply rule configurations, time-window evaluation, keyword pattern matching, per-sender cooldown intervals, and daily reply rate limiting.
**Key blocks:**
- `class AutoReplyRepository` — Encapsulates state, business contracts, and operations for [AutoReplyRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/BackupRepository.kt`
*581 lines*
**What it is:** Architecture definition and implementation for [BackupRepository].
**What it does:** Provides transactional backup export and restore mechanisms to convert all application SQLite tables to and from structured JSON format for user export/import.
**Key blocks:**
- `class BackupRepository` — Supports both high-speed Kotlinx Serialization typed payloads and schema-agnostic raw table dumps

### `app/src/main/java/com/jmd/superapp/data/repo/DailyCampaignRepository.kt`
*541 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignRepository].
**What it does:** Governs dynamic contact pool filtering, daily quota pacing, campaign ledger recording, and unique cycle deduplication for high-volume scheduled outreach.
**Key blocks:**
- `class DailyCampaignRepository` — Encapsulates state, business contracts, and operations for [DailyCampaignRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/LogRepository.kt`
*273 lines*
**What it is:** Architecture definition and implementation for [LogRepository].
**What it does:** Persists and queries task execution logs for SMS, WhatsApp, and call actions. Maintains execution history with timestamps, delivery status, and failure diagnostics.
**Key blocks:**
- `class LogRepository` — Encapsulates state, business contracts, and operations for [LogRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/PrivacyRepository.kt`
*432 lines*
**What it is:** Architecture definition and implementation for [PrivacyRepository].
**What it does:** Provides transactional CRUD persistence for per-application privacy configurations in the local SQLite database (`super.db`).
**Key blocks:**
- `class PrivacyRepository` — Encapsulates state, business contracts, and operations for [PrivacyRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/RecipientRepository.kt`
*202 lines*
**What it is:** Architecture definition and implementation for [RecipientRepository].
**What it does:** Manages saved contacts and grouped recipients used across campaign sequences and ad-hoc task creation.
**Key blocks:**
- `class RecipientRepository` — Encapsulates state, business contracts, and operations for [RecipientRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/TaskRepository.kt`
*567 lines*
**What it is:** Architecture definition and implementation for [TaskRepository].
**What it does:** Provides thread-safe CRUD operations, JSON array serialization of multi-recipient contacts, execution status transitions, and delivery deduplication for scheduled tasks.
**Key blocks:**
- `class TaskRepository` — Encapsulates state, business contracts, and operations for [TaskRepository]

### `app/src/main/java/com/jmd/superapp/data/repo/WhatsAppRepository.kt`
*775 lines*
**What it is:** Architecture definition and implementation for [WhatsAppRepository].
**What it does:** Manages harvested WhatsApp contacts, group items, chat title validation heuristics, contact book cross-referencing, and profile photo resolution.
**Key blocks:**
- `class WhatsAppRepository` — Encapsulates state, business contracts, and operations for [WhatsAppRepository]

## `com.jmd.superapp.engine` — 4 file(s)

### `app/src/main/java/com/jmd/superapp/engine/ContactIngestionEngine.kt`
*318 lines*
**What it is:** Architecture definition and implementation for [ContactIngestionEngine].
**What it does:** Coordinates contact and recipient ingestion across device ContactsContract, CallLog, cellular SMS inbox/outbox history, and WhatsApp chat history scans.
**Key blocks:**
- `class ContactIngestionEngine` — Encapsulates state, business contracts, and operations for [ContactIngestionEngine]

### `app/src/main/java/com/jmd/superapp/engine/SequentialTaskEngine.kt`
*1971 lines*
**What it is:** Architecture definition and implementation for [SequentialTaskEngine].
**What it does:** Centralized sequential execution orchestrator for scheduled tasks across SMS, WhatsApp chats, WhatsApp Status stories, Phone Calls, Reminders, and Sound Mode events.
**Key blocks:**
- `class SequentialTaskEngine` — Guarantees serial execution of UI task tasks via [uiTaskMutex] and safe concurrency for background cellular SMS dispatches via [smsMutex]

### `app/src/main/java/com/jmd/superapp/engine/SmsSender.kt`
*339 lines*
**What it is:** Architecture definition and implementation for [SmsSender].
**What it does:** Coordinates cellular SMS transmission, dual-SIM slot binding, multipart PDU assembly, radio modem acknowledgement latching, and quota reconciliation.
**Key blocks:**
- `class SmsSender` — Utilizes [com.jmd.superapp.compat.SmsManagerCompat] and [SubscriptionManager] to route messages through user-selected SIM slots (SIM 1 or…

### `app/src/main/java/com/jmd/superapp/engine/WhatsAppSender.kt`
*298 lines*
**What it is:** Architecture definition and implementation for [WhatsAppSender].
**What it does:** Coordinates intent generation and accessibility service dispatch for WhatsApp (Personal & Business) direct chats, group messages, and status stories.
**Key blocks:**
- `class WhatsAppSender` — Interfaces with [TaskAccessibilityService] to execute scheduled taps, node inputs, and send button clicks across consumer WhatsApp and Wh…

## `com.jmd.superapp.receiver` — 9 file(s)

### `app/src/main/java/com/jmd/superapp/receiver/AccessibilityWatchdogReceiver.kt`
*118 lines*
**What it is:** Architecture definition and implementation for [AccessibilityWatchdogReceiver].
**What it does:** Periodic watchdog receiver testing accessibility service liveness and self-healing when unresponsive.
**Key blocks:**
- `class AccessibilityWatchdogReceiver` — Encapsulates state, business contracts, and operations for [AccessibilityWatchdogReceiver]

### `app/src/main/java/com/jmd/superapp/receiver/BootReceiver.kt`
*147 lines*
**What it is:** Architecture definition and implementation for [BootReceiver].
**What it does:** Catches [Intent.ACTION_BOOT_COMPLETED], [Intent.ACTION_LOCKED_BOOT_COMPLETED], and [Intent.ACTION_MY_PACKAGE_REPLACED] events to reschedule pending alarms, reinitialize accessibility watchdogs, and resume daily campaign cadences. [com.jmd.superapp.scheduler.DailyCampaignScheduler].
**Key blocks:**
- `class BootReceiver` — Encapsulates state, business contracts, and operations for [BootReceiver]

### `app/src/main/java/com/jmd/superapp/receiver/PhoneCallReceiver.kt`
*523 lines*
**What it is:** Architecture definition and implementation for [PhoneCallReceiver].
**What it does:** Monitors telephony call state transitions (RINGING, OFFHOOK, IDLE) to trigger scheduled SMS replies for missed calls, answered and ended incoming calls, or connected and ended outgoing calls. Accurately distinguishes between: 1. Missed Incoming Calls (rings but not answered) -> Triggers MISSED_CALL auto-reply. 2. Answered & Disconnected Incoming Calls (conversation occurred) -> Triggers CALL_ENDED auto-reply. 3. Answered & Disconnected Outgoing Calls (conversation occurred, duration > 0) -> Triggers CALL_ENDED auto-reply. 4. Unconnected Outgoing Calls (dialed by user but not answered, rejected, or cancelled, duration == 0) -> Strictly IGNORED (no auto-reply).
**Key blocks:**
- `class PhoneCallReceiver` — Encapsulates state, business contracts, and operations for [PhoneCallReceiver]

### `app/src/main/java/com/jmd/superapp/receiver/SmsDeliveryReceiver.kt`
*99 lines*
**What it is:** Receiver for carrier delivery reports attached to outgoing SMS PDUs.
**What it does:** [com.jmd.superapp.engine.SmsSender] attaches one delivered PendingIntent per PDU part. When the.
**Key blocks:**
- `class SmsDeliveryReceiver` — [com.jmd.superapp.engine.SmsSender] attaches one delivered PendingIntent per PDU part. When the

### `app/src/main/java/com/jmd/superapp/receiver/SmsReceiver.kt`
*228 lines*
**What it is:** Architecture definition and implementation for [SmsReceiver].
**What it does:** Catches incoming cellular SMS messages via [Telephony.Sms.Intents.SMS_RECEIVED_ACTION], inspects message body patterns against configured AutoReply rules, and dispatches responses.
**Key blocks:**
- `class SmsReceiver` — Encapsulates state, business contracts, and operations for [SmsReceiver]

### `app/src/main/java/com/jmd/superapp/receiver/SuperDeviceAdminReceiver.kt`
*298 lines*
**What it is:** Architecture definition and implementation for [SuperDeviceAdminReceiver].
**What it does:** Implements Android Device Administration callbacks enabling immediate device re-locking after autonomous WhatsApp messaging sessions.
**Key blocks:**
- `class SuperDeviceAdminReceiver` — Encapsulates state, business contracts, and operations for [SuperDeviceAdminReceiver]

### `app/src/main/java/com/jmd/superapp/receiver/TaskAlarmReceiver.kt`
*266 lines*
**What it is:** Architecture definition and implementation for [TaskAlarmReceiver].
**What it does:** Listens for exact RTC_WAKEUP alarms triggered by Android AlarmManager for scheduled task tasks. Wakes the device, logs alarm activation, updates task execution state, and enqueues the task ID into [com.jmd.superapp.engine.SequentialTaskEngine].
**Key blocks:**
- `class TaskAlarmReceiver` — Encapsulates state, business contracts, and operations for [TaskAlarmReceiver]

### `app/src/main/java/com/jmd/superapp/receiver/TaskDispatchReceiver.kt`
*691 lines*
**What it is:** Architecture definition and implementation for [TaskDispatchReceiver].
**What it does:** Exposes system-level endpoints callable via ADB CLI, scripts, and intent broadcasts:.
**Key blocks:**
- `class TaskDispatchReceiver`

### `app/src/main/java/com/jmd/superapp/receiver/TimeChangeReceiver.kt`
*57 lines*
**What it is:** Architecture definition and implementation for [TimeChangeReceiver].
**What it does:** Listens for system clock and timezone changes to immediately recalculate and reschedule all pending task alarms and daily outreach timers.
**Key blocks:**
- `class TimeChangeReceiver` — Encapsulates state, business contracts, and operations for [TimeChangeReceiver]

## `com.jmd.superapp.scheduler` — 8 file(s)

### `app/src/main/java/com/jmd/superapp/scheduler/AccessibilityWatchdogScheduler.kt`
*120 lines*
**What it is:** Architecture definition and implementation for [AccessibilityWatchdogScheduler].
**What it does:** Schedules recurring exact-while-idle alarms with Android AlarmManager to periodically evaluate the binder health of [com.jmd.superapp.service.TaskAccessibilityService].
**Key blocks:**
- `object AccessibilityWatchdogScheduler` — Integrates dual-layer redundancy: 1. High-frequency exact AlarmManager RTC_WAKEUP alarms. 2. Persistent periodic WorkManager tasks surviv…

### `app/src/main/java/com/jmd/superapp/scheduler/AccessibilityWatchdogWorker.kt`
*100 lines*
**What it is:** Architecture definition and implementation for [AccessibilityWatchdogWorker].
**What it does:** Background WorkManager [CoroutineWorker] executing resilient watchdog monitoring of [com.jmd.superapp.service.TaskAccessibilityService] and [com.jmd.superapp.service.TaskNotificationListenerService].
**Key blocks:**
- `class AccessibilityWatchdogWorker` — Encapsulates state, business contracts, and operations for [AccessibilityWatchdogWorker]

### `app/src/main/java/com/jmd/superapp/scheduler/AlarmScheduler.kt`
*533 lines*
**What it is:** Architecture definition and implementation for [AlarmScheduler].
**What it does:** Configures exact RTC_WAKEUP alarms with Android AlarmManager across API versions (API 26 through API 36+), strictly verifying [AlarmManager.canScheduleExactAlarms] on Android 12+ (API 31+) and falling back to inexact windowed alarms when exact alarm privileges are restricted.
**Key blocks:**
- `class AlarmScheduler` — Encapsulates state, business contracts, and operations for [AlarmScheduler]

### `app/src/main/java/com/jmd/superapp/scheduler/DailyCampaignScheduler.kt`
*304 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignScheduler].
**What it does:** Enqueues and coordinates WorkManager periodic background workers and immediate one-time batch executions for daily outreach campaigns.
**Key blocks:**
- `object DailyCampaignScheduler` — Encapsulates state, business contracts, and operations for [DailyCampaignScheduler]

### `app/src/main/java/com/jmd/superapp/scheduler/DailyCampaignWorker.kt`
*649 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignWorker].
**What it does:** Background WorkManager [CoroutineWorker] executing scheduled daily outreach batch campaigns. Coordinates dynamic contact ingestion, per-contact cooldowns, gap delays (pacing), SMS quota tracking, and dispatching via [SmsSender] or [WhatsAppSender].
**Key blocks:**
- `class DailyCampaignWorker` — Encapsulates state, business contracts, and operations for [DailyCampaignWorker]

### `app/src/main/java/com/jmd/superapp/scheduler/DailyTaskBatchScheduler.kt`
*20 lines*
**What it is:** Architecture definition and implementation for [DailyTaskBatchScheduler].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for DailyTaskBatchScheduler.
**Key blocks:**
- `typealias DailyTaskBatchScheduler` — Provides core logic, component interfaces, and subsystem bindings for DailyTaskBatchScheduler

### `app/src/main/java/com/jmd/superapp/scheduler/DatabaseMaintenanceWorker.kt`
*110 lines*
**What it is:** Architecture definition and implementation for [DatabaseMaintenanceWorker].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for DatabaseMaintenanceWorker.
**Key blocks:**
- `class DatabaseMaintenanceWorker` — Pruning execution logs older than 30 days. 2. Self-healing orphaned tasks stuck in RUNNING state. 3. Reclaiming SQLite disk space via VAC…

### `app/src/main/java/com/jmd/superapp/scheduler/WorkManagerScheduler.kt`
*71 lines*
**What it is:** Architecture definition and implementation for [WorkManagerScheduler].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for WorkManagerScheduler.
**Key blocks:**
- `object WorkManagerScheduler` — Encapsulates state, business contracts, and operations for [WorkManagerScheduler]

## `com.jmd.superapp.service` — 4 file(s)

### `app/src/main/java/com/jmd/superapp/service/TaskAccessibilityService.kt`
*3246 lines*
**What it is:** Architecture definition and implementation for [TaskAccessibilityService].
**What it does:** Background accessibility service delivering physical UI interaction, window inspection, dynamic popup dismissal, text population, and send button clicks across WhatsApp Messenger and WhatsApp Business. [com.jmd.superapp.whatsapp.WhatsAppGroupEngine], [com.jmd.superapp.whatsapp.PopupShield], [com.jmd.superapp.whatsapp.VoiceNoteProtectionGuard], [com.jmd.superapp.whatsapp.KeyguardUnlockEngine].
**Key blocks:**
- `class TaskAccessibilityService` — Implements strict security invariants:

### `app/src/main/java/com/jmd/superapp/service/TaskForegroundService.kt`
*144 lines*
**What it is:** Architecture definition and implementation for [TaskForegroundService].
**What it does:** Dedicated foreground service lifecycle manager ensuring background task visibility and clean notification teardown.
**Key blocks:**
- `class TaskForegroundService` — Encapsulates state, business contracts, and operations for [TaskForegroundService]

### `app/src/main/java/com/jmd/superapp/service/TaskNotificationListenerService.kt`
*304 lines*
**What it is:** Architecture definition and implementation for [TaskNotificationListenerService].
**What it does:** Processes incoming notifications across messaging apps (WhatsApp, WhatsApp Business, SMS, Telegram, Messenger) and dispatches scheduled replies via inline [RemoteInput] actions without launching UI on screen.
**Key blocks:**
- `class TaskNotificationListenerService` — Encapsulates state, business contracts, and operations for [TaskNotificationListenerService]

### `app/src/main/java/com/jmd/superapp/service/TaskTileService.kt`
*155 lines*
**What it is:** Architecture definition and implementation for [TaskTileService].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for TaskTileService.
**Key blocks:**
- `class TaskTileService` — Allows users to 1-tap pause Accessibility (via disableSelf) before opening banking or payment apps, and 1-tap resume or open Accessibilit…

## `com.jmd.superapp.telephony` — 1 file(s)

### `app/src/main/java/com/jmd/superapp/telephony/TelephonyCallEngine.kt`
*335 lines*
**What it is:** Autonomous Telephony Orchestration Engine providing native call dispatch, scheduled call tracking,.
**Key blocks:**
- `object TelephonyCallEngine` — Manages cellular call dispatch, tracking, termination, and state verification

## `com.jmd.superapp.ui.activity` — 4 file(s)

### `app/src/main/java/com/jmd/superapp/ui/activity/CallSimulationActivity.kt`
*279 lines*
**What it is:** Architecture definition and implementation for [CallSimulationActivity].
**What it does:** Fullscreen incoming call simulator activity playing real ringtones and vibration patterns, displaying custom caller name and number, and supporting Accept/Decline interactions.
**Key blocks:**
- `class CallSimulationActivity` — Encapsulates state, business contracts, and operations for [CallSimulationActivity]
- `typealias SimulatedCallActivity` — Coordinates onDestroy operations conforming to application constraints

### `app/src/main/java/com/jmd/superapp/ui/activity/DeviceAdminPromptActivity.kt`
*70 lines*
**What it is:** Transparent trampoline Activity that opens the system Device Administration prompt.
**What it does:** OEM skins (notably Samsung's SecDeviceAdminAdd) reject `ACTION_ADD_DEVICE_ADMIN` intents that.
**Key blocks:**
- `class DeviceAdminPromptActivity` — Starts the Device Admin intent from an Activity context (no NEW_TASK flag), then closes itself so

### `app/src/main/java/com/jmd/superapp/ui/activity/MainActivity.kt`
*243 lines*
**What it is:** Architecture definition and implementation for [MainActivity].
**What it does:** Primary Android Activity hosting Jetpack Compose UI, handling runtime permission requests, deep links, incoming SEND intent shares, dynamic app icon aliases, and theme state.
**Key blocks:**
- `class MainActivity` — Encapsulates state, business contracts, and operations for [MainActivity]

### `app/src/main/java/com/jmd/superapp/ui/activity/TaskLauncherActivity.kt`
*441 lines*
**What it is:** Architecture definition and implementation for [TaskLauncherActivity].
**What it does:** Transparent trampoline Activity that wakes the display, handles keyguard dismissal flags, and launches WhatsApp or phone intents from background contexts (e.g. AlarmManager or Service) complying with Android 10+ background activity start restrictions.
**Key blocks:**
- `class TaskLauncherActivity` — Encapsulates state, business contracts, and operations for [TaskLauncherActivity]

## `com.jmd.superapp.ui.components` — 4 file(s)

### `app/src/main/java/com/jmd/superapp/ui/components/AppStatusIndicators.kt`
*340 lines*
**What it is:** Architecture definition and implementation for [AppStatusIndicators].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for AppStatusIndicators.
**Key blocks:**
- `val IndicatorActiveGreen`
- `val IndicatorInactiveRed`
- `class SystemIndicator` — Provides domain logic and lifecycle management for SystemIndicator
- `object SystemIndicatorResolver` — Provides domain logic and lifecycle management for SystemIndicatorResolver
- `fun AppHeaderStatusIndicators` — Active = Green (#00E676), Inactive/Disabled = Red (#FF5252)

### `app/src/main/java/com/jmd/superapp/ui/components/Avatars.kt`
*265 lines*
**What it is:** Architecture definition and implementation for [Avatars].
**What it does:** Contact avatars with initials generation, color hashing, LruCache photo caching, and badge overlays for WhatsApp, SMS, and Call channel indicators.
**Key blocks:**
- `object AvatarBitmapCache` — Implements an in-memory ceiling of 500 bitmaps with synchronized read/write access to eliminate repeated disk I/O and ContentResolver que…
- `fun M3Avatar` — Features:
- `fun ContactAvatar` — Renders interactive Material 3 UI for [ContactAvatar] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/components/CompactComponents.kt`
*1137 lines*
**What it is:** Architecture definition and implementation for [CompactComponents].
**What it does:** Core reusable UI design building blocks adhering to Material 3 Expressive Orange specifications:.
**Key blocks:**
- `class AppButtonVariant` — Encapsulates state, business contracts, and operations for [AppButtonVariant]
- `fun AppButton` — Renders interactive Material 3 UI for [AppButton] adhering to application design invariants
- `fun AppBadge` — Renders interactive Material 3 UI for [AppBadge] adhering to application design invariants
- `fun AppSegmentedControl` — Renders interactive Material 3 UI for [AppSegmentedControl] adhering to application design invariants
- `fun AppChip` — Renders interactive Material 3 UI for [AppChip] adhering to application design invariants
- `fun AppToggleRow` — Renders interactive Material 3 UI for [AppToggleRow] adhering to application design invariants
- `fun CompactSwitch` — Renders interactive Material 3 UI for [CompactSwitch] adhering to application design invariants
- `fun AppCard` — Renders interactive Material 3 UI for [AppCard] adhering to application design invariants
- `fun AppCardElevated` — Renders interactive Material 3 UI for [AppCardElevated] adhering to application design invariants
- `fun AppStatCard` — Renders interactive Material 3 UI for [AppStatCard] adhering to application design invariants
- `fun AppEmptyState` — Renders interactive Material 3 UI for [AppEmptyState] adhering to application design invariants
- `fun AppDialogCard` — Renders interactive Material 3 UI for [AppDialogCard] adhering to application design invariants
- …and 4 more

### `app/src/main/java/com/jmd/superapp/ui/components/Pickers.kt`
*703 lines*
**What it is:** Architecture definition and implementation for [Pickers].
**What it does:** Inline non-intrusive Date & Time pickers (AppDateTimePicker, appSegmentedTimePicker), search inputs, and filter chips preventing disruptive modal popups.
**Key blocks:**
- `fun AppDateTimePicker` — Allows users to configure dispatch timestamps with instant visual feedback without disruptive fullscreen dialogs. Supports Quick Day sele…
- `fun AppInputField` — Renders interactive Material 3 Compact UI for [AppInputField] adhering to application design invariants
- `fun AppSearchField` — Renders interactive Material 3 UI for [AppSearchField] adhering to application design invariants

## `com.jmd.superapp.ui.model` — 1 file(s)

### `app/src/main/java/com/jmd/superapp/ui/model/UiModels.kt`
*99 lines*
**What it is:** Architecture definition and implementation for [UiModels].
**What it does:** Defines lightweight presentation models, recipient chips, task type picker items, and auto-reply selection items for Compose screens. `AutoReplyScreen`.
**Key blocks:**
- `class AutoReplyTypeItem` — Encapsulates the visual styling, iconography, and event description for configured reply triggers
- `class SelectedRecipient` — Encapsulates state, business contracts, and operations for [SelectedRecipient]
- `class TaskTypeItem` — Maps user selection to specific channel dispatch configurations and specialized task modes

## `com.jmd.superapp.ui.screen` — 5 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/CallSimulationScreen.kt`
*292 lines*
**What it is:** Architecture definition and implementation for [CallSimulationScreen].
**What it does:** Incoming simulated phone call user interface rendering caller photo/avatar, contact name, and animated accept/decline action triggers.
**Key blocks:**
- `fun CallSimulationScreen` — Renders interactive Material 3 UI for [CallSimulationScreen] adhering to application design invariants
- `fun SimulatedCallScreen` — Renders interactive Material 3 UI for [SimulatedCallScreen] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/MainAppHost.kt`
*386 lines*
**What it is:** Architecture definition and implementation for [MainAppHost].
**What it does:** Primary UI application host managing bottom navigation tabs, deep-link text ingestion, full-screen composer sheets, sequence studios, and task type selection dialogs.
**Key blocks:**
- `fun MainAppHost` — Renders interactive Material 3 UI for [MainAppHost] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/MoreScreen.kt`
*291 lines*
**What it is:** Architecture definition and implementation for [MoreScreen].
**What it does:** Hub screen navigating to secondary features: Settings, Documentation, Call Simulation, Auto-Reply, and System Diagnostics.
**Key blocks:**
- `fun MoreScreenView` — Renders interactive Material 3 UI for [MoreScreenView] adhering to application design invariants
- `fun MenuItemCard` — Renders Material 3 interface elements and handles user interaction for MenuItemCard

### `app/src/main/java/com/jmd/superapp/ui/screen/PermissionScreen.kt`
*336 lines*
**What it is:** Architecture definition and implementation for [PermissionScreen].
**What it does:** Material 3 Expressive System Health Hub presenting real-time privilege states:.
**Key blocks:**
- `fun PermissionCenterView` — Renders interactive Material 3 UI for [PermissionCenterView] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/TaskTypeSelectionDialog.kt`
*485 lines*
**What it is:** Architecture definition and implementation for [TaskTypeSelectionDialog].
**What it does:** Modal task picker displaying clear, categorized task types: SMS, WhatsApp, WhatsApp Business, WhatsApp Status (sub-flow for Personal vs Business W4B), Phone Calls, WhatsApp Voice Calls, Call Simulations, Contact Sequences, Daily Campaigns, Alarms, and Sound Modes. `MainAppHost`.
**Key blocks:**
- `fun TaskTypeSelectionDialog` — Supported Dispatch Pathways:
- `fun DialogOptionTile` — Renders Material 3 interface elements and handles user interaction for DialogOptionTile

## `com.jmd.superapp.ui.screen.autoreply` — 3 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/autoreply/AutoReplyCardComponents.kt`
*513 lines*
**What it is:** Architecture definition and implementation for [AutoReplyCardComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for AutoReplyCardComponents.
**Key blocks:**
- `fun AutoReplyRuleCard` — Renders interactive Material 3 UI for [AutoReplyRuleCard] adhering to application design invariants
- `fun AutoReplyLimitsCard` — Renders interactive Material 3 UI for [AutoReplyLimitsCard] adhering to application design invariants
- `fun AutoReplyMessageCard` — Renders the editable reply body, its character counter and the insertable token chips

### `app/src/main/java/com/jmd/superapp/ui/screen/autoreply/AutoReplyDialogs.kt`
*858 lines*
**What it is:** Architecture definition and implementation for [AutoReplyDialogs].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for AutoReplyDialogs.
**Key blocks:**
- `fun AutoReplyTypeSelectionSheet` — Renders interactive Material 3 UI for [AutoReplyTypeSelectionSheet] adhering to application design invariants
- `fun AutoReplyRuleDialog` — Renders interactive Material 3 UI for [AutoReplyRuleDialog] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/autoreply/AutoReplyScreen.kt`
*303 lines*
**What it is:** Architecture definition and implementation for [AutoReplyScreen].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for AutoReplyScreen.
**Key blocks:**
- `fun AutoReplyScreen` — Renders interactive Material 3 UI for [AutoReplyScreen] adhering to application design invariants

## `com.jmd.superapp.ui.screen.campaign` — 4 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/campaign/DailyCampaignCardComponents.kt`
*1388 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignCardComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for DailyCampaignCardComponents.
**Key blocks:**
- `fun ActiveCampaignOverviewCard` — Renders Material 3 interface elements and handles user interaction for ActiveCampaignOverviewCard
- `fun PausedCampaignOverviewCard` — Renders interactive Material 3 UI for [PausedCampaignOverviewCard] adhering to application design invariants
- `fun DailyCampaignEmptyStateCard` — Renders interactive Material 3 UI for [DailyCampaignEmptyStateCard] adhering to application design invariants
- `fun ChannelSelectionCard` — Renders interactive Material 3 UI for [ChannelSelectionCard] adhering to application design invariants
- `fun WhatsAppBusinessReadinessCard` — Renders interactive Material 3 UI for [WhatsAppBusinessReadinessCard] adhering to application design invariants
- `fun DailyCampaignBannerCard` — Renders interactive Material 3 UI for [DailyCampaignBannerCard] adhering to application design invariants
- `fun DailyCampaignConfigCard` — Renders interactive Material 3 UI for [DailyCampaignConfigCard] adhering to application design invariants
- `fun DailySmsQuotaCard` — Renders interactive Material 3 UI for [DailySmsQuotaCard] adhering to application design invariants
- `fun WhatsAppA11yHealthBar` — Renders interactive Material 3 UI for [WhatsAppA11yHealthBar] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/campaign/DailyCampaignDialogComponents.kt`
*506 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignDialogComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for DailyCampaignDialogComponents.
**Key blocks:**
- `fun CreateDailyCampaignSheet` — Renders Material 3 interface elements and handles user interaction for CreateDailyCampaignSheet
- `fun DailyCampaignChannelSelectionDialog` — Renders interactive Material 3 UI for [DailyCampaignChannelSelectionDialog] adhering to application design invariants
- `fun DailyChannelOptionCard` — Renders Material 3 interface elements and handles user interaction for DailyChannelOptionCard

### `app/src/main/java/com/jmd/superapp/ui/screen/campaign/DailyCampaignListItemComponents.kt`
*528 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignListItemComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for DailyCampaignListItemComponents.
**Key blocks:**
- `fun DailyContactPoolHeaderCard` — Renders Material 3 interface elements and handles user interaction for DailyContactPoolHeaderCard
- `fun DailySentLedgerHeaderCard` — Renders interactive Material 3 UI for [DailySentLedgerHeaderCard] adhering to application design invariants
- `fun ContactItemCard` — Renders interactive Material 3 UI for [ContactItemCard] adhering to application design invariants
- `fun LedgerItemCard` — Renders interactive Material 3 UI for [LedgerItemCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/campaign/DailyCampaignScreen.kt`
*811 lines*
**What it is:** Architecture definition and implementation for [DailyCampaignScreen].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for DailyCampaignScreen.
**Key blocks:**
- `fun DailyAutoSenderScreen` — Renders interactive Material 3 UI for [DailyAutoSenderScreen] adhering to application design invariants

## `com.jmd.superapp.ui.screen.compose` — 7 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposeCallSection.kt`
*657 lines*
**What it is:** Architecture definition and implementation for [ComposeCallSection].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposeCallSection.
**Key blocks:**
- `fun ComposeCallSimulationAndSoundCard` — Renders interactive Material 3 UI for [ComposeCallSimulationAndSoundCard] adhering to application design invariants
- `fun ComposeSimulatedCallAndSoundCard` — Renders interactive Material 3 UI for [ComposeSimulatedCallAndSoundCard] adhering to application design invariants
- `fun ComposePhoneCallCard`

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposeHeaderFooter.kt`
*173 lines*
**What it is:** Architecture definition and implementation for [ComposeHeaderFooter].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposeHeaderFooter.
**Key blocks:**
- `fun ComposeTopHeader` — Renders interactive Material 3 UI for [ComposeTopHeader] adhering to application design invariants
- `fun ComposeBottomBar` — Renders interactive Material 3 UI for [ComposeBottomBar] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposeMessageSection.kt`
*544 lines*
**What it is:** Architecture definition and implementation for [ComposeMessageSection].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposeMessageSection.
**Key blocks:**
- `fun ComposeRecipientsAndMessageCard` — Renders interactive Material 3 UI for [ComposeRecipientsAndMessageCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposePickerSheets.kt`
*1078 lines*
**What it is:** Architecture definition and implementation for [ComposePickerSheets].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposePickerSheets.
**Key blocks:**
- `fun isSamePhoneNumber` — Coordinates isSamePhoneNumber operations conforming to application constraints
- `fun ContactPoolPickerSheet` — Renders interactive Material 3 UI for [ContactPoolPickerSheet] adhering to application design invariants
- `fun WhatsAppContactsPickerSheet` — Renders interactive Material 3 UI for [WhatsAppContactsPickerSheet] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposeScheduleCard.kt`
*387 lines*
**What it is:** Architecture definition and implementation for [ComposeScheduleCard].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposeScheduleCard.
**Key blocks:**
- `fun ComposeScheduleOptionsCard` — Renders interactive Material 3 UI for [ComposeScheduleOptionsCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposeStatusSection.kt`
*662 lines*
**What it is:** Architecture definition and implementation for [ComposeStatusSection].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposeStatusSection.
**Key blocks:**
- `fun WhatsAppStatusComposerCard` — Renders Material 3 interface elements and handles user interaction for WhatsAppStatusComposerCard
- `fun WhatsAppBusinessStatusComposerCard` — Renders interactive Material 3 UI for [WhatsAppBusinessStatusComposerCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/compose/ComposeTaskScreen.kt`
*803 lines*
**What it is:** Architecture definition and implementation for [ComposeTaskScreen].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for ComposeTaskScreen.
**Key blocks:**
- `fun ComposeTaskScreen` — Renders interactive Material 3 UI for [ComposeTaskScreen] adhering to application design invariants

## `com.jmd.superapp.ui.screen.logs` — 2 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/logs/LogDetailScreen.kt`
*334 lines*
**What it is:** Architecture definition and implementation for [LogDetailScreen].
**What it does:** Detailed view presenting technical execution telemetry for an individual [ExecutionLog] item. Displays timestamp, recipient identifiers, channel details, dispatch payloads, raw error stack traces (if failed), and an instant one-tap retry dispatch trigger.
**Key blocks:**
- `fun LogDetailView` — Renders interactive Material 3 UI for [LogDetailView] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/logs/LogsScreen.kt`
*409 lines*
**What it is:** Architecture definition and implementation for [LogsScreen].
**What it does:** Primary coordinator screen managing:.
**Key blocks:**
- `fun LogsView` — Renders interactive Material 3 UI for [LogsView] adhering to application design invariants

## `com.jmd.superapp.ui.screen.permission` — 5 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/permission/AppPrivilegeScreen.kt`
*260 lines*
**What it is:** Architecture definition and implementation for [AppPrivilegeScreen].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for AppPrivilegeScreen.
**Key blocks:**
- `class PrivilegeCategory` — Encapsulates state, business contracts, and operations for [PrivilegeCategory]
- `class AppPrivilege` — Encapsulates state, business contracts, and operations for [AppPrivilege]

### `app/src/main/java/com/jmd/superapp/ui/screen/permission/PermissionDialogs.kt`
*215 lines*
**What it is:** Architecture definition and implementation for [PermissionDialogs].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for PermissionDialogs.
**Key blocks:**
- `fun AdbSetupModalDialog` — Renders interactive Material 3 UI for [AdbSetupModalDialog] adhering to application design invariants
- `fun A11yDisclosureDialog` — Renders interactive Material 3 UI for [A11yDisclosureDialog] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/permission/PermissionGuidanceCards.kt`
*289 lines*
**What it is:** Architecture definition and implementation for [PermissionGuidanceCards].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for PermissionGuidanceCards.
**Key blocks:**
- `fun OemAutostartCard` — Renders interactive Material 3 UI for [OemAutostartCard] adhering to application design invariants
- `fun SideloadedAppHelpCard` — Renders interactive Material 3 UI for [SideloadedAppHelpCard] adhering to application design invariants
- `fun AutoHealWatchdogCard` — Renders interactive Material 3 UI for [AutoHealWatchdogCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/permission/PermissionHeroCard.kt`
*301 lines*
**What it is:** Architecture definition and implementation for [PermissionHeroCard].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for PermissionHeroCard.
**Key blocks:**
- `fun PermissionHeroCard` — Renders interactive Material 3 UI for [PermissionHeroCard] adhering to application design invariants
- `fun SubsystemStatusChip` — Renders interactive Material 3 UI for [SubsystemStatusChip] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/permission/PermissionRowItem.kt`
*168 lines*
**What it is:** Architecture definition and implementation for [PermissionRowItem].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for PermissionRowItem.
**Key blocks:**
- `fun PermissionSectionHeader` — Renders interactive Material 3 UI for [PermissionSectionHeader] adhering to application design invariants
- `fun PermissionStatusActiveChip` — Renders interactive Material 3 UI for [PermissionStatusActiveChip] adhering to application design invariants
- `fun PermissionGrantActionButton` — Strictly adheres to 48dp minimum accessible touch target

## `com.jmd.superapp.ui.screen.settings` — 10 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsBackupRestoreCardComponents.kt`
*383 lines*
**What it is:** Architecture definition and implementation for [SettingsBackupRestoreCardComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsBackupRestoreCardComponents.
**Key blocks:**
- `fun SettingsBackupRestoreCard` — Renders interactive Material 3 UI for [SettingsBackupRestoreCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsBankingShieldCardComponents.kt`
*270 lines*
**What it is:** Architecture definition and implementation for [SettingsBankingShieldCardComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsBankingShieldCardComponents.
**Key blocks:**
- `fun SettingsBankingShieldCard` — Ensures all tasks (Calls, SMS, WhatsApp, Alarms) run uninterrupted 24/7 while strictly isolating from and never inspecting banking, payme…

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsChannelsCardComponents.kt`
*443 lines*
**What it is:** Architecture definition and implementation for [SettingsChannelsCardComponents].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsChannelsCardComponents.
**Key blocks:**
- `fun SettingsChannelsCard` — Renders interactive Material 3 UI for [SettingsChannelsCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsFormattingCardComponents.kt`
*443 lines*
**What it is:** Settings card for dynamic variable formats.
**What it does:** Exposes date, time, salutation and currency formatting for the tokens listed in [MessageVariables].
**Key blocks:**
- `fun SettingsFormattingCard` — Renders interactive Material 3 UI for [SettingsFormattingCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsPreferencesSection.kt`
*468 lines*
**What it is:** Architecture definition and implementation for [SettingsPreferencesSection].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsPreferencesSection.
**Key blocks:**
- `fun SettingsPreferencesCards` — Renders interactive Material 3 UI for [SettingsPreferencesCards] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsProtectedAppsSection.kt`
*261 lines*
**What it is:** Searchable picker of every launchable app, persisted as the protected banking list.
**What it does:** Loads installed launcher apps, lets the owner tick/untick them (with a one-tap suggested.
**Key blocks:**
- `class InstalledApp`
- `val BANKING_HINTS`
- `fun ProtectedAppsSection` — Loads installed launcher apps, lets the owner tick/untick them (with a one-tap suggested

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsScreen.kt`
*284 lines*
**What it is:** Architecture definition and implementation for [SettingsScreen].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsScreen.
**Key blocks:**
- `class SettingsSubdivision` — Encapsulates state, business contracts, and operations for [SettingsSubdivision]
- `fun SettingsView` — Renders interactive Material 3 UI for [SettingsView] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsScreenAssistCard.kt`
*343 lines*
**What it is:** Architecture definition and implementation for [SettingsScreenAssistCard].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsScreenAssistCard.
**Key blocks:**
- `fun SettingsScreenAssistCard` — Renders interactive Material 3 UI for [SettingsScreenAssistCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SettingsStatusIndicatorsGuideCard.kt`
*221 lines*
**What it is:** Architecture definition and implementation for [SettingsStatusIndicatorsGuideCard].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SettingsStatusIndicatorsGuideCard.
**Key blocks:**
- `fun SettingsStatusIndicatorsGuideCard` — Renders interactive Material 3 UI for [SettingsStatusIndicatorsGuideCard] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/screen/settings/SystemLogViewerScreen.kt`
*1111 lines*
**What it is:** Architecture definition and implementation for [SystemLogViewerScreen].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SystemLogViewerScreen.
**Key blocks:**
- `class ParsedSystemLogItem` — Encapsulates state, business contracts, and operations for [ParsedSystemLogItem]
- `fun SystemLogViewerView` — Renders interactive Material 3 UI for [SystemLogViewerView] adhering to application design invariants
- `fun ActionLogCard` — Renders interactive Material 3 UI for [ActionLogCard] adhering to application design invariants
- `fun LedgerLogCard` — Renders interactive Material 3 UI for [LedgerLogCard] adhering to application design invariants
- `fun ErrorLogCard` — Renders interactive Material 3 UI for [ErrorLogCard] adhering to application design invariants
- `fun RawLogCard` — Renders interactive Material 3 UI for [RawLogCard] adhering to application design invariants
- `fun parseCsvLine` — Executes [parseCsvLine] logic in accordance with system and security invariants

## `com.jmd.superapp.ui.screen.tasks` — 2 file(s)

### `app/src/main/java/com/jmd/superapp/ui/screen/tasks/TaskCardComponents.kt`
*424 lines*
**What it is:** Architecture definition and implementation for [TaskCardComponents].
**What it does:** Interactive presentation card displaying an individual scheduled task with channel badge, schedule timestamp,.
**Key blocks:**
- `fun ComfortableSwitch`
- `fun ComfortableIconButton`
- `fun ComfortableActionButton`
- `fun TaskCard` — Renders a comfortable, highly readable, structured card layout with optimal typography and touch targets

### `app/src/main/java/com/jmd/superapp/ui/screen/tasks/TasksScreen.kt`
*294 lines*
**What it is:** Architecture definition and implementation for [TasksScreen].
**What it does:** Primary coordinator screen managing scheduled task viewing, searching, filtering, and creation.
**Key blocks:**
- `fun TasksListScreen` — Renders interactive Material 3 UI for [TasksListScreen] with single-column layout of compact grid cards

## `com.jmd.superapp.ui.theme` — 2 file(s)

### `app/src/main/java/com/jmd/superapp/ui/theme/MaterialIconMapper.kt`
*181 lines*
**What it is:** Architecture definition and implementation for [MaterialIconMapper].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for MaterialIconMapper.
**Key blocks:**
- `object MaterialIconMapper` — Encapsulates state, business contracts, and operations for [MaterialIconMapper]
- `fun MaterialIconBadge` — Renders interactive Material 3 UI for [MaterialIconBadge] adhering to application design invariants

### `app/src/main/java/com/jmd/superapp/ui/theme/Theme.kt`
*612 lines*
**What it is:** Architecture definition and implementation for [Theme].
**What it does:** Defines the Material 3 Expressive Orange Design System:.
**Key blocks:**
- `val AppDesignTokens`
- `object OrangeBrandPalette` — Provides domain logic and lifecycle management for OrangeBrandPalette
- `object Material3DesignTokens` — Encapsulates state, business contracts, and operations for [Material3DesignTokens]
- `val SuperTypography`
- `val DarkColorScheme`
- `val LightColorScheme`
- `fun SuperTheme` — Supports SYSTEM (default), LIGHT, DARK, and Android 12+ Wallpaper Dynamic Color
- `fun isAppDarkTheme` — Eliminates theme-mismatch contrast bugs across all screens and custom components
- `object AppSemanticColors` — Encapsulates state, business contracts, and operations for [AppSemanticColors]
- `object AppLayoutGrid` — Formulates standard responsive spacing, corner radii, elevation, touch targets, and adaptive layout boundaries across the entire application

## `com.jmd.superapp.ui.widget` — 1 file(s)

### `app/src/main/java/com/jmd/superapp/ui/widget/SuperAppWidget.kt`
*150 lines*
**What it is:** Architecture definition and implementation for [SuperAppWidget].
**What it does:** Provides core logic, component interfaces, and subsystem bindings for SuperAppWidget.
**Key blocks:**
- `class SuperAppWidget` — Renders on-device service status and provides 1-tap fast access to scheduled tasks
- `class SuperAppWidgetReceiver` — Encapsulates state, business contracts, and operations for [SuperAppWidgetReceiver]

## `com.jmd.superapp.whatsapp` — 12 file(s)

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppCallEngine.kt`
*911 lines*
**What it is:** Architecture definition and implementation for [WhatsAppCallEngine].
**What it does:** WhatsApp Voice Call engine inspecting the accessibility hierarchy to locate and click the voice call action in personal and business WhatsApp conversations, confirm calling dialogs, and verify VoIP call screen transitions.
**Key blocks:**
- `class WhatsAppCallEngine` — Provides domain logic and lifecycle management for WhatsAppCallEngine

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppChatScanner.kt`
*1364 lines*
**What it is:** Architecture definition and implementation for [WhatsAppChatScanner].
**What it does:** Discovery engine for harvesting WhatsApp direct chats and groups via accessibility node traversal, scrolling, and pool enrichment.
**Key blocks:**
- `class ScanTarget` — Provides domain logic and lifecycle management for ScanTarget
- `class RowKind` — Provides domain logic and lifecycle management for ScanTarget
- `class WhatsAppChatScanner` — Encapsulates state, business contracts, and operations for [WhatsAppChatScanner]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppDialogHandler.kt`
*229 lines*
**What it is:** Architecture definition and implementation for [WhatsAppDialogHandler].
**What it does:** WhatsApp dialog and confirmation handler. Gracefully handles in-app modal prompts, "Continue" or "OK" disclaimers, and network/session popups strictly scoped to WhatsApp packages.
**Key blocks:**
- `object WhatsAppDialogHandler` — Encapsulates state, business contracts, and operations for [WhatsAppDialogHandler]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppDirectSendEngine.kt`
*583 lines*
**What it is:** Architecture definition and implementation for [WhatsAppDirectSendEngine].
**What it does:** Direct message dispatcher executing accessibility tree inspection, input field text population, clipboard synchronization, and send button clicks across WhatsApp and WhatsApp Business conversations.
**Key blocks:**
- `class WhatsAppDirectSendEngine` — Encapsulates state, business contracts, and operations for [WhatsAppDirectSendEngine]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppDirectVoipDispatcher.kt`
*141 lines*
**What it is:** Architecture definition and implementation for [WhatsAppDirectVoipDispatcher].
**What it does:** Queries the device ContactsProvider for synced WhatsApp and WhatsApp Business VoIP contact data URIs (`vnd.android.cursor.item/vnd.com.whatsapp.voip.call` and `vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call`) and initiates direct VoIP calls via native Android intents without requiring UI button scraping.
**Key blocks:**
- `object WhatsAppDirectVoipDispatcher` — Encapsulates state, business contracts, and operations for [WhatsAppDirectVoipDispatcher]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppGroupEngine.kt`
*524 lines*
**What it is:** Architecture definition and implementation for [WhatsAppGroupEngine].
**What it does:** Scheduled group messaging dispatcher. Locates WhatsApp groups via search query, opens the group chat, verifies conversation context, and invokes text transmission.
**Key blocks:**
- `class WhatsAppGroupEngine` — Encapsulates state, business contracts, and operations for [WhatsAppGroupEngine]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppIntentDispatcher.kt`
*294 lines*
**What it is:** Architecture definition and implementation for [WhatsAppIntentDispatcher].
**What it does:** Formulates and fires explicit deep link intents (`api.whatsapp.com/send?phone=...` and package-targeted actions) launching WhatsApp or WhatsApp Business chats, groups, and status updates while attaching security metadata.
**Key blocks:**
- `object WhatsAppIntentDispatcher` — Encapsulates state, business contracts, and operations for [WhatsAppIntentDispatcher]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsAppStatusEngine.kt`
*800 lines*
**What it is:** Architecture definition and implementation for [WhatsAppStatusEngine].
**What it does:** Dedicated status story publishing engine dispatching text and photo/video story posting for both personal WhatsApp and WhatsApp Business. Navigates to Updates tab, clicks pencil/camera FAB, enters story caption/text, confirms My Status audience, and clicks publish.
**Key blocks:**
- `class WhatsAppStatusEngine` — Encapsulates state, business contracts, and operations for [WhatsAppStatusEngine]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsappAccessibilityNodeUtils.kt`
*655 lines*
**What it is:** Architecture definition and implementation for [WhatsappAccessibilityNodeUtils].
**What it does:** Provides resilient accessibility node BFS search algorithms, boundary inspection, gesture dispatch, physical coordinate tapping, and text-entry routines for UI actions.
**Key blocks:**
- `object AccessibilityNodeUtils` — Encapsulates state, business contracts, and operations for [AccessibilityNodeUtils]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsappKeyguardAssistEngine.kt`
*907 lines*
**What it is:** Architecture definition and implementation for [WhatsappKeyguardAssistEngine].
**What it does:** Provides autonomous lockscreen unlocking (swipe gestures, accessibility PIN entry, coordinate tapping) and security relocking post-task via [android.app.admin.DevicePolicyManager].
**Key blocks:**
- `class KeyguardAssistEngine` — Handles detection of active lockscreens, executing upward swipe gestures to invoke the PIN bouncer, sequentially entering PIN digits via …
- `typealias KeyguardUnlockEngine` — Handles gesture completion or cancellation dispatch for onCancelled

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsappPopupShield.kt`
*468 lines*
**What it is:** Architecture definition and implementation for [WhatsappPopupShield].
**What it does:** Scans window hierarchies to detect and dismiss intrusive blocking overlays, system permission dialogs, incoming call caller ID overlays (e.g. Truecaller), and notification shades before starting UI actions.
**Key blocks:**
- `object PopupShield` — Encapsulates state, business contracts, and operations for [PopupShield]

### `app/src/main/java/com/jmd/superapp/whatsapp/WhatsappVoiceNoteProtectionGuard.kt`
*226 lines*
**What it is:** Architecture definition and implementation for [WhatsappVoiceNoteProtectionGuard].
**What it does:** Prevents accidental voice note recording triggers when attempting to click the WhatsApp Send button. Detects whether the button at the send position has transitioned from a Voice Note mic icon to a Send paper airplane.
**Key blocks:**
- `object VoiceNoteProtectionGuard` — Encapsulates state, business contracts, and operations for [VoiceNoteProtectionGuard]

