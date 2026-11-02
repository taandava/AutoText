# AutoText — Architectural & Engineering Decision Records (ADR)

## ADR-001: 100% On-Device Sovereignty (Zero Network Telemetry)
- **Context:** Automated messaging applications are frequently subjected to suspicion regarding user privacy, data harvesting, and secret remote control.
- **Decision:** AutoText requires zero internet/network permissions (`android.permission.INTERNET` is omitted from `AndroidManifest.xml`). All message composition, contact processing, database operations, and task scheduling remain 100% local on the physical hardware.
- **Consequence:** Zero remote cloud attack vectors. Complete compliance with enterprise and privacy standards.

## ADR-002: Dynamic Zero-Detection Stealth Architecture (Banking Shield)
- **Context:** Modern banking and UPI apps (Google Pay, PhonePe, Paytm, YONO SBI, HDFC Bank, etc.) inspect `AccessibilityManager.getEnabledAccessibilityServiceList()` and block operations if any third-party accessibility service is enabled.
- **Decision:** 
  1. Accessibility configuration is strictly scoped with `android:packageNames` in `accessibility_service_config.xml` to WhatsApp, Phone/Dialer, and SystemUI.
  2. With `WRITE_SECURE_SETTINGS` granted via ADB or root, AutoText dynamically registers its service only when an automation task fires, and immediately unbinds/detaches upon completion (`releaseAccessibilityAfterAutomation`).
  3. When an excluded or banking app is foregrounded, `AutoTextAccessibilityService` immediately calls `disableSelf()`.
  4. Quick Settings Tile (`AutoTextA11yTileService`) provides an instant 1-tap manual pause/resume option.
- **Consequence:** Banking apps never detect AutoText running in the background.

## ADR-003: Dedicated Protected Application Manager (Default-Deny Policy)
- **Context:** Automation applications must never risk touching, inspecting, or injecting gestures into financial or payment apps.
- **Decision:** Engineered `ProtectedApplicationManager.kt` which implements a default-deny policy. All UI interactions require explicit target verification immediately prior to tap dispatch. If an unauthorized or banking package is detected, all pending callbacks are dropped and the operation is marked safely interrupted.
- **Consequence:** Complete isolation of banking, investment, wallet, and UPI workflows.

## ADR-004: Sliced Polling with Early Call Termination Detection
- **Context:** Monolithic delay loops (`delay(duration * 1000L)`) during calls caused two major bugs: (1) false "Call Duration Elapsed" alarms and unnecessary hangup attempts if the call was rejected, busy, or hung up early; (2) inability to respond to Emergency Stop kill switches in real-time.
- **Decision:** Replaced monolithic delays with 250ms sliced polling loops observing `TelephonyManagerCompat` (multi-SIM call state), TelecomManager, and in-call accessibility nodes. If the call concludes early or Emergency Stop is asserted, the wait loop terminates immediately.
- **Consequence:** Responsive, error-free call holding and prompt emergency stop reaction.

## ADR-005: Physical Gesture Coordinate Tap for Keyguard PIN Auto-Unlock
- **Context:** Modern Android lockscreens reject synthetic keyboard event injection (`ACTION_SET_TEXT`) into the Keyguard PIN bouncer field.
- **Decision:** Engineered physical gesture tap emulation in `KeyguardUnlockEngine.kt` that queries PIN keypad digit node bounds and dispatches `dispatchGesture` with `Path` stroke taps directly to physical screen coordinates.
- **Consequence:** Unattended automated tasks execute reliably on secured devices.

## ADR-006: Hard-Locked Portrait Orientation Invariant
- **Context:** Quick-settings panel interactions or screen rotation can disrupt accessibility gesture coordinates and Jetpack Compose bounds.
- **Decision:** Hard-locked portrait orientation in `AndroidManifest.xml` across all activities and enforced `Settings.System.ACCELEROMETER_ROTATION = 0` via `AccessibilityHealthManager.enforceAutoRotateInvariant`.
- **Consequence:** Deterministic coordinate spaces for all touch interactions.

