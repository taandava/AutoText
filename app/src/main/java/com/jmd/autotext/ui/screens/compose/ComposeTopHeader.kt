package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.ui.components.BentoBadge
import com.jmd.autotext.ui.components.GlassSegmentedControl
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun ComposeTopHeader(
    selectedChannel: ChannelType,
    taskToEdit: ScheduledTask?,
    composeModeTab: Int,
    onComposeModeTabChanged: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BentoGlassTokens.GlassSurfaceElevated)
            .border(
                width = 1.dp,
                brush = BentoGlassTokens.GlassBorderGradient,
                shape = BentoGlassTokens.ShapeMedium
            )
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = BentoGlassTokens.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (taskToEdit != null) "Edit Task" else "Schedule Task",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                        Text(
                            text = if (taskToEdit != null) "Modify ${taskToEdit.channel.displayName} Configuration" else "Automated Action Dispatcher",
                            fontSize = 12.sp,
                            color = BentoGlassTokens.TextMuted
                        )
                    }
                }

                BentoBadge(
                    text = selectedChannel.displayName,
                    color = BentoGlassTokens.NeonCyan
                )
            }

            if (taskToEdit == null) {
                Spacer(modifier = Modifier.height(6.dp))
                GlassSegmentedControl(
                    items = listOf("Single Task", "Contact Sequence (Multi-Task)"),
                    selectedIndex = composeModeTab,
                    onItemSelected = onComposeModeTabChanged,
                    activeColor = BentoGlassTokens.ElectricIndigo
                )
            }
        }
    }
}
