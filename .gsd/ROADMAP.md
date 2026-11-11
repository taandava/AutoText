---
milestone: Helper v1.3.0 Production Hardening
version: 1.3.0
updated: 2026-11-11T11:51:00+05:30
---

# Roadmap

> **Current Phase:** Phase 6 - Production Release & Live Hardware Validation
> **Status:** ✅ Complete / Active

## Must-Haves (from SPEC)

- [x] Zero network permissions (`android.permission.INTERNET` omitted)
- [x] 18 Autonomous Communication & Hardware channels operational
- [x] NPCI UPI & Google Pay Common Library accessibility isolation
- [x] WhatsApp group harvesting, backward reset, and prefix normalization
- [x] Biometric-safe screen wake and `GLOBAL_ACTION_LOCK_SCREEN` auto-lock
- [x] ContactSequenceStudio 3000-contact selection sheet rendering above Scaffold
- [x] 26/26 unit tests passing with zero failures
- [x] Physical device execution verified on Vivo / iQOO 13 (`10BF1G0HCD0024R`)

---

## Phases

### Phase 1: Core Architecture & Offline Data Sovereignty
**Status:** ✅ Complete  
**Objective:** Relational SQLite schema (16 tables), direct boot device-encrypted storage, hardware Keystore AES-256-GCM encryption, and complete elimination of network permissions.  
**Requirements:** REQ-01, REQ-06  

**Plans:**
- [x] Plan 1.1: SQLite `helper.db` normalized schema & DAO layer
- [x] Plan 1.2: Hardware Keystore master key wrapper (`EncryptedKeyguardStore.kt`)
- [x] Plan 1.3: PreferenceManager dual-key encrypted preferences

---

### Phase 2: Communication Channels & Automation Engine
**Status:** ✅ Complete  
**Objective:** Autonomous task dispatch across 18 operational channels including carrier SMS, WhatsApp direct/business/groups, telephony, call simulation, and reminders.  
**Requirements:** REQ-05, REQ-06  

**Plans:**
- [x] Plan 2.1: `SequentialTaskEngine.kt` concurrency mutex, queue channel, and sliced delays
- [x] Plan 2.2: `SmsSender.kt` multipart carrier SMS dispatch with TRAI 100/day quota guard
- [x] Plan 2.3: `CallSimulationActivity.kt` fullscreen incoming call simulator with audio/haptics

---

### Phase 3: Obfuscation-Resilient WhatsApp Discovery & Group Harvesting
**Status:** ✅ Complete  
**Objective:** Robust chat and group scanning supporting consumer WhatsApp and WhatsApp Business across dynamic UI variants.  
**Requirements:** REQ-05  

**Plans:**
- [x] Plan 3.1: Exact `android:id/list` targeting inside ViewPager fragments
- [x] Plan 3.2: 3-pass backward swipe reset (`topScrollAttempts < 3`)
- [x] Plan 3.3: Vertical bounding-box row validation (`40dp` - `180dp`, min 60% width)
- [x] Plan 3.4: Virtual entity indexing (`group:GroupName`) and search dispatch

---

### Phase 4: Biometric-Safe Keyguard Assistance & Auto-Lock
**Status:** ✅ Complete  
**Objective:** Unattended device wake, keyguard PIN assistance, and hardware screen locking preserving fingerprint and face unlock trust.  
**Requirements:** REQ-04  

**Plans:**
- [x] Plan 4.1: `TaskLauncherActivity.kt` background trampoline and keyguard dismissal
- [x] Plan 4.2: `KeyguardAssistEngine.kt` assistive touch PIN entry and cooldown abort
- [x] Plan 4.3: `GLOBAL_ACTION_LOCK_SCREEN` power button simulation

---

### Phase 5: NPCI UPI / Google Pay Shield & Dynamic Standby
**Status:** ✅ Complete  
**Objective:** Complete isolation from banking and financial applications to ensure Google Pay, PhonePe, and UPI payments work with zero accessibility conflicts.  
**Requirements:** REQ-02, REQ-03  

**Plans:**
- [x] Plan 5.1: Removal of all banking packages from `accessibility_service_config.xml`
- [x] Plan 5.2: Elimination of `flagRetrieveInteractiveWindows`
- [x] Plan 5.3: Strict non-null invariant on `serviceInfo.packageNames` in `PrivacyManager.kt`
- [x] Plan 5.4: Bank-Safe Dynamic On-Demand Standby (0% presence in secure settings when idle)
- [x] Plan 5.5: Quick Settings 1-Tap Pause Tile (`TaskTileService.kt`)

---

### Phase 6: Production Release & Live Hardware Validation
**Status:** ✅ Complete  
**Objective:** Verification on target hardware (`10BF1G0HCD0024R`, Vivo / iQOO 13, Android 16 API 36), unit test certification (26/26 tests passing), signed production release APK (`app-release.apk`), and live Google Pay payment validation.  
**Requirements:** All REQs  

**Plans:**
- [x] Plan 6.1: Unit test certification via `.\gradlew :app:testDebugUnitTest` (26/26 passed)
- [x] Plan 6.2: Signed production APK generation via `.\gradlew :app:assembleRelease`
- [x] Plan 6.3: Streamed install and permission granting on device `10BF1G0HCD0024R`
- [x] Plan 6.4: Live Google Pay payment verification on device
- [x] Plan 6.5: Documentation synchronization across SRS, README, and assets
