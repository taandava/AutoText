package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassChip
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun ComposeChannelSelector(
    selectedChannel: ChannelType,
    onSelectChannel: (ChannelType) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge
    ) {
        Text(
            "Select Action Channel",
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = BentoGlassTokens.TextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        val availableChannels = ChannelType.values().toList()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            availableChannels.chunked(2).forEach { rowChannels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowChannels.forEach { channel ->
                        val isSelected = selectedChannel == channel
                        val channelIcon = when (channel) {
                            ChannelType.SMS -> Icons.AutoMirrored.Filled.Message
                            ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS -> Icons.AutoMirrored.Filled.Send
                            ChannelType.EMAIL -> Icons.Default.Email
                            ChannelType.CALL -> Icons.Default.Call
                            ChannelType.FAKE_CALL -> Icons.AutoMirrored.Filled.PhoneCallback
                            ChannelType.REMINDER -> Icons.Default.Alarm
                            ChannelType.SOUND_MODE -> Icons.AutoMirrored.Filled.VolumeUp
                        }
                        GlassChip(
                            selected = isSelected,
                            onClick = { onSelectChannel(channel) },
                            label = channel.displayName,
                            icon = channelIcon,
                            activeColor = BentoGlassTokens.ElectricIndigo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowChannels.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
