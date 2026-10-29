/**
 * Package: com.jmd.autotext.ui.components
 * Module: UI Material 3 Expressive Components Layer
 *
 * Core reusable UI design building blocks adhering to Material 3 Expressive Orange specifications:
 * - Compact buttons, cards, filter chips, badges, segmented controls, and toggle rows.
 * - Zero obsolete glass shaders, glowing borders, or heavy decorative shadows.
 * - Full-width layout support with compact spacing and accessible 48dp touch targets.
 *
 * Linked With:
 * - Theme: `ThemeKt`, [com.jmd.autotext.ui.theme.AppDesignTokens]
 * - Screens: Used across all Compose screens in [com.jmd.autotext.ui.screen]
 */
package com.jmd.autotext.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.theme.AppDesignTokens
import com.jmd.autotext.ui.theme.Material3DesignTokens

// ============================================================================
// MATERIAL 3 EXPRESSIVE BUTTONS & BADGES
// ============================================================================

/**
 * Style variants for the [glassButton] component.
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
 * Reusable Material 3 Expressive Button with responsive ripple and compact vertical footprint.
 */
@Composable
fun glassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: RoundedCornerShape = AppDesignTokens.ShapeSmall,
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    emoji: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = AppDesignTokens.SpringFast,
        label = "m3ButtonScale"
    )

    val containerColor = when (variant) {
        GlassButtonVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        GlassButtonVariant.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        GlassButtonVariant.SUCCESS -> MaterialTheme.colorScheme.tertiary
        GlassButtonVariant.DANGER -> MaterialTheme.colorScheme.error
        GlassButtonVariant.OUTLINE -> Color.Transparent
    }

    val contentColor = when (variant) {
        GlassButtonVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        GlassButtonVariant.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        GlassButtonVariant.SUCCESS -> MaterialTheme.colorScheme.onTertiary
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
                    modifier = Modifier.size(16.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Compact status indicator badge using Material 3 tonal color container.
 */
@Composable
fun appBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    showDot: Boolean = true,
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
        if (!emoji.isNullOrEmpty()) {
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
 * Segmented control switch with Material 3 Expressive pill indicator.
 */
@Composable
fun glassSegmentedControl(
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
 * Material 3 Filter Chip with compact dimensions and animated selection feedback.
 */
@Composable
fun glassChip(
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
            if (!emoji.isNullOrEmpty()) {
                Text(text = emoji, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) effectiveActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
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
 * Standard settings and preference toggle row with icon, title, subtitle, and compact M3 switch.
 */
@Composable
fun glassToggleRow(
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
 * Compact custom animated switch toggle.
 */
@Composable
fun compactSwitch(
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
 * Standard full-width Material 3 card container using tonal surface tokens.
 */
@Composable
fun appCard(
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
 * Elevated card container featuring a prominent accent top border.
 */
@Composable
fun appCardElevated(
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

    val outlineColor = if (accentGlow != null) accentGlow else MaterialTheme.colorScheme.outlineVariant

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
 * Metric summary card displaying numerical counters, category titles, and status hints.
 */
@Composable
fun appStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector? = null,
    emoji: String? = null,
    iconTint: Color = AppDesignTokens.NeonCyan,
    subText: String? = null,
    onClick: (() -> Unit)? = null
) {
    appCard(
        modifier = modifier,
        shape = AppDesignTokens.ShapeSmall,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        if (!emoji.isNullOrEmpty()) {
            Text(text = emoji, fontSize = 18.sp)
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
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
 * Centered empty state placeholder with icon/emoji, title, and clear action button.
 */
@Composable
fun appEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    appCard(
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
            if (!emoji.isNullOrEmpty()) {
                Text(text = emoji, fontSize = 26.sp)
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
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
                glassButton(
                    text = actionText,
                    onClick = onActionClick,
                    variant = GlassButtonVariant.PRIMARY
                )
            }
        }
    }
}

/**
 * Modal dialog container card wrapping elevated surfaces.
 */
@Composable
fun appDialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    appCardElevated(
        modifier = modifier.fillMaxWidth(),
        shape = Material3DesignTokens.ShapeMedium,
        accentGlow = MaterialTheme.colorScheme.primary,
        content = content
    )
}

/**
 * Universal sleek top bar for secondary and sub-screens.
 * Provides edge-to-edge statusBarsPadding, compact height (~46dp content),
 * bold title, optional subtitle, back navigation, and customizable actions.
 */
@Composable
fun appSubScreenHeader(
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
