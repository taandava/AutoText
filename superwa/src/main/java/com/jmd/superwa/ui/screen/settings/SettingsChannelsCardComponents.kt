/**
 * Package: com.jmd.superwa.ui.screen.settings
 * Module: SuperWA Subsystem
 *
 * What it is:
 * Settings card for SuperWA channels: WhatsApp pacing, organic typing delay,
 * auto-reply cooldown, and contact sync intervals.
 */

package com.jmd.superwa.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.ui.components.*

/**
 * SuperWA channel controls: WhatsApp dispatch delay, typing pace, auto-reply throttle, and pool sync.
 */
@Composable
fun SettingsChannelsCard(
    context: Context,
    prefs: PreferenceManager = remember { PreferenceManager(context) },
    modifier: Modifier = Modifier
) {
    var waDelay by remember { mutableIntStateOf(prefs.whatsappDispatchDelaySeconds) }
    var waTypingDelay by remember { mutableIntStateOf(prefs.whatsappTypingDelayMs) }
    var autoReplyCooldown by remember { mutableIntStateOf(prefs.autoReplyCooldownSeconds) }
    var contactSyncHours by remember { mutableIntStateOf(prefs.contactSyncIntervalHours) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. WhatsApp Dispatch & Pacing
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("WhatsApp Dispatch", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${waDelay}s Pace", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Adjust organic delay between dispatches and realistic typing pace.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Dispatch Delay:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val delays = listOf(1, 2, 3, 5, 8, 10)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                delays.forEach { s ->
                    AppChip(
                        selected = waDelay == s,
                        onClick = {
                            waDelay = s
                            prefs.whatsappDispatchDelaySeconds = s
                        },
                        label = "${s}s",
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Typing Delay:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val typingDelays = listOf(
                0 to "Instant",
                400 to "400ms",
                800 to "800ms",
                1500 to "1.5s"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                typingDelays.forEach { (ms, label) ->
                    AppChip(
                        selected = waTypingDelay == ms,
                        onClick = {
                            waTypingDelay = ms
                            prefs.whatsappTypingDelayMs = ms
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Auto-Reply Throttle & Contact Sync
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Pool Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${autoReplyCooldown}s Throttle", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Prevent reply flooding and set periodic address book sync frequency.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Reply Throttle:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val cooldowns = listOf(10, 30, 60, 120, 300)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                cooldowns.forEach { c ->
                    AppChip(
                        selected = autoReplyCooldown == c,
                        onClick = {
                            autoReplyCooldown = c
                            prefs.autoReplyCooldownSeconds = c
                        },
                        label = "${c}s",
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
