# AutoText 📱⚡

> **Enterprise-Grade Automated Messaging, Scheduled Multi-Channel Dispatch, Regulatory Quota Compliance & Smart Auto-Responder for Android**

AutoText (`com.jmd.autotext`) is an offline-first, privacy-respecting Android automation suite designed for maximum reliability across **SMS**, **WhatsApp**, **WhatsApp Business**, and **Phone Calls**. Built exclusively with **Modern Android (Jetpack Compose, Kotlin Coroutines, Room/SQLite, WorkManager, and Accessibility Services)**, AutoText operates with **zero fixed notifications** and 100% on-device data sovereignty.

---

## 🌟 Key Capabilities & Architectural Pillars

### 1. 🇮🇳 Regulatory 100 SMS/Day TRAI Quota Guard
- **Device-Wide Sent SMS Accounting**: Queries the native telephony provider (`content://sms/sent`) merged with AutoText dispatch logs to capture every single SMS sent by any application on the device.
- **Strict Hard-Stop Enforcer**: Automatically halts any outgoing SMS queue as soon as 100 messages are reached within a calendar day (00:00 - 23:59), preventing SIM bans and telecom fines under Indian TRAI regulations.
- **Multi-Part GSM-7 & UCS-2 Segment Metering**: Dynamic segmentation engine accounts for 160-char GSM-7 standard chunks and 70-char Unicode/Emoji chunks to calculate true carrier segment consumption before dispatch.
- **Priority Next-Cycle Dispatch**: Remaining queued and failed messages are given priority in the next execution cycle before contacting new numbers.

### 2. 🤖 Unattended WhatsApp & WhatsApp Business Automation
- **Intent Deep-Link Trampoline**: Hands-free background dispatch directly via `https://api.whatsapp.com/send?phone=...&text=...` or standard `vnd.android.cursor.item/vnd.com.whatsapp.profile`.
- **Intelligent Accessibility Node Traversal**: `AutoTextAccessibilityService` and `WhatsAppStatusEngine` scan resource IDs (`com.whatsapp:id/send`, `com.whatsapp.w4b:id/send`, `com.whatsapp:id/caption_text`) with fallback content-description queries and multi-lingual button support.
- **WhatsApp Status & Story Publisher**: Automates publishing image/video statuses to your WhatsApp contacts with customized captions and verifies publication before cleanly exiting.
- **Chat Scanner**: Inspects active WhatsApp chats and groups, capturing group titles, subject lines, and participant counts directly into SQLite without requiring external third-party scraping tools.

### 3. 🔐 Screen Wake, Auto-Unlock & Auto-Lock Engine
- **Wake & Keyguard Dismissal**: Leverages `PowerManager` partial wake locks (`AutoText:DispatchWakeLock`) and `KeyguardManager.requestDismissKeyguard()` to bring Android to life at the exact scheduled alarm instant.
- **Biometric to PIN Keypad Transition**: Detects fingerprint or swipe lockscreens, automatically swipes up via `dispatchGesture()`, and types the user's encrypted local PIN on the system lock keypad before dispatching scheduled actions.
- **Immediate Device Re-Lock**: Instantly engages Device Policy Manager (`DevicePolicyManager.lockNow()`) or Accessibility Global Action (`GLOBAL_ACTION_LOCK_SCREEN`) to keep the phone secure immediately following background execution.

### 4. 📅 Scheduled Tasks & Multi-Channel Campaigns
- **Precision Alarm Scheduling**: Exact minute/second triggers driven by `AlarmManager.setExactAndAllowWhileIdle()`.
- **Supported Channels**:
  - Direct SMS (with SIM 1 / SIM 2 selection)
  - Direct WhatsApp
  - Direct WhatsApp Business
  - WhatsApp Status Stories
  - Direct Phone Calls
  - Simulated Fake Calls (with audio ringers and realistic caller UI)
  - Scheduled Audio Alarms & Reminders
- **Flexible Recurrence Patterns**: One-Time, Daily, Weekdays, Weekends, Weekly, Monthly, or Custom repeat intervals.
- **Dynamic Template Personalization**: Dynamically populates tags: `{name}`, `{first_name}`, `{phone}`, `{date}`, `{time}`, `{day}`.

### 5. 📞 Smart Auto-Reply Engine
- **Multi-Trigger Responses**: Auto-responds to Missed Calls, Ended Calls, Incoming SMS, and WhatsApp notifications via `AutoTextNotificationListenerService`.
- **Strict Indian Mobile Number Verification**: Filters out landlines, shortcodes, 140/160 telemarketing lines, and toll-free numbers.
- **Frequency Capping & Anti-Spam Cooldowns**: Limits replies to Once/Day, 3x/Day, Custom limits, or specified cooldown delays (e.g., 10 minutes) to eliminate infinite response loops.

### 6. 🛡️ Silent Watchdog & Self-Healing
- **Zero Persistent Notification Footprint**: Background tasks leverage Doze-safe alarm schedules and `WorkManager` workers instead of cluttering the status bar with persistent sticky notifications.
- **Accessibility Health Manager**: Constantly monitors the health of `AutoTextAccessibilityService` and triggers automated recovery if the Android system kills or deprioritizes background services.
- **File Logger with Rotation**: Thread-safe structured logging (`autotext_execution.log`) with automatic 2 MB log rotation and in-app system log viewer.

---

## 🎨 Pure Material 3 Design System

AutoText adheres 100% to Google's **Material Design 3 (Material You)** guidelines:
- **Solid High-Contrast Surface Hierarchy**:
  - `SurfaceDark` (`#0D131F`) - Base Application Canvas
  - `GlassSurfaceSubtle` (`#101725`) - Surface Container Low
  - `GlassSurfaceDefault` (`#162032`) - Surface Container (Primary Card Fill)
  - `GlassSurfaceElevated` (`#1D2A40`) - Surface Container High
  - `GlassSurfaceHover` (`#253550`) - Surface Container Highest
- **M3 Vibrant Semantic Color Tokens**:
  - `NeonCyan` (`#38BDF8`) - M3 Sky 400 Primary Accent
  - `VividEmerald` (`#10B981`) - Secondary Accent (WhatsApp / Success)
  - `ElectricIndigo` (`#818CF8`) - Tertiary Accent (Automation)
  - `RadiantAmber` (`#F59E0B`) - Warning / Quota Exceeded
  - `CyberRose` (`#EF4444`) - Error / Destructive Action
- **Typography**: Complete M3 Typography scale (Display, Headline, Title, Body, Label) calibrated for readability under high ambient light and OLED dark mode.
- **Material 3 Shapes**: Standardized `ShapeSmall` (8.dp), `ShapeMedium` (12.dp), `ShapeLarge` (16.dp), and `ShapePill` (999.dp).

---

## 🏗️ Technical Architecture & Directory Structure

```
com.jmd.autotext
├── AutoTextApplication.kt            # App startup, channel creation & watchdog bootstrap
├── MainActivity.kt                   # Root host activity, edge-to-edge window insets & nav host
├── data/
│   ├── DatabaseHelper.kt             # SQLite database schemas, migrations & raw helpers
│   ├── repository/
│   │   ├── AppRepository.kt          # Tasks, templates, contacts & execution logs repository
│   │   ├── AutoReplyRepository.kt    # Rules, triggers, cooldowns & reply history
│   │   ├── CampaignRepository.kt     # Daily bulk campaigns & contact sequencing
│   │   └── WhatsAppRepository.kt     # Scanned WhatsApp chats, groups & participant data
│   └── PreferenceManager.kt          # Encrypted local preferences (PIN, limits, toggles)
├── model/
│   └── Models.kt                     # Data models (Task, Log, Contact, Campaign, Rule, Group)
├── scanner/
│   └── ContactIngestionEngine.kt     # Syncs phone contacts, recent SMS & call logs
├── scheduler/
│   ├── AlarmScheduler.kt             # Exact alarm scheduling via AlarmManager
│   ├── DailyCampaignScheduler.kt     # WorkManager daily campaign orchestration
│   ├── DailyCampaignWorker.kt        # Background campaign execution worker
│   └── Receivers.kt                  # BootReceiver, AlarmReceiver, TimeChangeReceiver
├── sender/
│   ├── SmsSender.kt                  # Telephony multi-part SMS dispatch & status broadcast
│   └── WhatsAppAutomationSender.kt  # Intent launcher & lockscreen wake trampoline
├── service/
│   ├── AccessibilityHealthManager.kt # Auto-heal watchdog for accessibility services
│   ├── AutoTextAccessibilityService.kt # UI automation crawler & screen unlock engine
│   ├── AutoTextDeviceAdminReceiver.kt# Screen locking device administration receiver
│   ├── AutoTextNotificationListenerService.kt # Notification listener for auto-replies
│   ├── PhoneCallReceiver.kt          # Call state listener (missed / ended triggers)
│   ├── SmsReceiver.kt                # Incoming SMS broadcast receiver
│   └── accessibility/
│       ├── AccessibilityNodeUtils.kt # Recursive node traversal, text injection & click actions
│       ├── WhatsAppChatScanner.kt    # Automated WhatsApp group and chat inspector
│       └── WhatsAppStatusEngine.kt   # Hands-free WhatsApp status & story poster
└── ui/
    ├── automation/                   # Foreground automation launcher trampoline
    ├── fakecall/                     # Simulated incoming call UI
    ├── reminder/                     # Alarm reminder dialog & ringer
    ├── components/                   # Material 3 Design System Components
    │   ├── BentoCards.kt             # M3Card, M3CardElevated, BentoStatTile, BentoEmptyState
    │   ├── BentoControls.kt          # M3Button, BentoBadge, M3Chip, GlassToggleRow
    │   ├── BentoInputs.kt            # M3InputField, M3SearchField, CharacterCounter
    │   ├── BentoPickers.kt           # Inline M3 Date & Time pickers
    │   └── Material3DesignTokens.kt  # Unified Material 3 design tokens & aliases
    ├── theme/
    │   ├── BentoGlassTokens.kt       # Color palettes, surface levels & animation specs
    │   ├── Material3DesignTokens.kt  # Aliased M3 tokens
    │   └── Theme.kt                  # MaterialTheme configuration & DarkColorScheme
    └── screens/
        ├── DashboardScreen.kt        # Summary statistics, quick actions & quota meters
        ├── TasksListScreen.kt        # Filterable list of scheduled tasks
        ├── ComposeTaskScreen.kt      # Task creation studio with channel picker
        ├── DailyAutoSenderScreen.kt  # Bulk campaign runner & quota controller
        ├── AutoReplyScreen.kt        # Auto-response rules & triggers manager
        ├── ContactSequenceStudio.kt  # Multi-stage contact workflow sequencer
        └── MoreScreen.kt             # Diagnostics, permissions, scanner & settings
```

---

## 🔒 Permissions & Security Model

AutoText operates **100% locally on your device**. No telemetry, no external ad SDKs, no cloud tracking.
- `SEND_SMS` / `READ_SMS` / `RECEIVE_SMS`: Dispatching messages, monitoring regulatory 100 SMS limits, and handling SMS auto-replies.
- `READ_CONTACTS`: Contact picker and name personalization.
- `READ_PHONE_STATE` / `READ_CALL_LOG` / `CALL_PHONE`: Detecting missed calls and executing scheduled phone calls.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`: Firing scheduled messages at exact timestamps.
- `SYSTEM_ALERT_WINDOW`: Displaying countdown overlays during automated WhatsApp dispatches.
- `BIND_ACCESSIBILITY_SERVICE`: Unattended WhatsApp navigation, chat scanning, and lockscreen PIN input.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: Reading incoming WhatsApp notifications to trigger automated responses.
- `BIND_DEVICE_ADMIN`: Instantly locking the device screen after automated tasks complete.

---

## 🚀 Building & Testing

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or newer
- JDK 17+
- Android SDK 34 (Android 14) minimum SDK 26 (Android 8.0)

### Building via Gradle
```bash
# Clean and assemble debug APK
./gradlew assembleDebug

# Run unit test suite
./gradlew testDebugUnitTest

# Install on connected USB device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Internal proprietary application developed for automated communication workflows. All rights reserved.
