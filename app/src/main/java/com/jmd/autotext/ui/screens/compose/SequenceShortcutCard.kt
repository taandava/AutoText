package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassButton
import com.jmd.autotext.ui.components.GlassButtonVariant
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun SequenceShortcutCard(
    onBuildSequence: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.AltRoute,
                    contentDescription = null,
                    tint = BentoGlassTokens.NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Contact Sequence Studio", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                    Text("Schedule multiple messages per number at intervals", color = BentoGlassTokens.TextMuted, fontSize = 12.sp)
                }
            }
            GlassButton(
                text = "Build Sequence",
                icon = Icons.Default.Add,
                onClick = onBuildSequence,
                variant = GlassButtonVariant.SECONDARY,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = BentoGlassTokens.ShapePill
            )
        }
    }
}
