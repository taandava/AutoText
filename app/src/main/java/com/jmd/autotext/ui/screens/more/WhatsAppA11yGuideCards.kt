@file:Suppress("FunctionName")

package com.jmd.autotext.ui.screens.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun WhatsAppA11yStatusCard(context: Context, isA11yActive: Boolean) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isA11yActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.12f) else BentoGlassTokens.CyberRose.copy(alpha = 0.12f),
        borderColor = if (isA11yActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.35f) else BentoGlassTokens.CyberRose.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isA11yActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isA11yActive) BentoGlassTokens.VividEmerald else BentoGlassTokens.CyberRose,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isA11yActive) "Accessibility Service Active" else "Accessibility Service Inactive",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isA11yActive) BentoGlassTokens.VividEmerald else BentoGlassTokens.CyberRose
                )
                Text(
                    text = if (isA11yActive) "Ready to auto-send WhatsApp and auto-unlock" else "Required for WhatsApp sending and PIN auto-unlock",
                    fontSize = 11.5.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
            text = if (isA11yActive) "Accessibility Settings" else "Turn ON Accessibility Service",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            },
            icon = Icons.Default.AccessibilityNew,
            variant = if (isA11yActive) GlassButtonVariant.SUCCESS else GlassButtonVariant.DANGER,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WhatsAppRestrictedSettingsCard(context: Context) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = BentoGlassTokens.RadiantAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Android 13 / 14: Sideloaded App Fix",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = BentoGlassTokens.RadiantAmber
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "If turning on Accessibility says \"Restricted setting - For your security, this setting is unavailable\":\n\n" +
                    "1. Tap 'Open App Info' button below.\n" +
                    "2. In the top-right corner, tap the 3 dots (⋮).\n" +
                    "3. Tap 'Allow restricted settings'.\n" +
                    "4. Enter your phone PIN/fingerprint.\n" +
                    "5. Return here and enable Accessibility!",
            fontSize = 12.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
            text = "Open App Info (Tap 3 Dots)",
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            },
            icon = Icons.Default.Settings,
            variant = GlassButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WhatsAppBatteryOptimizationCard(context: Context) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Text("Battery & Background Restrictions", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "To ensure tasks trigger on time when phone is locked:\n" +
                    "• Exempt AutoText from battery optimization.\n" +
                    "• On Samsung: Device Care -> Battery -> Background usage limits -> Never sleeping apps -> Add AutoText.\n" +
                    "• On Xiaomi / Vivo / Oppo: Enable 'Autostart' in App settings.",
            fontSize = 11.5.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassButton(
            text = "Exempt From Battery Optimization",
            onClick = {
                try {
                    @android.annotation.SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(fallbackIntent)
                    } catch (_: Exception) {
                        // In extremely restricted kiosk/OEM profiles
                    }
                }
            },
            icon = Icons.Default.BatteryChargingFull,
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WhatsAppLiveTestCard(
    context: Context,
    testPhone: String,
    onTestPhoneChange: (String) -> Unit,
    testMessage: String,
    onTestMessageChange: (String) -> Unit
) {
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Live WhatsApp Send Test", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Test sending a WhatsApp message right now. Lock your screen right after tapping if you want to test auto-unlock!", fontSize = 11.5.sp, color = BentoGlassTokens.TextMuted)

        Spacer(modifier = Modifier.height(10.dp))

        GlassInputField(
            value = testPhone,
            onValueChange = onTestPhoneChange,
            label = "Phone Number",
            placeholder = "e.g. 9876543210",
            leadingIcon = Icons.Default.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            accentColor = BentoGlassTokens.VividEmerald
        )

        Spacer(modifier = Modifier.height(8.dp))

        GlassInputField(
            value = testMessage,
            onValueChange = onTestMessageChange,
            label = "Message Text",
            placeholder = "Type test message...",
            leadingIcon = Icons.Default.EditNote,
            accentColor = BentoGlassTokens.VividEmerald
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
            text = "Dispatch Test WhatsApp Now",
            onClick = {
                if (testPhone.trim().isEmpty()) {
                    Toast.makeText(context, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                    return@GlassButton
                }
                val sender = WhatsAppAutomationSender(context)
                val success = sender.send(
                    taskId = 9999L,
                    channel = ChannelType.WHATSAPP,
                    recipientPhone = testPhone.trim(),
                    messageText = testMessage.trim()
                )
                if (success) {
                    Toast.makeText(context, "Dispatched WhatsApp! Accessibility will tap Send.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to launch WhatsApp.", Toast.LENGTH_SHORT).show()
                }
            },
            icon = Icons.Default.PlayArrow,
            variant = GlassButtonVariant.SUCCESS,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
