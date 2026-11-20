/**
 * Package: com.jmd.supersms.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsPreferencesSection].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsPreferencesSection.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.text.font.FontWeight], [androidx.compose.ui.text.input.KeyboardType]
 * - Core Security & Utilities: [com.jmd.supersms.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [androidx.compose.foundation.clickable], [androidx.compose.foundation.text.KeyboardOptions], [androidx.compose.material.icons.Icons], [androidx.compose.material.icons.filled.Palette]
 */

package com.jmd.supersms.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
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
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.ui.components.*
import com.jmd.supersms.ui.theme.AppDesignTokens

/**
 * What it is:
 * Operational controls for default international dialing prefix, multi-SIM routing, touch coordinate offsets, dispatch delays, and visual application theme.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsPreferencesCards] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsPreferencesSection], [SettingsPreferencesCards]
 * @param prefs Preferences manager reading and storing configuration flags.
 * @param modifier Optional layout modifier.
 * @param onThemeChanged Callback invoked when active theme selection is changed.
 */
@Composable
fun SettingsPreferencesCards(
    prefs: PreferenceManager,
    modifier: Modifier = Modifier,
    onThemeChanged: (String) -> Unit = {}
) {
    var countryCode by remember { mutableStateOf(prefs.defaultCountryCode) }
    var simSlot by remember { mutableIntStateOf(prefs.defaultSimSlot) }
    var appTheme by remember { mutableStateOf(prefs.appThemeMode) }
    var contactSyncHours by remember { mutableIntStateOf(prefs.contactSyncIntervalHours) }
    var dailySmsLimit by remember { mutableIntStateOf(prefs.dailySmsSafetyLimit) }
    var smsQuotaEnabled by remember { mutableStateOf(prefs.isSmsQuotaLimitEnabled) }
    var dispatchSound by remember { mutableStateOf(prefs.isDispatchNotificationSoundEnabled) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // App Theme Selector
        var dynamicColor by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("App Theme", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AppBadge(text = if (dynamicColor) "Dynamic System" else appTheme, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Material 3 Expressive Gold & Slate identity with light/dark adaptation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            val themes = listOf(
                "SYSTEM" to "System",
                "LIGHT" to "Light",
                "DARK" to "Dark"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                themes.forEach { (mode, label) ->
                    AppChip(
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
                AppToggleRow(
                    title = "Dynamic Color",
                    subtitle = "Derive accent tones dynamically from Android wallpaper palette",
                    checked = dynamicColor,
                    onCheckedChange = { checked ->
                        dynamicColor = checked
                        prefs.isDynamicColorEnabled = checked
                        onThemeChanged(appTheme)
                    },
                    icon = Icons.Filled.Palette
                )
            }
        }




        // Contact Auto-Sync Frequency
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sync Frequency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AppBadge(text = if (contactSyncHours == 0) "Manual" else "Every ${contactSyncHours}h", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Background synchronization interval to ingest phonebook contacts into Super.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    4 to "4 Hours",
                    12 to "12 Hours",
                    24 to "24 Hours",
                    0 to "Manual"
                ).forEach { (hours, label) ->
                    AppChip(
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
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Delivery Sound", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Play subtle alert sound upon successful background dispatch", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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



        // Default SIM Slot
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("SIM Slot", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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

        // Default Country Dialing Code (India +91)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Country Code", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AppBadge(text = if (countryCode == "+91") "India (+91)" else countryCode, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Default dialing prefix applied when standardizing 10-digit mobile numbers for tasks and replies.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("+91" to "+91 India", "+1" to "+1 US/CA", "+44" to "+44 UK", "+971" to "+971 UAE").forEach { (code, label) ->
                    AppChip(
                        selected = countryCode == code,
                        onClick = {
                            countryCode = code
                            prefs.defaultCountryCode = code
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            var customCodeText by remember(countryCode) { mutableStateOf(countryCode) }
            AppInputField(
                value = customCodeText,
                onValueChange = { newStr ->
                    val clean = if (newStr.startsWith("+")) "+" + newStr.drop(1).filter(Char::isDigit) else "+" + newStr.filter(Char::isDigit)
                    customCodeText = clean
                    if (clean.length in 2..5) {
                        countryCode = clean
                        prefs.defaultCountryCode = clean
                    }
                },
                label = "Custom Code",
                placeholder = "+91",
                leadingIcon = Icons.Default.Public,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // Cellular SMS Quota & Safety Controls
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SMS Quota", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                Text("SMS Limit:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        100 to "100",
                        500 to "500",
                        1000 to "1,000",
                        5000 to "5,000"
                    ).forEach { (limitVal, label) ->
                        AppChip(
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
