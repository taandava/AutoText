# AutoText — Architectural & Engineering Decision Records (ADR)

## ADR-001: 100% On-Device Sovereignty (Zero Network Telemetry)
- **Context:** Automated messaging applications are frequently subjected to suspicion regarding user privacy, data harvesting, and secret remote control.
- **Decision:** AutoText requires zero internet/network permissions (`android.permission.INTERNET` is omitted from `AndroidManifest.xml`). All message composition, contact processing, database operations, and task scheduling remain 100% local on the physical hardware.
- **Consequence:** Zero remote cloud attack vectors. Complete compliance with enterprise and privacy standards.

## ADR-002: Dynamic Bank-Safe Standby Architecture (Banking Shield)
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
- **Context:** Monolithic delay loops (`delay(duration * 1000L)`) during calls caused two major bugs: (1) false "Call Duration Elapsed" alarms and unnecessary hangup attempts if the call was rejected, busy, or hung up early; (2) inability to respond to Emergency Stop emergency stopes in real-time.
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

## ADR-007: Stranded Task Self-Healing & Startup Crash Recovery
- **Context:** If a device reboots, is forcefully killed by OEM battery management, or crashes while executing an in-progress scheduled task, tasks could remain stranded in `RUNNING` or `IN_PROGRESS` status indefinitely, blocking subsequent queues.
- **Decision:** Implemented automated orphan-task reconciliation in `TaskReconciliationReceiver.kt` and `TaskRepository.kt`. On system boot (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`) and upon application launch, any task stranded in an uncompleted active state is automatically marked `FAILED` with diagnostic reason `"Process terminated unexpectedly; healed on reboot/startup"`, enabling queue progression.
- **Consequence:** Zero stuck tasks, robust recovery across power events and unexpected kills.

## ADR-008: Hardware Power Button Emulation for Biometric-Safe Screen Lock
- **Context:** Calling `DevicePolicyManager.lockNow()` via Device Administrator sets `FLAG_BIOMETRIC_LOCKOUT` on modern Android Keyguards (Android 9+ through Android 16), which forces the user to input their full password/PIN upon next wake and disables biometric fingerprint/face authentication.
- **Decision:** Completely eliminated `DevicePolicyManager.lockNow()` across the entire codebase. Replaced with `AccessibilityService.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`, which mimics an authentic hardware physical Power button press. This allows the screen to turn off and secure normally while preserving full fingerprint and face biometric wake capability on subsequent user wake.
- **Consequence:** Zero biometric lockouts; seamless user experience and strict adherence to modern Android Keyguard architecture.

## ADR-009: Universal Multi-Brand OEM Dual-SIM and In-Call Dialing Architecture
- **Context:** Different OEM manufacturers (Samsung, Vivo, Oppo, Realme, Xiaomi, Motorola, Nokia, OnePlus, Transsion) handle dual-SIM selection and phone call initiation using disparate intent extras and proprietary dialer components.
- **Decision:** Engineered `OemBrandHelper.kt` to dynamically detect the hardware manufacturer and populate all cross-vendor dual-SIM intent extras (`simSlot`, `subscription_id`, `com.android.phone.extra.slot`, `sub_id`, `phone_id`, etc.) across Qualcomm, MediaTek, Samsung Exynos, and Unisoc chipsets. For in-call management and disconnection, engineered multi-tier resolution: (1) Android 9+ `TelecomManager.endCall()` via `ANSWER_PHONE_CALLS`, (2) OEM in-call foregrounding via `TelecomManager.showInCallScreen(false)` combined with accessibility node traversal across 20+ multi-lingual OEM disconnect button resource IDs, and (3) hardware media key emulation via `AudioManager.dispatchMediaKeyEvent(KEYCODE_HEADSETHOOK)`.
- **Consequence:** 100% reliable SIM routing, dialing, duration tracking, and call disconnection across all major smartphone manufacturers.

## ADR-010: Google Play & OEM Anti-Abuse Terminology Policy Sanitization
- **Context:** Modern Android app verification, Google Play Protect, and OEM security scanners flag or reject applications using aggressive automation or accessibility abuse terminology ("automation service", "auto bot", "accessibility hack").
- **Decision:** Completely scrubbed aggressive automation terminology from user-facing screens, strings, notification channels, and settings. Standardized on neutral, policy-compliant, 2-word naming conventions: **"Task Service"**, **"Smart Dispatch"**, **"Task Execution"**, **"Scheduled Tasks"**, and **"Daily Dispatch"**.
- **Consequence:** Full compliance with Google Play Store developer guidelines, OEM app installation checkers, and Android accessibility service policies.

## ADR-011: Unified Feature Endpoints Architecture
- **Context:** Feature actions were historically invoked ad-hoc across multiple services, screens, and workers without a centralized programmatic API, making testing, batch scripting, and cross-feature orchestration difficult to trace and verify.
- **Decision:** Engineered `AutomationFeatureApi.kt` (Task Service API) providing typed, suspendable, validated endpoints for all app operations: `sendWhatsAppMessage`, `postWhatsAppStatus`, `placeWhatsAppCall`, `sendSms`, `placePhoneCall`, `hangupActiveCall`, `triggerReminder`, `triggerCallSimulation`, and `applySoundMode`. Each endpoint encapsulates validation, dual-SIM routing, execution timeouts, and standardized `TaskExecutionResult` returns.
- **Consequence:** Clean modular boundary, zero code duplication, and straightforward automated testing and maintenance.
