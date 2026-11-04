# AutoText — Complete Feature Inventory & Acceptance Matrix

| Feature Domain | Subfeature | Implementation Component | Verification Status | Notes |
|:---|:---|:---|:---|:---|
| **Task Lifecycle** | DRAFT State | `Models.kt` (`TaskStatus.DRAFT`) | **PASS** | State machine transitions verified |
| | VALIDATING State | `Models.kt`, `TaskContentValidator.kt` | **PASS** | Input & quota validated |
| | READY State | `Models.kt`, `TaskRepository.kt` | **PASS** | Armed and ready for trigger |
| | SCHEDULED State | `AlarmScheduler.kt`, `TaskAlarmReceiver.kt` | **PASS** | Exact alarm registration |
| | RUNNING State | `SequentialTaskEngine.kt` | **PASS** | Mutex locked, active tracking |
| | RUNNING Recovery | `TaskRepository.recoverInterruptedRunningTasks()`, `SequentialTaskEngine.kt` | **PASS** | Auto-reconciles orphaned RUNNING tasks on boot, reschedule, and engine pipeline exit |
| | SUCCESS State | `SequentialTaskEngine.kt`, `LogRepository.kt` | **PASS** | Recorded in DB with execution log |
| | FAILED State | `SequentialTaskEngine.kt`, `AutomationSafetyManager.kt` | **PASS** | Recorded with failure reason |
| | CANCELLED State | `SequentialTaskEngine.cancelTask()`, `AlarmScheduler.cancelTask()` | **PASS** | Active job cancelled, alarms disarmed |
| | PAUSED State | `TaskRepository.toggleTask()`, `TasksScreen.kt` | **PASS** | Suppresses alarm until resumed |
| **Scheduling** | Immediate Execution | `TasksScreen.kt` ("Send Now") | **PASS** | Bypasses alarm, queues directly |
| | One-Time Exact Alarm | `AlarmScheduler.kt` | **PASS** | RTC_WAKEUP exact alarm |
| | Repeating (Seconds) | `AlarmScheduler.calculateNextTrigger` | **PASS** | Sliced timer scheduling |
| | Repeating (Minutes/Hours) | `AlarmScheduler.calculateNextTrigger` | **PASS** | Exact interval addition |
| | Daily & Weekly Schedules | `AlarmScheduler.calculateNextTrigger` | **PASS** | Day-of-week bitmask logic |
| | Monthly / Custom Intervals | `AlarmScheduler.calculateNextTrigger` | **PASS** | Calendar math with leap day handling |
| | Timezone Change Recovery | `TimeChangeReceiver.kt` | **PASS** | Reschedules active pending alarms |
| | Boot & Upgrade Recovery | `BootReceiver.kt` | **PASS** | Re-registers pending alarms after restart |
| **Recipients** | Single / Multiple Contacts | `ComposeTaskScreen.kt`, `RecipientRepository.kt` | **PASS** | Multi-recipient comma-separated pool |
| | Number Normalization | `PhoneNumberNormalizer.kt` | **PASS** | Strips formatting, adds country codes |
| | CSV Contacts Import | `ContactImportView.kt` | **PASS** | Parses CSV headers, validates phones |
| | Ingestion from Device | `ContactIngestionEngine.kt` | **PASS** | Scans contacts, call logs, SMS |
| **SMS** | Multi-SIM Cellular Modem | `SmsSender.kt`, `SmsManagerCompat.kt` | **PASS** | Explicit SIM slot binding |
| | Multi-part PDU SMS | `SmsSender.kt` | **PASS** | Automatically splits & tracks parts |
| | SMS Quota Management | `SmsQuotaManager.kt` | **PASS** | Prevents carrier block from burst limits |
| | Incoming SMS Auto-Reply | `SmsReceiver.kt`, `AutoReplyRepository.kt` | **PASS** | Trigger pattern match with cooldown |
| **Phone Calls** | Direct Call Execution | `SequentialTaskEngine.executeCallTask()` | **PASS** | TelecomManager.placeCall with SIM selection |
| | Dialer Intent Fallback | `SequentialTaskEngine.executeCallTask()` | **PASS** | ACTION_DIAL fallback when unprivileged |
| | Call Duration Holding | `SequentialTaskEngine.executeCallTask()` | **PASS** | 250ms sliced polling with idle check |
| | Early Hangup Detection | `TelephonyManagerCompat.kt` | **PASS** | Detects IDLE transition and avoids false alarm |
| | Automated Call Termination | `AutoTextAccessibilityService.endPhoneCall()` | **PASS** | TelecomManager.endCall / in-call UI tap |
| | Inter-call Delay Pacing | `SequentialTaskEngine.executeCallTask()` | **PASS** | Enforces configured seconds between calls |
| | Sequence Ordering | `SequentialTaskEngine.executeCallTask()` | **PASS** | GIVEN vs RANDOM ordering |
| **WhatsApp** | Personal & Business WA | `WhatsAppPackageHelper.kt` | **PASS** | Identifies `com.whatsapp` and `com.whatsapp.w4b` |
| | Direct Chat Automation | `WhatsAppDirectSendEngine.kt` | **PASS** | Inserts text, verifies composer cleared |
| | Media Attachment Support | `WhatsAppAutomationSender.kt` | **PASS** | Dispatches image/video URI with caption |
| | Group Message Search | `WhatsAppGroupEngine.kt` | **PASS** | Searches group title in chats tab |
| | Status Story Posting | `WhatsAppStatusEngine.kt` | **PASS** | Posts to Updates/Status tab |
| | Outbound VoIP Calls | `WhatsAppCallEngine.kt` | **PASS** | Taps voice call button, verifies connection |
| | VoIP Duration Auto-Hangup | `WhatsAppCallEngine.kt` | **PASS** | Scans conversation timer, ends call cleanly |
| **Banking Protection** | Default-Deny UI Policy | `ProtectedApplicationManager.kt` | **PASS** | Only authorized targets receive UI taps |
| | Financial App Catalog | `ProtectedApplicationManager.KNOWN_PROTECTED_PACKAGES` | **PASS** | 60+ Indian and global banking/UPI apps |
| | Pre-Action Context Check | `ProtectedApplicationManager.verifyContextBeforeInteraction` | **PASS** | Verifies foreground package before taps |
| | Dynamic Standby Detachment | `AccessibilityHealthManager.kt` | **PASS** | Detaches a11y service during idle periods |
| | Google Pay Zero Block | `SettingsBankingShieldCard.kt`, `accessibility_service_config.xml` | **PASS** | Scoped packageNames, 1-tap pause tile |
| **Screen Auto-Unlock** | Screen Wake & Keyguard Dismiss | `AutomationLauncherActivity.kt` | **PASS** | PowerManager wake lock & KeyguardManager |
| | Physical Tap PIN Unlock | `KeyguardUnlockEngine.kt` | **PASS** | Enters PIN digits via gesture coordinates |
| | Auto-Relock Postcondition | `SequentialTaskEngine.executeDeviceLockPostcondition` | **PASS** | Minimizes to home, triggers lockNow |
| **Safety & Control** | Global Emergency Stop | `AutomationSafetyManager.kt` | **PASS** | Instant emergency stop via broadcast or UI |
| | Human Touch Detection | `AutoTextAccessibilityService.kt` | **PASS** | Yields to human user, auto-postpones task |
| | Portrait Orientation Guard | `AccessibilityHealthManager.enforceAutoRotateInvariant` | **PASS** | Restores portrait lock (ACCELEROMETER_ROTATION=0) |
| **Persistence & Logs** | Thread-Safe SQLite WAL | `DatabaseHelper.kt` | **PASS** | 15 tables with transactional integrity |
| | Execution History Audit | `LogRepository.kt`, `LogsScreen.kt` | **PASS** | Full audit log with recipient & status |
| | Backup & Restoration | `BackupRepository.kt`, `SettingsBackupRestoreCard.kt` | **PASS** | JSON export and import of application state (including `TABLE_APP_PRIVACY_CONFIGS`) |
| **Permissions & Diagnostics** | Runtime Permission Management | `PermissionManager.kt`, `PermissionScreen.kt` | **PASS** | Dynamic SDK-adaptive permissions including Call Log and Media |
| | OEM Autostart Diagnostics | `AccessibilityHealthManager.kt` | **PASS** | Multi-OEM intents with Vivo/iQOO specific component fallbacks |
| **Call Simulation & Alarms** | Pixel In-Call Simulation UI | `CallSimulationActivity.kt` | **PASS** | Answer, In-Call Timer (00:19), Mute, Speaker, End Call, and Decline buttons verified on physical device |
| | Full-Screen Heads-Up Alarm | `AlarmReminderActivity.kt` | **PASS** | Fullscreen alarm display with interactive Dismiss button verified on physical device |
| **UI & Navigation Controls** | Tasks Screen & Channel Selector | `MainAppHost.kt`, `TaskTypeSelectionDialog.kt` | **PASS** | Tabs, Single Tasks vs Sequences switcher, + button, 14-channel dialog, ComposeTaskScreen Cancel button |
| | Campaigns & Sync Engine | `DailyCampaignScreen.kt` | **PASS** | Active campaigns list, recipient status, Sync Pool button, and batch limit steppers verified |
| | Auto-Reply Control Engine | `AutoReplyScreen.kt` | **PASS** | Engine master enable switch, keyword rule dialogs, test broadcast verification |
| | Tools & Diagnostics Suite | `MoreScreen.kt`, `TemplatesScreen.kt`, `LogsScreen.kt` | **PASS** | Message Templates (5), Execution History (200 records + SMS filters), System Traces (1500 records), Privacy & App Visibility (Banking Shield list) |
| | Contacts & Groups Hub | `ContactsScreen.kt` | **PASS** | 3 tabs verified: Custom & CSV, Dynamic Pool (2518 phonebook leads with origin tags & sequence creators), and WA Groups |
| | Settings & Security Suite | `SettingsScreen.kt`, `SettingsBackupRestoreCard.kt` | **PASS** | 7/7 Healthy cards, PIN screen assistance, Backup to SD card (`AutoText_Backup_*.json` - 2.21MB), In-app Markdown guide reader |
