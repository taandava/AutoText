package com.jmd.autotext.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.receiver.TaskAlarmReceiver
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens
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

    LaunchedEffect(Unit) {
        while (true) {
            a11yRunning = AccessibilityHealthManager.isServiceOperational()
            stealthGuard = prefs.isBankingShieldEnabled
            dynamicA11y = prefs.isDynamicA11yOnDemandEnabled
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
                        text = "Stealth Automation Guard",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Uninterrupted automation with banking app isolation",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            appBadge(
                text = if (stealthGuard) "STEALTH GUARD ACTIVE" else if (a11yRunning) "WA HELPER ON" else "STANDBY",
                color = if (stealthGuard) MaterialTheme.colorScheme.primary else if (a11yRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                showDot = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Informational explanation banner
        Surface(
            color = if (stealthGuard) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else AppDesignTokens.GlassSurfaceSubtle,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "AutoText automations (Calls, SMS, WhatsApp, Alarms) run uninterrupted 24/7 in the background. With Stealth Guard active, the engine completely isolates from and never inspects banking, UPI, or financial apps (SBI YONO, Google Pay, PhonePe, Paytm, etc.), preventing any interference or accessibility detection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stealth Protection Mode",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (stealthGuard) "Active by default • All automations run uninterrupted with banking app isolation" else "Standard mode • All automations running normally",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = stealthGuard,
                onCheckedChange = { enable ->
                    stealthGuard = enable
                    prefs.isBankingShieldEnabled = enable
                    AccessibilityHealthManager.ensureAllServicesActive(context)
                    context.showAppToast(if (enable) "Stealth Guard Active: Complete Banking Isolation" else "Standard Automation Mode")
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dynamic Zero-Detection Mode",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (hasWriteSecure) {
                        appBadge(text = "ZERO DETECTION", color = MaterialTheme.colorScheme.primary)
                    } else {
                        appBadge(text = "NEEDS PERMISSION", color = MaterialTheme.colorScheme.outline)
                    }
                }
                Text(
                    text = if (dynamicA11y)
                        "Active • Accessibility kept OFF when idle. Auto-enables for 2s only during WhatsApp dispatches, then retracts. Banking apps cannot detect AutoText."
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
                        context.showAppToast("Dynamic Zero-Detection Active: A11y Off When Idle")
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
                    context.showAppToast("Services Verified & Active")
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
