package com.jmd.autotext.ui.screens.more.permissions

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassButton
import com.jmd.autotext.ui.components.GlassButtonVariant
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun KeepOpenRecentAppsCard(context: Context = LocalContext.current) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = BentoGlassTokens.RadiantAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Keep Open in Recent Apps (Prevent Killing)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BentoGlassTokens.TextPrimary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "To prevent Samsung One UI, Xiaomi, or OnePlus from killing the app or stopping its accessibility engine:\n\n" +
                    "• Samsung: Open Recent Apps (||| button) -> Tap AutoText icon at top of the card -> Tap 'Keep open' (padlock icon).\n" +
                    "• Xiaomi / HyperOS: In Recent Apps -> Long-press AutoText card -> Tap Padlock icon.\n" +
                    "• Oppo / Vivo / OnePlus: In Recent Apps -> Tap 3 dots (⋮) on AutoText -> Select 'Lock'.",
            fontSize = 11.5.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        GlassButton(
            text = "Open OEM Device Care / Background Settings",
            onClick = { AccessibilityHealthManager.openOemBackgroundSettings(context) },
            icon = Icons.Default.Settings,
            variant = GlassButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
