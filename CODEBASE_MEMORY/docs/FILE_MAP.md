# AutoText — File Map & Component Directory

## 1. Directory Structure

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── AUTOTEXT_FEATURES_AND_LOGIC.md
├── java/com/jmd/autotext/
│   ├── AutoTextApplication.kt
│   ├── api/
│   │   └── AutomationFeatureApi.kt
│   ├── compat/
│   │   ├── AccessibilityCompat.kt
│   │   ├── ClipboardCompat.kt
│   │   ├── PackageManagerCompat.kt
│   │   ├── SmsManagerCompat.kt
│   │   └── TelephonyManagerCompat.kt
│   ├── core/
│   │   ├── AccessibilityHealthManager.kt
│   │   ├── AppExtensions.kt
│   │   ├── AppFileLogger.kt
│   │   ├── AutomationSafetyManager.kt
│   │   ├── MessageTemplateResolver.kt
│   │   ├── OemBrandHelper.kt
│   │   ├── OverlayPermissionHelper.kt
│   │   ├── PermissionManager.kt
│   │   ├── PhoneNumberNormalizer.kt
│   │   ├── PrivacyManager.kt
│   │   ├── ProtectedApplicationManager.kt
│   │   ├── SmsQuotaManager.kt
│   │   ├── TaskContentValidator.kt
│   │   └── WhatsAppPackageHelper.kt
│   ├── data/
│   │   ├── db/
│   │   │   └── DatabaseHelper.kt
│   │   ├── model/
│   │   │   ├── Models.kt
│   │   │   └── PrivacyModels.kt
│   │   ├── prefs/
│   │   │   └── PreferenceManager.kt
│   │   └── repo/
│   │       ├── AppRepository.kt
│   │       ├── AutoReplyRepository.kt
│   │       ├── BackupRepository.kt
│   │       ├── DailyCampaignRepository.kt
│   │       ├── LogRepository.kt
│   │       ├── PrivacyRepository.kt
│   │       ├── RecipientRepository.kt
│   │       ├── SequenceRepository.kt
│   │       ├── TaskRepository.kt
│   │       ├── TemplateRepository.kt
│   │       └── WhatsAppRepository.kt
│   ├── engine/
│   │   ├── ContactIngestionEngine.kt
│   │   ├── SequentialTaskEngine.kt
│   │   ├── SmsSender.kt
│   │   └── WhatsAppAutomationSender.kt
│   ├── receiver/
│   │   ├── AccessibilityWatchdogReceiver.kt
│   │   ├── AutoTextDeviceAdminReceiver.kt
│   │   ├── BootReceiver.kt
│   │   ├── PhoneCallReceiver.kt
│   │   ├── SmsReceiver.kt
│   │   ├── TaskAlarmReceiver.kt
│   │   └── TimeChangeReceiver.kt
│   ├── scheduler/
│   │   ├── AccessibilityWatchdogScheduler.kt
│   │   ├── AccessibilityWatchdogWorker.kt
│   │   ├── AlarmScheduler.kt
│   │   ├── DailyCampaignScheduler.kt
│   │   └── DailyCampaignWorker.kt
│   ├── service/
│   │   ├── AutoTextA11yTileService.kt
│   │   ├── AutoTextAccessibilityService.kt
│   │   ├── AutoTextNotificationListenerService.kt
│   │   └── TaskForegroundService.kt
│   ├── ui/
│   │   ├── activity/
│   │   │   ├── AutomationLauncherActivity.kt
│   │   │   ├── CallSimulationActivity.kt
│   │   │   ├── MainActivity.kt
│   │   │   └── ReminderAlarmActivity.kt
│   │   ├── components/
│   │   │   ├── AppStatusIndicators.kt
│   │   │   ├── Avatars.kt
│   │   │   ├── GlassComponents.kt
│   │   │   └── Pickers.kt
│   │   ├── model/
│   │   │   └── UiModels.kt
│   │   ├── screen/
│   │   │   ├── CallSimulationScreen.kt
│   │   │   ├── DocumentationScreen.kt
│   │   │   ├── MainAppHost.kt
│   │   │   ├── MoreScreen.kt
│   │   │   ├── PermissionScreen.kt
│   │   │   ├── ReminderScreen.kt
│   │   │   ├── TaskTypeSelectionDialog.kt
│   │   │   ├── WhatsAppSetupScreen.kt
│   │   │   ├── autoreply/
│   │   │   │   ├── AutoReplyCards.kt
│   │   │   │   ├── AutoReplyDialogs.kt
│   │   │   │   └── AutoReplyScreen.kt
│   │   │   ├── campaign/
│   │   │   │   ├── DailyCampaignCards.kt
│   │   │   │   ├── DailyCampaignDialogs.kt
│   │   │   │   ├── DailyCampaignListItems.kt
│   │   │   │   └── DailyCampaignScreen.kt
│   │   │   ├── compose/
│   │   │   │   ├── ComposeCallSection.kt
│   │   │   │   ├── ComposeGroupSection.kt
│   │   │   │   ├── ComposeHeaderFooter.kt
│   │   │   │   ├── ComposeMessageSection.kt
│   │   │   │   ├── ComposePickerSheets.kt
│   │   │   │   ├── ComposeScheduleCard.kt
│   │   │   │   ├── ComposeStatusSection.kt
│   │   │   │   └── ComposeTaskScreen.kt
│   │   │   ├── contacts/
│   │   │   │   ├── ContactAddEditDialog.kt
│   │   │   │   ├── ContactCards.kt
│   │   │   │   ├── ContactImportView.kt
│   │   │   │   └── ContactsScreen.kt
│   │   │   ├── logs/
│   │   │   │   ├── LogDetailView.kt
│   │   │   │   └── LogsScreen.kt
│   │   │   ├── privacy/
│   │   │   │   ├── AppPrivacyDetailDialog.kt
│   │   │   │   └── PrivacySettingsScreen.kt
│   │   │   ├── sequence/
│   │   │   │   ├── ContactSequenceCard.kt
│   │   │   │   └── ContactSequenceStudio.kt
│   │   │   ├── settings/
│   │   │   │   ├── SettingsAutoUnlockCard.kt
│   │   │   │   ├── SettingsBackupRestoreCard.kt
│   │   │   │   ├── SettingsBankingShieldCard.kt
│   │   │   │   ├── SettingsDocumentationCard.kt
│   │   │   │   ├── SettingsPreferencesSection.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   ├── SettingsStatusIndicatorsGuideCard.kt
│   │   │   │   └── SystemLogViewerScreen.kt
│   │   │   ├── tasks/
│   │   │   │   ├── TaskCard.kt
│   │   │   │   └── TasksScreen.kt
│   │   │   └── templates/
│   │   │       ├── TemplateCard.kt
│   │   │       ├── TemplateFormView.kt
│   │   │       └── TemplatesScreen.kt
│   │   └── theme/
│   │       └── Theme.kt
│   └── whatsapp/
│       ├── AccessibilityNodeUtils.kt
│       ├── KeyguardUnlockEngine.kt
│       ├── PopupShield.kt
│       ├── VoiceNoteProtectionGuard.kt
│       ├── WhatsAppCallEngine.kt
│       ├── WhatsAppChatScanner.kt
│       ├── WhatsAppDialogHandler.kt
│       ├── WhatsAppDirectSendEngine.kt
│       ├── WhatsAppGroupEngine.kt
│       ├── WhatsAppIntentDispatcher.kt
│       └── WhatsAppStatusEngine.kt
└── res/
    ├── layout/
    ├── mipmap-*/
    ├── values/
    └── xml/
        ├── accessibility_service_config.xml
        ├── backup_rules.xml
        ├── data_extraction_rules.xml
        ├── device_admin.xml
        └── file_paths.xml

app/src/test/
└── java/com/jmd/autotext/
    └── AutoTextComprehensiveTest.kt
```
