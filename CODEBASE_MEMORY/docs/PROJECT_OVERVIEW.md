# AutoText — Executive Project Overview

## 1. Project Mission & Identity
**AutoText** (`com.jmd.autotext`) is a high-reliability, 100% on-device Android communication and task scheduling application designed for unattended multi-channel message dispatch and voice call execution.

## 2. Technical Profile
- **Package Identifier:** `com.jmd.autotext`
- **Application Version:** `1.0.0` (Version Code `1`)
- **Language:** 100% Idiomatic Kotlin (0% Java)
- **UI Framework:** Jetpack Compose with Material 3 Expressive & Glassmorphism Design System
- **Minimum SDK:** API 27 (Android 8.1 Oreo MR1)
- **Compile & Target SDK:** API 37 (Android 16+)
- **Build System:** Gradle 9.8.2 / AGP 9.4.1 / Kotlin 2.0.21
- **Architecture:** Clean Layered Architecture (UI / Domain / Engine / Data / Services / Compat / API)
- **Data Sovereignty:** 100% On-Device Sovereignty. Zero third-party cloud analytics, tracking, or network telemetry. No `android.permission.INTERNET` requested.
- **Policy Compliance:** 100% compliant with Google Play and Android anti-abuse policies ("Task", 2-word terminology conventions).

## 3. Core Capability Channels
1. **SMS (Cellular Modem Dispatch):** Multi-SIM subscription routing (SIM 1 / SIM 2), dynamic token replacement, quota limit management, multi-part PDU tracking across Qualcomm, MediaTek, Samsung Exynos, and Unisoc chipsets.
2. **WhatsApp Direct Chat:** Accessibility-driven direct chat messaging, phone normalization, media attachment support, semantic Send button resolution with Voice Note protection.
3. **WhatsApp Business Direct Chat:** Dedicated support for `com.whatsapp.w4b` with account isolation.
4. **WhatsApp Group Messaging:** Search-and-dispatch task dispatch to group chats.
5. **WhatsApp Status Story Publishing:** Background posting of text and image/video statuses to personal WhatsApp Status.
6. **WhatsApp Business Status Publishing:** Background posting of text and media statuses to WhatsApp Business Status.
7. **Cellular Phone Calls:** Universal multi-brand OEM dual-SIM dialing (Samsung, Vivo, Oppo, Realme, Xiaomi, Motorola, Nokia, Transsion), exact call duration holding with early termination detection, and multi-tier scheduled hangup (`ANSWER_PHONE_CALLS`, `TelecomManager.endCall()`, in-call accessibility node traversal, media key emulation).
8. **WhatsApp VoIP Calls:** Outbound voice calls, dropdown vs toolbar disambiguation, connected status detection via conversation timer inspection, duration holding, and scheduled hangup.
9. **WhatsApp Business VoIP Calls:** Voice calling through WhatsApp Business with duration holding and auto-hangup.
10. **Simulated Incoming Calls (Call Simulation):** Full-screen incoming call simulation with multi-tier ringtones, vibration motor pulsing, and realistic caller UI skins.
11. **System Reminders / Alarms:** Android native System Alarm Clock registration and heads-up alarm triggers.
12. **Sound Mode Profiles:** Do-Not-Disturb / Audio profile switcher (Normal, Vibrate, Silent) with volume level management.
13. **Contact Sequences & Drip Campaigns:** Multi-step follow-up sequences across SMS and WhatsApp with customizable delays.
14. **Daily Contact Campaigns:** Dynamic pool scanning from contacts/call-logs/SMS with paced dispatch throttles.
15. **Auto-Reply Engine:** Notification listener and broadcast-based keyword auto-reply rules for WhatsApp and SMS with cooldown guards.
16. **Unified Programmatic API:** `TaskFeatureApi.kt` providing typed, suspendable endpoints for all features.

## 4. Security & Safety Pillars
- **Bank-Safe Smart Standby Guard:** Dynamic on-demand detachment of Accessibility Service to ensure banking and UPI applications (Google Pay, PhonePe, Paytm, Saraswat Bank, YONO SBI, HDFC, ICICI, etc.) operate with zero interference or alerts.
- **Dedicated Protected Application Manager:** Strict default-deny policy, real-time context verification before any UI action, zero traversal or inspection of financial apps, and instant callback cancellation.
- **Biometric-Safe Hardware Power Auto-Lock:** Emulates physical hardware Power button press (`GLOBAL_ACTION_LOCK_SCREEN`) rather than `DevicePolicyManager.lockNow()`, preserving fingerprint and face biometric wake.
- **Scheduled Screen Wake & Keyguard Dismiss Engine:** Physical gesture-based PIN unlock for unattended scheduled task execution.
- **Emergency Stop & Circuit Breakers:** Instant global emergency stop via persistent UI buttons or secure system broadcasts.
- **Orientation Invariant Guard:** Hard-locked portrait orientation ensuring UI coordinate consistency.
- **Orphan Task Auto-Reconciliation:** Quick healing of interrupted or reboot-stranded tasks.
