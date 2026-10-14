package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun ComposeFakeCallAndSoundCard(
    selectedChannel: ChannelType,
    fakeCallerName: String,
    onFakeCallerNameChange: (String) -> Unit,
    fakeCallerNumber: String,
    onFakeCallerNumberChange: (String) -> Unit,
    fakeCallStyle: String,
    onFakeCallStyleChange: (String) -> Unit,
    soundModeSetting: String,
    onSoundModeSettingChange: (String) -> Unit
) {
    when (selectedChannel) {
        ChannelType.FAKE_CALL -> {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                shape = BentoGlassTokens.ShapeLarge
            ) {
                Text(
                    "Fake Call Simulation Settings",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassInputField(
                    value = fakeCallerName,
                    onValueChange = onFakeCallerNameChange,
                    label = "Caller Name",
                    placeholder = "Office / Manager / Home",
                    leadingIcon = Icons.Default.Person,
                    accentColor = BentoGlassTokens.CyberRose
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassInputField(
                    value = fakeCallerNumber,
                    onValueChange = onFakeCallerNumberChange,
                    label = "Caller Phone Number",
                    placeholder = "+91 98765 43210",
                    leadingIcon = Icons.Default.Phone,
                    accentColor = BentoGlassTokens.CyberRose
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Screen Style Theme:", color = BentoGlassTokens.TextMuted, fontSize = 11.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("PIXEL", "SAMSUNG", "WHATSAPP").forEach { style ->
                        GlassChip(
                            selected = fakeCallStyle == style,
                            onClick = { onFakeCallStyleChange(style) },
                            label = style,
                            activeColor = BentoGlassTokens.CyberRose,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        ChannelType.SOUND_MODE -> {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                shape = BentoGlassTokens.ShapeLarge
            ) {
                Text(
                    "Sound Profile Mode",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SILENT", "VIBRATE", "NORMAL").forEach { mode ->
                        GlassChip(
                            selected = soundModeSetting == mode,
                            onClick = { onSoundModeSettingChange(mode) },
                            label = mode,
                            activeColor = BentoGlassTokens.NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        else -> Unit
    }
}
