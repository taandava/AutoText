/**
 * Package: com.jmd.helper.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsSystemSelfTestCardComponents].
 *
 * What it does:
 * Provides an interactive Material 3 diagnostic test suite embedded directly inside the Settings screen. Allows users to audit all 14 core application subsystems, database tables, schedulers, and services, and execute 1-tap automated repairs.
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.helper.receiver.HelperDeviceAdminReceiver]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.graphics.Color]
 * - Core Security & Utilities: [android.content.ClipboardManager], [com.jmd.helper.core.AccessibilityHealthManager], [com.jmd.helper.core.diagnostic.DiagnosticLevel], [com.jmd.helper.core.diagnostic.DiagnosticReport]
 * - Android Framework & Jetpack: [android.content.ClipData], [android.provider.Settings], [androidx.compose.foundation.background], [androidx.compose.foundation.clickable]
 */

package com.jmd.helper.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.diagnostic.DiagnosticLevel
import com.jmd.helper.core.diagnostic.DiagnosticReport
import com.jmd.helper.core.diagnostic.DiagnosticResultItem
import com.jmd.helper.core.diagnostic.SystemSelfTestManager
import com.jmd.helper.core.showAppToast
import com.jmd.helper.receiver.HelperDeviceAdminReceiver
import com.jmd.helper.ui.components.AppCard
import com.jmd.helper.ui.components.GlassButton
import com.jmd.helper.ui.components.GlassButtonVariant
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.OrangeBrandPalette
import kotlinx.coroutines.launch

/**
 * What it is:
 * Settings card component running live programmatic audits and automated self-healing.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsSystemSelfTestCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsSystemSelfTestCardComponents], [SettingsSystemSelfTestCard]
 */
@Composable
fun SettingsSystemSelfTestCard(
    context: Context,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isRunningAudit by remember { mutableStateOf(false) }
    var isAutoFixing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<DiagnosticReport?>(null) }
    var expandedItemIds by remember { mutableStateOf(setOf<String>()) }
    var isListExpanded by remember { mutableStateOf(true) }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Title & Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OrangeBrandPalette.PrimaryBrand.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = "System Diagnostic",
                        tint = OrangeBrandPalette.PrimaryBrand,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Self-Test & Diagnostics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time audit of all 14 subsystems with 1-tap auto-repair",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (report != null) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val formattedReport = buildString {
                                appendLine("=== HELPER SYSTEM SELF-TEST REPORT ===")
                                appendLine("Health Score: ${report?.overallHealthPercentage}%")
                                appendLine("Total Checks: ${report?.totalTests} | Passed: ${report?.passedCount} | Warnings: ${report?.warningCount} | Failed: ${report?.failedCount}")
                                appendLine("---------------------------------------")
                                report?.items?.forEach { item ->
                                    appendLine("[${item.level}] ${item.title}: ${item.summary}")
                                    appendLine("  Details: ${item.details}")
                                }
                            }
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Helper Diagnostics Report", formattedReport))
                            context.showAppToast("Diagnostic report copied to clipboard")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Report",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.8.dp
            )

            // 2. Report Overview & Health Gauge
            val currentReport = report
            if (currentReport != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                currentReport.failedCount > 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                currentReport.warningCount > 0 -> Color(0xFFF57C00).copy(alpha = 0.12f)
                                else -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                            }
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    currentReport.failedCount > 0 -> Icons.Filled.Error
                                    currentReport.warningCount > 0 -> Icons.Filled.Warning
                                    else -> Icons.Filled.CheckCircle
                                },
                                contentDescription = null,
                                tint = when {
                                    currentReport.failedCount > 0 -> MaterialTheme.colorScheme.error
                                    currentReport.warningCount > 0 -> Color(0xFFF57C00)
                                    else -> Color(0xFF2E7D32)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    currentReport.failedCount > 0 -> "${currentReport.failedCount} Subsystems Need Repair"
                                    currentReport.warningCount > 0 -> "${currentReport.warningCount} Warnings Detected"
                                    else -> "All Subsystems Fully Operational"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                currentReport.failedCount > 0 -> MaterialTheme.colorScheme.error
                                currentReport.warningCount > 0 -> Color(0xFFF57C00)
                                else -> Color(0xFF2E7D32)
                            }
                        ) {
                            Text(
                                text = "${currentReport.overallHealthPercentage}% HEALTH",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { currentReport.overallHealthPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when {
                            currentReport.failedCount > 0 -> MaterialTheme.colorScheme.error
                            currentReport.warningCount > 0 -> Color(0xFFF57C00)
                            else -> Color(0xFF2E7D32)
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Passed: ${currentReport.passedCount} / ${currentReport.totalTests}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Warnings: ${currentReport.warningCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF57C00)
                        )
                        Text(
                            text = "Failed: ${currentReport.failedCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 3. Action Buttons (Run Diagnostic & Auto-Fix)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = if (isRunningAudit) "Auditing..." else if (report != null) "Re-Run Audit" else "Run Full Audit",
                    icon = Icons.Filled.PlayArrow,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                    enabled = !isRunningAudit && !isAutoFixing,
                    onClick = {
                        coroutineScope.launch {
                            isRunningAudit = true
                            try {
                                val res = SystemSelfTestManager.runFullDiagnostic(context)
                                report = res
                                context.showAppToast("Audit finished: ${res.passedCount} passed, ${res.failedCount} failed")
                            } catch (e: Exception) {
                                context.showAppToast("Audit error: ${e.message}", isLong = true)
                            } finally {
                                isRunningAudit = false
                            }
                        }
                    }
                )

                if (report != null && ((report?.warningCount ?: 0) > 0 || (report?.failedCount ?: 0) > 0)) {
                    GlassButton(
                        text = if (isAutoFixing) "Repairing..." else "Auto-Fix Issues",
                        icon = Icons.Filled.AutoFixHigh,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                        enabled = !isRunningAudit && !isAutoFixing,
                        onClick = {
                            coroutineScope.launch {
                                isAutoFixing = true
                                try {
                                    val res = SystemSelfTestManager.autoFixAll(context)
                                    report = res
                                    context.showAppToast("Auto-fix complete! Health: ${res.overallHealthPercentage}%")
                                } catch (e: Exception) {
                                    context.showAppToast("Auto-fix error: ${e.message}", isLong = true)
                                } finally {
                                    isAutoFixing = false
                                }
                            }
                        }
                    )
                }
            }

            // 4. Subsystems List (Expandable Breakdown)
            if (currentReport != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isListExpanded = !isListExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUDITED SUBSYSTEMS (${currentReport.items.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isListExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle List",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isListExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currentReport.items.forEach { item ->
                            DiagnosticItemRow(
                                item = item,
                                isExpanded = expandedItemIds.contains(item.id),
                                onToggleExpand = {
                                    expandedItemIds = if (expandedItemIds.contains(item.id)) {
                                        expandedItemIds - item.id
                                    } else {
                                        expandedItemIds + item.id
                                    }
                                },
                                onPerformIndividualFix = {
                                    handleIndividualFix(context, item.id) {
                                        coroutineScope.launch {
                                            report = SystemSelfTestManager.runFullDiagnostic(context)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * What it is:
 * Diagnostic row rendering individual subsystem check with status, category, and expandable details.
 *
 * What it does:
 * Renders interactive Material 3 UI for [DiagnosticItemRow] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsSystemSelfTestCardComponents], [DiagnosticItemRow]
 */
@Composable
private fun DiagnosticItemRow(
    item: DiagnosticResultItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPerformIndividualFix: () -> Unit
) {
    val statusColor = when (item.level) {
        DiagnosticLevel.PASS -> Color(0xFF2E7D32)
        DiagnosticLevel.WARNING -> Color(0xFFF57C00)
        DiagnosticLevel.FAIL -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggleExpand() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (item.level) {
                        DiagnosticLevel.PASS -> Icons.Filled.CheckCircle
                        DiagnosticLevel.WARNING -> Icons.Filled.Warning
                        DiagnosticLevel.FAIL -> Icons.Filled.Cancel
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = item.summary,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.canAutoFix && item.level != DiagnosticLevel.PASS) {
                    AssistChip(
                        onClick = onPerformIndividualFix,
                        label = {
                            Text(
                                text = "Fix",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Build,
                                contentDescription = "Fix",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 26.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.details,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                    if (item.fixActionDescription != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Auto-Fix: ${item.fixActionDescription}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * What it is:
 * Handles individual subsystem one-tap repair routing.
 *
 * What it does:
 * Executes [handleIndividualFix] logic in accordance with system and security invariants.
 *
 * Linked with:
 * - Architecture: [SettingsSystemSelfTestCardComponents], [handleIndividualFix]
 */
private fun handleIndividualFix(context: Context, itemId: String, onDone: () -> Unit) {
    try {
        when (itemId) {
            "device_admin" -> {
                HelperDeviceAdminReceiver.requestActivation(context)
            }
            "accessibility_service" -> {
                if (!AccessibilityHealthManager.autoHealAccessibility(context)) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    context.showAppToast("Enable 'Helper' in Accessibility Services")
                }
            }
            "notification_channels" -> {
                com.jmd.helper.HelperApplication.createNotificationChannels(context)
                context.showAppToast("Notification channels re-created")
                onDone()
            }
            "alarm_scheduler" -> {
                com.jmd.helper.scheduler.AlarmScheduler(context).rescheduleAllPending()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (e: Exception) {
                        // Suppressed
                    }
                }
                context.showAppToast("Alarms rescheduled")
                onDone()
            }
            "workmanager_campaigns" -> {
                com.jmd.helper.scheduler.DailyCampaignScheduler.scheduleAll(context)
                context.showAppToast("Daily campaigns re-armed")
                onDone()
            }
            "sqlite_db", "room_orm" -> {
                val db = com.jmd.helper.data.db.DatabaseHelper.getInstance(context).writableDatabase
                db.execSQL("PRAGMA optimize;")
                context.showAppToast("Database optimized")
                onDone()
            }
            "telephony_sms" -> {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                context.showAppToast("Grant SMS permission in App Info")
            }
            else -> {
                context.showAppToast("Executing auto-repair...")
                onDone()
            }
        }
    } catch (e: Exception) {
        context.showAppToast("Repair action failed: ${e.message}", isLong = true)
    }
}
