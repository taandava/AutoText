# AUTOTEXT — COMPLETE PROJECT AUDIT, REPAIR & PRODUCTIONIZATION

**Role:** Act as a senior Android engineer, Kotlin specialist, application architect, QA engineer, database engineer, and security auditor. Inspect, repair, test, and productionize the EXISTING AutoText project. Do not merely generate recommendations or rewrite documentation.

## 1. PROJECT BASELINE

- **Application:** AutoText
- **Package:** `com.jmd.autotext`
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **Minimum SDK:** API 26 (Android 8.0)
- **Target SDK:** Verify the latest stable SDK supported by the installed toolchain and the current Google Play requirements. API 36 is the minimum target for new apps and updates submitted to Google Play from August 31, 2026; use API 37 only if the required platform and toolchain are stable and compatible.
- **Database:** SQLite using `SQLiteOpenHelper`, versioned migrations, transactions, indexes, and conflict handling.
- **Concurrency:** Kotlin Coroutines, background IO operations, main-thread UI updates, and appropriate concurrency controls.
- **Scheduling:** `AlarmManager`, `WorkManager`, and foreground services only where appropriate and permitted.
- **Visual direction:** Google Material Design 3 (Material You) official design system with dynamic color theming, M3 tonal elevation & depth shadows, official pill-shaped components, and high-contrast OLED surface containers.

Treat this specification as the intended design, NOT proof that the listed files or features already exist or work.

## 2. MANDATORY AUDIT BEFORE CHANGES

Inspect the entire repository before editing anything, including:

- Gradle files, version catalog, dependencies, Android manifest, permissions, resources, ProGuard/R8 rules, and signing configuration.
- All Kotlin source files, Compose screens, repositories, models, receivers, services, database code, schedulers, accessibility engines, and utilities.
- Existing tests, build errors, warnings, crash logs, duplicate implementations, incomplete functions, and disconnected UI actions.
- Database tables, migrations, foreign keys, indexes, transactions, and compatibility with existing user data.
- Feature flows from the UI through repositories and schedulers to actual execution and result logging.

Build an internal dependency map and identify the root cause of every material problem before making changes.

**Do not assume that a file exists because it is mentioned in this specification. Do not invent a replacement architecture without examining the actual project.**

Produce a concise defect list, but continue directly into implementation. Do not stop after an audit or wait for approval for routine engineering decisions.

## 3. NON-NEGOTIABLE DEVELOPMENT RULES

1. Preserve all existing working features and user data. Make changes incrementally and avoid unnecessary rewrites.
2. Fix root causes, not just compiler errors or visible symptoms.
3. No fake implementations, placeholder screens, TODO-only functions, hardcoded success responses, simulated send confirmations, or buttons that do nothing.
4. Every UI control must connect to the correct application logic and report its actual result.
5. Use actual installed SDKs, current official Android documentation, and compatible dependencies. Do not invent APIs, permissions, or library versions.
6. Never mark a message as sent merely because an alarm fired, a worker started, an intent opened, or a button was clicked.
7. Never silently bypass the Android lock screen, collect or enter a device PIN, circumvent system security controls, silently enable accessibility, or suppress legally required foreground-service notifications.
8. Do not disable existing functionality merely to make the build pass. If a feature cannot work because of platform restrictions, document the exact limitation and implement the safest usable alternative.
9. Do not expose signing passwords, API keys, tokens, private keys, or user data in logs, source code, reports, or the final ZIP.
10. Keep business logic outside Compose screens. Use repositories, domain models, and dedicated execution components with clear responsibilities.

## 4. REQUIRED FEATURE IMPLEMENTATION

Audit, repair, and integrate every applicable feature below into the existing project.

### A. Scheduling and task execution

Support one-time tasks, daily/weekly/monthly recurrence, custom intervals where supported, reminders, editable tasks, cancellation, pausing, resuming, and multi-step contact sequences.

Verify:

- Correct next-execution calculation, including time zones, daylight-saving transitions where applicable, month-end dates, leap years, and missed executions.
- Unique task identity, atomic state transitions, duplicate-execution prevention, and concurrency-safe scheduling.
- Rescheduling after reboot, application updates where applicable, time changes, time-zone changes, and restored permissions.
- Recovery of pending tasks from SQLite without duplicating alarms.
- Correct handling of `PendingIntent` identity and cancellation.
- Exact-alarm permission checks, permission revocation, denied permissions, Doze mode, battery restrictions, and Android background-execution limits.
- Correct use of `WorkManager` for deferrable work and `AlarmManager` for genuinely time-sensitive user actions.
- A visible, user-understandable fallback when exact execution is unavailable.

Do not describe `WorkManager` as an exact-time scheduler or use a 15-minute watchdog as a guaranteed keep-alive mechanism.

### B. Recipients, imports and contact ingestion

Implement and verify:

- Recipient creation, editing, deletion, grouping, searching, and selection.
- CSV and Excel imports/exports with validation, encoding handling, useful error reports, and duplicate detection.
- Phone-number normalization, country-code handling, Indian mobile-number validation where appropriate, and preservation of international numbers.
- Contact, call-log, and SMS ingestion only through applicable Android permissions and supported APIs.
- Permission-denied, revoked-permission, missing-data, and malformed-file handling.
- Independent processing state for each recipient and each communication channel.

Do not silently collect or expose contacts, call logs, or message content without the required permission and user disclosure.

### C. SMS engine

Audit and repair `SmsSender.kt`, `SmsQuotaManager.kt`, subscription selection, receivers, and result handling.

Required behavior:

- Single-part and multipart SMS handling with correct segment calculation.
- Dual-SIM selection using the appropriate subscription APIs.
- Correct runtime permission handling and subscription availability checks.
- Unique sent/delivery `PendingIntent` instances and segment-level result tracking.
- Accurate handling of sending failures, invalid numbers, missing SIMs, service errors, and device restrictions.
- Explicit handling of `ask_before_send`, retry policy, cooldowns, and cancellation.
- Structured execution logs and accurate task-state updates.
- Appropriate safeguards against duplicate sends.

Separate **queued, attempted, submitted, delivered, failed, and unknown** outcomes. A delivery receipt is not always available, and a carrier submission result does not prove delivery.

Implement SMS quotas as configurable safeguards based on verified current operator requirements and applicable regulations. Calculate multipart segments correctly. Do not claim that one universal, hard-coded 100-SMS-per-day limit guarantees TRAI compliance. Clearly distinguish verified legal or carrier limits from optional application-level limits.

### D. WhatsApp and WhatsApp Business

Audit and repair direct messaging through supported intents/deep links and existing, explicitly enabled accessibility workflows.

Verify:

- Dynamic resolution of installed WhatsApp clients via `WhatsAppPackageHelper`: automatically detects standard WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`).
- Full support for WhatsApp Business contacts: queries both standard phonebook tables and WhatsApp Business account profiles (`vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile`).
- Automatic contact avatar extraction: persists photo URIs (`content://com.android.contacts/contacts/{id}/photo`) in SQLite `dynamic_contact_pool.photo_uri`, rendering native contact avatars across all contact pickers and campaign cards.
- Dual-label contact rendering: displays full contact display name in bold accompanied by formatted mobile number in distinct monospace styling.
- Correct recipient resolution, phone-number formatting, message encoding, and launch-intent handling.
- Composer detection, message-text verification, send-button detection, and safe click handling.
- Protection against accidental microphone/voice-note activation.
- Recovery from dialogs, permission prompts, keyboards, changed layouts, missing applications, and unexpected screens.
- Explicit results and failure reasons for every recipient.
- User-configurable delays, cancellation, retry handling, and duplicate-send protection.

### E. WhatsApp groups and Status

Audit and repair the group and Status engines rather than merely wiring their buttons to existing launch intents.

**Groups:**
- Dual Discovery Channels:
  1. Accessibility Deep Scanner (`WhatsAppChatScanner`): Automatically navigates to the active WhatsApp package, engages the Groups filter tab (`conversations_filter_debug_view_id_groups`), harvests visible group titles, and scrolls to discover all existing groups.
  2. In-App Interactive Tap Picker (`MANUAL_PICK_CHAT`): Allows users to open WhatsApp directly, tap any group or chat item, and automatically captures the group name with instant return to AutoText.
- Group candidate resolution, ambiguous or duplicate names, correct chat opening, recipient/group identity, message composition, sending workflow, and failure recovery.

**Status:** Verify support for the intended text/media workflow, media URI permissions, supported media types, caption handling where applicable, publication steps, and outcome reporting.

Use supported APIs, intents, and explicitly enabled, deterministic accessibility workflows where appropriate. Respect third-party application behavior, Android security restrictions, and applicable platform policies.

WhatsApp interfaces can change independently of AutoText. Do not promise universal or invisible execution. When actual publication or sending cannot be verified, report the result as unverified rather than successful.

### F. Accessibility automation and health monitoring

Audit:

- `AutoTextAccessibilityService`
- `AccessibilityHealthManager`
- `AccessibilityNodeUtils`
- `KeyguardUnlockEngine`
- `VoiceNoteProtectionGuard`
- `WhatsAppDirectSendEngine`
- `WhatsAppGroupEngine`
- `WhatsAppStatusEngine`
- Dialog handling, intent dispatching, and launcher activities.

Implement robust lifecycle management, null-safe node traversal, correct window selection, visibility and bounds checks, stale-node handling, gesture safety, timeouts, cancellation, and meaningful diagnostics.

Use accessibility only for clearly disclosed, user-configured workflows that comply with Android and Google Play policy.

The health manager may detect a disconnected or disabled service, record diagnostics, and guide the user to the appropriate Settings screen. It must not attempt to silently re-enable the service or bypass system controls.

If the device is locked and the intended action requires interactive access, preserve the task and notify the user, or report a retryable failure. Never automate PIN entry or bypass the lock screen.

Do not add a silent watchdog simply to keep a disabled accessibility service running.

### G. Auto-reply, forwarding and phone calls

Verify all supported triggers for:

- Incoming SMS.
- Missed calls and ended calls, where supported by the applicable Android version and permissions.
- WhatsApp notification replies, when a suitable notification and supported reply action are available.
- Incoming-message and call-event forwarding.
- Contact/non-contact filters, keyword matching, cooldowns, maximum replies per day, and rule enable/disable controls.

Cooldowns must consider the latest applicable reply across all dates, while daily counters must use the correctly defined local calendar date.

Prevent duplicate receiver events, reply loops, repeated forwarding, and concurrent rule execution. Normalize numbers before comparing them. Expand dynamic variables such as `{name}`, `{time}`, and `{date}` safely.

Distinguish actual phone-call functions from the app's local fake-call simulation. Never represent a simulated call as a real carrier call or claim call functionality that Android does not expose.

### H. Daily campaigns and contact sequences

Verify:

- Separate SMS and WhatsApp campaign configurations.
- Dynamic contact pools, independent channel histories, daily limits, priorities, retry states, and batch intervals.
- Correct daily reset using a defined local date.
- Fair recipient selection, no accidental repeats, persistence across restarts, and safe pause/resume/stop behavior.
- Per-recipient execution records and meaningful campaign statistics.
- Multi-step sequences, delay calculations, template expansion, and recovery after interruption.

Use database transactions and atomic task claims where necessary to prevent multiple workers from sending the same item.

### I. Templates, database, backups and logs

Inspect the real schema and implement safe, versioned SQLite migrations.

Use:

- Transactions for multi-table operations.
- Appropriate primary keys, foreign keys, indexes, and uniqueness constraints.
- Parameterized SQL and consistent error handling.
- Background database operations using coroutines.
- Safe import validation and atomic restore with rollback.
- JSON backup/export and restore without corrupting existing data.
- Structured logs containing timestamps, channel, task ID, recipient reference, status, and sanitized failure details.
- Thread-safe rotating file logs without leaking full message content unnecessarily.

SQLiteOpenHelper does not encrypt a database automatically. Verify whether actual database encryption exists before describing `autotext.db` as encrypted. Use a properly implemented encryption solution if required and compatible with the project; otherwise document the limitation accurately.

### J. UI, permissions, and performance optimization

Preserve the complete feature set while making the mobile UI consistent, responsive, and production quality.

Use Jetpack Compose and Google Material 3 (Material You):
- **Dynamic Color Theming**: Automatically utilizes `dynamicDarkColorScheme(context)` on Android 12+ (API 31+) while preserving the curated high-contrast dark OLED palette on earlier releases.
- **Official Material 3 Components**: Standardized on `Material3Card` / `Material3CardElevated` with genuine M3 tonal elevation & depth shadows, `Material3Button` and `Material3Badge` with official pill shapes (`ShapePill`), and `Material3InputField` / `Material3SearchField`.
- **Performance & Stutter Elimination**:
  - Background telephony and contact ingestion throttled to at most once every 6 hours via persistent `lastContactIngestionTime` in `PreferenceManager` (strictly avoided on every `onResume`).
  - All database queries, telephony quota lookups, and campaign configuration retrievals offloaded to `Dispatchers.IO`.
  - LazyColumns backed by stable unique keys (`items(list, key = { it.id })`) to eliminate redundant recomposition passes.

Keep all user-facing controls connected to actual functionality. No decorative controls presented as operational features.

Request permissions contextually, explain why they are needed, handle denial and permanent denial, and provide recovery instructions. Do not repeatedly prompt or trap the user in permission screens.

## 5. ARCHITECTURE AND DATA INTEGRITY

Keep the codebase modular and aligned with the existing package structure:

- `data/` and `data/repository/`: SQLite, persistence, CRUD, backup, configuration, and query logic.
- `model/`: typed domain models and explicit status enums.
- `scheduler/`: alarms, workers, reboot recovery, and time-change handling.
- `sender/`: SMS and communication-channel dispatchers.
- `service/` and `service/accessibility/`: Android event handling and controlled accessibility workflows.
- `scanner/`: permission-aware contact and telephony ingestion.
- `ui/`: Compose screens, Material 3 design tokens (`Material3DesignTokens.kt`), and standardized M3 components.
- `util/`: phone normalization, quotas, permissions, logging, and common utilities.

Retain existing filenames and public interfaces where practical. Refactor only when needed to correct defects or eliminate demonstrated duplication.

Review `tasks`, `contact_sequences`, `sequence_steps`, `dynamic_pool`, `daily_campaign_config`, `auto_reply_rules`, `auto_reply_history`, `forward_rules`, `templates`, `recipients`, `whatsapp_groups`, and `execution_logs`, together with any additional tables actually present.

Every state change must be persisted consistently. If the database schema changes, implement an upgrade path from existing user databases; never fix a migration problem by deleting user data or silently recreating the database.

## 6. TESTING AND ACCEPTANCE CRITERIA

Create or repair automated tests for the available business logic, repositories, recurrence calculations, quotas, number normalization, auto-reply cooldowns, campaign selection, database migrations, and duplicate-execution prevention.

Run, where supported by the repository:

`./gradlew clean`

`./gradlew testDebugUnitTest`

`./gradlew lintDebug`

`./gradlew assembleDebug`

Also run relevant instrumentation tests when an emulator/device and required permissions are available.

Fix all compilation errors and material regressions introduced by your changes. Review lint findings and distinguish genuine defects from justified warnings.

Verify the application manifest, Gradle configuration, package ID, required permissions, Compose navigation, database initialization, task creation, task editing, scheduling, cancellation, boot recovery, SMS result tracking, campaign execution, and accessibility failure handling.

Test realistic failure paths, not only successful paths: denied permissions, no SIM, app missing, database errors, reboot, duplicate triggers, locked device, disabled accessibility, and WhatsApp UI changes.

Do not claim that WhatsApp delivery, Status publication, carrier delivery, reboot recovery, or physical-device behavior is verified unless the relevant behavior was actually tested. Clearly distinguish unit-tested, build-verified, emulator-tested, and physical-device-tested functionality.

## 7. RELEASE AND FINAL DELIVERABLES

Complete the work in the current repository. Do not stop after producing recommendations or an implementation plan.

Deliver:

1. **Fixed source project:** All necessary source and resource changes, with migrations and tests.
2. **Buildable project ZIP:** A clean Android Studio-importable archive containing all necessary project files.
3. **Debug APK:** Generate `app/build/outputs/apk/debug/app-debug.apk` if the build succeeds.
4. **Release APK:** Produce a signed release APK only if valid signing credentials are available and signing succeeds. Never invent credentials or overwrite the user's existing signing configuration.
5. **Audit report:** Summarize root causes, repairs, tests performed, build results, remaining limitations, and features that could not be verified.
6. **File-level change summary:** List important modified, added, or removed files and explain why.
7. **Build instructions:** Provide the exact commands to build, test, install, and open the project.

Exclude unnecessary build caches, machine-specific `local.properties`, private signing keys, passwords, and other secrets from the source ZIP. Do not remove essential Gradle wrapper files or project configuration. Distribute any required private signing materials separately and securely.

Verify that the ZIP opens, the project structure is intact, and the APK exists before reporting either as generated. Preserve a recoverable copy of the original project before making destructive changes.

## FINAL RULE

**A feature is not complete because a class, screen, button, receiver, or intent exists. It is complete only when its end-to-end behavior is implemented, its result is accurately recorded, its failure paths are handled, and the available tests pass.**

Prioritize a stable, buildable, testable application over claims of having every feature. Never conceal a limitation, fabricate a test result, mark an unverified action successful, or sacrifice working functionality to make the project appear complete.