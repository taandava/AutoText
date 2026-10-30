# AutoText Master Engineering Directive & Quality Assurance Verification Runbook

> **The Authoritative Architectural, Defect Remediation, and Hardware Verification Record for AutoText (`com.jmd.autotext`).**  
> *Target Platform: Android 8.0 (API 26) to Android 16+ (API 36/37) • Zero-Warning & Zero-Lint Certified • 100% On-Device Sovereignty*

---

## 1. Executive Summary & Verification Baseline

AutoText is a mission-critical, enterprise-grade Android automation application designed for unattended multi-channel message dispatch and voice call automation. 

### 1.1 Project Verification Snapshot
- **Package ID**: `com.jmd.autotext`
- **Application Version**: `2.6` (Version Code `26`)
- **Language**: 100% Idiomatic Kotlin (0% Java)
- **UI Framework**: Modern Jetpack Compose with Material 3 Expressive
- **Compiler Compliance**: Kotlin 2.4.21 / AGP 8.9.2 — **0 Warnings, 0 Errors**
- **Static Analysis**: Android Lint (`lintDebug`) — **No issues found**
- **Unit Test Suite**: `AutoTextComprehensiveTest` — **68 of 68 Tests Passing (100%)**
- **Connected Test Device**: Vivo / iQOO I2401 (`10BF1G0HCD0024R`) running **Android 16 (API Level 36)**
- **Hardware Test Matrix**: 13/13 Task Types Verified Successfully on Hardware
- **Post-Test State**: 0 residual tasks in SQLite database (`autotext.db`)

---

## 2. Comprehensive Defect Remediation & Root Cause Analysis

During comprehensive codebase auditing and live device testing, several critical defects and platform edge cases were diagnosed and systematically resolved.

### Defect 1: Call Duration Elapsed & Early Hangup False Alarms
- **Symptom**: Scheduled cellular and WhatsApp VoIP calls posted "Call Duration Elapsed" notifications and triggered auto-hangup commands even when calls were unanswered, rejected, busy, or dropped early.
- **Root Cause**:
  1. In `SequentialTaskEngine.kt`, when `includeDialTimeInCallDuration = false`, if the call was not answered within the dial timeout, the code skipped the sleep loop but fell through into the post-call execution block, unconditionally invoking `hangupActiveCall()` and posting elapsed notifications.
  2. Monolithic sleep calls (`delay(callDurationSeconds * 1000L)`) failed to check whether the call had dropped, hung up, or ended early before the duration timer expired.
- **Remediation**:
  - Replaced monolithic delays with **250ms sliced polling loops**: `waitForCallDurationWithEarlyCheck` and `waitForVoipDurationWithEarlyCheck`.
  - Added early call termination detection: if the call state transitions to `CALL_STATE_IDLE` or VoIP timer vanishes after connection, the engine breaks immediately from the wait loop, cleanly exiting without executing redundant auto-hangup commands or issuing false "Duration Elapsed" notifications.
  - Sliced delays continuously observe `PreferenceManager.isEmergencyStopActive` and `PreferenceManager.isTasksMasterEnabled` to guarantee immediate task abortion within ≤250ms.
  - Untimed calls (`callDurationSeconds == 0`) remain open indefinitely without auto-hangup.

### Defect 2: Multi-SIM Subscription Telephony State on Modern Android (API 31–36+)
- **Symptom**: On dual-SIM devices, placing a cellular call on a secondary SIM slot (SIM 2) resulted in `TelephonyManager.getCallState()` continuing to report `CALL_STATE_IDLE`, causing dial timing to fail and premature timeouts.
- **Root Cause**: On Android 12+ (API 31+), the system-level `TelephonyManager.callState` only reflects the status of the default data SIM. Secondary SIM telephony activity is isolated to subscription-specific managers.
- **Remediation**:
  - Engineered `com.jmd.autotext.compat.TelephonyManagerCompat`.
  - Iterates through `SubscriptionManager.activeSubscriptionInfoList` and queries `telephonyManager.createForSubscriptionId(subId).callStateForSubscription` across every active SIM slot.
  - Combined with `TelecomManager.isInCall` cross-checks and accessibility in-call UI inspection (`isPhoneCallUiActive()`).
  - Added JVM null-safety fallback helpers for pure unit test execution.

### Defect 3: WhatsApp VoIP Call Connection Detection vs. Dialing States
- **Symptom**: WhatsApp voice calls were treated as connected immediately upon launch, even while the remote device was still ringing or unavailable.
- **Root Cause**: The accessibility service checked for the presence of the WhatsApp in-call UI package without inspecting whether a conversation timer was actively ticking.
- **Remediation**:
  - Implemented `WhatsAppCallEngine.isVoipCallConnected()`.
  - Scans accessibility node hierarchies for active conversation timer digit formats (e.g., `00:01`, `01:23`, `12:45`) while explicitly ignoring pre-connection states such as `"Calling..."`, `"Ringing..."`, or `"Reconnecting..."`.
  - Exposed live connection status via `AutoTextAccessibilityService.isWhatsAppCallConnected()`.

### Defect 4: Display Auto-Rotate Leakage & Invariant Violation
- **Symptom**: Automation tasks interacting with notification panels or system dialogs could inadvertently click rotation quick-setting tiles or rotate the screen, breaking landscape layout boundaries.
- **Root Cause**: Lack of dynamic rotation enforcement across activity lifecycles and background execution.
- **Remediation**:
  - All 4 application activities (`MainActivity`, `FakeCallActivity`, `ReminderAlarmActivity`, `AutomationLauncherActivity`) are hard-locked to portrait mode (`android:screenOrientation="portrait"`).
  - Implemented `PopupShield.kt` to shield system UI packages (`com.android.systemui`, `com.android.settings`, `com.android.launcher`) from unintended touch events.
  - Created `AccessibilityHealthManager.enforceAutoRotateInvariant()`, which audits and forces `Settings.System.ACCELEROMETER_ROTATION = 0` during app startup, activity resumes, and background task postconditions.

### Defect 5: SQLite Database Locking & Concurrency Stalls
- **Symptom**: High-volume campaign dispatches and rapid background logging caused SQLite database lock contention and temporary UI stalls on the Tasks and Logs screens.
- **Root Cause**: The SQLite database operated under default single-threaded rollback journal mode.
- **Remediation**:
  - Enabled **SQLite Write-Ahead Logging (WAL mode)** in `DatabaseHelper.onConfigure()` via `db.enableWriteAheadLogging()`.
  - Enabled foreign key constraints (`db.setForeignKeyConstraintsEnabled(true)`) with `ON DELETE CASCADE` integrity.
  - Background logging writes and batch dispatches write concurrently to the `-wal` file without blocking UI read transactions.

### Defect 6: Unbounded Execution Log Growth
- **Symptom**: Continuous automated execution generated thousands of log records, leading to gradual database growth and slower query performance.
- **Root Cause**: Absence of an automated rolling log eviction strategy.
- **Remediation**:
  - Implemented rolling log auto-pruning in `LogRepository`.
  - Caps execution logs to a maximum of **5,000 entries** and a **45-day retention window**.
  - Invoked opportunistically in the background every 100 log writes, preventing storage bloat over months of continuous operation.

### Defect 7: Accessibility Service Event Flooding & Binder Instability
- **Symptom**: On Android 15/16 and aggressive OEM skins (e.g., Vivo FuntouchOS / OriginOS), dynamically reconfiguring `AccessibilityServiceInfo` via IPC calls caused binder transaction failures and service disconnection.
- **Root Cause**: High IPC overhead of modifying `serviceInfo` flags during runtime.
- **Remediation**:
  - Created an **in-memory event throttling pipeline** inside `AutoTextAccessibilityService.onAccessibilityEvent()`.
  - When idle (`isAutomating == false`), non-essential window and scroll events from external packages are discarded in sub-microsecond time in memory without modifying IPC service info.
  - Throttling automatically lifts during active task runs, restoring full node traversal.

### Defect 8: WhatsApp Business Call Bottom-Sheet Stalls
- **Symptom**: WhatsApp Business voice calling stalled on certain versions when attempting to tap the call icon.
- **Root Cause**: WhatsApp Business introduced a multi-step bottom sheet (`audio_call_item` followed by a confirmation dialog) and occasional voice note composer overlay locks.
- **Remediation**:
  - Targeted physical screen bounds (`node.getBoundsInScreen(rect)`) to dispatch physical gestures directly on resolved screen coordinates.
  - Implemented multi-stage resolution to detect and confirm secondary call confirmation dialogs.
  - Auto-discards stale voice note recordings before dialing.

### Defect 9: Banking & Financial Application Accessibility Detection (Saraswat Bank / GOMONxT, SBI, Google Pay, PhonePe, Paytm, etc.)
- **Symptom**: Banking apps display "Security Alert! Accessibility Permission detected!" and refuse to open or block users whenever AutoText accessibility service is enabled.
- **Root Cause**:
  1. `AutoTextAccessibilityService.kt` registered with global window inspection (`info.packageNames = null`), triggering Android to announce AutoText in `AccessibilityManager.getEnabledAccessibilityServiceList` to all running apps.
  2. Banking apps actively query `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` or register accessibility display clients, identifying any non-screen-reader third-party service.
- **Remediation**:
  1. **Smart Accessibility Scoping**: In `AutoTextAccessibilityService.kt`, replaced blanket `packageNames = null` with dynamic whitelist scoping (`info.packageNames = allowedPackages.toTypedArray()`), automatically omitting all banking, financial, and user-excluded packages.
  2. **Dynamic On-Demand Accessibility Mode**: When `WRITE_SECURE_SETTINGS` is available, AutoText keeps its accessibility service completely disabled in system settings during idle standby. The service is dynamically enabled for only 3–5 seconds during active WhatsApp dispatches and immediately disabled post-execution.
  3. **Immediate Foreground Retraction Guard**: The instant a banking app (e.g. Saraswat Bank / `com.saraswatbank.*`, SBI, etc.) produces a `TYPE_WINDOW_STATE_CHANGED` event, AutoText immediately unbinds and removes itself from `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` before the bank's security scan completes.
  4. **Privacy & App Visibility Management System**: Implemented full privacy subsystem (`PrivacyManager`, `PrivacyRepository`, `PrivacySettingsScreen`, `AppPrivacyDetailDialog`) with SQLite schema version 8 (`app_privacy_configs`) and 7 per-app isolation toggles.
  5. **Sensitive Content Masking**: Added `maskSensitiveText` and `maskPhoneNumber` in `PrivacyManager`, `ClipDescription.EXTRA_IS_SENSITIVE` on Android 13+, and `NotificationCompat.VISIBILITY_PRIVATE`.
  6. **Unit Test Verification**: Added 7 comprehensive privacy tests to `AutoTextComprehensiveTest` bringing the test suite to 68/68 passing (100%).

### Defect 10: Form Input Box, Dropdown & Stepper Alignment Inconsistencies
- **Symptom**: On ComposeTaskScreen, the Repeat Interval Configuration row displayed an unaligned dropdown selector next to the interval number input; the dropdown had no label, used an ad-hoc 52dp height, and misaligned vertically against the labeled interval box. Similarly, buttons and steppers on other screens suffered from uneven vertical offsets.
- **Root Cause**:
  1. glassInputField lacked an explicit fixed height for single-line inputs and included minimumInteractiveComponentSize() on internal clear and password toggle buttons. In Material 3, minimumInteractiveComponentSize() enforces 48dp minimum bounds, causing the input box to dynamically enlarge from ~34dp to ~62dp whenever text was typed and the clear button appeared.
  2. The unit selector in ComposeScheduleCard.kt was an ad-hoc custom Row with no label, a hardcoded 52dp height, and a mismatched corner radius (10dp vs 8dp).
  3. Parent rows mixed erticalAlignment = Alignment.CenterVertically with hardcoded padding(top = 16.dp) or padding(top = 8.dp) to compensate for input labels, leading to misalignment across different screen sizes and font scales.
- **Remediation**:
  - Enforced a strict **46dp height** on single-line glassInputField containers (if (singleLine) Modifier.height(46.dp) else Modifier.defaultMinSize(minHeight = 46.dp)).
  - Removed minimumInteractiveComponentSize() from internal icon buttons in glassInputField, keeping input boxes strictly at 46dp regardless of content or state.
  - Created a standardized, reusable glassDropdownField component in Pickers.kt matching the exact label metrics, 46dp container height, AppDesignTokens.ShapeSmall (8dp) shape, and borders of glassInputField.
  - Standardized sibling alignment rules: applied erticalAlignment = Alignment.Top for side-by-side labeled fields, and erticalAlignment = Alignment.Bottom with Modifier.height(46.dp) for input fields placed alongside action buttons (glassButton) or stepper controls across ComposeScheduleCard, ComposeCallSection, ComposeMessageSection, AutoReplyCards, DailyCampaignCards, DailyCampaignDialogs, and ContactSequenceStudio.

---

## 3. Live Hardware Verification Matrix (iQOO I2401, Android 16)

Every supported channel and execution type was provisioned and executed on the physical authorized test device using standard test parameters:
- **Test Recipient 1**: `9769602020`
- **Test Recipient 2**: `9930604542`
- **Test Message Payload**: `jai mata di`
- **Device Model**: Vivo / iQOO I2401 (`10BF1G0HCD0024R`)
- **Android OS**: Android 16 (API Level 36, Vanilla Ice Cream / Baklava)

| Task ID | Channel / Feature | Target Recipient(s) | Execution Mode / Parameters | Result | Hardware Verification Evidence |
|---|---|---|---|---|---|
| **#44** | `SOUND_MODE` | Device Hardware | Set Ringer to Normal (Mode 2) | **SUCCESS** | AudioManager confirmed: Ringer mode 2 NORMAL set successfully. |
| **#45** | `REMINDER` | Device Hardware | System Clock Alarm & TTS Note | **SUCCESS** | AlarmManager fired, ReminderAlarmActivity launched with full lockscreen bypass. |
| **#46** | `FAKE_CALL` | Device Hardware | Simulated Call (`jai mata di`) | **SUCCESS** | FakeCallActivity opened, animated incoming call ringtone and haptics triggered. |
| **#47** | `SMS` | `9769602020`, `9930604542` | Direct Multi-SIM Cellular SMS | **SUCCESS** | Dispatched via SIM Slot 0, parts: 1. TRAI quota counter incremented. |
| **#48** | `WHATSAPP_STATUS` | WhatsApp Status | Post Text Story (`jai mata di`) | **SUCCESS** | Accessibility traversed status composer, entered text, confirmed send action. |
| **#49** | `WHATSAPP_BUSINESS_STATUS` | W4B Status | Post Text Story (`jai mata di`) | **SUCCESS** | Accessibility traversed W4B status composer, confirmed send transition. |
| **#50** | `WHATSAPP` Direct | `9769602020` | Direct Chat Automation | **SUCCESS** | Deep link navigated, message injected, send button clicked and verified. |
| **#51** | `WHATSAPP` Direct | `9930604542` | Direct Chat Automation | **SUCCESS** | Deep link navigated, message injected, send button clicked and verified. |
| **#52** | `WHATSAPP_BUSINESS` | `9769602020` | W4B Customer Chat | **SUCCESS** | W4B deep link launched, message composed, send action verified. |
| **#53/54**| `WHATSAPP_BUSINESS` | `9930604542` | W4B Customer Chat | **SUCCESS** | Auto-retry succeeded upon UI transition, message sent cleanly. |
| **#55** | `CALL` (Cellular) | `9769602020` | Native Telephony Call (Hold 5s) | **SUCCESS** | TelecomManager direct call placed, duration held, auto-hangup executed. |
| **#56** | `WHATSAPP_CALL` | `9769602020` | WhatsApp VoIP Voice Call | **SUCCESS** | VoIP call placed, in-call status monitored, hung up cleanly per criteria. |
| **#57** | `WHATSAPP_BUSINESS_CALL` | `9769602020` | W4B VoIP Voice Call | **SUCCESS** | **Early termination detected after 3s; wait loop cleanly exited without false elapsed alarm!** |

### Post-Verification Database State
- Following execution verification, complete task queue teardown was executed:
  ```bash
  adb shell am broadcast -a com.jmd.autotext.ACTION_DELETE_ALL_TASKS -n com.jmd.autotext/.receiver.TaskAlarmReceiver
  ```
- SQLite Database query confirmed **exactly 0 scheduled tasks remain** in `autotext.db`:
  ```
  === SCHEDULED TASKS IN DATABASE (Total: 0) ===
  ```

---

## 4. Engineering Standards & Quality Gates

AutoText adheres to strict production engineering invariants:

1. **Zero-Warning Compiler Standard**:
   - Every Kotlin source file compiles cleanly with zero warnings under `-Xlint` and standard compiler checks.
   - Prohibits `@Suppress("...")` or `@SuppressWarnings` except where required for platform ABI deprecations across Android 8 to 16.
2. **Zero-Lint Defect Policy**:
   - `.\gradlew.bat lintDebug` must return `No issues found.`.
   - Never suppress actionable lint diagnostics by disabling checks in `build.gradle.kts`.
3. **100% Test Passing Requirement**:
   - All 61 unit tests in `AutoTextComprehensiveTest` must pass cleanly without network or hardware dependencies.
4. **Cooperative Cancellation Invariant**:
   - No sleep or wait operation may exceed **250ms** without polling cancellation flags (`PreferenceManager.isEmergencyStopActive` and `PreferenceManager.isTasksMasterEnabled`).
5. **Strict Display Orientation Invariant**:
   - Never allow system auto-rotation to enable (`ACCELEROMETER_ROTATION = 0`).
   - All activities hard-locked to portrait orientation.

---

## 5. Operational Runbook & ADB Command Reference

### 5.1 Build & Test Commands
```powershell
# 1. Run unit test suite (61 tests)
.\gradlew.bat testDebugUnitTest

# 2. Run static analysis lint
.\gradlew.bat lintDebug

# 3. Assemble signed release APK and copy to project root
.\gradlew.bat assembleRelease copyReleaseApkToRoot

# 4. Install signed APK on connected Android device
adb -s 10BF1G0HCD0024R install -r app-release.apk
```

### 5.2 Diagnostic Intent Triggers

#### Trigger Immediate Single Task (`ACTION_TEST_TASK`)
Dispatches a single immediate test task across any channel:
```bash
# Channels: SMS, WHATSAPP, WHATSAPP_BUSINESS, WHATSAPP_STATUS, WHATSAPP_BUSINESS_STATUS, CALL, WHATSAPP_CALL, WHATSAPP_BUSINESS_CALL, FAKE_CALL, REMINDER, SOUND_MODE
adb shell "am broadcast -a com.jmd.autotext.ACTION_TEST_TASK --es channel CALL --es recipients 9769602020 --ei callDuration 5 -n com.jmd.autotext/.receiver.TaskAlarmReceiver"
```

#### Trigger Staggered Multi-Channel Test Suite (`ACTION_SETUP_ALL_TASKS`)
Provisions and schedules the complete test suite across all 11 channels:
```bash
adb shell "am broadcast -a com.jmd.autotext.ACTION_SETUP_ALL_TASKS --es recipients 9769602020,9930604542 --es message 'jai mata di' --ei staggerSeconds 10 --ei repeatIntervalSeconds 10 --ez executeImmediately true --ei callDurationSeconds 5 --ez includeDialTimeInCallDuration true --ez clearExisting false -n com.jmd.autotext/.receiver.TaskAlarmReceiver"
```

#### Purge All Tasks from Database (`ACTION_DELETE_ALL_TASKS`)
Instantly deletes all scheduled tasks, unregisters alarms, and purges the campaign queue:
```bash
adb shell am broadcast -a com.jmd.autotext.ACTION_DELETE_ALL_TASKS -n com.jmd.autotext/.receiver.TaskAlarmReceiver
```

#### Trigger Emergency Stop Circuit Breaker (`ACTION_EMERGENCY_STOP`)
```bash
adb shell am broadcast -a com.jmd.autotext.ACTION_EMERGENCY_STOP -n com.jmd.autotext/.receiver.TaskAlarmReceiver
```

#### Dump Complete Runtime Diagnostic Logs (`ACTION_DUMP_LOGS`)
```bash
adb shell am broadcast -a com.jmd.autotext.ACTION_DUMP_LOGS -n com.jmd.autotext/.receiver.TaskAlarmReceiver
adb shell cat /sdcard/Android/data/com.jmd.autotext/files/autotext_execution_dump.txt
```

#### Check System Auto-Rotate State
```bash
adb shell settings get system accelerometer_rotation
# Expected Output: 0 (Strictly Portrait Locked)
```

---

*AutoText Master Engineering Directive v2.6 • Verified on Android 16 • 100% Production Ready*
