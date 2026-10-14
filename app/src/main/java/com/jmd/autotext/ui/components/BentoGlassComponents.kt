@file:Suppress("FunctionName")

package com.jmd.autotext.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmd.autotext.ui.theme.Material3DesignTokens

/**
 * Material 3 Design System Official Component Architecture
 * Full Google Material 3 (Material You) system component suite:
 * - Cards: [M3Card], [M3CardElevated], [M3StatCard], [M3EmptyState], [M3DialogCard]
 * - Controls: [M3Button], [M3Badge], [M3Chip], [M3SegmentedControl], [M3ToggleRow]
 * - Inputs: [M3InputField], [M3SearchField]
 * - Pickers: [M3DateTimePicker]
 */

@Composable
fun M3Card(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    backgroundColor: Color = Material3DesignTokens.GlassSurfaceDefault,
    borderColor: Color = Material3DesignTokens.GlassBorderSubtle,
    borderWidth: Dp = 1.dp,
    elevation: Dp = Material3DesignTokens.ElevationLevel1,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = Material3Card(
    modifier = modifier,
    shape = shape,
    backgroundColor = backgroundColor,
    borderColor = borderColor,
    borderWidth = borderWidth,
    elevation = elevation,
    onClick = onClick,
    content = content
)

@Composable
fun M3CardElevated(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    accentGlow: Color? = null,
    elevation: Dp = Material3DesignTokens.ElevationLevel2,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = Material3CardElevated(
    modifier = modifier,
    shape = shape,
    accentGlow = accentGlow,
    elevation = elevation,
    onClick = onClick,
    content = content
)

@Composable
fun M3DialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = Material3DialogCard(
    modifier = modifier,
    content = content
)

@Composable
fun M3StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color = Material3DesignTokens.NeonCyan,
    subText: String? = null,
    onClick: (() -> Unit)? = null
) = Material3StatCard(
    modifier = modifier,
    title = title,
    value = value,
    icon = icon,
    iconTint = iconTint,
    subText = subText,
    onClick = onClick
)

@Composable
fun M3EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) = Material3EmptyState(
    icon = icon,
    title = title,
    description = description,
    modifier = modifier,
    actionText = actionText,
    onActionClick = onActionClick
)
