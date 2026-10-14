package com.jmd.autotext.ui.screens.more.permissions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
fun StatusPill(label: String, isOk: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = BentoGlassTokens.ShapePill,
        color = if (isOk) BentoGlassTokens.VividEmerald.copy(alpha = 0.15f) else BentoGlassTokens.CyberRose.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (isOk) BentoGlassTokens.VividEmerald.copy(alpha = 0.4f) else BentoGlassTokens.CyberRose.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isOk) BentoGlassTokens.VividEmerald else BentoGlassTokens.CyberRose, shape = RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOk) BentoGlassTokens.VividEmerald else BentoGlassTokens.CyberRose
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    desc: String,
    btnText: String,
    isGranted: Boolean = false,
    onClick: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isGranted) BentoGlassTokens.VividEmerald.copy(alpha = 0.08f) else BentoGlassTokens.GlassSurfaceElevated,
        borderColor = if (isGranted) BentoGlassTokens.VividEmerald.copy(alpha = 0.25f) else BentoGlassTokens.GlassBorderSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (isGranted) {
                Surface(
                    shape = BentoGlassTokens.ShapePill,
                    color = BentoGlassTokens.VividEmerald.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "ACTIVE",
                        color = BentoGlassTokens.VividEmerald,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(desc, color = BentoGlassTokens.TextMuted, fontSize = 11.5.sp)
        Spacer(modifier = Modifier.height(10.dp))
        GlassButton(
            text = btnText,
            onClick = onClick,
            variant = if (isGranted) GlassButtonVariant.SUCCESS else GlassButtonVariant.SECONDARY,
            shape = BentoGlassTokens.ShapePill
        )
    }
}
