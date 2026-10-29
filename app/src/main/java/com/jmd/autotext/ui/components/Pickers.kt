

/**
 * Package: com.jmd.autotext.ui.components
 * Module: UI Inline Pickers Layer
 *
 * Inline non-intrusive Date & Time pickers (appDateTimePicker, appSegmentedTimePicker),
 * search inputs, and filter chips preventing disruptive modal popups.
 *
 * Linked With:
 * - Theme: `ThemeKt`, [com.jmd.autotext.ui.theme.AppDesignTokens]
 * - Screens: `ComposeTaskScreen`, `DailyCampaignScreen`
 */
package com.jmd.autotext.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.showAppTimePicker
import com.jmd.autotext.ui.theme.AppDesignTokens
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATE AND TIME PICKER COMPONENTS ====================


/**
 * Inline glassmorphic Date and Time picker presenting segmented interactive selectors.
 *
 * Allows users to configure dispatch timestamps with instant visual feedback without
 * disruptive fullscreen dialogs. Supports Quick Day selectors (Today, Tomorrow, Pick Date),
 * interactive Hour/Minute segment steppers, AM/PM toggle, and quick interval chips (+1h, +2h, Tonight).
 *
 * @param timestampMillis Currently selected trigger epoch millisecond timestamp.
 * @param onTimestampChanged Callback invoked whenever user alters date, hour, minute, or AM/PM state.
 * @param modifier Compose [Modifier] for container layout and sizing.
 * @param accentColor Theme accent color used for active selection highlights.
 * @param showDate True to render the date selection segment row.
 * @param showTime True to render the time stepper and quick increment buttons.
 */
@Composable
fun appDateTimePicker(
    timestampMillis: Long,
    onTimestampChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    showDate: Boolean = true,
    showTime: Boolean = true,
) {
    val effectiveAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val cal = remember(timestampMillis) {
        Calendar.getInstance().apply { timeInMillis = if (timestampMillis > 0) timestampMillis else System.currentTimeMillis() }
    }
    var year by remember(timestampMillis) { mutableIntStateOf(cal[Calendar.YEAR]) }
    var month by remember(timestampMillis) { mutableIntStateOf(cal[Calendar.MONTH]) }
    var day by remember(timestampMillis) { mutableIntStateOf(cal[Calendar.DAY_OF_MONTH]) }
    var hour by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

    fun updateTimestamp(newYear: Int = year, newMonth: Int = month, newDay: Int = day, newHour: Int = hour, newMinute: Int = minute) {
        val c = Calendar.getInstance().apply {
            set(newYear, newMonth, newDay, newHour, newMinute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        year = newYear
        month = newMonth
        day = newDay
        hour = newHour
        minute = newMinute
        onTimestampChanged(c.timeInMillis)
    }

    val displayDateStr = remember(year, month, day) {
        val c = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(c.time)
    }

    val displayTimeStr = remember(hour, minute) {
        val amPm = if (hour >= 12) "PM" else "AM"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        "%02d:%02d %s".format(h12, minute, amPm)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppDesignTokens.ShapeMedium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, effectiveAccent.copy(alpha = 0.25f), AppDesignTokens.ShapeMedium)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = effectiveAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Target Schedule",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = AppDesignTokens.ShapePill,
                color = effectiveAccent.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.4f)),
            ) {
                Text(
                    text = "$displayDateStr • $displayTimeStr",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = effectiveAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showDate) {
                Surface(
                    shape = AppDesignTokens.ShapeMedium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppDesignTokens.ShapeMedium)
                        .clickable {
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d -> updateTimestamp(newYear = y, newMonth = m, newDay = d) },
                                year, month, day
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Select Date",
                            tint = effectiveAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Scheduled Date", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                displayDateStr,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (showTime) {
                Surface(
                    shape = AppDesignTokens.ShapeMedium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppDesignTokens.ShapeMedium)
                        .clickable {
                            showAppTimePicker(context, hour, minute) { h, m ->
                                updateTimestamp(newHour = h, newMinute = m)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = "Select Time",
                            tint = effectiveAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Scheduled Time", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                displayTimeStr,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==================== FORM INPUT FIELDS & SEARCH COMPONENTS ====================


/**
 * Reusable frosted glass text input field supporting animated focus states, password masking,
 * error presentation, custom icons, and optional clear buttons.
 *
 * @param value Current string value of the input.
 * @param onValueChange Callback receiving modified string content.
 * @param modifier Layout [Modifier].
 * @param label Floating or persistent header label text.
 * @param placeholder Hint text displayed when value is empty.
 * @param leadingIcon Optional leading [ImageVector] displayed inside input bounds.
 * @param trailingIcon Optional trailing action [ImageVector].
 * @param onTrailingIconClick Optional click handler for trailing icon.
 * @param isPassword If true, applies password masking with toggleable visibility icon.
 * @param errorMessage Validation error string displayed beneath input.
 * @param enabled Interactivity state flag.
 * @param singleLine Restricts input to single line if true.
 * @param maxLines Maximum visible line height.
 * @param minLines Minimum visible line height.
 * @param keyboardOptions Soft keyboard type and action configuration.
 * @param keyboardActions Callbacks for keyboard action buttons.
 * @param showClearButton Shows quick clear button when text is non-empty.
 * @param accentColor Accent color for focused border and label tinting.
 */
@Composable
fun glassInputField(
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
    accentColor: Color = Color.Unspecified
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val effectiveAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val hasError = !errorMessage.isNullOrBlank()

    val targetBorderColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isFocused -> effectiveAccent
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 220),
        label = "glassInputBorder"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 220),
        label = "glassInputBg"
    )

    val labelColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isFocused -> effectiveAccent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppDesignTokens.ShapeSmall)
                .background(animatedBgColor)
                .border(
                    width = if (isFocused || hasError) 1.5.dp else 1.dp,
                    color = animatedBorderColor,
                    shape = AppDesignTokens.ShapeSmall
                )
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isFocused) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                }

                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(effectiveAccent),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None
                    )
                }

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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = if (isFocused) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact pill-shaped glassmorphic search input field.
 *
 * @param value Current search query text.
 * @param onValueChange Callback invoked as search query changes.
 * @param modifier Layout [Modifier].
 * @param placeholder Search bar hint text (defaults to "Search...").
 * @param leadingIcon Optional search icon vector.
 * @param onClear Callback invoked when user clicks the clear action button.
 */
@Composable
fun glassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val effectiveAccent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(AppDesignTokens.ShapePill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                1.dp,
                if (isFocused) effectiveAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                AppDesignTokens.ShapePill
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
                    tint = if (isFocused) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(effectiveAccent)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
