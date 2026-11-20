/**
 * Package: com.jmd.supersms.ui.screen.settings
 * Module: SuperSMS Subsystem
 *
 * What it is:
 * Settings card for SuperSMS channels: carrier SMS quotas, multi-SIM routing,
 * simulated call parameters, and contact sync intervals.
 */

package com.jmd.supersms.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.ui.components.*

/**
 * SuperSMS channel controls: SIM selection, SMS quotas, call simulation, and pool sync.
 */
@Composable
fun SettingsChannelsCard(
    context: Context,
    prefs: PreferenceManager = remember { PreferenceManager(context) },
    modifier: Modifier = Modifier
) {
    var simSlot by remember { mutableIntStateOf(prefs.defaultSimSlot) }
    var smsQuotaEnabled by remember { mutableStateOf(prefs.isSmsQuotaLimitEnabled) }
    var dailySmsLimit by remember { mutableIntStateOf(prefs.dailySmsSafetyLimit) }
    var callSimDuration by remember { mutableIntStateOf(prefs.callSimulationRingDurationSeconds) }
    var callSimName by remember { mutableStateOf(prefs.callSimulationDefaultName) }
    var callSimNumber by remember { mutableStateOf(prefs.callSimulationDefaultNumber) }
    var callSimStyle by remember { mutableStateOf(prefs.callSimulationDefaultStyle) }
    var contactSyncHours by remember { mutableIntStateOf(prefs.contactSyncIntervalHours) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Carrier SMS & Multi-SIM Routing
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("SIM Routing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = if (simSlot == -1) "Default" else "SIM ${simSlot + 1}", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select preferred outbound SIM and configure daily safety quotas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("SIM Slot:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val slots = listOf(
                0 to "SIM 1",
                1 to "SIM 2",
                -1 to "Default"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                slots.forEach { (slot, label) ->
                    AppChip(
                        selected = simSlot == slot,
                        onClick = {
                            simSlot = slot
                            prefs.defaultSimSlot = slot
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            AppToggleRow(
                title = "SMS Quota",
                subtitle = "Cap daily outgoing dispatches to prevent carrier blocking",
                checked = smsQuotaEnabled,
                onCheckedChange = { checked ->
                    smsQuotaEnabled = checked
                    prefs.isSmsQuotaLimitEnabled = checked
                },
                icon = Icons.Filled.Security
            )

            if (smsQuotaEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Safety Threshold: $dailySmsLimit SMS/Day", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                val limits = listOf(20, 50, 80, 100)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    limits.forEach { lim ->
                        AppChip(
                            selected = dailySmsLimit == lim,
                            onClick = {
                                dailySmsLimit = lim
                                prefs.dailySmsSafetyLimit = lim
                            },
                            label = "$lim/day",
                            activeColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Simulated Incoming Call Settings
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Call Simulation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = callSimStyle, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Configure identity, ring duration, and visual skin for incoming calls.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = callSimName,
                    onValueChange = {
                        callSimName = it
                        prefs.callSimulationDefaultName = it
                    },
                    label = { Text("Caller Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = callSimNumber,
                    onValueChange = {
                        callSimNumber = it
                        prefs.callSimulationDefaultNumber = it
                    },
                    label = { Text("Phone Number") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Screen Skin:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val styles = listOf(
                "PIXEL" to "Pixel",
                "SAMSUNG" to "Samsung"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (st, label) ->
                    AppChip(
                        selected = callSimStyle == st,
                        onClick = {
                            callSimStyle = st
                            prefs.callSimulationDefaultStyle = st
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Ringing Timeout:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val durations = listOf(15, 30, 45, 60)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                durations.forEach { d ->
                    AppChip(
                        selected = callSimDuration == d,
                        onClick = {
                            callSimDuration = d
                            prefs.callSimulationRingDurationSeconds = d
                        },
                        label = "${d}s",
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Contact Pool Sync
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Pool Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${contactSyncHours}h Interval", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Set periodic address book and SMS contact sync frequency.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Sync Interval:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val syncIntervals = listOf(2, 6, 12, 24)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                syncIntervals.forEach { h ->
                    AppChip(
                        selected = contactSyncHours == h,
                        onClick = {
                            contactSyncHours = h
                            prefs.contactSyncIntervalHours = h
                        },
                        label = "Every ${h}h",
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
