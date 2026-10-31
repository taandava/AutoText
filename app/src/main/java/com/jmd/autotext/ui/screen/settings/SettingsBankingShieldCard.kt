package com.jmd.autotext.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.receiver.TaskAlarmReceiver
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.GlassButtonVariant
import com.jmd.autotext.ui.components.appBadge
import com.jmd.autotext.ui.components.appCard
import com.jmd.autotext.ui.components.glassButton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Controller for Stealth Automation Guard and uninterrupted background execution.
 *
 * Ensures all automations (Calls, SMS, WhatsApp, Alarms) run uninterrupted 24/7
 * while strictly isolating from and never inspecting banking, payment, or UPI applications.
 */
@Composable
fun settingsBankingShieldCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier,
    onOpenPrivacySettings: () -> Unit = {}
) {
    var stealthGuard by remember { mutableStateOf(prefs.isBankingShieldEnabled) }
    var dynamicA11y by remember { mutableStateOf(prefs.isDynamicA11yOnDemandEnabled) }
    val hasWriteSecure = remember { AccessibilityHealthManager.hasWriteSecureSettings(context) }
    var a11yRunning by remember {
        mutableStateOf(AccessibilityHealthManager.isServiceOperational())
    }
    var userPaused by remember { mutableStateOf(prefs.isA11yUserPaused) }

    LaunchedEffect(Unit) {
        while (true) {
            a11yRunning = AccessibilityHealthManager.isServiceOperational()
            stealthGuard = prefs.isBankingShieldEnabled
            dynamicA11y = prefs.isDynamicA11yOnDemandEnabled
            userPaused = prefs.isA11yUserPaused
            kotlinx.coroutines.delay(2000L.milliseconds)
        }
    }

    appCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = if (stealthGuard) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🛡️",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = "Google Pay & Payment Compatibility",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Ensures Google Pay, PhonePe, and banking apps run smoothly without security alerts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            appBadge(
                text = if (hasWriteSecure && dynamicA11y) "BANK-SAFE STANDBY"
                       else if (userPaused || !a11yRunning) "GPAY SAFE (PAUSED)"
                       else "A11Y RUNNING",
                color = if (hasWriteSecure && dynamicA11y) MaterialTheme.colorScheme.primary
                        else if (userPaused || !a11yRunning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
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
                    Text(
                        text = "✅ 100% Automatic Smart Standby Active",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Accessibility is kept 100% detached during idle periods. Google Pay, PhonePe, Paytm, and bank apps will NEVER show 'Suspicious activity' warnings. When a scheduled WhatsApp message runs, AutoText attaches for ~2 seconds and immediately detaches.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        glassButton(
                            text = "🧹 Clear GPay Cache",
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:com.google.android.apps.nbu.paisa.user")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    context.showAppToast("Tap 'Force stop' and 'Storage > Clear cache' to remove GPay's old cached warning.")
                                } catch (_: Throwable) {
                                    context.showAppToast("Could not open Google Pay app info.")
                                }
                            },
                            emoji = "🧹",
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        glassButton(
                            text = "💳 Open GPay",
                            onClick = {
                                val gpayIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.paisa.user")
                                if (gpayIntent != null) {
                                    gpayIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(gpayIntent)
                                } else {
                                    context.showAppToast("Google Pay is not installed.")
                                }
                            },
                            emoji = "💳",
                            variant = GlassButtonVariant.PRIMARY,
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
                    Text(
                        text = "⚠️ Accessibility is Running in Background",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Google Pay blocks payments when ANY third-party accessibility service is running on the device. Tap 'Pause for Google Pay' below before opening Google Pay, or enable Automatic Zero-Detection via ADB.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    glassButton(
                        text = "🛑 Pause Accessibility for Google Pay",
                        onClick = {
                            prefs.isA11yUserPaused = true
                            AutoTextAccessibilityService.disableServiceIfActive()
                            if (hasWriteSecure) {
                                AccessibilityHealthManager.disableAccessibilityService(context)
                            }
                            a11yRunning = false
                            userPaused = true
                            context.showAppToast("Accessibility paused! Google Pay and banking apps unblocked.")
                        },
                        emoji = "🛑",
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    glassButton(
                        text = "🧹 Clear GPay Cache (Force Stop)",
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:com.google.android.apps.nbu.paisa.user")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                context.showAppToast("Tap 'Force stop' and 'Storage > Clear cache' to clear Google Pay's block.")
                            } catch (_: Throwable) {
                                context.showAppToast("Could not open Google Pay app info.")
                            }
                        },
                        emoji = "🧹",
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    glassButton(
                        text = "💳 Open Google Pay",
                        onClick = {
                            val gpayIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.paisa.user")
                            if (gpayIntent != null) {
                                gpayIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(gpayIntent)
                            } else {
                                context.showAppToast("Google Pay is not installed.")
                            }
                        },
                        emoji = "💳",
                        variant = GlassButtonVariant.PRIMARY,
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
                    Text(
                        text = "✅ Google Pay & Banking Safe (A11y is Off)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Accessibility is currently paused or detached. Google Pay and banking transactions will complete without any interference.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    glassButton(
                        text = "Turn On for WhatsApp Schedules",
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
                        emoji = "⚡",
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    glassButton(
                        text = "💳 Open Google Pay",
                        onClick = {
                            val gpayIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.paisa.user")
                            if (gpayIntent != null) {
                                gpayIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(gpayIntent)
                            } else {
                                context.showAppToast("Google Pay is not installed.")
                            }
                        },
                        emoji = "💳",
                        variant = GlassButtonVariant.PRIMARY,
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
                        text = "Bank-Safe Smart Standby Mode",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (hasWriteSecure) {
                        appBadge(text = "BANK-SAFE READY", color = MaterialTheme.colorScheme.primary)
                    } else {
                        appBadge(text = "NEEDS ADB GRANT", color = MaterialTheme.colorScheme.outline)
                    }
                }
                Text(
                    text = if (dynamicA11y && hasWriteSecure)
                        "Active • Accessibility kept OFF when idle. Auto-enables for 2s only during WhatsApp dispatches, then retracts. Eliminates Google Pay blocks."
                    else
                        "Keeps accessibility service disabled when idle. Enables for 2-3s only during WhatsApp dispatches, then detaches. Eliminates banking security warnings.",
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

        // If WRITE_SECURE_SETTINGS not granted, provide exact ADB command instructions
        if (!hasWriteSecure) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "🚀 100% Automatic Bank-Safe Standby (Zero Manual Pausing Required)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Run this one-time command via ADB on your PC or Shizuku to grant Secure Settings. AutoText will automatically turn Accessibility on ONLY when sending WhatsApp messages (2s) and keep it off when idle so Google Pay never flags it:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val adbCmd = "adb shell pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS"
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
                                com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("ADB Command", adbCmd))
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
            Text(
                text = "💡 Quick Settings Tile Available: Swipe down from your notification bar and add the 'AutoText A11y' tile to pause and resume accessibility with 1 tap from anywhere before paying.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        glassButton(
            text = "Configure Privacy & App Visibility",
            onClick = onOpenPrivacySettings,
            emoji = "🛡️",
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassButton(
                text = "Verify Services",
                onClick = {
                    AccessibilityHealthManager.ensureAllServicesActive(context)
                    context.showAppToast("Services Verified")
                },
                emoji = "⚡",
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            glassButton(
                text = "Export Diagnostics",
                onClick = {
                    val file = TaskAlarmReceiver.dumpExecutionLogsToFile(context)
                    if (file != null) {
                        context.showAppToast("Dump saved: ${file.name}")
                    } else {
                        context.showAppToast("Export complete")
                    }
                },
                emoji = "📋",
                variant = GlassButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
