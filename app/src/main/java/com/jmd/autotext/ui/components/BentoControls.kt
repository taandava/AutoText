@file:Suppress("FunctionName")

package com.jmd.autotext.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.*

enum class GlassButtonVariant {
    PRIMARY,        // Gradient fill
    SECONDARY,      // Frosted translucent surface
    SUCCESS,        // Emerald gradient
    DANGER,         // Rose gradient
    OUTLINE         // Thin glass outline
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: RoundedCornerShape = BentoGlassTokens.ShapePill,
    elevation: Dp = BentoGlassTokens.ElevationLevel1,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = BentoGlassTokens.SpringFast,
        label = "glassButtonScale"
    )

    val backgroundModifier = when (variant) {
        GlassButtonVariant.PRIMARY -> Modifier.background(BentoGlassTokens.NeonCyan)
        GlassButtonVariant.SUCCESS -> Modifier.background(BentoGlassTokens.VividEmerald)
        GlassButtonVariant.DANGER -> Modifier.background(BentoGlassTokens.CyberRose)
        GlassButtonVariant.SECONDARY -> Modifier
            .background(BentoGlassTokens.GlassSurfaceElevated)
            .border(1.dp, BentoGlassTokens.GlassBorderSubtle, shape)
        GlassButtonVariant.OUTLINE -> Modifier
            .background(Color.Transparent)
            .border(1.dp, BentoGlassTokens.GlassBorderMedium, shape)
    }

    val contentColor = when (variant) {
        GlassButtonVariant.PRIMARY -> Color(0xFF003548)
        GlassButtonVariant.SUCCESS -> Color(0xFF003822)
        GlassButtonVariant.DANGER -> Color(0xFF450A0A)
        GlassButtonVariant.SECONDARY, GlassButtonVariant.OUTLINE -> BentoGlassTokens.TextPrimary
    }

    val rippleColor = when (variant) {
        GlassButtonVariant.PRIMARY, GlassButtonVariant.SUCCESS, GlassButtonVariant.DANGER -> Color.Black.copy(alpha = 0.2f)
        GlassButtonVariant.SECONDARY, GlassButtonVariant.OUTLINE -> BentoGlassTokens.NeonCyan.copy(alpha = 0.2f)
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = if (enabled && variant != GlassButtonVariant.OUTLINE) elevation else 0.dp, shape = shape, clip = false)
            .then(backgroundModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = rippleColor),
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Bento Micro Status Badge with Glowing Dot (M3 Pill Shaped)
 */
@Composable
fun BentoBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BentoGlassTokens.NeonCyan,
    showDot: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(BentoGlassTokens.ShapePill)
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.35f), BentoGlassTokens.ShapePill)
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Bento Glass Segmented Switcher / Filter Control
 */
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = BentoGlassTokens.NeonCyan
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BentoGlassTokens.ShapePill)
            .background(BentoGlassTokens.GlassSurfaceElevated)
            .border(1.dp, BentoGlassTokens.GlassBorderSubtle, BentoGlassTokens.ShapePill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val interactionSource = remember { MutableInteractionSource() }
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) activeColor else Color.Transparent,
                animationSpec = tween(180),
                label = "segmentedBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF002738) else BentoGlassTokens.TextMuted,
                animationSpec = tween(180),
                label = "segmentedText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(BentoGlassTokens.ShapePill)
                    .background(bgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = activeColor.copy(alpha = 0.2f)),
                        onClick = { onItemSelected(index) }
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Bento Empty State Component
 */


/**
 * Bento Glass Interactive Chip / Filter Pill
 */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = BentoGlassTokens.NeonCyan
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor.copy(alpha = 0.22f) else BentoGlassTokens.GlassSurfaceElevated,
        animationSpec = tween(180),
        label = "glassChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) activeColor else BentoGlassTokens.GlassBorderSubtle,
        animationSpec = tween(180),
        label = "glassChipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else BentoGlassTokens.TextMuted,
        animationSpec = tween(180),
        label = "glassChipText"
    )

    Box(
        modifier = modifier
            .clip(BentoGlassTokens.ShapePill)
            .background(bgColor)
            .border(1.dp, borderColor, BentoGlassTokens.ShapePill)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = activeColor.copy(alpha = 0.25f)),
                onClick = onClick
            )
            .padding(horizontal = 7.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) activeColor else BentoGlassTokens.TextMuted,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Bento Glass Toggle Row for Settings and Switches
 */
@Composable
fun GlassToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = BentoGlassTokens.NeonCyan,
    activeTrackColor: Color = BentoGlassTokens.NeonCyan
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
            }
            Column {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BentoGlassTokens.TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = BentoGlassTokens.TextMuted,
                    lineHeight = 13.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeTrackColor,
                uncheckedThumbColor = BentoGlassTokens.TextMuted,
                uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
            )
        )
    }
}

/**
 * Compact Switch (40x22dp).
 *
 * Material3's `Switch` reserves a fixed ~52x32dp slot regardless of `Modifier.scale()`,
 * because scale is a draw-time transform only. This replacement shrinks the *layout*
 * footprint too, while keeping a >=32dp touch target for accessibility.
 */
@Composable
fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = BentoGlassTokens.NeonCyan
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else BentoGlassTokens.GlassSurfaceElevated,
        animationSpec = tween(180),
        label = "compactSwitchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(180),
        label = "compactSwitchThumb"
    )

    Box(
        modifier = modifier
            .size(width = 40.dp, height = 32.dp)
            .clip(BentoGlassTokens.ShapePill)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = activeColor.copy(alpha = 0.25f))
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 22.dp)
                .clip(BentoGlassTokens.ShapePill)
                .background(trackColor)
                .border(
                    0.5.dp,
                    if (checked) Color.Transparent else BentoGlassTokens.GlassBorderSubtle,
                    BentoGlassTokens.ShapePill
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else BentoGlassTokens.TextMuted)
            )
        }
    }
}

@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BentoGlassTokens.NeonCyan,
    showDot: Boolean = true
) = BentoBadge(text = text, modifier = modifier, color = color, showDot = showDot)

// Material 3 Canonical Aliases
typealias Material3ButtonVariant = GlassButtonVariant

@Composable
fun Material3Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: RoundedCornerShape = BentoGlassTokens.ShapePill,
    elevation: Dp = BentoGlassTokens.ElevationLevel1,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
) = GlassButton(text, onClick, modifier, variant, icon, enabled, isLoading, shape, elevation, contentPadding)

@Composable
fun Material3Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BentoGlassTokens.NeonCyan,
    showDot: Boolean = true
) = BentoBadge(text, modifier, color, showDot)

@Composable
fun Material3Chip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = BentoGlassTokens.NeonCyan
) = GlassChip(selected, onClick, label, modifier, icon, activeColor)

@Composable
fun Material3SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = BentoGlassTokens.NeonCyan
) = GlassSegmentedControl(items, selectedIndex, onItemSelected, modifier, activeColor)

@Composable
fun Material3ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = BentoGlassTokens.NeonCyan,
    activeTrackColor: Color = BentoGlassTokens.NeonCyan
) = GlassToggleRow(title, subtitle, checked, onCheckedChange, modifier, icon, iconTint, activeTrackColor)


