# AutoText Features, Architecture & Logic Manual

> **Comprehensive In-App Guide to AutoText: System Architecture, Telephony Quotas, Accessibility Automation, and Security Specifications.**

---

## 1. Executive Summary & Project Purpose

AutoText is an enterprise-grade, privacy-respecting Android automation suite designed for high-reliability communication across multiple channels:
- **SMS (Single & Multi-SIM)**
- **WhatsApp (Consumer)**
- **WhatsApp Business (W4B)**
- **WhatsApp Status Stories**
- **Scheduled Direct Phone Calls**
- **Simulated Fake Incoming Calls**
- **Exact Audio Reminders & Alarms**

### Core Architectural Philosophy:
1. **100% Local Execution**: All contacts, message templates, scheduled tasks, and execution logs reside strictly inside the on-device SQLite database. No external tracking, cloud servers, or third-party telemetry.
2. **Zero Clutter Status Bar**: Unlike legacy automation apps that keep a permanent sticky notification running 24/7, AutoText uses Android's Doze-safe `AlarmManager.setExactAndAllowWhileIdle()` and background `WorkManager` workers. No ongoing foreground service notifications clutter the user's status bar.
3. **Self-Healing Watchdog**: Proactively detects and recovers from system kills of Android's Accessibility and Notification Listener services across Android 10 through Android 15.
4. **Material Design 3 (M3) Foundation**: Deep obsidian dark palette (`#080C14`), solid high-contrast container surfaces (`#162032`), and vibrant semantic accents designed for crisp legibility and zero glare.

---

## 2. Architecture & Data Layer

AutoText follows a layered, reactive architecture decoupled across Data, Business Logic, System Integrations, and Jetpack Compose UI.

### Database Schemas (SQLite / `DatabaseHelper.kt`)
The local SQLite database (`autotext.db`) maintains strict schema integrity across multiple tables:

1. **`tasks`**:
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `channel` (TEXT: "SMS", "WHATSAPP", "WHATSAPP_BUSINESS", "WHATSAPP_STATUS", "CALL", "FAKE_CALL", "REMINDER")
   - `recipient_phone` (TEXT)
   - `recipient_name` (TEXT)
   - `message` (TEXT)
   - `scheduled_epoch` (INTEGER)
   - `recurrence_type` (TEXT: "ONCE", "DAILY", "WEEKDAYS", "WEEKENDS", "WEEKLY", "MONTHLY", "CUSTOM")
   - `recurrence_interval_days` (INTEGER)
   - `recurrence_days_of_week` (TEXT: comma-separated day numbers)
   - `sim_slot` (INTEGER: 0 for SIM 1, 1 for SIM 2, -1 for default)
   - `media_uri` (TEXT: local file URI for status images/videos)
   - `status` (TEXT: "ACTIVE", "PAUSED", "COMPLETED")
   - `created_at` (INTEGER)

2. **`execution_logs`**:
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `task_id` (INTEGER)
   - `channel` (TEXT)
   - `recipient_phone` (TEXT)
   - `recipient_name` (TEXT)
   - `message_snippet` (TEXT)
   - `status` (TEXT: "SUCCESS", "FAILED", "SKIPPED_QUOTA", "SKIPPED_HOLIDAY")
   - `error_message` (TEXT)
   - `execution_timestamp` (INTEGER)
   - `sms_parts_count` (INTEGER: number of carrier segments)

3. **`contacts` & `contact_groups`**:
   - Stores synchronized phonebook contacts, recent call log contacts, and scanned WhatsApp groups.
   - Enforces unique phone number constraints with automatic E.164 sanitization.

4. **`whatsapp_scanned_groups`**:
   - `id` (INTEGER PRIMARY KEY AUTOINCREMENT)
   - `group_title` (TEXT)
   - `group_id` (TEXT)
   - `participant_count` (INTEGER)
   - `last_scanned_epoch` (INTEGER)

5. **`auto_reply_rules` & `auto_reply_logs`**:
   - Rules specifying triggers (Missed Call, Ended Call, Incoming SMS, Incoming WhatsApp).
   - Cooldown period tracking per recipient to prevent reply loops.

---

## 3. Scheduled Tasks & Exact Alarm Engine

AutoText schedules all automated actions with microsecond precision:

### Scheduling Mechanism
- Uses `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pendingIntent)`.
- Re-registers all active alarms automatically upon device restart via `BootReceiver` (`android.intent.action.BOOT_COMPLETED` and `android.intent.action.MY_PACKAGE_REPLACED`).
- Dynamic Recurrence Recalculation: After every execution, `AlarmScheduler` calculates the exact next trigger epoch based on the recurrence rules:
  - **DAILY**: Exactly +24 hours.
  - **WEEKDAYS**: Skips Saturday and Sunday.
  - **WEEKENDS**: Skips Monday through Friday.
  - **WEEKLY**: Exactly +7 days.
  - **MONTHLY**: Advances by 1 calendar month preserving the day-of-month.

### Multi-Channel Execution Trampoline
When the alarm triggers:
1. `AlarmReceiver` receives the broadcast and acquires a high-priority `PowerManager` WakeLock.
2. If the task is an **SMS**, `SmsSender` dispatches immediately via `SmsManager` without waking the screen.
3. If the task is a **WhatsApp**, **Call**, or **Status Story**, `AutomationLauncherActivity` is launched over the lockscreen using `showWhenLocked` and `turnScreenOn`.
4. After dispatch completes, the wake lock is safely released.

---

## 4. Daily Bulk Sender & TRAI 100 SMS Quota Compliance

To protect users against Indian Telecom Regulatory Authority (TRAI) penalties and SIM card blacklisting, AutoText embeds a strict carrier quota guard.

### Device-Wide Sent SMS Accounting
- Most automation tools only track messages sent from their own interface. AutoText continuously queries the native Android Telephony Provider:
  ```kotlin
  val uri = Uri.parse("content://sms/sent")
  val cursor = contentResolver.query(
      uri,
      arrayOf("_id", "date"),
      "date >= ?",
      arrayOf(startOfDayEpoch.toString()),
      null
  )
  ```
- Any message sent via Google Messages, Samsung Messages, Truecaller, or AutoText counts toward the 100 SMS daily ceiling.

### Hard-Stop Enforcer
- When total sent SMS >= 100 in the current calendar day, the SMS campaign runner **immediately hard-stops**.
- Outstanding messages are retained in the database with status `SKIPPED_QUOTA` and automatically scheduled to resume on the next calendar day starting at 00:01 AM.

### GSM-7 vs. UCS-2 Segment Metering
- Standard 7-bit GSM characters allow 160 characters per SMS segment.
- If a message contains any Unicode characters (e.g., Hindi, Tamil, Arabic, emojis like 🚀), the carrier switches to 16-bit UCS-2 encoding (70 characters per segment).
- AutoText's live character counter calculates true segment usage in real-time, preventing unexpected quota exhaustion.

---

## 5. WhatsApp & WhatsApp Business Automation & Screen Unlock Engine

AutoText achieves fully unattended WhatsApp messaging and Status publishing through deep accessibility node crawling.

### Screen Wake & Keyguard Auto-Unlock
1. **Screen Wake**: `PowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "AutoText:Unlock")` wakes the display.
2. **Keyguard Dismissal**: `KeyguardManager.requestDismissKeyguard()` requests the keypad.
3. **Automated Swipe-Up**: If biometric prompt or swipe screen is displayed, `AutoTextAccessibilityService` dispatches a vertical touch gesture from (540, 1800) to (540, 400).
4. **PIN Input**: The service scans accessibility nodes for numeric PIN keypads, enters the user's encrypted 4-to-6 digit PIN, and confirms unlock.

### Accessibility Node Traversal
- AutoText launches WhatsApp directly using deep link intents:
  `https://api.whatsapp.com/send?phone=+91XXXXXXXXXX&text=EncodedMessage`
- The accessibility crawler searches for send buttons across known resource IDs:
  - `com.whatsapp:id/send`
  - `com.whatsapp.w4b:id/send`
  - Accessibility content descriptions: "Send", "Envoyer", "Enviar", "भेजें".
- When a send button is located, it invokes `node.performAction(AccessibilityNodeInfo.ACTION_CLICK)`.
- If an "Invalid Phone Number" dialog appears (`com.whatsapp:id/alertTitle`), the crawler intercepts the dialog, dismisses it, records `FAILED_INVALID_NUMBER` in the execution log, and returns safely to AutoText.

### WhatsApp Status Story Poster
- Prepares the chosen image or video from app storage.
- Shares directly to WhatsApp's Status Composer via Intent:
  `component = ComponentName("com.whatsapp", "com.whatsapp.camera.CameraActivity")`
- Automatically finds the status caption field (`com.whatsapp:id/caption_text`), injects the text via `ACTION_SET_TEXT`, and clicks the Status Send button (`com.whatsapp:id/send`).
- Verifies return to the WhatsApp Updates tab and automatically re-locks the device.

---

## 6. Auto-Reply Rules Engine

AutoText provides an intelligent auto-responder for communications when the user is driving, in meetings, or outside business hours.

### Triggers Supported
1. **Missed Calls**: Detected via `PhoneCallReceiver` listening to `TelephonyManager.ACTION_PHONE_STATE_CHANGED`.
2. **Ended Calls**: Triggers a follow-up message when an outbound or inbound call completes.
3. **Incoming SMS**: Intercepted via `SmsReceiver` with high broadcast priority.
4. **WhatsApp Messages**: Captured via `AutoTextNotificationListenerService`.

### Strict Indian Mobile Number Validation
To avoid wasting SMS or triggering telecom carrier penalties:
- Only numbers matching valid 10-digit Indian mobile formats (`^[6-9]\d{9}$`) or international E.164 numbers are permitted.
- Fixed landlines, toll-free lines (1800), shortcodes (e.g., bank alerts, OTPs), and 140/160 telemarketing lines are completely ignored.

### Anti-Spam Frequency Capping & Cooldowns
- **Once Per Day**: Responds to a contact only once in a 24-hour cycle.
- **Cooldown Interval**: Configurable delay (e.g., 10 minutes) to prevent infinite reply loops between two auto-replying devices.

---

## 7. Silent Watchdog & Service Self-Healing

Android aggressively kills background accessibility services and notification listeners on modern OEM skins (MIUI, OneUI, ColorOS).

### Accessibility Health Monitor (`AccessibilityHealthManager.kt`)
- Runs periodically in the background to verify that `AutoTextAccessibilityService` is actively bound:
  ```kotlin
  fun isAccessibilityServiceRunning(context: Context): Boolean {
      val enabledServices = Settings.Secure.getString(
          context.contentResolver,
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      )
      return enabledServices?.contains(context.packageName) == true
  }
  ```
- If the service is enabled in settings but dropped by the Android OS binder, AutoText logs a warning, alerts the user with an actionable system notification, and attempts automatic re-binding upon the next app foreground.

### Zero Persistent Notification Policy
- Standard automation apps place an annoying, persistent "Service is running" icon in the status bar.
- AutoText uses zero foreground services during idle periods, maintaining zero status bar footprint.
- Foreground services are only temporarily activated during active bulk dispatch campaigns and immediately terminated upon completion.

---

## 8. Security, Device Privacy & Local Storage

AutoText is designed from the ground up to guarantee 100% data privacy:
- **Zero Cloud Storage**: All contacts, numbers, message templates, and logs are stored in private internal app storage (`/data/data/com.jmd.autotext/databases/`).
- **Encrypted Preferences**: Sensitive credentials such as the screen unlock PIN are stored locally with hardware-backed encryption keys where supported.
- **Log Sanitation**: Personal recipient names and sensitive message contents can be purged or exported directly by the user from the System Diagnostics screen.
- **Safe Device Admin**: The Device Admin privilege is used strictly for calling `lockNow()` to secure the device immediately after unattended dispatches.

---

## 9. Troubleshooting, Logcat Debugging & FAQs

### Common Issues & Solutions

1. **Accessibility Service Turns Off Automatically**:
   - Cause: Aggressive OEM battery optimization (Samsung, Xiaomi, Vivo).
   - Solution: Go to Device Settings > Apps > AutoText > Battery > Set to "Unrestricted". Turn off "Remove permissions if app isn't used".

2. **WhatsApp Fails to Send on Locked Device**:
   - Cause: Screen unlock PIN is incorrect or accessibility gestures are blocked.
   - Solution: Verify your screen PIN in AutoText Settings > Screen Auto-Unlock. Ensure the device screen can be woken by notifications.

3. **SMS Not Sending**:
   - Cause: Daily 100 SMS quota reached or SIM balance depleted.
   - Solution: Check the Dashboard Quota Meter. If 100 SMS have been sent across the device today, dispatch will resume at 00:01 AM tomorrow.

4. **Viewing Live Diagnostics**:
   - Open More > System Diagnostics.
   - You can view real-time log entries, copy diagnostic details, or export the full 2 MB rotating `autotext_execution.log` file.

---
*AutoText Engine v2.4 • Offline-First Multi-Channel Dispatch Suite*
