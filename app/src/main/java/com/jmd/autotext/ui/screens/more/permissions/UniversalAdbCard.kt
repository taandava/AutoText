package com.jmd.autotext.ui.screens.more.permissions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
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
fun UniversalAdbCard(
    isA11yActive: Boolean,
    isAdminActive: Boolean,
    hasOverlay: Boolean,
    hasSecurePerm: Boolean,
    onAutoHealClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val universalAdbCommand = "adb shell dpm set-device-admin --user 0 com.jmd.autotext/.service.AutoTextDeviceAdminReceiver && " +
            "adb shell pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS && " +
            "adb shell settings put secure enabled_accessibility_services com.jmd.autotext/com.jmd.autotext.service.AutoTextAccessibilityService && " +
            "adb shell settings put secure accessibility_enabled 1 && " +
            "adb shell cmd appops set com.jmd.autotext SYSTEM_ALERT_WINDOW allow && " +
            "adb shell dumpsys deviceidle whitelist +com.jmd.autotext"

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated,
        borderColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = BentoGlassTokens.NeonCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "⚡ Master Auto-Heal & Universal Fixer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Text(
                    text = "One-click automated recovery & permission synchronization",
                    fontSize = 11.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Health Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill("A11y", isA11yActive, Modifier.weight(1f))
            StatusPill("Admin", isAdminActive, Modifier.weight(1f))
            StatusPill("Overlay", hasOverlay, Modifier.weight(1f))
            StatusPill("Secure", hasSecurePerm, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Auto-Heal automatically restarts dead accessibility binders and synchronizes secure system settings. For 100% full automation (including device admin, lockscreen popup, and battery whitelist without prompts), run the universal ADB command below.",
            fontSize = 11.5.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 16.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Auto-Heal Now",
                onClick = onAutoHealClick,
                icon = Icons.Default.Refresh,
                variant = GlassButtonVariant.SUCCESS,
                modifier = Modifier.weight(1f)
            )

            GlassButton(
                text = "Copy 1-Line ADB Setup",
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Universal ADB Command", universalAdbCommand))
                    Toast.makeText(context, "Copied universal 1-line ADB setup command to clipboard!", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Default.ContentCopy,
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}

