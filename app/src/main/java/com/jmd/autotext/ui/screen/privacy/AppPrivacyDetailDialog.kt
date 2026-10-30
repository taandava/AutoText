/**
 * Package: com.jmd.autotext.ui.screen.privacy
 * Module: UI Privacy Layer
 *
 * Provides a dialog sheet to inspect and customize granular privacy controls for an individual application.
 */
package com.jmd.autotext.ui.screen.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jmd.autotext.data.model.AppInfoItem
import com.jmd.autotext.data.model.AppPrivacyConfig
import com.jmd.autotext.ui.components.appBadge
import com.jmd.autotext.ui.components.glassButton
import com.jmd.autotext.ui.components.GlassButtonVariant

@Composable
fun AppPrivacyDetailDialog(
    appInfo: AppInfoItem,
    initialConfig: AppPrivacyConfig,
    onSave: (AppPrivacyConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var isExcluded by remember { mutableStateOf(initialConfig.isExcluded) }
    var disableAutomation by remember { mutableStateOf(initialConfig.disableAutomation) }
    var blockOverlays by remember { mutableStateOf(initialConfig.blockOverlays) }
    var suppressNotifications by remember { mutableStateOf(initialConfig.suppressNotifications) }
    var disableClipboard by remember { mutableStateOf(initialConfig.disableClipboard) }
    var maskSensitiveContent by remember { mutableStateOf(initialConfig.maskSensitiveContent) }
    var disableRules by remember { mutableStateOf(initialConfig.disableRules) }
    var customNotes by remember { mutableStateOf(initialConfig.customNotes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header: App Label & Package Identifier
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appInfo.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = appInfo.packageName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (appInfo.isBankingOrFinancial) {
                        appBadge(text = "FINANCIAL", color = MaterialTheme.colorScheme.error, showDot = true)
                    } else if (appInfo.isSystemApp) {
                        appBadge(text = "SYSTEM", color = MaterialTheme.colorScheme.secondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Toggles
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Master Isolation Switch
                    PrivacyControlToggle(
                        title = "Shield & Exclude Application",
                        subtitle = "Isolates AutoText completely from this app and omits it from accessibility targets.",
                        checked = isExcluded,
                        onCheckedChange = { isExcluded = it }
                    )

                    // 2. Block Automation Actions
                    PrivacyControlToggle(
                        title = "Block AutoText Automation Actions",
                        subtitle = "Prevents any simulated taps, message dispatches, or navigation targeted at this app.",
                        checked = disableAutomation,
                        enabled = isExcluded,
                        onCheckedChange = { disableAutomation = it }
                    )

                    // 3. Block Overlays & Wake Windows
                    PrivacyControlToggle(
                        title = "Block Overlays & Wake Windows",
                        subtitle = "Suppresses floating banners, popups, and fake call alerts while using this app.",
                        checked = blockOverlays,
                        enabled = isExcluded,
                        onCheckedChange = { blockOverlays = it }
                    )

                    // 4. Suppress Notifications
                    PrivacyControlToggle(
                        title = "Suppress Heads-Up Notifications",
                        subtitle = "Mutes non-critical notification alerts while this application is in the foreground.",
                        checked = suppressNotifications,
                        enabled = isExcluded,
                        onCheckedChange = { suppressNotifications = it }
                    )

                    // 5. Disable Clipboard Read/Write
                    PrivacyControlToggle(
                        title = "Disable Clipboard Operations",
                        subtitle = "Never copies, pastes, or reads clipboard content when interacting with this app.",
                        checked = disableClipboard,
                        enabled = isExcluded,
                        onCheckedChange = { disableClipboard = it }
                    )

                    // 6. Mask Sensitive Content
                    PrivacyControlToggle(
                        title = "Mask Sensitive Content in Audit Logs",
                        subtitle = "Obfuscates message bodies and phone numbers with asterisks in execution history.",
                        checked = maskSensitiveContent,
                        enabled = isExcluded,
                        onCheckedChange = { maskSensitiveContent = it }
                    )

                    // 7. Disable Trigger Rules
                    PrivacyControlToggle(
                        title = "Disable Incoming Trigger Rules",
                        subtitle = "Ignores notification auto-replies or automated triggers originating from this app.",
                        checked = disableRules,
                        enabled = isExcluded,
                        onCheckedChange = { disableRules = it }
                    )

                    // Optional Notes
                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        label = { Text("Security Notes / Audit Label (Optional)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Actions: Reset Defaults, Cancel, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    glassButton(
                        text = "Reset",
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isExcluded = true
                            disableAutomation = true
                            blockOverlays = true
                            suppressNotifications = true
                            disableClipboard = true
                            maskSensitiveContent = true
                            disableRules = true
                            customNotes = ""
                        }
                    )
                    glassButton(
                        text = "Cancel",
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    glassButton(
                        text = "Save",
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.2f),
                        onClick = {
                            val updated = initialConfig.copy(
                                isExcluded = isExcluded,
                                disableAutomation = disableAutomation,
                                blockOverlays = blockOverlays,
                                suppressNotifications = suppressNotifications,
                                disableClipboard = disableClipboard,
                                maskSensitiveContent = maskSensitiveContent,
                                disableRules = disableRules,
                                customNotes = customNotes.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(updated)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyControlToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        )
    }
}

