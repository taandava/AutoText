# AutoText Design System & UI/UX Architecture Specification

> **The Authoritative Design System, UI Component Library, and User Experience Specification for AutoText (`com.jmd.autotext`).**  
> *Material 3 Expressive • Premium Orange Identity • Information-Dense Compact Layouts • Full-Width Responsiveness • Edge-to-Edge System Integration*

---

## 1. Design System Philosophy & Core Principles

AutoText is engineered as an enterprise-grade, offline-first Android automation platform. The visual and interaction design directly reflects its core operational characteristics: **maximum situational awareness, immediate control, zero visual clutter, and strict system compliance**.

### 1.1 Core Design Pillars

1. **Information-Dense Compact Ergonomics**:
   - Modern mobile automation requires managing complex queues, recipient lists, execution logs, and timing criteria on a single screen without endless scrolling.
   - Component dimensions, paddings, and line heights are optimized to display 25%–40% more actionable data per viewport compared to standard Material 3 templates while preserving accessible touch targets (minimum 48dp).

2. **Material 3 Expressive Foundations**:
   - Implements Google's Material 3 Expressive design language, featuring purposeful shape contrasts, dynamic color tonal mapping, and contextual elevation containers.
   - Avoids skeuomorphic blurs, heavy frosted glass effects, or GPU-intensive drop shadows that degrade frame rates during rapid accessibility automation.

3. **Signature Orange Brand Identity**:
   - A bold, high-contrast, premium orange color scheme establishes visual hierarchy and draws immediate attention to critical controls, active channels, and execution statuses.
   - Calibrated against dark neutral backgrounds and warm light containers to guarantee WCAG 2.2 AA accessibility standards across all lighting conditions.

4. **Full-Width Edge-to-Edge Responsiveness**:
   - All cards, list rows, action surfaces, and status grids span the full usable width of the display gutters (12dp–16dp).
   - Seamlessly integrates with Android 15/16 edge-to-edge drawing, accommodating system gesture insets, display cutouts, and IME soft keyboard transitions without duplicate padding or layout jitter.

5. **Strict Display Invariant Protection**:
   - All activities (`MainActivity`, `FakeCallActivity`, `ReminderAlarmActivity`, `AutomationLauncherActivity`) are hard-locked to portrait orientation (`android:screenOrientation="portrait"`).
   - The UI architecture guarantees zero layout re-measurement or activity recreation triggered by device rotation sensors.

---

## 2. Design Tokens & Theme Foundation (`AppDesignTokens`)

The entire design system is centralized within `com.jmd.autotext.ui.theme.AppDesignTokens`, eliminating hardcoded magic numbers, scattered hex values, or conflicting component themes.

```
com.jmd.autotext.ui.theme
├── AppDesignTokens.kt    # Centralized tokens (Colors, Spacing, Shapes, Elevation, Typo)
├── Color.kt              # Color primitives and palette definitions
├── Theme.kt              # AutoTextTheme (Light, Dark, Dynamic) with LocalTokens provider
└── Type.kt               # Typography scales and font definitions
```

### 2.1 Color Palette & Semantic Tonal Roles

AutoText features a cohesive dual-theme color architecture tailored for both day and night use:

| Role | Light Theme Hex | Dark Theme Hex | Semantic Usage |
|---|---|---|---|
| **Primary Brand Orange** | `#E76F00` | `#FF9800` | Primary buttons, active tabs, FAB, key accents |
| **Accent Orange Light** | `#FFB77A` | `#FFB77A` | Selected chips, hover states, secondary indicators |
| **Accent Orange Dark** | `#CC5F00` | `#E65100` | Border outlines, pressed button states |
| **Background** | `#FFF8F2` | `#12100E` | Root scaffold background |
| **Surface** | `#FFFFFF` | `#1C1916` | Main cards, modal sheets, top app bar |
| **Surface Variant** | `#F5EBE1` | `#28231E` | Nested containers, search bars, input backgrounds |
| **Surface Container Highest**| `#E8DCD0` | `#38322B` | Segmented buttons, unselected pill chips |
| **On Primary** | `#FFFFFF` | `#000000` | Text/icons placed over primary orange |
| **On Surface (Text Main)** | `#1F1B17` | `#F5EBE1` | High-contrast body text, headings |
| **On Surface Variant (Muted)**| `#7A6F65` | `#A89D91` | Secondary labels, timestamps, metadata |
| **Outline / Border** | `#DECBC0` | `#443B33` | Card strokes, input outlines (1dp crisp borders) |

#### Real-Time Status & Channel Semantic Colors
- **Emerald Green (`#00E676`)**: Active/Healthy service state, Successful task execution (`STATUS_SUCCESS`).
- **Carmine Red (`#FF5252`)**: Inactive/Attention service state, Failed execution (`STATUS_FAILED`), Emergency Stop (`🛑 STOP`).
- **Amber Warning (`#FFB300`)**: Pending execution, Rate quota warning, Battery optimization prompt.
- **Electric Blue (`#29B6F6`)**: Informational notices, SMS channel badge, SIM slot indicator.
- **WhatsApp Green (`#25D366`)**: Consumer WhatsApp channel identification.
- **WhatsApp Business Teal (`#128C7E`)**: WhatsApp Business (W4B) channel identification.
- **Deep Purple (`#7C4DFF`)**: Automation Sequences, Reminder Alarms.

### 2.2 Spacing & Rhythm Tokens

Grid spacing is derived from a consistent 4dp base grid with dedicated micro-steps for compact data rows:

```kotlin
object Spacing {
    val micro: Dp = 2.dp          // Inner badge padding, tight icon offsets
    val extraSmall: Dp = 4.dp     // Status dot margins, chip inner spacing
    val small: Dp = 6.dp          // Metadata row gaps, compact icon button padding
    val medium: Dp = 8.dp         // Standard item spacing, card internal vertical rhythm
    val standard: Dp = 12.dp      // Card inner padding, list item horizontal margins
    val large: Dp = 16.dp         // Screen gutter padding, section breaks
    val extraLarge: Dp = 20.dp    // Screen header bottom margin
    val section: Dp = 24.dp       // Major functional division spacing
}
```

### 2.3 Shapes & Corner Radius System

AutoText balances rounded friendly aesthetics with sharp, clear boundary definition:

```kotlin
object Shapes {
    val extraSmall = RoundedCornerShape(4.dp)   // Tiny tags, SIM slot indicators
    val small = RoundedCornerShape(8.dp)        // Input fields, filter chips, action buttons
    val medium = RoundedCornerShape(12.dp)      // Task cards, campaign cards, dialog sheets
    val large = RoundedCornerShape(16.dp)       // Bottom navigation bar, bottom sheets
    val full = RoundedCornerShape(999.dp)       // Status pill badges, circular action buttons
}
```

### 2.4 Typography Hierarchy

Calibrated with compact line heights to prevent excessive vertical expansion on multi-line messages:

- **Headline Large (24sp / Bold)**: Top navigation screen titles.
- **Headline Medium (20sp / SemiBold)**: Card group headers, section headings.
- **Title Medium (16sp / SemiBold)**: Task card recipient titles, dialog titles.
- **Title Small (14sp / Medium)**: Channel selector names, active button labels.
- **Body Medium (14sp / Regular)**: Message body preview, input text.
- **Body Small (12sp / Regular)**: Secondary metadata, execution error traces, schedule details.
- **Label Small (10sp / Bold)**: Status chips, SIM slot tags, quota counters.

---

## 3. Responsive Top Header & 7-Indicator System Health Architecture

The header of the AutoText dashboard is the central command center for the application. It delivers instantaneous, non-intrusive visibility into all critical Android system services required for 100% unattended automation.

```
┌────────────────────────────────────────────────────────────────────────┐
│  AutoText [v2.6]                [🛑 STOP]  [+] New Task  [⚙️ Settings] │
├────────────────────────────────────────────────────────────────────────┤
│  [🛡️ Bank] [📱 Orient] [♿ Access] [🔔 Notif] [🔒 Admin] [⚡ Batt] [⏱️ Sched]│
└────────────────────────────────────────────────────────────────────────┘
```

### 3.1 The 7 System Health Indicators

Each indicator continuously polls or observes underlying platform APIs and renders as an interactive, compact status pill.
- **Emerald Green (`#00E676`)**: Service fully operational and granted.
- **Carmine Red (`#FF5252`)**: Service disabled, ungranted, or requires user intervention.

| # | Indicator Name | Visual Icon | Verification Method | Tap Action |
|---|---|---|---|---|
| 1 | **Stealth Guard (Banking Shield)** | `Shield` | Verifies `AccessibilityHealthManager.isStealthShieldActive()` | Opens Banking Shield explanation dialog |
| 2 | **Screen Orientation Guard** | `ScreenLockPortrait` | Checks `Settings.System.ACCELEROMETER_ROTATION == 0` | Re-enforces portrait lock (`ACCELEROMETER_ROTATION = 0`) |
| 3 | **Accessibility Service Binder** | `Accessibility` | `AutoTextAccessibilityService.isConnected` StateFlow | Opens Android Accessibility Settings page |
| 4 | **Notification Quick-Reply** | `NotificationsActive` | `NotificationManagerCompat.getEnabledListenerPackages` | Opens Notification Listener Settings page |
| 5 | **Device Admin (Screen Lock)** | `Lock` | `DevicePolicyManager.isAdminActive(adminComponent)` | Opens Device Admin activation prompt |
| 6 | **Unrestricted Battery / Doze**| `BatteryChargingFull` | `PowerManager.isIgnoringBatteryOptimizations` | Opens Battery Optimization exemption prompt |
| 7 | **Master Scheduler Engine** | `Schedule` | `PreferenceManager.isTasksMasterEnabled` | Toggles master task scheduler ON / OFF |

### 3.2 Quick Action Controls

1. **Safety Emergency Stop (`🛑 STOP`)**:
   - Immediate circuit breaker.
   - Sets `PreferenceManager.isEmergencyStopActive = true`.
   - Aborts ongoing executions, wakes any waiting 250ms polling loops, clears accessibility touch queues, and cancels pending alarms.
   - Changes visual state to high-alert pulsing red until dismissed.
2. **New Task Quick Action (`+`)**:
   - Opens the multi-channel `ComposeTaskScreen`.
   - Compact icon-only button on small phone screens, expanding to `[+ New Task]` on wide screens.

---

## 4. Complete Screen-by-Screen UI/UX Inventory

```
Navigation Architecture
├── TasksScreen (Home Dashboard / Queue Management)
├── ComposeTaskScreen (Universal Task Creation)
├── ExecutionLogsScreen (Audit History & Diagnostic Export)
├── DailyAutoSenderScreen (Recurring Outreach Campaigns)
├── SequenceStudioScreen (Multi-Step Choreography)
├── AutoReplyScreen (Call & SMS Trigger Engine)
└── SettingsScreen (Security, Quotas & Manual)
```

### 4.1 `TasksScreen` (Master Execution Dashboard)
- **Header Summary Card**: Displays counts for **Scheduled**, **Completed**, and **Failed** tasks.
- **Channel Quick Filters**: Horizontal scrolling chip row to filter by channel: All, SMS, WhatsApp, W4B, Status, Call, VoIP Call, Reminder, Fake Call.
- **Compact `TaskCard` Component**:
  - Left: Channel Avatar with icon and color-coded SIM slot badge (if applicable).
  - Center: Recipient name/number in Title Medium, message snippet or call duration in Body Small, next execution timestamp with countdown tag.
  - Right: Active toggle switch (`Switch`), status chip (PENDING, RUNNING, COMPLETED, FAILED), three-dot contextual action menu (Run Now, Edit, Duplicate, Delete).
- **Empty State**: Minimalist vector illustration, helpful onboarding text, and a prominent `[Create Your First Task]` primary button.

### 4.2 `ComposeTaskScreen` (Universal Multi-Channel Task Creator)
- **Channel Selector Carousel**: Multi-browse icon cards with real-time selection border for all supported dispatch modes:
  - `SMS` (Cellular Short Message Service)
  - `WHATSAPP` (Consumer WhatsApp Direct Send)
  - `WHATSAPP_BUSINESS` (WhatsApp Business Customer Send)
  - `WHATSAPP_STATUS` (Consumer Status Story)
  - `WHATSAPP_BUSINESS_STATUS` (Business Status Story)
  - `CALL` (Native Telephony Phone Call)
  - `WHATSAPP_CALL` (WhatsApp Voice VoIP Call)
  - `WHATSAPP_BUSINESS_CALL` (WhatsApp Business Voice VoIP Call)
  - `FAKE_CALL` (Simulated Incoming Phone Call)
  - `REMINDER` (System Clock Alarm & TTS Announcement)
  - `SOUND_MODE` (Ringer Mode Automation: Normal, Vibrate, Silent)
- **Recipient Entry Field**:
  - Input field with instant E.164 normalization preview.
  - Quick `[👥 Contacts]` picker button opening a search-filtered contact selection bottom-sheet.
  - Multi-recipient chip field with comma/enter tokenization.
- **Dynamic Content Composer**:
  - Clean, zero-default text area (initializes to empty string without hardcoded sample text).
  - Dynamic token insert bar: `[{name}]`, `[{time}]`, `[{date}]`, `[{day}]`.
  - Live character and GSM-7 vs. UCS-2 multi-part SMS PDU counter (e.g., `"142/160 (1 SMS)"` or `"75/70 (2 SMS - Unicode)"`).
- **Call Duration & Timing Criteria Section**:
  - Appears dynamically when `CALL`, `WHATSAPP_CALL`, or `WHATSAPP_BUSINESS_CALL` is selected.
  - Slider and numerical input for **Call Duration** (0 to 300 seconds; 0 = untimed open call).
  - **Dial Timing Criteria Toggle**:
    - `"Include Dialing Time in Call Duration"` switch.
    - Explicit helper label explaining that when disabled, duration timing begins strictly upon active connection (`CALL_STATE_OFFHOOK` or VoIP active timer digits `00:01`).
- **Scheduling & Recurrence Section**:
  - Interactive Date Picker and Time Picker with 12h/24h locale format support.
  - Recurrence interval dropdown: Once, Every 10 Minutes, Hourly, Daily, Weekly, Custom Cron.

### 4.3 `ExecutionLogsScreen` (Audit Timeline & Diagnostics)
- **Top Search & Date Filter Bar**: Instant client-side text filtering across recipient names, numbers, error codes, and channel types.
- **Log Item Timeline Card**:
  - Status indicator strip (Emerald Green for SUCCESS, Carmine Red for FAILED, Gray for SKIPPED).
  - Channel badge, recipient, formatted execution timestamp (`yyyy-MM-dd HH:mm:ss`).
  - Expandable drawer revealing detailed execution telemetry:
    - Transport duration (ms).
    - SIM slot index and carrier network operator name.
    - Accessibility touch trace coordinates.
    - Stack trace or error diagnosis message if failed.
- **Diagnostic Export Action**:
  - Generates comprehensive `.txt` or `.csv` export.
  - Direct intent trigger to export full internal state (`ACTION_DUMP_LOGS`).

### 4.4 `DailyAutoSenderScreen` (Campaign Studio)
- **Campaign Configuration Header**: Campaign name, active status toggle, scheduled daily start time.
- **Recipient Pool Manager**:
  - Visual ledger of total recipients, messages sent today, remaining queue.
  - Bulk CSV/VCF contact import action.
- **Pacing & Compliance Controls**:
  - Inter-message delay slider (5s to 120s random jitter) to prevent account flagging on WhatsApp.
  - TRAI SMS quota compliance meter (e.g., `"42/100 SMS dispatched today"`).

### 4.5 `SequenceStudioScreen` (Multi-Step Choreography)
- **Visual Node Pipeline**:
  - Step-by-step choreographic cards connected by visual vertical flow lines.
  - Example: `Step 1: Send SMS` ➔ `Delay: 30s` ➔ `Step 2: Place Cellular Call (Hold 15s)` ➔ `Delay: 60s` ➔ `Step 3: Send WhatsApp Follow-up`.
- **Drag-and-Drop Reordering**: Long-press drag handle to re-sequence actions.
- **Conditional Branching Configuration**: On failure or no-answer, branch to alternative fallback channel.

### 4.6 `AutoReplyScreen` (Intelligent Event Trigger Hub)
- **Trigger Type Tabs**: **Calls** (Missed, Rejected, Ended) and **Messages** (SMS, WhatsApp).
- **Call Trigger Configuration**:
  - Duration-based discrimination: Only trigger if missed (`duration == 0`), or only after connected conversation (`duration > 0`).
  - Contact filter: Everyone, Contacts Only, Unknown Numbers Only, Blacklist.
- **Keyword Match Rules**: Exact match, Contains, Regex pattern matching.

### 4.7 `SettingsScreen` (System Health & Configuration)
- **Interactive Status Indicators Health Guide**: Expandable cards providing one-tap setup guides and direct deep-links to Android system settings for each of the 7 system health services.
- **Security & PIN Setup Card**:
  - Unattended Screen Unlock PIN input.
  - Displays encryption state: **Hardware Keystore AES-256-GCM** (`HW_ENC:`) or **Direct Boot Fallback** (`ENC:`).
  - Device Admin activation button for instant post-task screen re-locking.
- **TRAI Regulatory Quota Card**:
  - 100 SMS/day rate limit status.
  - Override switch (`"Enable Unlimited SMS Dispatch"`).
- **Embedded Technical Manual**:
  - One-tap access to the built-in, offline Markdown viewer rendering `AUTOTEXT_FEATURES_AND_LOGIC.md` directly inside the app.

---

## 5. Dedicated Full-Screen Interaction Activities

### 5.1 `FakeCallActivity` (Simulated Incoming OEM Phone Call)
- **Full-Screen Lockscreen Bypass**: Configured with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and `FLAG_KEEP_SCREEN_ON`.
- **Realistic OEM Incoming Call Interface**:
  - High-resolution caller avatar, contact name, phone number, and location metadata.
  - Realistic animated sliding answer handle (Swipe right to answer, Swipe left to decline).
  - Ringtone playback using system default phone ringtone and custom haptic vibration patterns.
- **In-Call Connected State**:
  - Transitions to active call timer (`00:01`, `00:02`...).
  - Functional in-call controls: Mute, Keypad, Speakerphone, Add Call, Hold.
  - Auto-hangup timer triggers realistic call conclusion sound and returns to home screen.

### 5.2 `ReminderAlarmActivity` (Full-Screen Alarm & Voice Notification)
- **Lockscreen Audio Alert**:
  - Bypasses keyguard using high-priority alert window flags.
  - Plays system alarm audio stream (`AudioManager.STREAM_ALARM`) at maximum volume even in Silent/DND mode if configured.
- **Text-to-Speech (TTS) Voice Synthesis**:
  - Speaks the scheduled reminder title and message body repeatedly using Android TTS engine.
- **Interactive Controls**:
  - Large, accessible `[Snooze (5m)]` tonal button.
  - Full-width `[Dismiss Alarm]` primary orange button.

---

## 6. Edge-to-Edge System Integration & Inset Handling

AutoText strictly complies with Android 15/16 edge-to-edge layout mandates:

```kotlin
// In MainActivity.kt
WindowCompat.setDecorFitsSystemWindows(window, false)
```

### 6.1 Avoiding Double Inset Padding
A common architectural defect in Compose applications is applying system bar padding on both the root `Scaffold` and individual screen content containers. AutoText enforces a single-responsibility inset model:

1. **Root Scaffold**: Consumes system insets for the top status bar and bottom navigation bar:
   ```kotlin
   Scaffold(
       modifier = Modifier.fillMaxSize(),
       contentWindowInsets = WindowInsets.safeDrawing
   ) { innerPadding ->
       Box(modifier = Modifier.padding(innerPadding)) {
           AppNavigation(...)
       }
   }
   ```
2. **Inner Content Lists**: Use standard content padding without re-applying system bar insets:
   ```kotlin
   LazyColumn(
       contentPadding = PaddingValues(
           horizontal = AppDesignTokens.Spacing.large,
           vertical = AppDesignTokens.Spacing.medium
       )
   )
   ```

### 6.2 IME (Software Keyboard) Avoidance
- All form fields in `ComposeTaskScreen`, `AutoReplyScreen`, and `SettingsScreen` utilize `Modifier.imePadding()` on scrollable containers.
- Inputs automatically scroll into visible viewport focus upon keyboard appearance without clipping action buttons or obscuring input tokens.

---

## 7. Accessibility & Usability Engineering

1. **Touch Target Dimensions**:
   - Every interactive control (icon buttons, chips, switches, dropdown items) enforces a minimum touch bounding box of **48dp × 48dp**, regardless of visible visual icon dimensions (e.g., 20dp icon inside 48dp clickable box).
2. **Dynamic Font Scaling (Non-Linear Font Scaling Support)**:
   - All text sizes utilize scale-independent pixels (`sp`).
   - Containers utilize flexible wrapping or min-height constraints (`Modifier.defaultMinSize(minHeight = ...)`), preventing text truncation or overflow clipping when users set system font scale up to 200%.
3. **High-Contrast Screen Reader Semantics**:
   - Non-text controls possess clear, localized `contentDescription` attributes (e.g., `"Delete task"`, `"SIM Slot 1 active"`, `"Emergency Stop active"`).
   - Visual status indicators provide explicit semantic state descriptions via `Modifier.semantics { stateDescription = ... }`.

---

## 8. Motion & Transition Specifications

AutoText adopts a high-efficiency, native-feeling motion curve:

- **Easing Curve**: Standard `FastOutSlowInEasing` (CubicBezier 0.4, 0.0, 0.2, 1.0).
- **Navigation Transitions**: Horizontal sliding with subtle alpha fade (250ms duration):
  - Enter: `slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(animationSpec = tween(250))`
  - Exit: `slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(animationSpec = tween(250))`
- **Card Expansion / Collapse**: Animated size transitions (`animateContentSize(animationSpec = tween(200))`).
- **Emergency Stop Pulsing**: High-alert breathing animation (500ms cycle) drawing immediate operator attention.

---

## 9. Privacy & App Visibility Management UI Specifications

### 9.1 Overview & Ergonomics
The **Privacy & App Visibility** interface (`PrivacySettingsScreen.kt`) provides comprehensive oversight of which applications AutoText interacts with or isolates from. It is engineered with information-dense compact ergonomics and responsive edge-to-edge drawing.

### 9.2 Key Component Hierarchy
1. **Global Stealth & Visibility Control Card**:
   - Master switches for **Smart Accessibility Scoping**, **Dynamic On-Demand Accessibility Mode**, **Lockscreen Notification Masking**, **Execution Log Masking**, **Overlay Suppression**, and **Clipboard Auto-Clear Timer** slider (10s to 300s).
   - High-contrast visual cards featuring informational tooltips and ADB privilege indicators (`WRITE_SECURE_SETTINGS`).
2. **Search & Category Filtering Toolbar**:
   - Real-time search text field querying application names and package IDs.
   - Horizontal scrolling category filter row utilizing `glassChip`:
     - `ALL`: Complete package catalog.
     - `EXCLUDED`: Active stealth isolation targets.
     - `BANKING`: Auto-tagged banking, UPI, and financial apps with high-visibility security shield badges.
     - `SYSTEM`: Android system packages.
     - `USER`: User-installed applications.
3. **Application List Rows (`AppInfoItem`)**:
   - 40dp rounded application icon loaded asynchronously via `AsyncImage` / `rememberAsyncImagePainter` with bitmap caching.
   - Two-line typography: Primary application title (`titleMedium`) and monospace package identifier (`bodySmall`).
   - Active status badges (`EXCLUDED`, `ISOLATED`, `BANKING`) in semantic alert colors.
   - Checkbox for multi-selection and click-through navigation to detailed configuration.
4. **Multi-Selection Floating Action Bar**:
   - Slides up when one or more applications are selected.
   - Quick batch actions: **Exclude All**, **Include All**, **Select All**, and **Clear Selection**.
5. **Granular Per-App Configuration Sheet (`AppPrivacyDetailDialog.kt`)**:
   - Material 3 modal bottom sheet with 7 distinct isolation switches:
     - *Exclude from Automation* (Master switch)
     - *Disable Direct Automation*
     - *Block Floating Overlays*
     - *Suppress Notifications*
     - *Disable Clipboard Access*
     - *Mask Content in Logs*
     - *Disable Auto-Reply Rules*
   - Custom notes input field for team/operator auditing.
   - Clean Action Bar with Save and Cancel actions.

---

*AutoText Design System v2.6 • Material 3 Expressive • 100% Native Jetpack Compose*
