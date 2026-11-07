# Task Tracking for Helper App Refactoring

## Phase 1: Foundation - File Renaming & Structure

### Task 1.1: Rename `ui/component/` → `ui/components/`
- [ ] Move `HelperAsyncImage.kt` from `ui/component/` to `ui/components/`
- [ ] Update all imports across project referencing `ui.component`

### Task 1.2: Rename Inconsistent File Names (Batch 1)
- [ ] `AutoReplyCards.kt` → `AutoReplyCardComponents.kt`
- [ ] `DailyCampaignCards.kt` → `DailyCampaignCardComponents.kt`
- [ ] `DailyCampaignDialogs.kt` → `DailyCampaignDialogComponents.kt`
- [ ] `DailyCampaignListItems.kt` → `DailyCampaignListItemComponents.kt`

### Task 1.3: Rename Inconsistent File Names (Batch 2)
- [ ] `ContactCards.kt` → `ContactCardComponents.kt`
- [ ] `LogDetailView.kt` → `LogDetailScreen.kt`
- [ ] `AppPrivilege.kt` → `AppPrivilegeScreen.kt`

### Task 1.4: Rename Inconsistent File Names (Batch 3)
- [ ] `SettingsBackupRestoreCard.kt` → `SettingsBackupRestoreCardComponents.kt`
- [ ] `SettingsBankingShieldCard.kt` → `SettingsBankingShieldCardComponents.kt`
- [ ] `SettingsSystemSelfTestCard.kt` → `SettingsSystemSelfTestCardComponents.kt`

### Task 1.5: Rename Inconsistent File Names (Batch 4)
- [ ] `TemplateCard.kt` → `TemplateCardComponents.kt`
- [ ] `TaskCard.kt` → `TaskCardComponents.kt`

### Task 1.6: Rename WhatsApp Package Files
- [ ] `AccessibilityNodeUtils.kt` → `WhatsappAccessibilityNodeUtils.kt`
- [ ] `KeyguardAssistEngine.kt` → `WhatsappKeyguardAssistEngine.kt`
- [ ] `PopupShield.kt` → `WhatsappPopupShield.kt`
- [ ] `VoiceNoteProtectionGuard.kt` → `WhatsappVoiceNoteProtectionGuard.kt`
- [ ] `WhatsAppCallEngine.kt` → `WhatsappCallEngine.kt`
- [ ] `WhatsAppChatScanner.kt` → `WhatsappChatScanner.kt`
- [ ] `WhatsAppDialogHandler.kt` → `WhatsappDialogHandler.kt`
- [ ] `WhatsAppDirectSendEngine.kt` → `WhatsappDirectSendEngine.kt`
- [ ] `WhatsAppIntentDispatcher.kt` → `WhatsappIntentDispatcher.kt`
- [ ] `WhatsAppStatusEngine.kt` → `WhatsappStatusEngine.kt`

### Task 1.7: Rename Core Utilities (Batch 1)
- [ ] `AppDateTimeUtils.kt` → `DateTimeUtils.kt`
- [ ] `AppExtensions.kt` → `ContextExtensions.kt`
- [ ] `AppFileLogger.kt` → `FileLogger.kt`
- [ ] `AppLifecycleObserver.kt` → `LifecycleObserver.kt`
- [ ] `AppPostureManager.kt` → `PostureManager.kt`
- [ ] `AppTrace.kt` → `TraceUtils.kt`
- [ ] `BiometricAuthManager.kt` → `BiometricAuth.kt`
- [ ] `MessageTemplateResolver.kt` → `TemplateResolver.kt`
- [ ] `PhoneNumberNormalizer.kt` → `PhoneNumberUtils.kt`
- [ ] `OverlayPermissionHelper.kt` → `OverlayPermissionUtils.kt`
- [ ] `TaskContentValidator.kt` → `ContentValidator.kt`
- [ ] `TaskSafetyManager.kt` → `SafetyManager.kt`
- [ ] `WhatsAppPackageHelper.kt` → `WhatsappPackageUtils.kt`

## Phase 2: API Consolidation

### Task 2.1: Create `HelperApi` Interface
- [ ] Define all unified endpoints
- [ ] Consolidate request DTOs from scattered data classes
- [ ] Standardize return types (`FeatureExecutionResult`)
- [ ] Add comprehensive KDoc documentation

### Task 2.2: Create `HelperApiImpl` Implementation
- [ ] Implement all endpoints delegating to internal engines
- [ ] Wire repositories, services, and engines
- [ ] Standardize error handling patterns
- [ ] Add logging and diagnostic info

### Task 2.3: Migrate `TaskFeatureApi` → `HelperApi`
- [ ] Update all imports from `TaskFeatureApi` → `HelperApi`
- [ ] Update all call sites across project
- [ ] Verify all endpoints still function correctly
- [ ] Remove deprecated `TaskFeatureApi` after migration

### Task 2.4: Create Request/Response DTOs
- [ ] `MessageRequest.kt`
- [ ] `CallRequest.kt`
- [ ] `StatusRequest.kt`
- [ ] `ReminderRequest.kt`
- [ ] `CameraRequest.kt`
- [ ] `AudioRecordRequest.kt`
- [ ] `MediaPlaybackRequest.kt`
- [ ] `TtsRequest.kt`
- [ ] `ClipboardRequest.kt`
- [ ] `HapticRequest.kt`
- [ ] `ScreenLockRequest.kt`
- [ ] `BrightnessRequest.kt`
- [ ] `FlashlightRequest.kt`
- [ ] `BatteryRequest.kt`
- [ ] `ConnectivityRequest.kt`
- [ ] `DiagnosticRequest.kt`

## Phase 3: Engine Refactoring

### Task 3.1: Group Engines by Domain
- [ ] Messaging engines: WhatsAppSender, WhatsAppDirectSendEngine, WhatsAppChatScanner
- [ ] Calling engines: TelecomCallEngine, WhatsAppCallEngine, WhatsAppIntentDispatcher
- [ ] Media engines: CameraEngine, AudioRecorderEngine, MediaPlayerEngine, TtsEngine
- [ ] System engines: FlashlightEngine, BrightnessEngine, ScreenLockEngine, ClipboardEngine, HapticEngine
- [ ] WhatsApp engines: WhatsAppStatusEngine, WhatsAppDialogHandler, WhatsAppKeyguardAssistEngine

### Task 3.2: Define Engine Interfaces
- [ ] `MessageEngine` interface
- [ ] `CallEngine` interface
- [ ] `MediaEngine` interface
- [ ] `SystemEngine` interface
- [ ] `WhatsAppEngine` interface

### Task 3.3: Implement Each Engine
- [ ] Implement using unified patterns from Phase 2
- [ ] Remove duplicate code across engines
- [ ] Package-private access where possible
- [ ] Comprehensive unit tests

## Phase 4: UI & Navigation

### Task 4.1: Consolidate UI Components
- [ ] Extract common primitives to `ui/components/common/`
- [ ] Buttons.kt, Cards.kt, Inputs.kt, Dialogs.kt, Lists.kt
- [ ] Update all screen composables to use shared components

### Task 4.2: Create Type-Safe Navigation Routes
- [ ] `AppRoutes.kt` with all screen routes
- [ ] `RouteExtensions.kt` with navigation helpers
- [ ] Update all NavHost destinations

### Task 4.3: Extract Design Tokens
- [ ] Colors.kt, Typography.kt, Spacing.kt, Shapes.kt, Motion.kt

## Phase 5: Testing

### Task 5.1: Unit Tests for All Public API
- [ ] Write tests for HelperApi endpoints
- [ ] Write mock tests for engines
- [ ] Coverage target: 90%+

### Task 5.2: Integration Tests
- [ ] Repository tests with Room TestHelper
- [ ] Preference/DB interaction tests
- [ ] API integration tests

### Task 5.3: UI Tests for All Screens
- [ ] Compose testing for all composables
- [ ] Navigation tests
- [ ] State flow tests

### Task 5.4: Screenshot Tests
- [ ] Paparazzi / Roborazzi setup
- [ ] All screens in Light/Dark
- [ ] All locale variations

### Task 5.5: E2E Tests
- [ ] Critical feature flows
- [ ] Accessibility tests
- [ ] Performance benchmarks

## Phase 6: Polish & Verification

### Task 6.1: Fix Lint Issues
- [ ] Zero warnings target
- [ ] Code style compliance
- [ ] Import cleanup

### Task 6.2: Performance Baseline
- [ ] Startup time measurement
- [ ] Memory profiling
- [ ] Battery impact assessment

### Task 6.3: Manual Verification Checklist
- [ ] All 12+ critical feature flows
- [ ] All UI interactions
- [ ] Theme switching
- [ ] Accessibility compliance
- [ ] Multi-SIM scenarios

### Task 6.4: Release Build Verification
- [ ] assembleRelease
- [ ] lint release
- [ ] Signing verification