package com.jmd.autotext.ui.screens.more.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun SettingsPreferencesCards(
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var countryCode by remember { mutableStateOf(prefs.defaultCountryCode) }
    var appLock by remember { mutableStateOf(prefs.isAppLockEnabled) }
    var simSlot by remember { mutableIntStateOf(prefs.defaultSimSlot) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // WhatsApp Default Country Code
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Text("Default Country Code", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Auto-prepended to 10-digit numbers for WhatsApp routing.", color = BentoGlassTokens.TextMuted, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            GlassInputField(
                value = countryCode,
                onValueChange = {
                    countryCode = it.filter { ch -> ch.isDigit() }
                    prefs.defaultCountryCode = countryCode
                },
                label = "Country Code (e.g. 91, 1, 44)",
                placeholder = "91",
                leadingIcon = Icons.Default.Public,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = BentoGlassTokens.NeonCyan
            )
        }

        // App Security PIN
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Text("App Security Lock", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Require PIN to open the AutoText app itself.", color = BentoGlassTokens.TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AutoText In-App Protection", color = BentoGlassTokens.TextMuted, fontSize = 13.sp)
                Switch(
                    checked = appLock,
                    onCheckedChange = {
                        appLock = it
                        prefs.isAppLockEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BentoGlassTokens.NeonCyan,
                        checkedTrackColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
                    )
                )
            }
        }

        // Default SIM Slot
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Text("Default SIM Slot", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Preferred carrier SIM card for outbound SMS messages.", color = BentoGlassTokens.TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("Default" to 0, "SIM 1" to 1, "SIM 2" to 2).forEach { (label, slot) ->
                    GlassChip(
                        selected = simSlot == slot,
                        onClick = {
                            simSlot = slot
                            prefs.defaultSimSlot = slot
                        },
                        label = label,
                        activeColor = BentoGlassTokens.NeonCyan
                    )
                }
            }
        }
    }
}
