package com.jmd.autotext.ui.screens.more

import com.jmd.autotext.ui.screens.*

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.*
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextDeviceAdminReceiver
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screens.more.permissions.*
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.OverlayPermissionHelper
import java.text.SimpleDateFormat
import java.util.*

// ----------------- SUB SCREEN: PERMISSIONS -----------------
@android.annotation.SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterView(context: Context, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission Center & Troubleshooting", color = BentoGlassTokens.TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
            )
        },
        containerColor = BentoGlassTokens.VoidBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Super Auto-Heal & Universal Fixer
            item {
                SuperAutoHealAndPermissionFixerCard(context)
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Individual Permissions & System Access", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BentoGlassTokens.TextPrimary)
                Text("Ensure these are granted so WhatsApp auto-unlock, alarms, and background sending run smoothly.", fontSize = 11.5.sp, color = BentoGlassTokens.TextSecondary)
            }

            // 2. Device Administration (Auto-Lock phone after task completes)
            item {
                val isAdminActive = AutoTextDeviceAdminReceiver.isAdminActive(context)
                PermissionItem(
                    title = if (isAdminActive) "Device Administration: Active" else "Device Administration (Hardware Auto-Lock)",
                    desc = "Allows AutoText to instantly and securely lock the device (via lockNow) after unattended scheduled WhatsApp/SMS tasks finish.",
                    btnText = if (isAdminActive) "Device Admin Active" else "Activate Device Admin",
                    isGranted = isAdminActive,
                    onClick = {
                        if (!isAdminActive) {
                            if (!AutoTextDeviceAdminReceiver.requestActivation(context)) {
                                Toast.makeText(
                                    context,
                                    "Could not open Device Admin screen directly. Please enable AutoText in system Settings > Security > Device admin apps.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Toast.makeText(context, "Device Administrator is already enabled for AutoText!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 3. Floating Button & Overlay Permission (SYSTEM_ALERT_WINDOW)
            item {
                val hasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
                PermissionItem(
                    title = if (hasOverlay) "Display Over Other Apps: Granted" else "Display Over Other Apps / Floating Window",
                    desc = "Critical for Android 10+ and Vivo / Xiaomi / Samsung to wake the screen from sleep, launch over the lockscreen bouncer, and show countdown status.",
                    btnText = if (hasOverlay) "Overlay Permission Granted" else "Grant Overlay Permission",
                    isGranted = hasOverlay,
                    onClick = {
                        context.startActivity(OverlayPermissionHelper.getManageOverlayIntent(context))
                    }
                )
            }

            // 4. Exact Alarms & Reminders Permission
            item {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager?.canScheduleExactAlarms() == true
                } else {
                    true
                }
                PermissionItem(
                    title = if (canExactAlarm) "Exact Alarms & Reminders: Granted" else "Schedule Exact Alarms & Reminders",
                    desc = "Required by Android 12+ to fire scheduled messages and reminders at the precise exact second without power-saver jitter.",
                    btnText = if (canExactAlarm) "Exact Alarms Granted" else "Grant Exact Alarm Permission",
                    isGranted = canExactAlarm,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } else {
                            Toast.makeText(context, "Exact alarms are automatically permitted on your Android version", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 5. Accessibility Automation Service
            item {
                val isA11y = AutoTextAccessibilityService.isServiceRunning
                PermissionItem(
                    title = if (isA11y) "Accessibility Automation: Active" else "Accessibility Automation Service",
                    desc = "Required to auto-send scheduled WhatsApp messages and auto-unlock phone via lockscreen PIN.",
                    btnText = if (isA11y) "Accessibility Active" else "Accessibility Settings",
                    isGranted = isA11y,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                )
            }

            // 6. Android 13/14 Sideloaded App Fix (Restricted Settings)
            item {
                PermissionItem(
                    title = "Android 13 / 14 Sideloaded App Fix",
                    desc = "If Accessibility is greyed out: Go to App Info -> Tap 3 dots (top right) -> 'Allow restricted settings' -> then turn Accessibility on.",
                    btnText = "Open App Info (Tap 3 Dots)",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 7. Notification Listener Access
            item {
                val hasNotif = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                PermissionItem(
                    title = if (hasNotif) "Notification Listener: Active" else "Notification Listener Access",
                    desc = "Required for WhatsApp auto-replies directly from background without waking screen.",
                    btnText = if (hasNotif) "Notification Access Active" else "Notification Access Settings",
                    isGranted = hasNotif,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                )
            }

            // 8. Battery Optimization Exemption
            item {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                PermissionItem(
                    title = if (isIgnoringBattery) "Battery Optimization: Exempted" else "Battery Optimization Exemption",
                    desc = "Prevents Android from freezing timers, ensuring scheduled messages trigger on the exact minute.",
                    btnText = if (isIgnoringBattery) "Battery Exemption Active" else "Exempt from Battery Saver",
                    isGranted = isIgnoringBattery,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {
                                // Restricted environment fallback
                            }
                        }
                    }
                )
            }

            // 9. Contacts, SMS & Call Log Access
            item {
                PermissionItem(
                    title = "Contacts, SMS & Call Log Access",
                    desc = "Required to auto-discover mobile contacts into the internal dynamic pool for daily SMS campaigns.",
                    btnText = "App Permissions",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item {
                KeepOpenRecentAppsCard(context)
            }
        }
    }
}

@Composable
fun SuperAutoHealAndPermissionFixerCard(context: Context) {
    var hasSecurePerm by remember { mutableStateOf(AccessibilityHealthManager.hasWriteSecureSettings(context)) }
    val isAdminActive = AutoTextDeviceAdminReceiver.isAdminActive(context)
    val hasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
    val isA11yActive = AutoTextAccessibilityService.isServiceRunning

    val universalAdbCommand = "adb shell \"pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS && pm grant com.jmd.autotext android.permission.READ_PHONE_STATE && pm grant com.jmd.autotext android.permission.CALL_PHONE && pm grant com.jmd.autotext android.permission.READ_CONTACTS && pm grant com.jmd.autotext android.permission.POST_NOTIFICATIONS && pm grant com.jmd.autotext android.permission.READ_SMS && pm grant com.jmd.autotext android.permission.SEND_SMS && appops set com.jmd.autotext SYSTEM_ALERT_WINDOW allow && appops set com.jmd.autotext TURN_SCREEN_ON allow && appops set com.jmd.autotext SCHEDULE_EXACT_ALARM allow && appops set com.jmd.autotext START_ACTIVITIES_FROM_BACKGROUND allow && appops set com.jmd.autotext ACCESS_RESTRICTED_SETTINGS allow && appops set com.jmd.autotext AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore && dumpsys deviceidle whitelist +com.jmd.autotext && settings put secure enabled_accessibility_services com.jmd.autotext/com.jmd.autotext.service.AutoTextAccessibilityService && settings put secure accessibility_enabled 1 && settings put secure enabled_notification_listeners com.jmd.autotext/com.jmd.autotext.service.AutoTextNotificationListenerService && dpm set-active-admin com.jmd.autotext/.service.AutoTextDeviceAdminReceiver\""

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (hasSecurePerm && isA11yActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.12f) else BentoGlassTokens.ElectricIndigo.copy(alpha = 0.14f),
        borderColor = if (hasSecurePerm && isA11yActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.35f) else BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasSecurePerm && isA11yActive) Icons.Default.VerifiedUser else Icons.Default.AutoFixHigh,
                contentDescription = null,
                tint = if (hasSecurePerm && isA11yActive) BentoGlassTokens.VividEmerald else BentoGlassTokens.NeonCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "⚡ Master Auto-Heal & Universal Fixer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Text(
                    text = "One-click automated recovery & permission synchronization",
                    fontSize = 11.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Health Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill("A11y", isA11yActive, Modifier.weight(1f))
            StatusPill("Admin", isAdminActive, Modifier.weight(1f))
            StatusPill("Overlay", hasOverlay, Modifier.weight(1f))
            StatusPill("Secure", hasSecurePerm, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Auto-Heal automatically restarts dead accessibility binders and synchronizes secure system settings. For 100% full automation (including device admin, lockscreen popup, and battery whitelist without prompts), run the universal ADB command below.",
            fontSize = 11.5.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 16.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Auto-Heal Now",
                onClick = {
                    val healed = AccessibilityHealthManager.ensureServiceAlive(context)
                    hasSecurePerm = AccessibilityHealthManager.hasWriteSecureSettings(context)
                    if (healed) {
                        Toast.makeText(context, "🎉 Accessibility active & health verified!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Heal attempted. For full background automation, grant permissions or paste the 1-line ADB command.", Toast.LENGTH_LONG).show()
                    }
                },
                icon = Icons.Default.Refresh,
                variant = GlassButtonVariant.SUCCESS,
                modifier = Modifier.weight(1f)
            )

            GlassButton(
                text = "Copy 1-Line ADB Setup",
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Universal ADB Command", universalAdbCommand))
                    Toast.makeText(context, "Copied universal 1-line ADB setup command to clipboard!", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Default.ContentCopy,
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, isOk: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = BentoGlassTokens.ShapePill,
        color = if (isOk) BentoGlassTokens.VividEmerald.copy(alpha = 0.15f) else BentoGlassTokens.CyberRose.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOk) BentoGlassTokens.VividEmerald.copy(alpha = 0.4f) else BentoGlassTokens.CyberRose.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isOk) BentoGlassTokens.VividEmerald else BentoGlassTokens.CyberRose, shape = RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOk) BentoGlassTokens.VividEmerald else BentoGlassTokens.CyberRose
            )
        }
    }
}

@Composable
fun KeepOpenRecentAppsCard(context: Context = androidx.compose.ui.platform.LocalContext.current) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = BentoGlassTokens.RadiantAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Keep Open in Recent Apps (Prevent Killing)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BentoGlassTokens.TextPrimary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "To prevent Samsung One UI, Xiaomi, or OnePlus from killing the app or stopping its accessibility engine:\n\n" +
                    "• Samsung: Open Recent Apps (||| button) -> Tap AutoText icon at top of the card -> Tap 'Keep open' (padlock icon).\n" +
                    "• Xiaomi / HyperOS: In Recent Apps -> Long-press AutoText card -> Tap Padlock icon.\n" +
                    "• Oppo / Vivo / OnePlus: In Recent Apps -> Tap 3 dots (⋮) on AutoText -> Select 'Lock'.",
            fontSize = 11.5.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        GlassButton(
            text = "Open OEM Device Care / Background Settings",
            onClick = { AccessibilityHealthManager.openOemBackgroundSettings(context) },
            icon = Icons.Default.Settings,
            variant = GlassButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PermissionItem(
    title: String,
    desc: String,
    btnText: String,
    isGranted: Boolean = false,
    onClick: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isGranted) BentoGlassTokens.VividEmerald.copy(alpha = 0.08f) else BentoGlassTokens.GlassSurfaceElevated,
        borderColor = if (isGranted) BentoGlassTokens.VividEmerald.copy(alpha = 0.25f) else BentoGlassTokens.GlassBorderSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (isGranted) {
                Surface(
                    shape = BentoGlassTokens.ShapePill,
                    color = BentoGlassTokens.VividEmerald.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "ACTIVE",
                        color = BentoGlassTokens.VividEmerald,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(desc, color = BentoGlassTokens.TextMuted, fontSize = 11.5.sp)
        Spacer(modifier = Modifier.height(10.dp))
        GlassButton(
            text = btnText,
            onClick = onClick,
            variant = if (isGranted) GlassButtonVariant.SUCCESS else GlassButtonVariant.SECONDARY,
            shape = BentoGlassTokens.ShapePill
        )
    }
}
