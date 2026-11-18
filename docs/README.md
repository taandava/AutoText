# Super — Documentation Index

| Document | Purpose |
|---|---|
| [`../CHAT_MEMORY.md`](../CHAT_MEMORY.md) | Project memory: decisions, every fix made, device verification, open items |
| [`FILE_REFERENCE.md`](FILE_REFERENCE.md) | Per-file and per-declaration reference for all Kotlin sources |
| [`../README.md`](../README.md) | What the app is, feature list, build instructions |
| [`../HOW_TO_RUN_OR_UPDATE_PROJECT.md`](../HOW_TO_RUN_OR_UPDATE_PROJECT.md) | Step-by-step run / update / release guide |

## Reading order for a new contributor

1. `CHAT_MEMORY.md` → why the app behaves the way it does (especially the accessibility and
   keyguard-unlock constraints, and the Device Admin NEW_TASK restriction).
2. `docs/FILE_REFERENCE.md` → where each subsystem lives.
3. `README.md` → build, install, permissions.

## Key subsystems

| Area | Entry points |
|---|---|
| App bootstrap | `SuperApplication.kt`, `core/SuperAppInitializer.kt` |
| Scheduling | `scheduler/AlarmScheduler.kt`, `scheduler/DailyCampaignScheduler.kt`, `receiver/TaskAlarmReceiver.kt` |
| Dispatch | `engine/SequentialTaskEngine.kt`, `engine/SmsSender.kt`, `engine/WhatsAppSender.kt`, `telephony/TelephonyCallEngine.kt` |
| Automation (accessibility) | `service/TaskAccessibilityService.kt`, `whatsapp/WhatsappKeyguardAssistEngine.kt` |
| Reply automation | `service/TaskNotificationListenerService.kt`, `core/ReplyLoopGuard.kt` |
| Banking shield | `core/ProtectedApplicationManager.kt`, `core/AccessibilityHealthManager.kt` |
| Persistence | `data/db/DatabaseHelper.kt`, `data/repo/*`, `data/prefs/*` |
| UI | `ui/screen/MainAppHost.kt` and the `ui/screen/**` packages |
| Privileges | `core/PermissionManager.kt`, `ui/screen/PermissionScreen.kt`, `ui/screen/permission/*` |
