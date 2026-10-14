package com.jmd.autotext.ui.screens.more.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun SettingsDeviceIdentityCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var ownerName by remember { mutableStateOf(prefs.userName) }
    var deviceName by remember { mutableStateOf(prefs.customDeviceName) }

    val rawModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}".trim()
    val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = BentoGlassTokens.GlassSurfaceDefault,
        borderColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = BentoGlassTokens.NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Device Identity & Owner Profile",
                    fontWeight = FontWeight.Bold,
                    color = BentoGlassTokens.TextPrimary,
                    fontSize = 15.sp
                )
            }
            BentoBadge(text = "Device Info", color = BentoGlassTokens.NeonCyan)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Customize your owner name and device identifier for local organization and logs.",
            color = BentoGlassTokens.TextMuted,
            fontSize = 11.5.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassInputField(
            value = ownerName,
            onValueChange = {
                ownerName = it
                prefs.userName = it
            },
            label = "Owner / User Name",
            placeholder = "e.g. Dilip Sharma",
            leadingIcon = Icons.Default.Person,
            accentColor = BentoGlassTokens.NeonCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        GlassInputField(
            value = deviceName,
            onValueChange = {
                deviceName = it
                prefs.customDeviceName = it
            },
            label = "Device Runner Name",
            placeholder = "e.g. Office Runner / Primary Device",
            leadingIcon = Icons.Default.PhoneAndroid,
            accentColor = BentoGlassTokens.VividEmerald
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware Specifications Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BentoGlassTokens.GlassSurfaceSubtle, RoundedCornerShape(10.dp))
                .border(1.dp, BentoGlassTokens.GlassBorderSubtle, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("HARDWARE MODEL:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted, fontWeight = FontWeight.Medium)
                    Text(rawModel, fontSize = 11.5.sp, color = BentoGlassTokens.NeonCyan, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SYSTEM OS:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted, fontWeight = FontWeight.Medium)
                    Text(osVersion, fontSize = 11.5.sp, color = BentoGlassTokens.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DEVICE ID:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted, fontWeight = FontWeight.Medium)
                    Text(prefs.uniqueDeviceId, fontSize = 11.sp, color = BentoGlassTokens.VividEmerald, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        GlassButton(
            text = "Save Device Identity",
            onClick = {
                prefs.userName = ownerName.trim()
                prefs.customDeviceName = deviceName.trim()
                Toast.makeText(context, "Identity saved successfully!", Toast.LENGTH_SHORT).show()
            },
            icon = Icons.Default.Check,
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
