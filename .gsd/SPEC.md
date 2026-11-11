# SPEC.md — Helper Project Specification

> **Status**: `FINALIZED`
> **Version**: 1.3.0 (Build 4)
> **Platform**: Android 8.1 - 16+ (API 27 - 37)
> **Compliance**: 100% On-Device Sovereignty • Zero Internet Permissions • Banking App Protection Shield

## Vision
Helper is a commercial-grade, high-reliability autonomous hardware and communication task automation engine built natively for Android. Operating on a pure zero-trust, 100% offline security architecture, Helper omits `android.permission.INTERNET` entirely, guaranteeing complete data sovereignty and strict compliance with Google Play Store policies, NPCI UPI security frameworks, and Android Accessibility guidelines.

## Goals
1. **Autonomous 18-Channel Task Automation** — Unattended scheduling and execution across SMS, WhatsApp direct, WhatsApp business, WhatsApp groups, status publishing, calls, call simulation, alarms, audio recording, and hardware toggles.
2. **Absolute Data Sovereignty** — Zero network permissions (`android.permission.INTERNET` omitted); all credentials, task schedules, logs, and contacts reside exclusively on-device in SQLite and device-encrypted storage.
3. **NPCI UPI & Banking App Protection Shield** — Complete isolation from financial applications (Google Pay, PhonePe, Paytm, BHIM, 60+ banks). Zero accessibility inspection or interference with payment flows via Dynamic On-Demand Standby.
4. **Biometric-Safe Hardware Auto-Lock** — Unattended keyguard PIN entry assistance with post-send screen locking via `GLOBAL_ACTION_LOCK_SCREEN`, preserving fingerprint and face unlock trust.
5. **Zero Code Warning Suppressions** — Strict adherence to modern Android and Kotlin idioms with 0 `@Suppress` annotations in handwritten code.

## Non-Goals (Out of Scope)
- Cloud synchronization, remote telemetry, or web scraping (violates zero-internet invariant).
- Sideloaded package installations or root exploit execution.
- Interception, logging, or harvesting of financial data, passwords, OTPs, or UPI PINs.
- Continuous persistent foreground service notifications when idle.

## Constraints
- Zero `android.permission.INTERNET` in `AndroidManifest.xml`.
- Screen display orientation hard-locked to portrait (`ACCELEROMETER_ROTATION = 0`).
- Strict NPCI Common Library isolation (`accessibility_service_config.xml` without banking packages, no `flagRetrieveInteractiveWindows`, non-null `info.packageNames`).
- TRAI regulatory quota: maximum 100 SMS per calendar day per SIM slot.
- Minimum 80% test coverage with 100% test pass rate.

## Success Criteria
- [x] All 26 unit tests passing cleanly (`testDebugUnitTest`).
- [x] Full R8 release compilation passing with signed APK generation (`assembleRelease`).
- [x] Verified on physical hardware (`10BF1G0HCD0024R`, Vivo / iQOO 13, Android 16 API 36).
- [x] Google Pay and UPI payment flows operational without accessibility blocking.
- [x] WhatsApp group harvesting and search dispatch operational with 100% accuracy.
- [x] ContactSequenceStudio 3000-contact selection sheet rendering above Scaffold without occlusion.

## Technical Requirements

| Requirement | Priority | Notes |
|-------------|----------|-------|
| REQ-01: Offline Data Sovereignty | Must-have | Zero internet permission, private SQLite storage |
| REQ-02: Banking App Protection Shield | Must-have | Default-deny isolation for 60+ banking/UPI packages |
| REQ-03: Dynamic On-Demand Standby | Must-have | Accessibility detached from settings while idle |
| REQ-04: Biometric-Safe Lock | Must-have | `GLOBAL_ACTION_LOCK_SCREEN` on API 28+ |
| REQ-05: WhatsApp Discovery & Dispatch | Must-have | Exact `android:id/list` targeting, backward reset passes |
| REQ-06: 18 Autonomous Channels | Must-have | SMS, WhatsApp, Calls, Reminders, Simulators, Hardware |
| REQ-07: Material 3 Expressive UI | Must-have | Glassmorphic cards, 8-point layout grid, vector icons |

---

*Last updated: 2026-11-11*
