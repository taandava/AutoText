# AutoText Material 3 Expressive Design System & UI Specification

> **The Authoritative Design System, Component Inventory & Ergonomic Specification for AutoText (`com.jmd.autotext`).**  
> *Engineered for High-Density Android Productivity, Zero-Popup Fluidity, and Expressive Glassmorphism.*

---

## 1. Design Philosophy: Expressive Glass & High-Density Usability

AutoText's interface is built on modern Material 3 Expressive principles combined with high-performance glassmorphism. It adheres strictly to the following ergonomic rules:

1. **Zero Unexpected Popups**: All primary configurations (dates, times, recurrence, SIM cards, call durations) are embedded inline within structured, scrollable cards. Users are never subjected to jarring full-screen modal interruptions.
2. **Compact Density & Zero Overflow**: On screens of any width (from compact 360dp phones to large foldable displays), typography, badges, and icon buttons adapt dynamically with text truncation (`TextOverflow.Ellipsis`) and strict sizing constraints (`26x26dp` for top actions).
3. **Harmonious Baseline Alignment**: Sibling form elements share identical container heights (`46.dp`), unified label typography, and matching corner radii (`ShapeSmall = 10.dp`).
4. **Offline Sovereignty & Transparent Status**: The system state (Stealth Guard, Orientation Guard, Accessibility, Notification Listener, Device Admin, Battery Whitelist, Master Engine) is permanently visible via 7 real-time status indicators in the header.

---

## 2. Core Design Tokens (`AppDesignTokens.kt`)

### 2.1 Spacing & Padding
- **Card Padding**: `12.dp` (compact) to `16.dp` (spacious)
- **Item Vertical Spacing**: `8.dp` to `10.dp`
- **Horizontal Row Gaps**: `6.dp` to `8.dp`
- **Field Height**: Exactly **`46.dp`** for all inputs, dropdowns, and inline buttons

### 2.2 Corner Radii
- **Card Radius (`ShapeLarge`)**: `16.dp`
- **Sub-Card / Surface Radius (`ShapeMedium`)**: `12.dp`
- **Form Input / Button Radius (`ShapeSmall`)**: `10.dp`
- **Pill / Indicator Radius (`ShapePill`)**: `50.dp` (CircleShape)

### 2.3 Expressive Palette & Tonal Elevation
- **Primary Accent (Cyan / Electric Blue)**: `#38BDF8` (Focus borders, primary buttons)
- **Secondary Accent (Indigo / Violet)**: `#818CF8` (System badges, alternate chips)
- **Tertiary Accent (Amber / Gold)**: `#FBBF24` (WhatsApp Status & scheduled triggers)
- **Success / Healthy**: `#00E676` (Active status indicators, verified deliveries)
- **Error / Kill Switch**: `#FF5252` (Emergency stop, critical alert badges)
- **Surface Elevation**: Subtle tonal surface containers (`surfaceContainerHigh` and `surfaceContainerHighest`) with animated alpha transitions.

---

## 3. Standardized Form Components

### 3.1 `glassInputField`
- **Container Height**: Fixed **`46.dp`** for single-line inputs.
- **Label Header**: `12.5.sp`, `FontWeight.SemiBold`, `start = 2.dp, bottom = 2.dp`.
- **Leading Icon**: `17.dp`, vertically centered.
- **Trailing Action**: `28.dp` touch target clipped with `CircleShape` (clear button or password toggle) that prevents container height jumping.

### 3.2 `glassDropdownField`
- **Container Height**: Fixed **`46.dp`** matching `glassInputField`.
- **Label Header**: Identical `12.5.sp`, `FontWeight.SemiBold` placement.
- **Border & Background**: Animated border color (`targetBorderColor`) and background highlight on expansion.
- **Visual Parity**: When placed in a `Row` alongside `glassInputField`, both fields align perfectly on top labels, bottom container bounds, and text baselines.

### 3.3 `glassButton`
- **Height**: Standard `46.dp` for inline action rows.
- **Variants**: `PRIMARY` (filled accent with subtle glow), `SECONDARY` (outline glass), `DANGER` (crimson warning).

---

## 4. Privacy & Stealth Management Screens

### 4.1 `PrivacySettingsScreen`
- **Top Summary Card**: Real-time badge showing shielded application count and master privacy toggle.
- **Toggles Section**:
  - Auto-Shield Banking & UPI Apps (automatic exclusion of known financial packages)
  - Smart Accessibility Scoping
  - Dynamic Zero-Detection Mode (On-Demand accessibility activation)
  - Content Masking on Lockscreen & Audit Logs
  - Overlay & Notification Suppression over Excluded Apps
- **Application Search & Filter Bar**:
  - Real-time search query matching app name and package ID
  - Filter chips: All, Shielded, Financial
  - Quick bulk actions: "Select All" and "Clear"
- **App List Items (`AppRowItem`)**:
  - Asynchronously loaded app icon with fallback letter avatar
  - App name, package ID, and "BANKING" badge
  - Quick exclusion toggle switch

### 4.2 `AppPrivacyDetailDialog`
Modal sheet providing granular 7-dimension control for individual applications:
1. Shield & Exclude Application
2. Block AutoText Automation Actions
3. Block Overlays & Wake Windows
4. Suppress Heads-Up Notifications
5. Disable Clipboard Read/Write
6. Mask Sensitive Content in Audit Logs
7. Disable Incoming Trigger Rules

---

## 5. Screen Orientation & Hardware Lock Protection

- **Hard-Locked Portrait Orientation**: Every activity (`MainActivity`, `FakeCallActivity`, `ReminderAlarmActivity`, `AutomationLauncherActivity`) specifies `android:screenOrientation="portrait"`.
- **System Invariant Guard**: `AccessibilityHealthManager.enforceAutoRotateInvariant` audits `Settings.System.ACCELEROMETER_ROTATION` and restores it to `0`.
- **Quick-Settings Shielding**: Accessibility node filtering strictly ignores rotation quick-setting tiles, guaranteeing zero screen rotation leakage during automated touch simulations.

---
*AutoText Design System Specification • Material 3 Expressive • 100% Production Ready*
