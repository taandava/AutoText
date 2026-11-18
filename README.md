# Super

Super is a personal, fully on-device scheduler for Android. It queues one-off or recurring actions — SMS, WhatsApp messages, WhatsApp status posts, phone and WhatsApp calls, reminders, call simulations — and runs them at the time you set. Everything stays on the device: the app declares no `android.permission.INTERNET`.

- **Package:** `com.jmd.superapp` (debug builds install as `com.jmd.superapp.debug`)
- **Min SDK:** 27 (Android 8.1) · **Target/compile SDK:** 37
- **UI:** Jetpack Compose + Material 3

## Features

- **Tasks:** SMS, WhatsApp, WhatsApp Business, WhatsApp groups, WhatsApp status, calls, WhatsApp calls, call simulation and local reminders, each with one-off or repeating schedules.
- **Daily campaigns:** paced batch sending with a per-day quota and interval, plus per-recipient tracking.
- **Auto-reply:** keyword rules for incoming SMS and chat notifications.
- **Contact pools:** contacts harvested from the address book, SMS and calls, plus manually added recipients and WhatsApp groups.
- **History:** execution log of every dispatch with status and details, plus carrier delivery reports for SMS (`SMS Delivery` entries) so "sent" and "delivered" are not conflated.
- **Permissions on demand:** first launch asks only for notifications; SMS, call, contact and media access are requested from the Permissions centre when a feature needs them.
- **Reply safety:** automatic replies skip the device's own number and suppress identical repeats inside a cooldown, which prevents bot-to-bot reply loops.
- **Banking app protection:** banking/UPI packages are excluded from the task service by package scoping, so opening one never lets the service inspect it. The service is not torn down when a banking app opens; there is an optional *strict detach* switch in Preferences if you prefer it to step aside completely.
- **Screen assist:** optional PIN-based unlock for unattended runs, with the PIN stored in hardware-backed Keystore.
- **Backup/restore** of the local database to a JSON file you choose.

## Build and test

Requirements: JDK 21, Android SDK with platform 37 and build-tools 36.

```bash
./gradlew :app:testDebugUnitTest   # JVM unit tests
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:assembleRelease     # signed release (super-release.jks via keystore.properties)
```

Install and grant the write-secure-settings permission used for optional service auto-repair:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.jmd.superapp.debug android.permission.WRITE_SECURE_SETTINGS
```

## Signing

`keystore.properties` and `super-release.jks` are local files used to sign release builds. Keep them out of any repository you publish.

## Notes

- The task service only operates on WhatsApp, WhatsApp Business, dialer and system UI packages. Banking, wallet and UPI packages are never included in its scope.
- Permissions are requested at runtime from the Permissions center; nothing is granted silently.

## Documentation

- [`CHAT_MEMORY.md`](CHAT_MEMORY.md) — project memory: decisions, every fix, device verification, open items.
- [`docs/FILE_REFERENCE.md`](docs/FILE_REFERENCE.md) — per-file and per-declaration reference for all Kotlin sources.
- [`docs/README.md`](docs/README.md) — documentation index and subsystem map.
