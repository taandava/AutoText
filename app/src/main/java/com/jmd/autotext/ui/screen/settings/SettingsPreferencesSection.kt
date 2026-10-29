package com.jmd.autotext.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Operational controls for default international dialing prefix, multi-SIM routing,
 * touch coordinate offsets, dispatch delays, and visual application theme.
 *
 * @param prefs Preferences manager reading and storing configuration flags.
 * @param modifier Optional layout modifier.
 * @param onThemeChanged Callback invoked when active theme selection is changed.
 */
@Composable
fun settingsPreferencesCards(
    prefs: PreferenceManager,
    modifier: Modifier = Modifier,
    onThemeChanged: (String) -> Unit = {}
) {
    var countryCode by remember { mutableStateOf(prefs.defaultCountryCode) }
    var appLock by remember { mutableStateOf(prefs.isAppLockEnabled) }
    var simSlot by remember { mutableIntStateOf(prefs.defaultSimSlot) }
    var appTheme by remember { mutableStateOf(prefs.appThemeMode) }
    var fallbackStrategy by remember { mutableStateOf(prefs.fallbackClickStrategy) }
    var gestureFallback by remember { mutableStateOf(prefs.isGestureFallbackEnabled) }
    var maxRetries by remember { mutableIntStateOf(prefs.fallbackMaxRetries) }
    var clickOffsetY by remember { mutableIntStateOf(prefs.customClickCoordinateOffsetY) }
    var waDelay by remember { mutableIntStateOf(prefs.whatsappDispatchDelaySeconds) }
    var contactSyncHours by remember { mutableIntStateOf(prefs.contactSyncIntervalHours) }
    var dailySmsLimit by remember { mutableIntStateOf(prefs.dailySmsSafetyLimit) }
    var smsQuotaEnabled by remember { mutableStateOf(prefs.isSmsQuotaLimitEnabled) }
    var dispatchSound by remember { mutableStateOf(prefs.isDispatchNotificationSoundEnabled) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // App Theme Selector
        var dynamicColor by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("App Theme & Aesthetics", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                appBadge(text = if (dynamicColor) "Dynamic System" else appTheme, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Material 3 Expressive Orange identity with light/dark adaptation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            val themes = listOf(
                "SYSTEM" to "System",
                "LIGHT" to "Light",
                "DARK" to "Dark"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                themes.forEach { (mode, label) ->
                    glassChip(
                        selected = appTheme == mode,
                        onClick = {
                            appTheme = mode
                            prefs.appThemeMode = mode
                            onThemeChanged(mode)
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(8.dp))
                glassToggleRow(
                    title = "System Dynamic Wallpaper Color",
                    subtitle = "Derive accent tones dynamically from Android wallpaper palette",
                    checked = dynamicColor,
                    onCheckedChange = { checked ->
                        dynamicColor = checked
                        prefs.isDynamicColorEnabled = checked
                        onThemeChanged(appTheme)
                    },
                    emoji = "🎨"
                )
            }
        }

        // WhatsApp Automation Speed & Timing
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WhatsApp Dispatch Speed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                appBadge(text = "${waDelay}s Pace", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Delay between automated WhatsApp message dispatches to keep sending organic and reliable.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    4 to "Fast (4s)",
                    8 to "Normal (8s)",
                    15 to "Safe (15s)",
                    25 to "Slow (25s)"
                ).forEach { (delaySec, label) ->
                    glassChip(
                        selected = waDelay == delaySec,
                        onClick = {
                            waDelay = delaySec
                            prefs.whatsappDispatchDelaySeconds = delaySec
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }


        // Contact Auto-Sync Frequency
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Contact Sync Frequency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                appBadge(text = if (contactSyncHours == 0) "Manual" else "Every ${contactSyncHours}h", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Background synchronization interval to ingest phonebook contacts into AutoText.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    4 to "4 Hours",
                    12 to "12 Hours",
                    24 to "24 Hours",
                    0 to "Manual"
                ).forEach { (hours, label) ->
                    glassChip(
                        selected = contactSyncHours == hours,
                        onClick = {
                            contactSyncHours = hours
                            prefs.contactSyncIntervalHours = hours
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Dispatch Audio Alert Notification
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Delivery Sound & Haptics", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Play subtle alert sound upon successful automated background dispatch", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Switch(
                    checked = dispatchSound,
                    onCheckedChange = {
                        dispatchSound = it
                        prefs.isDispatchNotificationSoundEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    )
                )
            }
        }

        // WhatsApp Default Country Code
        appCard(modifier = Modifier.fillMaxWidth()) {
            Text("Default Country Code", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Auto-prepended to 10-digit numbers for WhatsApp routing.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            glassInputField(
                value = countryCode,
                onValueChange = {
                    countryCode = it.filter { ch -> ch.isDigit() }
                    prefs.defaultCountryCode = countryCode
                },
                label = "Country Code (e.g. 91, 1, 44)",
                placeholder = "91",
                leadingIcon = Icons.Default.Public,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // Automation Fallback & Failure Recovery Customisation
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Automation Fallback Mechanism", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                appBadge(text = "Reliability", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Customize how AutoText recovers when WhatsApp UI buttons or views fail to click.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Dispatch Strategy:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))

            val strategies = listOf(
                "SMART_FALLBACK" to "Smart Auto",
                "A11Y_FIRST" to "A11y Node",
                "GESTURE_FORCE" to "Physical Tap"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                strategies.forEach { (stratKey, stratLabel) ->
                    glassChip(
                        selected = fallbackStrategy == stratKey,
                        onClick = {
                            fallbackStrategy = stratKey
                            prefs.fallbackClickStrategy = stratKey
                        },
                        label = stratLabel,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gesture Tap Fallback", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("If accessibility node click is ignored by WhatsApp, simulate physical screen tap on button bounds", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Switch(
                    checked = gestureFallback,
                    onCheckedChange = {
                        gestureFallback = it
                        prefs.isGestureFallbackEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            var retriesText by remember(maxRetries) { mutableStateOf(maxRetries.toString()) }
            glassInputField(
                value = retriesText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(3)
                    retriesText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        maxRetries = num
                        prefs.fallbackMaxRetries = num
                    }
                },
                label = "Failure Recovery Max Attempts (Passes)",
                placeholder = "35",
                leadingIcon = Icons.Default.Replay,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(10.dp))

            var offsetText by remember(clickOffsetY) { mutableStateOf(clickOffsetY.toString()) }
            glassInputField(
                value = offsetText,
                onValueChange = { newStr ->
                    val clean = if (newStr.startsWith("-")) "-" + newStr.drop(1).filter { it.isDigit() }.take(3) else newStr.filter { it.isDigit() }.take(3)
                    offsetText = clean
                    val num = clean.toIntOrNull()
                    if (num != null) {
                        clickOffsetY = num
                        prefs.customClickCoordinateOffsetY = num
                    }
                },
                label = "Custom Button Y-Offset Adjustment (Pixels)",
                placeholder = "0",
                leadingIcon = Icons.Default.Tune,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Adjust where fallback tap lands if button bounds differ on custom ROMs / foldables.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // App Security PIN
        appCard(modifier = Modifier.fillMaxWidth()) {
            Text("App Security Lock", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Require PIN to open the AutoText app itself.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AutoText In-App Protection", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Switch(
                    checked = appLock,
                    onCheckedChange = {
                        appLock = it
                        prefs.isAppLockEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                )
            }
        }

        // Default SIM Slot
        appCard(modifier = Modifier.fillMaxWidth()) {
            Text("Default SIM Slot", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Preferred carrier SIM card for outbound SMS messages.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf("Default" to 0, "SIM 1" to 1, "SIM 2" to 2).forEach { (label, slot) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            simSlot = slot
                            prefs.defaultSimSlot = slot
                        }
                    ) {
                        RadioButton(
                            selected = simSlot == slot,
                            onClick = {
                                simSlot = slot
                                prefs.defaultSimSlot = slot
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Cellular SMS Quota & Safety Controls
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cellular SMS Safety Quota", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (smsQuotaEnabled) "Daily carrier safeguard ($dailySmsLimit SMS/day)" else "Unlimited personal dispatch mode (no quota throttling)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = smsQuotaEnabled,
                    onCheckedChange = {
                        smsQuotaEnabled = it
                        prefs.isSmsQuotaLimitEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    )
                )
            }

            if (smsQuotaEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Daily SMS Ceiling (TRAI default 100):", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        100 to "100",
                        500 to "500",
                        1000 to "1,000",
                        5000 to "5,000"
                    ).forEach { (limitVal, label) ->
                        glassChip(
                            selected = dailySmsLimit == limitVal,
                            onClick = {
                                dailySmsLimit = limitVal
                                prefs.dailySmsSafetyLimit = limitVal
                            },
                            label = label,
                            activeColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
