# Implementation Plan: Helper App Refactoring & Universal API Consolidation

## Project Overview

The **Helper** app is a sophisticated Android automation engine with 18 operational channels. The codebase contains ~100+ Kotlin files across multiple layers. This plan addresses:
- **File/folder renaming** for consistency
- **Universal constraint formulation** to eliminate duplication
- **API endpoint consolidation** - converting all features into unified endpoints
- **Comprehensive testing** of all features, buttons, and UI flows

---

## User Review Required

> [!IMPORTANT]
> This plan involves **significant structural changes** including file renames, folder restructuring, and API layer consolidation. The app will be non-functional during the refactoring phase until all changes are complete.

> [!WARNING]
> **Breaking Changes Expected:**
> - Package structure changes (folder renames)
> - Import path updates across 100+ files
> - API surface consolidation (some internal APIs will be removed/changed)
> - Test file updates required

---

## Open Questions

> [!IMPORTANT]
> **Before proceeding, I need clarification on:**

1. **Naming Convention**: Should the project use `com.jmd.helper` (current) or a new package structure?
2. **API Layer**: Should `TaskFeatureApi` become the **single public API surface**, with all engines/services becoming internal implementations?
3. **Database Migration**: Are there existing Room migrations that must be preserved?
4. **Release Keystore**: The `app/release-key.jks` exists - should signing config remain unchanged?
5. **Min SDK**: Currently API 27 - should we bump to API 29+ for modern APIs?

---

## Proposed Changes

### Phase 1: File & Folder Renaming (Consistency)

#### [DELETE] `ui/component/` → Merge into `ui/components/`
| Current Path | New Path |
|---|---|
| `ui/component/HelperAsyncImage.kt` | `ui/components/HelperAsyncImage.kt` |

#### [RENAME] Inconsistent File Names
| Current | Proposed | Reason |
|---|---|---|
| `ui/screen/autoreply/AutoReplyCards.kt` | `ui/screen/autoreply/AutoReplyCardComponents.kt` | Plural "Cards" → Component suffix |
| `ui/screen/campaign/DailyCampaignCards.kt` | `ui/screen/campaign/DailyCampaignCardComponents.kt` | Consistency |
| `ui/screen/campaign/DailyCampaignDialogs.kt` | `ui/screen/campaign/DailyCampaignDialogComponents.kt` | Consistency |
| `ui/screen/campaign/DailyCampaignListItems.kt` | `ui/screen/campaign/DailyCampaignListItemComponents.kt` | Consistency |
| `ui/screen/contacts/ContactCards.kt` | `ui/screen/contacts/ContactCardComponents.kt` | Consistency |
| `ui/screen/logs/LogDetailView.kt` | `ui/screen/logs/LogDetailScreen.kt` | "View" → "Screen" |
| `ui/screen/permission/AppPrivilege.kt` | `ui/screen/permission/AppPrivilegeScreen.kt` | Add "Screen" suffix |
| `ui/screen/settings/SettingsBackupRestoreCard.kt` | `ui/screen/settings/SettingsBackupRestoreCardComponents.kt` | Consistency |
| `ui/screen/settings/SettingsBankingShieldCard.kt` | `ui/screen/settings/SettingsBankingShieldCardComponents.kt` | Consistency |
| `ui/screen/settings/SettingsSystemSelfTestCard.kt` | `ui/screen/settings/SettingsSystemSelfTestCardComponents.kt` | Consistency |
| `ui/screen/templates/TemplateCard.kt` | `ui/screen/templates/TemplateCardComponents.kt` | Consistency |
| `ui/screen/tasks/TaskCard.kt` | `ui/screen/tasks/TaskCardComponents.kt` | Consistency |
| `whatsapp/AccessibilityNodeUtils.kt` | `whatsapp/WhatsappAccessibilityNodeUtils.kt` | Package prefix |
| `whatsapp/KeyguardAssistEngine.kt` | `whatsapp/WhatsappKeyguardAssistEngine.kt` | Package prefix |
| `whatsapp/PopupShield.kt` | `whatsapp/WhatsappPopupShield.kt` | Package prefix |
| `whatsapp/VoiceNoteProtectionGuard.kt` | `whatsapp/WhatsappVoiceNoteProtectionGuard.kt` | Package prefix |
| `whatsapp/WhatsAppCallEngine.kt` | `whatsapp/WhatsappCallEngine.kt` | Package prefix |
| `whatsapp/WhatsAppChatScanner.kt` | `whatsapp/WhatsappChatScanner.kt` | Package prefix |
| `whatsapp/WhatsAppDialogHandler.kt` | `whatsapp/WhatsappDialogHandler.kt` | Package prefix |
| `whatsapp/WhatsAppDirectSendEngine.kt` | `whatsapp/WhatsappDirectSendEngine.kt` | Package prefix |
| `whatsapp/WhatsAppIntentDispatcher.kt` | `whatsapp/WhatsappIntentDispatcher.kt` | Package prefix |
| `whatsapp/WhatsAppStatusEngine.kt` | `whatsapp/WhatsappStatusEngine.kt` | Package prefix |

#### [RENAME] Core Utilities → Consistent Suffix
| Current | Proposed |
|---|---|
| `core/AppDateTimeUtils.kt` | `core/DateTimeUtils.kt` |
| `core/AppExtensions.kt` | `core/ContextExtensions.kt` |
| `core/AppFileLogger.kt` | `core/FileLogger.kt` |
| `core/AppLifecycleObserver.kt` | `core/LifecycleObserver.kt` |
| `core/AppPostureManager.kt` | `core/PostureManager.kt` |
| `core/AppTrace.kt` | `core/TraceUtils.kt` |
| `core/BiometricAuthManager.kt` | `core/BiometricAuth.kt` |
| `core/MessageTemplateResolver.kt` | `core/TemplateResolver.kt` |
| `core/PhoneNumberNormalizer.kt` | `core/PhoneNumberUtils.kt` |
| `core/OverlayPermissionHelper.kt` | `core/OverlayPermissionUtils.kt` |
| `core/TaskContentValidator.kt` | `core/ContentValidator.kt` |
| `core/TaskSafetyManager.kt` | `core/SafetyManager.kt` |
| `core/WhatsAppPackageHelper.kt` | `core/WhatsappPackageUtils.kt` |

---

### Phase 2: Universal Constraint Formulation (Deduplication)

#### Constraint 1: **Single API Surface** - `TaskFeatureApi`
All feature execution MUST route through `TaskFeatureApi` endpoints. Internal engines become private implementations.

```kotlin
// BEFORE: Direct engine access everywhere
WhatsAppSender(context).send(...)
SmsSender(context).sendSmsWithResult(...)
SequentialTaskEngine.execute(...)

// AFTER: Single API surface
TaskFeatureApi.sendWhatsAppMessage(context, repo, request)
TaskFeatureApi.sendSms(context, repo, request)
TaskFeatureApi.executeTask(context, repo, task)
```

#### Constraint 2: **Repository Pattern Unification**
All data access through `AppRepository` facade. Direct DAO access prohibited.

#### Constraint 3: **Settings/Prefs Single Source**
`PreferenceManager` is the ONLY prefs accessor. `EncryptedKeyguardStore` and `AppDataStore` are internal.

#### Constraint 4: **ChannelType as Universal Discriminator**
All routing, logging, UI, and execution branches on `ChannelType` enum.

#### Constraint 5: **Error Handling Standardization**
All endpoints return `FeatureExecutionResult` (or `Result<T>` for internal). No exceptions escape public API.

#### Constraint 6: **Async Pattern Unification**
- Public API: `suspend fun` returning `FeatureExecutionResult`
- Internal: `suspend fun` returning `Result<T>` or `Flow<T>`
- No callbacks, no bare `launch { }` in business logic

---

### Phase 3: API Endpoint Consolidation

#### New Unified API Surface (`api/HelperApi.kt`)

```kotlin
// Core execution endpoints (all suspend, all return FeatureExecutionResult)
interface HelperApi {
    // Messaging
    suspend fun sendMessage(request: MessageRequest): FeatureExecutionResult
    suspend fun sendBatch(request: BatchMessageRequest): List<FeatureExecutionResult>

    // Status/Stories
    suspend fun postStatus(request: StatusRequest): FeatureExecutionResult

    // Calling
    suspend fun placeCall(request: CallRequest): FeatureExecutionResult
    suspend fun hangupCall(taskId: Long, channel: ChannelType): FeatureExecutionResult

    // Reminders/Alarms
    suspend fun triggerReminder(request: ReminderRequest): FeatureExecutionResult
    suspend fun simulateCall(request: CallSimulationRequest): FeatureExecutionResult

    // System Control
    suspend fun setSoundMode(request: SoundModeRequest): FeatureExecutionResult
    suspend fun setBrightness(level: Int): FeatureExecutionResult
    suspend fun toggleFlashlight(pattern: FlashlightPattern?): FeatureExecutionResult

    // Media
    suspend fun capturePhoto(request: CameraRequest): FeatureExecutionResult
    suspend fun recordAudio(request: AudioRecordRequest): FeatureExecutionResult
    suspend fun playMedia(request: MediaPlaybackRequest): FeatureExecutionResult
    suspend fun synthesizeSpeech(request: TtsRequest): FeatureExecutionResult

    // Clipboard
    suspend fun setClipboard(text: String): FeatureExecutionResult
    suspend fun clearClipboard(): FeatureExecutionResult

    // Haptics
    suspend fun triggerHaptic(pattern: HapticPattern): FeatureExecutionResult

    // Device Admin
    suspend fun lockScreen(): FeatureExecutionResult
    suspend fun unlockScreen(pin: String): FeatureExecutionResult

    // Connectivity Info
    suspend fun getBatteryInfo(): FeatureExecutionResult
    suspend fun getConnectivityInfo(): FeatureExecutionResult

    // Diagnostics
    suspend fun runSelfTest(): FeatureExecutionResult
    suspend fun repairPermissions(): FeatureExecutionResult
}

// Request DTOs (consolidated from scattered data classes)
data class MessageRequest(
    val taskId: Long,
    val channel: ChannelType,
    val recipient: String,
    val message: String,
    val mediaUri: String = "",
    val simSlot: Int = 0,
    val shouldRelock: Boolean = false,
    val isBatch: Boolean = false
)

data class CallRequest(
    val taskId: Long,
    val channel: ChannelType, // CALL, WHATSAPP_CALL, WHATSAPP_BUSINESS_CALL
    val recipient: String,
    val simSlot: Int = 0,
    val durationSeconds: Int = 0,
    val includeDialTime: Boolean = false,
    val shouldRelock: Boolean = false
)

// ... other request DTOs consolidated
```

#### Engine → Internal Implementation Mapping

| Engine/Service | Becomes Internal Implementation Of |
|---|---|
| `WhatsAppSender` | `HelperApi.sendMessage` (WA channels) |
| `SmsSender` | `HelperApi.sendMessage` (SMS channel) |
| `WhatsAppIntentDispatcher` | `HelperApi.placeCall` (WA calls) + `HelperApi.sendMessage` (WA messages) |
| `WhatsAppCallEngine` | `HelperApi.placeCall` (WA call execution) |
| `WhatsAppStatusEngine` | `HelperApi.postStatus` |
| `WhatsAppDirectSendEngine` | `HelperApi.sendMessage` (direct send path) |
| `WhatsAppChatScanner` | `HelperApi` contact ingestion (internal) |
| `KeyguardAssistEngine` | `HelperApi.unlockScreen` |
| `SequentialTaskEngine` | `HelperApi.executeScheduledTask` (orchestrator) |
| `ContactIngestionEngine` | `HelperApi.ingestContacts` (internal) |
| `AlarmScheduler` | `HelperApi.scheduleTask` / `cancelTask` |
| `DailyCampaignScheduler` | `HelperApi.startDailyCampaign` / `stopDailyCampaign` |

---

### Phase 4: Folder Structure Reorganization

```
com.jmd.helper/
├── api/                          # PUBLIC API SURFACE
│   ├── HelperApi.kt              # Main interface (NEW - consolidates TaskFeatureApi)
│   ├── HelperApiImpl.kt          # Implementation (NEW)
│   ├── request/                  # Request DTOs (NEW)
│   │   ├── MessageRequest.kt
│   │   ├── CallRequest.kt
│   │   ├── StatusRequest.kt
│   │   ├── ReminderRequest.kt
│   │   ├── CameraRequest.kt
│   │   ├── AudioRecordRequest.kt
│   │   ├── MediaPlaybackRequest.kt
│   │   ├── TtsRequest.kt
│   │   ├── ClipboardRequest.kt
│   │   ├── HapticRequest.kt
│   │   ├── ScreenLockRequest.kt
│   │   ├── BrightnessRequest.kt
│   │   ├── FlashlightRequest.kt
│   │   ├── BatteryRequest.kt
│   │   ├── ConnectivityRequest.kt
│   │   └── DiagnosticRequest.kt
│   └── response/
│       └── FeatureExecutionResult.kt (existing)
│
├── core/                         # SHARED UTILITIES (NO BUSINESS LOGIC)
│   ├── extensions/
│   │   ├── ContextExtensions.kt
│   │   ├── ViewExtensions.kt
│   │   └── FlowExtensions.kt
│   ├── utils/
│   │   ├── DateTimeUtils.kt
│   │   ├── FileLogger.kt
│   │   ├── TraceUtils.kt
│   │   ├── PhoneNumberUtils.kt
│   │   ├── TemplateResolver.kt
│   │   ├── ContentValidator.kt
│   │   ├── OverlayPermissionUtils.kt
│   │   ├── WhatsappPackageUtils.kt
│   │   ├── PostureManager.kt
│   │   ├── LifecycleObserver.kt
│   │   ├── BiometricAuth.kt
│   │   ├── SafetyManager.kt
│   │   ├── PermissionManager.kt
│   │   ├── PrivacyManager.kt
│   │   ├── ProtectedAppManager.kt
│   │   ├── OemBrandHelper.kt
│   │   ├── AccessibilityHealthManager.kt
│   │   ├── SmsQuotaManager.kt
│   │   └── SystemSelfTestManager.kt
│   ├── compat/                   # Version compatibility (existing)
│   │   ├── AccessibilityCompat.kt
│   │   ├── ClipboardCompat.kt
│   │   ├── PackageManagerCompat.kt
│   │   ├── SmsManagerCompat.kt
│   │   └── TelephonyManagerCompat.kt
│   └── di/
│       └── HelperModule.kt       # Hilt/Koin module (NEW)
│
├── data/                         # DATA LAYER
│   ├── db/
│   │   ├── room/                 # Room entities, DAOs, Database
│   │   └── DatabaseHelper.kt
│   ├── model/                    # Domain models (existing Models.kt, etc.)
│   ├── prefs/
│   │   ├── PreferenceManager.kt
│   │   ├── EncryptedKeyguardStore.kt (internal)
│   │   └── AppDataStore.kt (internal)
│   └── repo/
│       ├── AppRepository.kt      # Main facade (existing)
│       └── ... specialized repos (existing)
│
├── engine/                       # INTERNAL IMPLEMENTATIONS (package-private)
│   ├── messaging/
│   │   ├── WhatsAppMessageEngine.kt
│   │   ├── SmsMessageEngine.kt
│   │   └── MessageEngine.kt (interface)
│   ├── calling/
│   │   ├── TelecomCallEngine.kt
│   │   ├── WhatsAppCallEngine.kt
│   │   └── CallEngine.kt (interface)
│   ├── status/
│   │   └── WhatsAppStatusEngine.kt
│   ├── media/
│   │   ├── CameraEngine.kt
│   │   ├── AudioRecorderEngine.kt
│   │   ├── MediaPlayerEngine.kt
│   │   └── TtsEngine.kt
│   ├── system/
│   │   ├── FlashlightEngine.kt
│   │   ├── BrightnessEngine.kt
│   │   ├── ScreenLockEngine.kt
│   │   ├── ClipboardEngine.kt
│   │   ├── HapticEngine.kt
│   │   ├── BatteryEngine.kt
│   │   └── ConnectivityEngine.kt
│   ├── whatsapp/
│   │   ├── WhatsAppChatScanner.kt
│   │   ├── WhatsAppDialogHandler.kt
│   │   ├── WhatsAppIntentDispatcher.kt
│   │   ├── WhatsAppAccessibilityNodeUtils.kt
│   │   ├── WhatsAppKeyguardAssistEngine.kt
│   │   ├── WhatsAppPopupShield.kt
│   │   └── WhatsAppVoiceNoteProtectionGuard.kt
│   ├── scheduler/
│   │   ├── TaskScheduler.kt
│   │   ├── CampaignScheduler.kt
│   │   └── MaintenanceScheduler.kt
│   └── orchestration/
│       ├── SequentialTaskEngine.kt
│       └── ContactIngestionEngine.kt
│
├── service/                      # ANDROID SYSTEM SERVICES
│   ├── TaskAccessibilityService.kt
│   ├── TaskForegroundService.kt
│   ├── TaskNotificationListenerService.kt
│   └── TaskTileService.kt
│
├── receiver/                     # BROADCAST RECEIVERS
│   ├── BootReceiver.kt
│   ├── HelperDeviceAdminReceiver.kt
│   ├── PhoneCallReceiver.kt
│   ├── SmsReceiver.kt
│   ├── TaskAlarmReceiver.kt
│   ├── TimeChangeReceiver.kt
│   └── AccessibilityWatchdogReceiver.kt
│
├── scheduler/                    # WORKMANAGER WORKERS
│   ├── AccessibilityWatchdogWorker.kt
│   ├── DailyCampaignWorker.kt
│   ├── DatabaseMaintenanceWorker.kt
│   └── WorkManagerScheduler.kt
│
├── ui/                           # UI LAYER
│   ├── activity/
│   │   ├── MainActivity.kt
│   │   ├── TaskLauncherActivity.kt
│   │   ├── ReminderAlarmActivity.kt
│   │   └── CallSimulationActivity.kt
│   ├── components/               # REUSABLE COMPONENTS
│   │   ├── GlassComponents.kt
│   │   ├── Avatars.kt
│   │   ├── Pickers.kt
│   │   ├── HelperAsyncImage.kt
│   │   ├── AppStatusIndicators.kt
│   │   └── common/               # NEW - shared UI primitives
│   │       ├── Buttons.kt
│   │       ├── Cards.kt
│   │       ├── Inputs.kt
│   │       ├── Dialogs.kt
│   │       └── Lists.kt
│   ├── navigation/
│   │   ├── AppNavigation.kt
│   │   └── routes/               # NEW - type-safe routes
│   │       ├── AppRoutes.kt
│   │       └── RouteExtensions.kt
│   ├── screen/
│   │   ├── MainAppHost.kt
│   │   ├── MoreScreen.kt
│   │   ├── PermissionScreen.kt
│   │   ├── DocumentationScreen.kt
│   │   ├── ReminderScreen.kt
│   │   ├── CallSimulationScreen.kt
│   │   ├── WhatsAppSetupScreen.kt
│   │   ├── TaskTypeSelectionDialog.kt
│   │   ├── autoreply/
│   │   ├── campaign/
│   │   ├── compose/
│   │   ├── contacts/
│   │   ├── logs/
│   │   ├── permission/
│   │   ├── privacy/
│   │   ├── sequence/
│   │   ├── settings/
│   │   ├── tasks/
│   │   └── templates/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── MaterialIconMapper.kt
│   │   └── tokens/               # NEW - design tokens
│   │       ├── Colors.kt
│   │       ├── Typography.kt
│   │       ├── Spacing.kt
│   │       ├── Shapes.kt
│   │       └── Motion.kt
│   ├── model/
│   │   └── UiModels.kt
│   └── widget/
│       └── HelperAppWidget.kt
│
└── HelperApplication.kt          # Application class (existing)
```

---

### Phase 5: Testing Strategy

#### Test Matrix

| Test Type | Target | Tools | Coverage Goal |
|---|---|---|---|
| **Unit Tests** | All `core/utils/*`, `data/repo/*`, `engine/*` | JUnit 5, MockK, Turbine | 90%+ |
| **Integration Tests** | `api/HelperApiImpl`, database, prefs | Robolectric, Room TestHelper | 80%+ |
| **UI Tests** | All screens, navigation, interactions | Compose Testing, Espresso | Critical paths 100% |
| **Screenshot Tests** | All screens in light/dark, locales | Paparazzi / Roborazzi | All screens |
| **E2E Tests** | Full feature flows (send msg, place call, etc.) | UIAutomator + Compose Test | 10 key flows |
| **Accessibility Tests** | All interactive elements | Accessibility Test Framework | 100% interactive elements |
| **Performance Tests** | Startup, DB queries, heavy operations | Macrobenchmark | Baseline + regression |

#### Critical Test Scenarios

1. **Messaging Flow**: Send WhatsApp → Verify accessibility completion → Log result
2. **SMS Flow**: Multi-SIM send → Verify modem delivery → Quota tracking
3. **Call Flow**: Place Telecom call → Hold duration → Hangup → Verify
4. **WhatsApp Call**: Launch → Connect → Duration → End → Verify
5. **Status Post**: Create status → Publish → Verify on Updates tab
6. **Reminder Alarm**: Trigger → Full-screen activity → TTS → Dismiss
7. **Call Simulation**: Trigger → Full-screen ringing → Answer/Decline
8. **Screen Lock/Unlock**: Lock → PIN entry → Unlock → Verify
9. **Daily Campaign**: Schedule → Execute batch → Respect limits → Log
10. **Permission Repair**: Detect missing → Guide user → Verify granted
11. **Privacy Shield**: Banking app foreground → Block overlays → Log
12. **Self-Test**: Run all diagnostics → Report pass/fail

---

## Verification Plan

### Automated Verification

```bash
# 1. Build verification
./gradlew :app:assembleDebug :app:assembleRelease

# 2. Lint (zero warnings target)
./gradlew :app:lintDebug --no-daemon

# 3. Unit tests (all must pass)
./gradlew :app:testDebugUnitTest --rerun-tasks

# 4. Connected Android tests (UI/Integration)
./gradlew :app:connectedDebugAndroidTest

# 5. Screenshot tests
./gradlew :app:verifyPaparazziDebug

# 6. Macrobenchmark (performance)
./gradlew :app:macrobenchmark
```

### Manual Verification Checklist

- [ ] App launches without crash (cold/warm start)
- [ ] Main navigation: Dashboard → Tasks → Contacts → Campaigns → Settings → More
- [ ] Compose Task Screen: All channel types selectable, form validation works
- [ ] WhatsApp message send: Test with WA and WA Business
- [ ] SMS send: Test both SIM slots
- [ ] Phone call: Place call, verify duration, hangup
- [ ] WhatsApp call: Place call, verify connection, hangup
- [ ] Status post: Text + media, both WA and WA Business
- [ ] Reminder alarm: Triggers full-screen, TTS plays, dismiss works
- [ ] Call simulation: Rings, answer/decline works
- [ ] Screen lock: Locks device, PIN unlock works
- [ ] Flashlight: Strobe patterns work
- [ ] Brightness: Slider adjusts system brightness
- [ ] Sound mode: Normal/Silent/Vibrate toggle works
- [ ] Daily campaign: Start/stop, respects limits, logs correctly
- [ ] Contact ingestion: Scans contacts, SMS, call logs, WA chats
- [ ] Privacy settings: Add/remove excluded apps, verify blocking
- [ ] Backup/Restore: Export → Import → Verify data integrity
- [ ] System self-test: All diagnostics pass
- [ ] All screens render correctly in Light/Dark theme
- [ ] All screens work at different font scales (85%-200%)
- [ ] All screens work in split-screen / foldable postures
- [ ] Accessibility: TalkBack navigation, content descriptions, focus order

---

## Execution Order

### Week 1: Foundation
1. Rename folders/files (Phase 1)
2. Create new package structure
3. Update all imports (automated + manual)
4. Verify build passes

### Week 2: API Consolidation
1. Create `HelperApi` interface + `HelperApiImpl`
2. Create consolidated request/response DTOs
3. Migrate `TaskFeatureApi` → `HelperApi`
4. Make engines `internal` implementations
5. Wire DI (Hilt/Koin module)

### Week 3: Engine Refactoring
1. Group engines by domain (messaging, calling, media, system)
2. Define engine interfaces
3. Implement each engine using unified patterns
4. Remove duplicate code across engines

### Week 4: UI & Navigation
1. Consolidate UI components
2. Create type-safe navigation routes
3. Extract design tokens
4. Verify all screens render

### Week 5: Testing
1. Write unit tests for all public API
2. Write integration tests for repositories
3. Write UI tests for all screens
4. Write screenshot tests
4. Write E2E tests for critical flows
5. Run full test suite

### Week 6: Polish & Verification
1. Fix all lint issues
2. Performance baseline
3. Manual verification checklist
4. Release build verification
5. Documentation update