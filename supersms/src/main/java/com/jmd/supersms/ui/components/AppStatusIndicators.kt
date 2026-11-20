/**
 * Package: com.jmd.supersms.ui.components
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AppStatusIndicators] in SuperSMS.
 */

package com.jmd.supersms.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jmd.supersms.core.OverlayPermissionHelper
import com.jmd.supersms.core.SmsQuotaManager
import com.jmd.supersms.data.prefs.PreferenceManager

val IndicatorActiveGreen = Color(0xFF00E676)
val IndicatorInactiveRed = Color(0xFFFF5252)

data class SystemIndicator(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val actionLabel: String? = null,
    val onAction: ((Context) -> Unit)? = null
)

object SystemIndicatorResolver {

    fun resolveAll(context: Context, prefs: PreferenceManager): List<SystemIndicator> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val quotaStatus = SmsQuotaManager.getQuotaStatus(context)
        val isQuotaSafe = !quotaStatus.isLimitReached

        val isOverlayActive = OverlayPermissionHelper.canDrawOverlays(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val isExactAlarmActive = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (alarmManager?.canScheduleExactAlarms() == true)

        return buildList {
            add(
                SystemIndicator(
                    id = "sms_permission",
                    name = "SMS Service",
                    description = "Allows SuperSMS to send and schedule cellular text messages.",
                    icon = Icons.Default.Sms,
                    isActive = hasSmsPermission,
                    actionLabel = if (!hasSmsPermission) "Grant Access" else null,
                    onAction = { ctx ->
                        runCatching {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${ctx.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            ctx.startActivity(intent)
                        }
                    }
                )
            )

            add(
                SystemIndicator(
                    id = "call_permission",
                    name = "Phone Calls",
                    description = "Allows SuperSMS to initiate cellular calls and track call duration.",
                    icon = Icons.Default.Call,
                    isActive = hasCallPermission,
                    actionLabel = if (!hasCallPermission) "Grant Access" else null,
                    onAction = { ctx ->
                        runCatching {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${ctx.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            ctx.startActivity(intent)
                        }
                    }
                )
            )

            add(
                SystemIndicator(
                    id = "sms_quota",
                    name = "Daily Quota",
                    description = "Carrier safe daily threshold: ${quotaStatus.totalSentToday}/${quotaStatus.limit} sent today.",
                    icon = Icons.Default.Speed,
                    isActive = isQuotaSafe,
                    actionLabel = null,
                    onAction = null
                )
            )

            add(
                SystemIndicator(
                    id = "battery_opt",
                    name = "Battery Care",
                    description = "Ensures background tasks execute reliably without OS suspension.",
                    icon = Icons.Default.BatteryChargingFull,
                    isActive = isBatteryUnrestricted,
                    actionLabel = if (!isBatteryUnrestricted) "Battery Care" else null,
                    onAction = { ctx ->
                        runCatching {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            ctx.startActivity(intent)
                        }
                    }
                )
            )

            add(
                SystemIndicator(
                    id = "exact_alarms",
                    name = "Exact Alarms",
                    description = "Guarantees tasks execute at the exact scheduled second.",
                    icon = Icons.Default.Alarm,
                    isActive = isExactAlarmActive,
                    actionLabel = if (!isExactAlarmActive) "Grant Alarm" else null,
                    onAction = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${ctx.packageName}".toUri()
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                ctx.startActivity(intent)
                            }
                        }
                    }
                )
            )

            add(
                SystemIndicator(
                    id = "display_overlay",
                    name = "Screen Overlay",
                    description = "Allows SuperSMS to display call alerts and countdown overlays.",
                    icon = Icons.Default.Layers,
                    isActive = isOverlayActive,
                    actionLabel = if (!isOverlayActive) "Grant Overlay" else null,
                    onAction = { ctx ->
                        OverlayPermissionHelper.launchOverlaySettings(ctx)
                    }
                )
            )
        }
    }
}

@Composable
fun AppHeaderStatusIndicators(
    indicators: List<SystemIndicator>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            indicators.forEach { indicator ->
                val tintColor = if (indicator.isActive) IndicatorActiveGreen else IndicatorInactiveRed
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = indicator.icon,
                        contentDescription = indicator.name,
                        tint = tintColor,
                        modifier = Modifier.size(10.5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicatorsDialog(
    indicators: List<SystemIndicator>,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allActive = indicators.all { it.isActive }
    val activeCount = indicators.count { it.isActive }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (allActive) IndicatorActiveGreen.copy(alpha = 0.15f) else IndicatorInactiveRed.copy(alpha = 0.15f),
                        border = BorderStroke(0.75.dp, if (allActive) IndicatorActiveGreen.copy(alpha = 0.5f) else IndicatorInactiveRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "$activeCount/${indicators.size} Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (allActive) IndicatorActiveGreen else IndicatorInactiveRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pure Native Cellular Telephony • Zero Special Services Needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(indicators.size) { index ->
                    val indicator = indicators[index]
                    val tintColor = if (indicator.isActive) IndicatorActiveGreen else IndicatorInactiveRed

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.5.dp, tintColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(tintColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = indicator.icon,
                                        contentDescription = indicator.name,
                                        tint = tintColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = indicator.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = indicator.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!indicator.isActive && indicator.actionLabel != null && indicator.onAction != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { indicator.onAction.invoke(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tintColor.copy(alpha = 0.18f),
                                        contentColor = tintColor
                                    ),
                                    border = BorderStroke(1.dp, tintColor.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = indicator.actionLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = if (indicator.isActive) "ACTIVE" else "OFF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tintColor,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    )
}
