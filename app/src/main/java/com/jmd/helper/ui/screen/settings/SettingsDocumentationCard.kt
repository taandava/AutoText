/**
 * Package: com.jmd.helper.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsDocumentationCard].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsDocumentationCard.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.text.font.FontWeight], [androidx.compose.ui.unit.dp]
 * - Android Framework & Jetpack: [androidx.compose.material.icons.Icons], [androidx.compose.material.icons.filled.MenuBook], [androidx.compose.material.icons.filled.Share], [androidx.compose.material3.Icon]
 */

package com.jmd.helper.ui.screen.settings

import androidx.compose.material.icons.automirrored.filled.*

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.screen.readDocumentationAsset
import com.jmd.helper.ui.theme.AppDesignTokens

/**
 * What it is:
 * Card linking to the complete technical specifications and architectural documentation asset.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsDocumentationCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsDocumentationCard], [SettingsDocumentationCard]
 * @param context Android context for launching chooser share intents.
 * @param onOpenDocumentation Callback invoked to view bundled documentation screen.
 * @param modifier Optional layout modifier.
 */
@Composable
fun SettingsDocumentationCard(
    context: Context,
    onOpenDocumentation: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Technical Manual & Logic Guide", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Complete architectural manual, 100 SMS daily TRAI limit, WhatsApp lockscreen unlock, silent watchdog, and 100% offline security logic bundled inside this APK as HELPER_FEATURES_AND_LOGIC.md.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        putExtra(Intent.EXTRA_SUBJECT, "Helper - Complete Specifications & Logic Guide")
                        putExtra(Intent.EXTRA_TEXT, doc)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Helper Documentation"))
                },
                icon = Icons.Filled.Share,
                variant = GlassButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
