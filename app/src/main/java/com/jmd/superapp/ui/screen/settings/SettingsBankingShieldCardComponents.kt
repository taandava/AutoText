/**
 * Package: com.jmd.superapp.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsBankingShieldCardComponents].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsBankingShieldCardComponents.
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.superapp.receiver.TaskAlarmReceiver]
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.text.font.FontFamily]
 * - Core Security & Utilities: [android.content.ClipboardManager], [androidx.core.net.toUri], [com.jmd.superapp.core.AccessibilityHealthManager], [com.jmd.superapp.core.showAppToast]
 * - Android Framework & Jetpack: [android.content.ClipData], [android.provider.Settings], [androidx.compose.foundation.background], [androidx.compose.foundation.border]
 */

package com.jmd.superapp.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.showAppToast
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.receiver.TaskAlarmReceiver
import com.jmd.superapp.service.TaskAccessibilityService
import com.jmd.superapp.ui.components.AppButtonVariant
import com.jmd.superapp.ui.components.AppBadge
import com.jmd.superapp.ui.components.AppCard
import com.jmd.superapp.ui.components.AppButton
import kotlin.time.Duration.Companion.milliseconds

/**
 * What it is:
 * Controller for Banking Shield Guard and uninterrupted background execution.
 *
 * What it does:
 * Ensures all tasks (Calls, SMS, WhatsApp, Alarms) run uninterrupted 24/7 while strictly isolating from and never inspecting banking, payment, or UPI applications.
 *
 * Linked with:
 * - Architecture: [SettingsBankingShieldCardComponents], [SettingsBankingShieldCard]
 */
@Composable
fun SettingsBankingShieldCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier,
    onOpenPrivacySettings: () -> Unit = {}
) {
    var isBankingShieldActive by remember { mutableStateOf(prefs.isBankingShieldEnabled) }
    var dynamicA11y by remember { mutableStateOf(prefs.isDynamicA11yOnDemandEnabled) }
    var strictBankingDetach by remember { mutableStateOf(prefs.isStrictBankingDetachEnabled) }
    val hasWriteSecure = remember { AccessibilityHealthManager.hasWriteSecureSettings(context) }
    var a11yRunning by remember {
        mutableStateOf(AccessibilityHealthManager.isServiceOperational())
    }
    var userPaused by remember { mutableStateOf(prefs.isA11yUserPaused) }

    LaunchedEffect(Unit) {
        while (true) {
            // isServiceOperational() crosses a binder boundary; keep it off the frame thread.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                a11yRunning = AccessibilityHealthManager.isServiceOperational()
                isBankingShieldActive = prefs.isBankingShieldEnabled
                dynamicA11y = prefs.isDynamicA11yOnDemandEnabled
                strictBankingDetach = prefs.isStrictBankingDetachEnabled
                userPaused = prefs.isA11yUserPaused
            }
            kotlinx.coroutines.delay(5000L.milliseconds)
        }
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = if (isBankingShieldActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).padding(end = 6.dp)
                )
                Column {
                    Text(
                        text = "Banking Shield",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Ensures UPI, payment, and banking apps run smoothly without accessibility conflicts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            AppBadge(
                text = "SHIELD ACTIVE",
                color = MaterialTheme.colorScheme.primary,
                showDot = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Google Pay & Banking status alert box
        if (hasWriteSecure && dynamicA11y) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Smart Standby",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Task is kept 100% detached during idle periods. UPI, payment, and banking apps will run without service warnings. When a scheduled task runs, Super attaches for ~2 seconds and immediately detaches.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppButton(
                            text = "Clear Cache",
                            onClick = {
                                try {
                                    val pm = context.packageManager
                                    val candidatePackages = listOf(
                                        "com.google.android.apps.nbu.paisa.user",
                                        "com.phonepe.app",
                                        "net.one97.paytm",
                                        "in.org.npci.upiapp",
                                        "com.sbi.lotusintouch",
                                        "com.yono.sbi",
                                        "com.dreamplug.androidapp"
                                    )
                                    val target = candidatePackages.firstOrNull { pkg ->
                                        try { pm.getPackageInfo(pkg, 0); true } catch (e: Throwable) {
                                            android.util.Log.d("SettingsBankingShieldCard", "Package $pkg not installed: ${e.message}")
                                            false
                                        }
                                    } ?: "com.google.android.apps.nbu.paisa.user"
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = "package:$target".toUri()
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    context.showAppToast("Tap 'Force stop' and 'Storage > Clear cache' to remove cached warnings.")
                                } catch (e: Throwable) {
                                    android.util.Log.w("SettingsBankingShieldCard", "Could not open payment app info: ${e.message}")
                                    context.showAppToast("Could not open payment app info.")
                                }
                            },
                            icon = Icons.Filled.CleaningServices,
                            variant = AppButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            text = "Open App",
                            onClick = {
                                val pm = context.packageManager
                                val paymentPackages = listOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp")
                                var launched = false
                                for (pkg in paymentPackages) {
                                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                                    if (launchIntent != null) {
                                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(launchIntent)
                                        launched = true
                                        break
                                    }
                                }
                                if (!launched) {
                                    val upiIntent = Intent(Intent.ACTION_VIEW, "upi://pay".toUri()).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    if (upiIntent.resolveActivity(pm) != null) {
                                        context.startActivity(Intent.createChooser(upiIntent, "Open App").apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    } else {
                                        context.showAppToast("No banking or payment application found.")
                                    }
                                }
                            },
                            icon = Icons.Filled.CreditCard,
                            variant = AppButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else if (a11yRunning) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Service Active",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Some banking and UPI payment apps flag active third-party accessibility services. Tap 'Pause Assistant for Banking' below before opening your banking apps, or enable Bank-Safe Smart Standby via ADB.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppButton(
                        text = "Pause Service",
                        onClick = {
                            prefs.isA11yUserPaused = true
                            TaskAccessibilityService.disableServiceIfActive()
                            if (hasWriteSecure) {
                                AccessibilityHealthManager.disableAccessibilityService(context)
                            }
                            a11yRunning = false
                            userPaused = true
                            context.showAppToast("Accessibility paused! Banking and payment apps isolated.")
                        },
                        icon = Icons.Filled.StopCircle,
                        variant = AppButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppButton(
                        text = "Clear Cache",
                        onClick = {
                            try {
                                val pm = context.packageManager
                                val candidatePackages = listOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm")
                                val target = candidatePackages.firstOrNull { pkg ->
                                    try { pm.getPackageInfo(pkg, 0); true } catch (e: Throwable) {
                                        android.util.Log.d("SettingsBankingShieldCard", "Package $pkg not installed: ${e.message}")
                                        false
                                    }
                                } ?: "com.google.android.apps.nbu.paisa.user"
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:$target".toUri()
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                context.showAppToast("Tap 'Force stop' and 'Storage > Clear cache' to clear cached blocks.")
                            } catch (e: Throwable) {
                                android.util.Log.w("SettingsBankingShieldCard", "Could not open payment app info: ${e.message}")
                                context.showAppToast("Could not open payment app info.")
                            }
                        },
                        icon = Icons.Filled.CleaningServices,
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppButton(
                        text = "Open App",
                        onClick = {
                            val pm = context.packageManager
                            val paymentPackages = listOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp")
                            var launched = false
                            for (pkg in paymentPackages) {
                                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(launchIntent)
                                    launched = true
                                    break
                                }
                            }
                            if (!launched) {
                                val upiIntent = Intent(Intent.ACTION_VIEW, "upi://pay".toUri()).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                if (upiIntent.resolveActivity(pm) != null) {
                                    context.startActivity(Intent.createChooser(upiIntent, "Open App").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                } else {
                                    context.showAppToast("No banking or payment application found.")
                                }
                            }
                        },
                        icon = Icons.Filled.CreditCard,
                        variant = AppButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Banking Safe",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Accessibility is currently paused or detached. Banking and payment transactions will complete without any interference.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppButton(
                        text = "Resume Service",
                        onClick = {
                            prefs.isA11yUserPaused = false
                            if (hasWriteSecure) {
                                AccessibilityHealthManager.enableAccessibilityService(context)
                                a11yRunning = true
                                userPaused = false
                                context.showAppToast("Accessibility service resumed.")
                            } else {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        },
                        icon = Icons.Filled.Bolt,
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppButton(
                        text = "Open App",
                        onClick = {
                            val pm = context.packageManager
                            val paymentPackages = listOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp")
                            var launched = false
                            for (pkg in paymentPackages) {
                                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(launchIntent)
                                    launched = true
                                    break
                                }
                            }
                            if (!launched) {
                                val upiIntent = Intent(Intent.ACTION_VIEW, "upi://pay".toUri()).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                if (upiIntent.resolveActivity(pm) != null) {
                                    context.startActivity(Intent.createChooser(upiIntent, "Open App").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                } else {
                                    context.showAppToast("No banking or payment application found.")
                                }
                            }
                        },
                        icon = Icons.Filled.CreditCard,
                        variant = AppButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Zero-Detection Mode Switch (for privileged users)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Smart Standby",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (hasWriteSecure) {
                        AppBadge(text = "SHIELD READY", color = MaterialTheme.colorScheme.primary)
                    } else {
                        AppBadge(text = "NEEDS PERMISSION", color = MaterialTheme.colorScheme.outline)
                    }
                }
                Text(
                    text = if (dynamicA11y && hasWriteSecure)
                        "Active • Accessibility kept OFF when idle. Auto-enables briefly during task dispatches, then retracts. Eliminates banking and payment app conflicts."
                    else
                        "Keeps accessibility service disabled when idle. Enables briefly only during task dispatches, then detaches. Eliminates banking and payment security warnings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = dynamicA11y,
                enabled = hasWriteSecure,
                onCheckedChange = { enable ->
                    dynamicA11y = enable
                    prefs.isDynamicA11yOnDemandEnabled = enable
                    if (enable) {
                        AccessibilityHealthManager.disableAccessibilityService(context)
                        context.showAppToast("Smart Standby Active: A11y Off When Idle")
                    } else {
                        AccessibilityHealthManager.enableAccessibilityService(context)
                        context.showAppToast("Continuous Accessibility Active")
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Strict Banking Detach Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Strict Detach",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Temporarily detach the task service while a banking or UPI payment app is active on screen. Off by default because package isolation already protects banking apps without interrupting scheduled tasks.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = strictBankingDetach,
                onCheckedChange = { enable ->
                    strictBankingDetach = enable
                    prefs.isStrictBankingDetachEnabled = enable
                    context.showAppToast(if (enable) "Strict Banking Detach Enabled" else "Strict Banking Detach Disabled")
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }

        // If WRITE_SECURE_SETTINGS not granted, provide exact ADB command instructions
        if (!hasWriteSecure) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.RocketLaunch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Safe Standby",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Run this one-time command via ADB on your PC or Shizuku to grant Secure Settings. Super will turn Accessibility on ONLY when sending messages and keep it off when idle so banking and payment apps never flag it:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val adbCmd = "adb shell pm grant com.jmd.superapp android.permission.WRITE_SECURE_SETTINGS"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = adbCmd,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                com.jmd.superapp.compat.ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("ADB Command", adbCmd))
                                context.showAppToast("Copied ADB command to clipboard!")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Command",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Settings Tile Tip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Quick Settings Tile Available: Swipe down from your notification bar and add the 'Super A11y' tile to pause and resume accessibility with 1 tap from anywhere before paying.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AppButton(
            text = "App Privacy",
            onClick = onOpenPrivacySettings,
            icon = Icons.Filled.Shield,
            variant = AppButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppButton(
                text = "Verify Services",
                onClick = {
                    AccessibilityHealthManager.ensureAllServicesActive(context)
                    context.showAppToast("Services Verified")
                },
                icon = Icons.Filled.Bolt,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            AppButton(
                text = "Export Diagnostics",
                onClick = {
                    val file = TaskAlarmReceiver.dumpExecutionLogsToFile(context)
                    if (file != null) {
                        context.showAppToast("Dump saved: ${file.name}")
                    } else {
                        context.showAppToast("Export complete")
                    }
                },
                icon = Icons.Filled.ContentCopy,
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
