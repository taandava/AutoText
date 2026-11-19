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
    modifier: Modifier = Modifier
) {
    val hasWriteSecure = remember { AccessibilityHealthManager.hasWriteSecureSettings(context) }

    LaunchedEffect(Unit) {
        // Enforce intelligent banking protection under the hood
        prefs.isBankingShieldEnabled = true
        prefs.isSmartAccessibilityScopingEnabled = true
        if (hasWriteSecure) {
            prefs.isDynamicA11yOnDemandEnabled = true
        }
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = "Select apps to isolate from accessibility conflicts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            AppBadge(
                text = "Active",
                color = MaterialTheme.colorScheme.primary,
                showDot = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        // Owner-curated list of banking/payment apps. All checked packages are automatically
        // isolated with zero-detection standby, event filtering, and overlay suppression.
        ProtectedAppsSection(context = context, prefs = prefs)

        Spacer(modifier = Modifier.height(12.dp))

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppButton(
                text = "Verify",
                onClick = {
                    AccessibilityHealthManager.ensureAllServicesActive(context)
                    context.showAppToast("Services Verified")
                },
                icon = Icons.Filled.Bolt,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            AppButton(
                text = "Export Logs",
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
