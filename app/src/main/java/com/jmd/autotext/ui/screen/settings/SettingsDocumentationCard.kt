package com.jmd.autotext.ui.screen.settings
import androidx.compose.material3.MaterialTheme

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screen.readDocumentationAsset
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Card linking to the complete technical specifications and architectural documentation asset.
 *
 * @param context Android context for launching chooser share intents.
 * @param onOpenDocumentation Callback invoked to view bundled documentation screen.
 * @param modifier Optional layout modifier.
 */
@Composable
fun settingsDocumentationCard(
    context: Context,
    onOpenDocumentation: () -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📖", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Technical Manual & Logic Guide", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Complete architectural manual, 100 SMS daily TRAI limit, automated WhatsApp lockscreen bypass, silent watchdog, and 100% offline security logic bundled inside this APK as AUTOTEXT_FEATURES_AND_LOGIC.md.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            glassButton(
                text = "Read Guide",
                onClick = onOpenDocumentation,
                emoji = "📖",
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            glassButton(
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
                emoji = "📤",
                variant = GlassButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
