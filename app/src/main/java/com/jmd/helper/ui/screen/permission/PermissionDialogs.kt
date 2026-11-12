/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [PermissionDialogs].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for PermissionDialogs.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Modifier], [androidx.compose.ui.text.font.FontFamily], [androidx.compose.ui.text.font.FontWeight], [androidx.compose.ui.unit.dp]
 * - Core Security & Utilities: [android.content.ClipboardManager], [com.jmd.helper.compat.ClipboardCompat], [com.jmd.helper.core.showAppToast]
 * - Android Framework & Jetpack: [android.content.ClipData], [android.provider.Settings], [androidx.compose.foundation.BorderStroke], [androidx.compose.foundation.rememberScrollState]
 */

package com.jmd.helper.ui.screen.permission

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.compat.ClipboardCompat
import com.jmd.helper.core.showAppToast
import com.jmd.helper.ui.theme.AppLayoutGrid

/**
 * Universal ADB Privilege Grant Command.
 */
const val UNIVERSAL_ADB_COMMAND =
    "adb shell \"pm grant com.jmd.helper android.permission.WRITE_SECURE_SETTINGS; " +
    "pm grant com.jmd.helper android.permission.READ_PHONE_STATE; " +
    "pm grant com.jmd.helper android.permission.CALL_PHONE; " +
    "pm grant com.jmd.helper android.permission.ANSWER_PHONE_CALLS; " +
    "pm grant com.jmd.helper android.permission.READ_CALL_LOG; " +
    "pm grant com.jmd.helper android.permission.READ_CONTACTS; " +
    "pm grant com.jmd.helper android.permission.POST_NOTIFICATIONS; " +
    "pm grant com.jmd.helper android.permission.READ_SMS; " +
    "pm grant com.jmd.helper android.permission.SEND_SMS; " +
    "pm grant com.jmd.helper android.permission.READ_MEDIA_IMAGES; " +
    "pm grant com.jmd.helper android.permission.READ_MEDIA_VIDEO; " +
    "appops set com.jmd.helper SYSTEM_ALERT_WINDOW allow; " +
    "appops set com.jmd.helper TURN_SCREEN_ON allow; " +
    "appops set com.jmd.helper SCHEDULE_EXACT_ALARM allow; " +
    "appops set com.jmd.helper START_ACTIVITIES_FROM_BACKGROUND allow; " +
    "appops set com.jmd.helper ACCESS_RESTRICTED_SETTINGS allow; " +
    "appops set com.jmd.helper AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore; " +
    "dumpsys deviceidle whitelist +com.jmd.helper; " +
    "dpm set-active-admin com.jmd.helper/.receiver.HelperDeviceAdminReceiver\""

/**
 * What it is:
 * Universal ADB Privilege Setup Dialog providing one-click command copying.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AdbSetupModalDialog] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionDialogs], [AdbSetupModalDialog]
 */
@Composable
fun AdbSetupModalDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Universal ADB Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                Text(
                    text = "Grants all required background execution privileges (secure settings, runtime permissions, and battery whitelist) with zero interactive prompts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(AppLayoutGrid.ChipCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(AppLayoutGrid.BorderWidthStandard, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = UNIVERSAL_ADB_COMMAND,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(AppLayoutGrid.SpaceStandard)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    ClipboardCompat.setPrimaryClip(
                        cm,
                        ClipData.newPlainText("Universal ADB Command", UNIVERSAL_ADB_COMMAND)
                    )
                    context.showAppToast("ADB command copied to clipboard")
                    onDismiss()
                }
            ) {
                Text("Copy Command", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * What it is:
 * Accessibility Service Policy & User Disclosure Dialog strictly adhering to Google Play policies.
 *
 * What it does:
 * Renders interactive Material 3 UI for [A11yDisclosureDialog] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionDialogs], [A11yDisclosureDialog]
 */
@Composable
fun A11yDisclosureDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Task Service Disclosure",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                Text(
                    text = "Helper uses Android's Accessibility Service API exclusively for user-directed automated task dispatch:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Hands-free scheduled message preparation and delivery in user-approved messaging applications (WhatsApp and WhatsApp Business).\n" +
                           "• Unattended device wake and user-configured PIN entry assistance when scheduled tasks trigger while locked.\n" +
                           "• Power button simulation (GLOBAL_ACTION_LOCK_SCREEN) post-dispatch to preserve biometric authentication.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Privacy & Safety Guarantees:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Helper does NOT collect, store, or transmit your private chat messages, keystrokes, personal passwords, or screen contents.\n" +
                           "• Helper strictly isolates itself from all banking, payment, and UPI applications.\n" +
                           "• 100% on-device execution with zero cloud telemetry.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            ) {
                Text("Agree & Open Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
