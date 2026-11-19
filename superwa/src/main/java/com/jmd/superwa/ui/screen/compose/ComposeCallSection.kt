/**
 * Package: com.jmd.superwa.ui.screen.compose
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [ComposeCallSection] in SuperWA.
 * Manages WhatsApp audio call target selection and timing configurations.
 */

package com.jmd.superwa.ui.screen.compose

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superwa.core.*
import com.jmd.superwa.data.model.*
import com.jmd.superwa.ui.components.*
import com.jmd.superwa.ui.model.*
import com.jmd.superwa.ui.theme.*

@Composable
fun ComposeCallSimulationAndSoundCard(
    selectedChannel: ChannelType,
    simulatedCallerName: String,
    onSimulatedCallerNameChange: (String) -> Unit,
    simulatedCallerNumber: String,
    onSimulatedCallerNumberChange: (String) -> Unit,
    simulatedCallStyle: String,
    onSimulatedCallStyleChange: (String) -> Unit,
    soundModeSetting: String,
    onSoundModeSettingChange: (String) -> Unit
) {
    // Delegated to SuperSMS in standalone architecture
}

@Composable
fun ComposePhoneCallCard(
    selectedChannel: ChannelType,
    onSelectChannel: ((ChannelType) -> Unit)? = null,
    selectedRecipients: List<SelectedRecipient>,
    duplicateWarning: String? = null,
    onDismissDuplicateWarning: () -> Unit = {},
    onClearAllRecipients: () -> Unit,
    onRemoveRecipient: (Int) -> Unit,
    recipientsInput: String,
    onRecipientsInputChange: (String) -> Unit,
    onAddManualRecipient: () -> Unit,
    onOpenPoolPicker: () -> Unit,
    onOpenContactsPicker: () -> Unit,
    onOpenWhatsAppContacts: (() -> Unit)? = null,
    onOpenWhatsAppPicker: (() -> Unit)? = null,
    callDurationSeconds: Int,
    onCallDurationChange: (Int) -> Unit,
    includeDialTime: Boolean,
    onIncludeDialTimeChange: (Boolean) -> Unit,
    callSequenceMode: String = "GIVEN",
    onCallSequenceModeChange: (String) -> Unit = {},
    delayBetweenCallsSeconds: Int = 10,
    onDelayBetweenCallsChange: (Int) -> Unit = {},
    callFailurePolicy: String = "CONTINUE",
    onCallFailurePolicyChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.tertiary

    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        borderColor = cardColor.copy(alpha = 0.5f),
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = cardColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        if (selectedRecipients.size > 1) "Call Targets (${selectedRecipients.size})" else "Call Target",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "WhatsApp voice call sequence",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selectedRecipients.isNotEmpty()) {
                IconButton(onClick = onClearAllRecipients, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (onSelectChannel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppChip(
                    selected = selectedChannel == ChannelType.WHATSAPP_CALL,
                    onClick = { onSelectChannel(ChannelType.WHATSAPP_CALL) },
                    label = "WhatsApp",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                AppChip(
                    selected = selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL,
                    onClick = { onSelectChannel(ChannelType.WHATSAPP_BUSINESS_CALL) },
                    label = "WA Business",
                    icon = Icons.Default.Business,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Picker Buttons
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenWhatsAppPicker != null) {
                AppButton(
                    text = if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL) "Pick W4B" else "Pick WA",
                    onClick = onOpenWhatsAppPicker,
                    variant = AppButtonVariant.PRIMARY,
                    icon = Icons.Default.Add,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
            if (onOpenWhatsAppContacts != null) {
                AppButton(
                    text = if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL) "W4B Contacts" else "WA Contacts",
                    onClick = onOpenWhatsAppContacts,
                    variant = AppButtonVariant.SECONDARY,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
            AppButton(
                text = "Contact Pool",
                onClick = onOpenPoolPicker,
                variant = AppButtonVariant.SECONDARY,
                icon = Icons.Default.Group,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = AppDesignTokens.ShapePill
            )
            AppButton(
                text = "Device Contacts",
                onClick = onOpenContactsPicker,
                variant = AppButtonVariant.PRIMARY,
                icon = Icons.Default.Contacts,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = AppDesignTokens.ShapePill
            )
        }

        if (duplicateWarning != null) {
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = duplicateWarning,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissDuplicateWarning, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (selectedRecipients.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedRecipients.forEachIndexed { idx, item ->
                    Surface(
                        shape = AppDesignTokens.ShapeSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    if (item.name.isNotBlank()) {
                                        Text(
                                            text = item.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = item.phone,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            IconButton(onClick = { onRemoveRecipient(idx) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            AppInputField(
                value = recipientsInput,
                onValueChange = onRecipientsInputChange,
                label = "Add Number",
                placeholder = "Enter number(s) e.g. +91 9876...",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppButton(
                text = "Add Number",
                onClick = onAddManualRecipient,
                variant = AppButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 14.dp),
                shape = AppDesignTokens.ShapeSmall,
                modifier = Modifier.height(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            var durationInputText by remember(callDurationSeconds) {
                mutableStateOf(if (callDurationSeconds > 0) callDurationSeconds.toString() else "0")
            }
            AppInputField(
                value = durationInputText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(5)
                    durationInputText = digits
                    onCallDurationChange(digits.toIntOrNull() ?: 0)
                },
                label = "Call Duration",
                placeholder = "Seconds (0 = Untimed)",
                leadingIcon = Icons.Default.Timer,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIncludeDialTimeChange(!includeDialTime) }
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeDialTime,
                    onCheckedChange = onIncludeDialTimeChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Count Dialing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Count call duration from dialing start", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            var delayInputText by remember(delayBetweenCallsSeconds) {
                mutableStateOf(delayBetweenCallsSeconds.toString())
            }
            AppInputField(
                value = delayInputText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(4)
                    delayInputText = digits
                    onDelayBetweenCallsChange(digits.toIntOrNull() ?: 10)
                },
                label = "Call Delay",
                placeholder = "10",
                leadingIcon = Icons.Default.HourglassBottom,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}
