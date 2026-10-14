package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmd.autotext.ui.components.GlassButton
import com.jmd.autotext.ui.components.GlassButtonVariant
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun ComposeBottomBar(
    isEditing: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
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
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassButton(
                text = "Cancel",
                onClick = onCancel,
                variant = GlassButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )

            GlassButton(
                text = if (isEditing) "Update Task" else "Schedule Task",
                onClick = onSave,
                variant = GlassButtonVariant.PRIMARY,
                icon = if (isEditing) Icons.Default.Check else Icons.Default.Schedule,
                modifier = Modifier.weight(1.8f)
            )
        }
    }
}
