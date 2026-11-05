# Proguard and R8 optimization rules for helper / O-O (Production Ready)

# Preserve annotations and line numbers for stack traces
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Data Models and Domain Entities (Preserve fields for SQLite mapping and JSON serialization)
-keep class com.jmd.autotext.data.model.** { *; }
-keepclassmembers class com.jmd.autotext.data.model.** { *; }

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
-keep class com.jmd.autotext.AutoTextApplication { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }

# Database and SQLite Helper
-keep class com.jmd.autotext.data.db.** { *; }
-keepclassmembers class com.jmd.autotext.data.db.** { *; }
