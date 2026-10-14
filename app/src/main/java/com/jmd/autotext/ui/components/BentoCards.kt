@file:Suppress("FunctionName")

package com.jmd.autotext.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.jmd.autotext.ui.theme.Material3DesignTokens

/**
 * Official Material 3 Card Component
 * Uses M3 ElevatedCard with authentic tonal elevation, depth shadows, and customizable shapes.
 */
@Composable
fun Material3Card(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    backgroundColor: Color = Material3DesignTokens.GlassSurfaceDefault,
    borderColor: Color = Material3DesignTokens.GlassBorderSubtle,
    borderWidth: Dp = 1.dp,
    elevation: Dp = Material3DesignTokens.ElevationLevel1,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = Material3DesignTokens.SpringFast,
        label = "m3CardScale"
    )

    ElevatedCard(
        modifier = modifier
            .scale(scale)
            .border(borderWidth, borderColor, shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = backgroundColor,
            contentColor = Material3DesignTokens.TextPrimary
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation + 2.dp,
            focusedElevation = elevation + 1.dp,
            hoveredElevation = elevation + 1.dp
        ),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            content = content
        )
    }
}

/**
 * Official Material 3 Elevated Card for High-Priority Sections
 * Uses Level 2+ tonal elevation with ambient depth and border highlights.
 */
@Composable
fun Material3CardElevated(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    accentGlow: Color? = null,
    elevation: Dp = Material3DesignTokens.ElevationLevel2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = Material3DesignTokens.SpringFast,
        label = "m3CardElevatedScale"
    )
    val borderBrush = if (accentGlow != null) {
        Brush.verticalGradient(
            listOf(accentGlow.copy(alpha = 0.5f), Material3DesignTokens.GlassBorderSubtle)
        )
    } else {
        Material3DesignTokens.GlassBorderGradient
    }

    ElevatedCard(
        modifier = modifier
            .scale(scale)
            .border(1.dp, borderBrush, shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = Material3DesignTokens.GlassSurfaceElevated,
            contentColor = Material3DesignTokens.TextPrimary
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation + 2.dp,
            focusedElevation = elevation + 1.dp,
            hoveredElevation = elevation + 1.dp
        ),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            content = content
        )
    }
}

/**
 * Material 3 Metric / Telemetry Stat Card
 */
@Composable
fun Material3StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color = Material3DesignTokens.NeonCyan,
    subText: String? = null,
    onClick: (() -> Unit)? = null
) {
    Material3Card(
        modifier = modifier,
        shape = Material3DesignTokens.ShapeSmall,
        backgroundColor = Material3DesignTokens.GlassSurfaceSubtle,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Material3DesignTokens.TextPrimary
        )
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = Material3DesignTokens.TextMuted,
            maxLines = 1
        )
        if (subText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subText,
                fontSize = 11.sp,
                color = iconTint,
                maxLines = 1
            )
        }
    }
}

/**
 * Material 3 Grid Row: Evenly distributes cards in a grid layout
 */
@Composable
fun Material3GridRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}

/**
 * Material 3 Empty State Container
 */
@Composable
fun Material3EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Material3Card(
        modifier = modifier.fillMaxWidth(),
        shape = Material3DesignTokens.ShapeMedium,
        backgroundColor = Material3DesignTokens.GlassSurfaceSubtle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = Material3DesignTokens.CardPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Material3DesignTokens.NeonCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Material3DesignTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = Material3DesignTokens.TextMuted,
                lineHeight = 15.sp
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(6.dp))
                GlassButton(
                    text = actionText,
                    onClick = onActionClick,
                    variant = GlassButtonVariant.PRIMARY
                )
            }
        }
    }
}

@Composable
fun Material3DialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Material3CardElevated(
        modifier = modifier.fillMaxWidth(),
        shape = Material3DesignTokens.ShapeMedium,
        accentGlow = Material3DesignTokens.NeonCyan,
        content = content
    )
}

// ========================================================
// Compatibility Aliases for seamless migration
// ========================================================
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    backgroundColor: Color = Material3DesignTokens.GlassSurfaceDefault,
    borderColor: Color = Material3DesignTokens.GlassBorderSubtle,
    borderWidth: Dp = 1.dp,
    elevation: Dp = Material3DesignTokens.ElevationLevel1,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = Material3Card(modifier, shape, backgroundColor, borderColor, borderWidth, elevation, onClick, content)

@Composable
fun BentoCardElevated(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    accentGlow: Color? = null,
    elevation: Dp = Material3DesignTokens.ElevationLevel2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = Material3CardElevated(modifier, shape, accentGlow, elevation, onClick, content)

@Composable
fun BentoStatTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color = Material3DesignTokens.NeonCyan,
    subText: String? = null,
    onClick: (() -> Unit)? = null
) = Material3StatCard(modifier, title, value, icon, iconTint, subText, onClick)

@Composable
fun BentoGridRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit
) = Material3GridRow(modifier, horizontalArrangement, content)

@Composable
fun BentoEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) = Material3EmptyState(icon, title, description, modifier, actionText, onActionClick)

@Composable
fun GlassDialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = Material3DialogCard(modifier, content)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    backgroundColor: Color = Material3DesignTokens.GlassSurfaceDefault,
    borderColor: Color = Material3DesignTokens.GlassBorderSubtle,
    borderWidth: Dp = 1.dp,
    elevation: Dp = Material3DesignTokens.ElevationLevel1,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = Material3Card(modifier, shape, backgroundColor, borderColor, borderWidth, elevation, onClick, content)

@Composable
fun AppCardElevated(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    accentGlow: Color? = null,
    elevation: Dp = Material3DesignTokens.ElevationLevel2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = Material3CardElevated(modifier, shape, accentGlow, elevation, onClick, content)
