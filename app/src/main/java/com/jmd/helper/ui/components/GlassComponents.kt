/**
 * Package: com.jmd.helper.ui.components
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [GlassComponents].
 *
 * What it does:
 * Core reusable UI design building blocks adhering to Material 3 Expressive Orange specifications:
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.draw.scale]
 * - Core Security & Utilities: [androidx.compose.animation.core.animateFloatAsState], [androidx.compose.animation.core.tween]
 * - Android Framework & Jetpack: [androidx.compose.animation.animateColorAsState], [androidx.compose.foundation.BorderStroke], [androidx.compose.foundation.background], [androidx.compose.foundation.border]
 */

package com.jmd.helper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.Material3DesignTokens

// ============================================================================
// MATERIAL 3 EXPRESSIVE BUTTONS & BADGES
// ============================================================================

/**
 * What it is:
 * Style variants for the [GlassButton] component.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [GlassButtonVariant].
 *
 * Linked with:
 * - Architecture: [GlassComponents], [GlassButtonVariant]
 */
enum class GlassButtonVariant {
    /** Primary theme button with high contrast brand orange fill. */
    PRIMARY,
    /** Tonal container button for secondary actions. */
    SECONDARY,
    /** Success green highlight for affirmative operations. */
    SUCCESS,
    /** Error red highlight for destructive operations. */
    DANGER,
    /** Subtle outlined button with outline stroke. */
    OUTLINE
}

/**
 * What it is:
 * Reusable Material 3 Expressive Button with responsive ripple, full text wrapping, and 48dp touch target.
 *
 * What it does:
 * Renders interactive Material 3 UI for [GlassButton] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [GlassButton]
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: RoundedCornerShape = AppDesignTokens.ShapeSmall,
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    emoji: String? = null,
    fontSize: TextUnit = 13.sp,
    maxLines: Int = 3,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = AppDesignTokens.SpringFast,
        label = "m3ButtonScale"
    )

    val isDark = com.jmd.helper.ui.theme.isAppDarkTheme()
    val containerColor = when (variant) {
        GlassButtonVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        GlassButtonVariant.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        GlassButtonVariant.SUCCESS -> if (isDark) Color(0xFF1B5E20) else Color(0xFF2E7D32)
        GlassButtonVariant.DANGER -> MaterialTheme.colorScheme.error
        GlassButtonVariant.OUTLINE -> Color.Transparent
    }

    val contentColor = when (variant) {
        GlassButtonVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        GlassButtonVariant.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        GlassButtonVariant.SUCCESS -> Color.White
        GlassButtonVariant.DANGER -> MaterialTheme.colorScheme.onError
        GlassButtonVariant.OUTLINE -> MaterialTheme.colorScheme.primary
    }

    val borderModifier = if (variant == GlassButtonVariant.OUTLINE) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .scale(scale)
            .then(borderModifier)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor.copy(alpha = 0.2f)),
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = elevation
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!emoji.isNullOrEmpty()) {
                        Text(text = emoji, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = maxLines,
                        softWrap = true,
                        overflow = overflow,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * What it is:
 * Compact status indicator badge using Material 3 tonal color container.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppBadge] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppBadge]
 */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    showDot: Boolean = true,
    icon: ImageVector? = null,
    emoji: String? = null
) {
    val effectiveColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(AppDesignTokens.ShapePill)
            .background(effectiveColor.copy(alpha = 0.12f))
            .border(0.75.dp, effectiveColor.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else if (!emoji.isNullOrEmpty()) {
            Text(
                text = emoji,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else if (showDot) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(effectiveColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = effectiveColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * What it is:
 * Segmented control switch with Material 3 Expressive pill indicator.
 *
 * What it does:
 * Renders interactive Material 3 UI for [GlassSegmentedControl] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [GlassSegmentedControl]
 */
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Unspecified
) {
    val effectiveActive = if (activeColor != Color.Unspecified) activeColor else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppDesignTokens.ShapeSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, AppDesignTokens.ShapeSmall)
            .padding(2.5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val interactionSource = remember { MutableInteractionSource() }
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) effectiveActive else Color.Transparent,
                animationSpec = tween(180),
                label = "m3SegmentedBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "m3SegmentedText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AppDesignTokens.ShapeSmall)
                    .background(bgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = effectiveActive.copy(alpha = 0.2f)),
                        onClick = { onItemSelected(index) }
                    )
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * What it is:
 * Material 3 Filter Chip with compact dimensions and animated selection feedback.
 *
 * What it does:
 * Renders interactive Material 3 UI for [GlassChip] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [GlassChip]
 */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = Color.Unspecified,
    emoji: String? = null
) {
    val effectiveActive = if (activeColor != Color.Unspecified) activeColor else MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) effectiveActive.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(180),
        label = "m3ChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) effectiveActive else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(180),
        label = "m3ChipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) effectiveActive else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "m3ChipText"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        shape = AppDesignTokens.ShapeSmall,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) effectiveActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else if (!emoji.isNullOrEmpty()) {
                Text(text = emoji, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * What it is:
 * Standard settings and preference toggle row with icon, title, subtitle, and compact M3 switch.
 *
 * What it does:
 * Renders interactive Material 3 UI for [GlassToggleRow] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [GlassToggleRow]
 */
@Composable
fun GlassToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    activeTrackColor: Color = Color.Unspecified,
    emoji: String? = null
) {
    val effectiveIconTint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.primary
    val effectiveTrackColor = if (activeTrackColor != Color.Unspecified) activeTrackColor else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!emoji.isNullOrEmpty()) {
                Text(text = emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveIconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = effectiveTrackColor,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

/**
 * What it is:
 * Compact custom animated switch toggle.
 *
 * What it does:
 * Renders interactive Material 3 UI for [CompactSwitch] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [CompactSwitch]
 */
@Composable
fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = AppDesignTokens.NeonCyan
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = activeColor,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

// ============================================================================
// MATERIAL 3 EXPRESSIVE CARDS (Full-Width, Compact Density)
// ============================================================================

/**
 * What it is:
 * Standard full-width Material 3 card container using tonal surface tokens.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppCard]
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = Material3DesignTokens.ShapeMedium,
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    elevation: Dp = Material3DesignTokens.ElevationLevel1,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveBg = if (backgroundColor != Color.Unspecified) backgroundColor else MaterialTheme.colorScheme.surfaceContainer
    val effectiveBorder = if (borderColor != Color.Unspecified) borderColor else MaterialTheme.colorScheme.outlineVariant

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = Material3DesignTokens.SpringFast,
        label = "appCardScale"
    )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .border(borderWidth, effectiveBorder, shape),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = effectiveBg,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = elevation
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDesignTokens.CardPaddingH, vertical = AppDesignTokens.CardPaddingV),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .border(borderWidth, effectiveBorder, shape),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = effectiveBg,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = elevation
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDesignTokens.CardPaddingH, vertical = AppDesignTokens.CardPaddingV),
                content = content
            )
        }
    }
}

/**
 * What it is:
 * Elevated card container featuring a prominent accent top border.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppCardElevated] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppCardElevated]
 */
@Composable
fun AppCardElevated(
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
        label = "appCardElevatedScale"
    )

    val outlineColor = accentGlow ?: MaterialTheme.colorScheme.outlineVariant

    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .border(1.dp, outlineColor, shape),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = elevation,
                pressedElevation = elevation + 2.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDesignTokens.CardPaddingH, vertical = AppDesignTokens.CardPaddingV),
                content = content
            )
        }
    } else {
        ElevatedCard(
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .border(1.dp, outlineColor, shape),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = elevation,
                pressedElevation = elevation + 2.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDesignTokens.CardPaddingH, vertical = AppDesignTokens.CardPaddingV),
                content = content
            )
        }
    }
}

/**
 * What it is:
 * Metric summary card displaying numerical counters, category titles, and status hints.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppStatCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppStatCard]
 */
@Composable
fun AppStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector? = null,
    emoji: String? = null,
    iconTint: Color = AppDesignTokens.NeonCyan,
    subText: String? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(
        modifier = modifier,
        shape = AppDesignTokens.ShapeSmall,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        } else if (!emoji.isNullOrEmpty()) {
            Text(text = emoji, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AppDesignTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = AppDesignTokens.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subText,
                fontSize = 10.5.sp,
                color = iconTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * What it is:
 * Centered empty state placeholder with icon/emoji, title, and clear action button.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppEmptyState] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppEmptyState]
 */
@Composable
fun AppEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = AppDesignTokens.CardPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (!emoji.isNullOrEmpty()) {
                Text(text = emoji, fontSize = 26.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppDesignTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = AppDesignTokens.TextMuted,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(
                    text = actionText,
                    onClick = onActionClick,
                    variant = GlassButtonVariant.PRIMARY
                )
            }
        }
    }
}

/**
 * What it is:
 * Modal dialog container card wrapping elevated surfaces.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppDialogCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppDialogCard]
 */
@Composable
fun AppDialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCardElevated(
        modifier = modifier.fillMaxWidth(),
        shape = Material3DesignTokens.ShapeMedium,
        accentGlow = MaterialTheme.colorScheme.primary,
        content = content
    )
}

/**
 * What it is:
 * Universal sleek top bar for secondary and sub-screens.
 *
 * What it does:
 * Provides edge-to-edge statusBarsPadding, compact height (~46dp content), bold title, optional subtitle, back navigation, and customizable actions.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppSubScreenHeader]
 */
@Composable
fun AppSubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/**
 * What it is:
 * Modern Material 3 Expressive Grouped Card Container.
 *
 * What it does:
 * Designed to cleanly encapsulate grouped settings and permission rows with unified borders and background.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppGroupCard]
 */
@Composable
fun AppGroupCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(com.jmd.helper.ui.theme.AppLayoutGrid.CardCornerRadius),
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(com.jmd.helper.ui.theme.AppLayoutGrid.BorderWidthStandard, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * What it is:
 * Clean hairline divider between items in an [AppGroupCard].
 *
 * What it does:
 * Renders interactive Material 3 UI for [AppGroupDivider] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppGroupDivider]
 */
@Composable
fun AppGroupDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 60.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(
            start = startIndent,
            end = com.jmd.helper.ui.theme.AppLayoutGrid.SpaceStandard
        ),
        thickness = com.jmd.helper.ui.theme.AppLayoutGrid.BorderWidthHairline,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * What it is:
 * Universal interactive row inside an [AppGroupCard].
 *
 * What it does:
 * Features leading squircle icon, title, subtitle, trailing action/status control, and expandable rationale accordion.
 *
 * Linked with:
 * - Architecture: [GlassComponents], [AppGroupRow]
 */
@Composable
fun AppGroupRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    trailingContent: @Composable () -> Unit = {},
    detailedPurpose: String? = null,
    onSettingsClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (detailedPurpose != null || onClick != null) {
                    Modifier.clickable {
                        if (detailedPurpose != null) {
                            isExpanded = !isExpanded
                        } else {
                            onClick?.invoke()
                        }
                    }
                } else Modifier
            )
            .padding(
                horizontal = com.jmd.helper.ui.theme.AppLayoutGrid.SpaceMedium,
                vertical = com.jmd.helper.ui.theme.AppLayoutGrid.SpaceStandard
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = com.jmd.helper.ui.theme.AppLayoutGrid.MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle leading icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = iconContainerColor,
                        shape = RoundedCornerShape(com.jmd.helper.ui.theme.AppLayoutGrid.SquircleCornerRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(com.jmd.helper.ui.theme.AppLayoutGrid.SpaceStandard))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(com.jmd.helper.ui.theme.AppLayoutGrid.SpaceMicro))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(com.jmd.helper.ui.theme.AppLayoutGrid.SpaceSmall))

            trailingContent()

            if (detailedPurpose != null) {
                Spacer(modifier = Modifier.width(com.jmd.helper.ui.theme.AppLayoutGrid.SpaceXS))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .scale(1f, if (isExpanded) -1f else 1f)
                )
            }
        }

        // Expandable Detail Accordion
        if (detailedPurpose != null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = com.jmd.helper.ui.theme.AppLayoutGrid.SpaceSmall,
                            start = 52.dp
                        )
                ) {
                    Text(
                        text = detailedPurpose,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                    if (onSettingsClick != null) {
                        Spacer(modifier = Modifier.height(com.jmd.helper.ui.theme.AppLayoutGrid.SpaceSmall))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onSettingsClick,
                                contentPadding = PaddingValues(
                                    horizontal = com.jmd.helper.ui.theme.AppLayoutGrid.SpaceSmall,
                                    vertical = com.jmd.helper.ui.theme.AppLayoutGrid.SpaceMicro
                                )
                            ) {
                                Text(
                                    text = "System Settings",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
