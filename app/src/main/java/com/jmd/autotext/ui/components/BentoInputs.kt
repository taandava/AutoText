@file:Suppress("FunctionName")

package com.jmd.autotext.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
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

/**
 * Universal Bento Glass 2.0 Input Component
 * Replaces every outdated text box with floating/animated label, frosted surface,
 * focus animation, leading/trailing icons, password show/hide, error feedback, and clear actions.
 */
@Composable
fun GlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    showClearButton: Boolean = true,
    accentColor: Color = BentoGlassTokens.NeonCyan
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val hasError = !errorMessage.isNullOrBlank()

    val targetBorderColor = when {
        hasError -> BentoGlassTokens.CyberRose
        isFocused -> accentColor
        else -> BentoGlassTokens.GlassBorderSubtle
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 220),
        label = "glassInputBorder"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isFocused) BentoGlassTokens.GlassSurfaceHover else BentoGlassTokens.GlassSurfaceElevated,
        animationSpec = tween(durationMillis = 220),
        label = "glassInputBg"
    )

    val labelColor = when {
        hasError -> BentoGlassTokens.CyberRose
        isFocused -> accentColor
        else -> BentoGlassTokens.TextMuted
    }

    Column(modifier = modifier) {
        // Compact Floating / Header Label
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
        )

        // Glass Text Field Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(BentoGlassTokens.ShapeSmall)
                .background(animatedBgColor)
                .border(
                    width = if (isFocused || hasError) 1.5.dp else 1.dp,
                    color = animatedBorderColor,
                    shape = BentoGlassTokens.ShapeSmall
                )
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Leading Icon
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isFocused) accentColor else BentoGlassTokens.TextMuted,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                }

                // Inner Input Field
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = BentoGlassTokens.TextDisabled,
                            fontSize = 14.sp
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused },
                        enabled = enabled,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        minLines = minLines,
                        textStyle = TextStyle(
                            color = BentoGlassTokens.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None
                    )
                }

                // Trailing Action / Password Toggle / Clear Button
                if (isPassword) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { passwordVisible = !passwordVisible },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = BentoGlassTokens.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else if (showClearButton && value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear text",
                            tint = BentoGlassTokens.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (trailingIcon != null) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(enabled = onTrailingIconClick != null) { onTrailingIconClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            tint = if (isFocused) accentColor else BentoGlassTokens.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}


@Suppress("FunctionName")
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(BentoGlassTokens.ShapePill)
            .background(BentoGlassTokens.GlassSurfaceElevated)
            .border(
                1.dp,
                if (isFocused) BentoGlassTokens.NeonCyan else BentoGlassTokens.GlassBorderSubtle,
                BentoGlassTokens.ShapePill
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) BentoGlassTokens.NeonCyan else BentoGlassTokens.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = BentoGlassTokens.TextDisabled,
                        fontSize = 13.5.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = BentoGlassTokens.TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(BentoGlassTokens.NeonCyan)
                )
            }

            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            onValueChange("")
                            onClear?.invoke()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = BentoGlassTokens.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// Material 3 Canonical Aliases
@Composable
fun Material3InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    showClearButton: Boolean = true,
    accentColor: Color = BentoGlassTokens.NeonCyan
) = GlassInputField(
    value, onValueChange, modifier, label, placeholder, leadingIcon,
    trailingIcon, onTrailingIconClick, isPassword, errorMessage, enabled,
    singleLine, maxLines, minLines, keyboardOptions, keyboardActions,
    showClearButton, accentColor
)

@Composable
fun Material3SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null
) = GlassSearchField(value, onValueChange, modifier, placeholder, leadingIcon, onClear)

