# AutoText — Executive Project Overview

## 1. Project Mission & Identity
**AutoText** (`com.jmd.autotext`) is a high-reliability, 100% on-device Android automation application designed for unattended multi-channel message dispatch and voice call automation.

## 2. Technical Profile
- **Package Identifier:** `com.jmd.autotext`
- **Application Version:** `1.0.0` (Version Code `1`)
- **Language:** 100% Idiomatic Kotlin (0% Java)
- **UI Framework:** Jetpack Compose with Material 3 Expressive & Glassmorphism Design System
- **Minimum SDK:** API 27 (Android 8.1 Oreo MR1)
- **Compile & Target SDK:** API 37 (Android 16+)
- **Build System:** Gradle 9.8.2 / AGP 9.4.1 / Kotlin 2.0.21
- **Architecture:** Clean Layered Architecture (UI / Domain / Engine / Data / Services / Compat)
- **Data Sovereignty:** 100% On-Device Sovereignty. Zero third-party cloud analytics, tracking, or network telemetry. No `android.permission.INTERNET` requested.

## 3. Core Capability Channels
1. **SMS (Cellular Modem Dispatch):** Multi-SIM subscription routing (SIM 1 / SIM 2), dynamic token replacement, quota limit management, multi-part PDU tracking.
2. **WhatsApp Direct Chat Automation:** Accessibility-driven direct chat messaging, phone normalization, media attachment support, auto-send confirmation.
3. **WhatsApp Business Direct Chat Automation:** Dedicated support for `com.whatsapp.w4b` with account isolation.
4. **WhatsApp Group Messaging:** Search-and-dispatch automation to group chats.
5. **WhatsApp Status Story Publishing:** Background posting of text and image/video statuses to personal WhatsApp Status.
6. **WhatsApp Business Status Publishing:** Background posting of text and media statuses to WhatsApp Business Status.
7. **Cellular Phone Calls:** TelecomManager and Intent-driven direct calls, explicit SIM account selection, call duration holding with early termination detection, and automatic hangup.
8. **WhatsApp VoIP Calls:** Automated outbound voice calls, connected status detection via conversation timer inspection, duration holding, and automated hangup.
9. **WhatsApp Business VoIP Calls:** Voice calling through WhatsApp Business with duration holding and auto-hangup.
10. **Simulated Incoming Calls (Fake Call):** Full-screen incoming call simulation with multi-tier ringtones, vibration motor pulsing, and realistic caller UI skins.
11. **System Reminders / Alarms:** Android native System Alarm Clock registration and heads-up alarm triggers.
12. **Sound Mode Profiles:** Do-Not-Disturb / Audio profile switcher (Normal, Vibrate, Silent) with volume level management.
13. **Contact Sequences & Drip Campaigns:** Multi-step follow-up sequences across SMS and WhatsApp with customizable delays.
14. **Daily Contact Campaigns:** Dynamic pool scanning from contacts/call-logs/SMS with paced dispatch throttles.
15. **Auto-Reply Engine:** Notification listener and broadcast-based keyword auto-reply rules for WhatsApp and SMS with cooldown guards.

## 4. Security & Safety Pillars
- **Bank-Safe Smart Standby Guard:** Dynamic on-demand detachment of Accessibility Service to ensure banking and UPI applications (Google Pay, PhonePe, Paytm, Saraswat Bank, YONO SBI, HDFC, ICICI, etc.) operate with zero interference or alerts.
- **Dedicated Protected Application Manager:** Strict default-deny policy, real-time context verification before any UI action, zero traversal or inspection of financial apps, and instant callback cancellation.
- **Automated Screen Wake & Keyguard Dismiss Engine:** Physical gesture-based PIN unlock for unattended scheduled automation execution.
- **Emergency Stop & Circuit Breakers:** Instant global emergency stop via persistent UI buttons or secure system broadcasts.
- **Orientation Invariant Guard:** Hard-locked portrait orientation ensuring UI automation coordinate consistency.

