# Proguard and R8 optimization rules for Super (Production Ready)

# Preserve annotations and line numbers for stack traces
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Data Models and Domain Entities (Preserve fields for SQLite mapping and JSON serialization)
-keep class com.jmd.supersms.data.model.** { *; }
-keepclassmembers class com.jmd.supersms.data.model.** { *; }

# WorkManager Workers and Factory classes
-keep class * extends androidx.work.Worker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Android System Services and Architecture Components
-keep class com.jmd.supersms.SuperApplication { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }

# Database and SQLite Super
-keep class com.jmd.supersms.data.db.** { *; }
-keepclassmembers class com.jmd.supersms.data.db.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}

# Room databases (also used internally by WorkManager, reflected by class name)
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepnames class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Jetpack DataStore
-keep class androidx.datastore.** { *; }

# AndroidX Security Crypto & Keystore
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.joda.time.**

# AndroidX Biometric
-keep class androidx.biometric.** { *; }

# AndroidX Startup
-keep class androidx.startup.** { *; }
-keep class com.jmd.supersms.core.SuperAppInitializer { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Jetpack Glance AppWidgets
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
-keep class com.jmd.supersms.ui.widget.** { *; }

# AndroidX Window Manager & Posture Detection
-keep class androidx.window.** { *; }
-dontwarn androidx.window.**

# AndroidX Tracing
-keep class androidx.tracing.** { *; }
-dontwarn androidx.tracing.**
