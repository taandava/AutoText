# Building and running Super

## Project layout

```
app/
├── build.gradle.kts          # Android application config (namespace com.jmd.superapp)
├── proguard-rules.pro        # R8 rules for release
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/jmd/superapp/
    │   ├── api/              # task execution entry points
    │   ├── compat/           # OS/API compatibility shims
    │   ├── core/             # permissions, privacy, health, keyguard assist
    │   ├── data/             # SQLite, preferences, repositories
    │   ├── engine/           # dispatch engines (SMS, WhatsApp, sequencing)
    │   ├── receiver/         # alarms, boot, calls, SMS, watchdog
    │   ├── scheduler/        # AlarmManager and WorkManager scheduling
    │   ├── service/          # task service, notification listener, quick tile
    │   ├── telephony/        # native call orchestration
    │   ├── ui/               # Compose screens, components, theme
    │   └── whatsapp/         # WhatsApp automation engines
    └── res/
```

## Requirements

- JDK 21 (`org.gradle.java.home` in `gradle.properties` points at it)
- Android SDK platform 37, build-tools 36
- Gradle via the bundled wrapper (9.8.2)

## Commands

```bash
./gradlew :app:compileDebugKotlin     # fast compile check
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # signed, minified release APK
```

Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.jmd.superapp.debug android.permission.WRITE_SECURE_SETTINGS
```

The grant is optional and only enables service auto-repair and on-demand standby control. Every other permission is requested in-app from the Permissions center.

## Extending

- New task type: add to `data/model/ChannelType.kt`, handle it in `engine/SequentialTaskEngine.kt` and `api/TaskFeatureApi.kt`, then expose it in the composer (`ui/screen/compose/`).
- New screen: add a composable under `ui/screen/` and wire it into `ui/screen/MainAppHost.kt` or `ui/screen/MoreScreen.kt`.
- UI rules: use the shared components in `ui/components/CompactComponents.kt` and tokens in `ui/theme/Theme.kt`.
