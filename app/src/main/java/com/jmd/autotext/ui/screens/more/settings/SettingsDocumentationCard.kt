package com.jmd.autotext.ui.screens.more.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Share
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
import com.jmd.autotext.ui.screens.more.readDocumentationAsset
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun SettingsDocumentationCard(
    context: Context,
    onOpenDocumentation: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Technical Manual & Logic Guide", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Complete architectural manual, 100 SMS daily TRAI limit, automated WhatsApp lockscreen bypass, silent watchdog, and 100% offline security logic bundled inside this APK as AUTOTEXT_FEATURES_AND_LOGIC.md.",
            color = BentoGlassTokens.TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GlassButton(
                text = "Read Guide",
                onClick = onOpenDocumentation,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "Share Guide",
                onClick = {
                    val doc = readDocumentationAsset(context)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AutoText - Complete Specifications & Logic Guide")
                        putExtra(Intent.EXTRA_TEXT, doc)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share AutoText Documentation"))
                },
                icon = Icons.Default.Share,
                variant = GlassButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
