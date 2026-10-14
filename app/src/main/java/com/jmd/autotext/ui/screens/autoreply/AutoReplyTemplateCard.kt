package com.jmd.autotext.ui.screens.autoreply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassChip
import com.jmd.autotext.ui.components.GlassInputField
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun AutoReplyTemplateCard(
    isCall: Boolean,
    replyText: String,
    onReplyTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge
    ) {
        GlassInputField(
            value = replyText,
            onValueChange = onReplyTextChanged,
            label = "Auto-Reply Message Text",
            placeholder = "Thanks for reaching out! Will call you back shortly.",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            accentColor = BentoGlassTokens.VividEmerald
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 13.sp, color = BentoGlassTokens.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        val tagList = listOf("{name}", "{first_name}", "{phone}", "{time}", "{date}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tagList.forEach { tag ->
                GlassChip(
                    selected = false,
                    onClick = { onReplyTextChanged(replyText + " $tag") },
                    label = tag,
                    activeColor = BentoGlassTokens.ElectricIndigo,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Quick Presets:", fontSize = 13.sp, color = BentoGlassTokens.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        val presets = if (isCall) {
            listOf(
                "I missed your call and will call back shortly.",
                "Thank you for calling! I will get back soon.",
                "Currently in a meeting. Will call back soon."
            )
        } else {
            listOf(
                "Currently in a meeting. Will reply shortly.",
                "Driving right now. Will reply once safe.",
                "Thanks for reaching out! Will reply soon."
            )
        }
        presets.forEach { preset ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BentoGlassTokens.ShapeMedium)
                    .background(BentoGlassTokens.GlassSurfaceDefault)
                    .border(1.dp, BentoGlassTokens.GlassBorderSubtle, BentoGlassTokens.ShapeMedium)
                    .clickable { onReplyTextChanged(preset) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(preset, fontSize = 13.sp, color = BentoGlassTokens.TextSecondary, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
