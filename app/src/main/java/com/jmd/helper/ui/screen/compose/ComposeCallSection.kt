package com.jmd.helper.ui.screen.compose

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
import com.jmd.helper.core.*
import com.jmd.helper.data.model.*
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.model.*
import com.jmd.helper.ui.theme.*

/**
 * Card managing simulated incoming phone call timing and audio profile.
 * @param onSimulatedCallerNameChange Callback when simulated name changes.
 * @param simulatedCallerNumber Simulated incoming phone number.
 * @param onSimulatedCallerNumberChange Callback when simulated number changes.
 * @param simulatedCallStyle Visual UI style for incoming call bouncer.
 * @param onSimulatedCallStyleChange Callback when call screen theme changes.
 * @param soundModeSetting Target sound profile (NORMAL, VIBRATE, SILENT).
 * @param onSoundModeSettingChange Callback when target sound mode is changed.
 */
@Composable
fun ComposeCallSimulationAndSoundCard(
    selectedChannel: ChannelType,
    simulatedCallerName: String,
    onSimulatedCallerNameChange: (String) -> Unit,
    simulatedCallerNumber: String,
    onSimulatedCallerNumberChange: (String) -> Unit,
    simulatedCallStyle: String,
    onSimulatedCallStyleChange: (String) -> Unit,
    soundModeSetting: String = "",
    onSoundModeSettingChange: (String) -> Unit = {}
) {
    when (selectedChannel) {
        ChannelType.CALL_SIMULATION -> {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = AppDesignTokens.ShapeLarge
            ) {
                Text(
                    "Call Simulation Settings",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassInputField(
                    value = simulatedCallerName,
                    onValueChange = onSimulatedCallerNameChange,
                    label = "Caller Name",
                    placeholder = "Office / Manager / Home",
                    leadingIcon = Icons.Default.Person,
                    accentColor = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassInputField(
                    value = simulatedCallerNumber,
                    onValueChange = onSimulatedCallerNumberChange,
                    label = "Caller Phone Number",
                    placeholder = "+91 98765 43210",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    accentColor = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Screen Style Theme:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("PIXEL" to "Pixel", "SAMSUNG" to "Samsung", "WHATSAPP" to "WhatsApp").forEach { (style, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSimulatedCallStyleChange(style) }
                        ) {
                            RadioButton(
                                selected = simulatedCallStyle == style,
                                onClick = { onSimulatedCallStyleChange(style) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

/**
 * Backward-compatible alias for [ComposeCallSimulationAndSoundCard].
 */
@Composable
fun ComposeSimulatedCallAndSoundCard(
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
    ComposeCallSimulationAndSoundCard(
        selectedChannel = selectedChannel,
        simulatedCallerName = simulatedCallerName,
        onSimulatedCallerNameChange = onSimulatedCallerNameChange,
        simulatedCallerNumber = simulatedCallerNumber,
        onSimulatedCallerNumberChange = onSimulatedCallerNumberChange,
        simulatedCallStyle = simulatedCallStyle,
        onSimulatedCallStyleChange = onSimulatedCallStyleChange,
        soundModeSetting = soundModeSetting,
        onSoundModeSettingChange = onSoundModeSettingChange
    )
}




/**
 * Recipient selector and calling policy card for cellular and WhatsApp outbound voice calls.
 *
 * @param selectedRecipients List of selected target phone numbers.
 * @param duplicateWarning Warning text if a contact is already added.
 * @param onDismissDuplicateWarning Callback to clear warning banner.
 * @param onClearAllRecipients Callback to empty recipient list.
 * @param onRemoveRecipient Callback to delete recipient at index.
 * @param recipientsInput Manual phone number input buffer.
 * @param onRecipientsInputChange Callback when input buffer is edited.
 * @param onAddManualRecipient Callback to parse and add manual number.
 * @param onOpenPoolPicker Callback to display dynamic pool selection sheet.
 * @param onOpenContactsPicker Callback to open system contact picker.
 * @param selectedChannel Channel configuration (CALL, WHATSAPP_CALL, WHATSAPP_BUSINESS_CALL).
 * @param onSelectChannel Callback to change calling channel.
 * @param onOpenWhatsAppPicker Optional callback to launch WhatsApp chat scanner.
 * @param onOpenWhatsAppContacts Optional callback to launch WhatsApp contacts sheet.
 * @param callDurationSeconds Auto-hangup duration timer in seconds.
 * @param onCallDurationChange Callback to update auto-hangup timer.
 * @param includeDialTime Whether to count dialing latency into duration limit.
 * @param onIncludeDialTimeChange Callback to toggle dial time inclusion.
 * @param callSequenceMode Dispatch order (GIVEN, RANDOM, etc.).
 * @param onCallSequenceModeChange Callback to update sequence mode.
 * @param delayBetweenCallsSeconds Pacing delay between consecutive phone calls.
 * @param onDelayBetweenCallsChange Callback to adjust inter-call delay.
 * @param callFailurePolicy Strategy on call failure (CONTINUE, RETRY, ABORT).
 * @param onCallFailurePolicyChange Callback to update failure policy.
 */
@Composable
fun ComposePhoneCallCard(
    selectedRecipients: List<SelectedRecipient>,
    duplicateWarning: String?,
    onDismissDuplicateWarning: () -> Unit,
    onClearAllRecipients: () -> Unit,
    onRemoveRecipient: (Int) -> Unit,
    recipientsInput: String,
    onRecipientsInputChange: (String) -> Unit,
    onAddManualRecipient: () -> Unit,
    onOpenPoolPicker: () -> Unit,
    onOpenContactsPicker: () -> Unit,
    selectedChannel: ChannelType = ChannelType.CALL,
    onSelectChannel: ((ChannelType) -> Unit)? = null,
    onOpenWhatsAppPicker: (() -> Unit)? = null,
    onOpenWhatsAppContacts: (() -> Unit)? = null,
    callDurationSeconds: Int = 0,
    onCallDurationChange: (Int) -> Unit = {},
    includeDialTime: Boolean = false,
    onIncludeDialTimeChange: (Boolean) -> Unit = {},
    callSequenceMode: String = "GIVEN",
    onCallSequenceModeChange: (String) -> Unit = {},
    delayBetweenCallsSeconds: Int = 5,
    onDelayBetweenCallsChange: (Int) -> Unit = {},
    callFailurePolicy: String = "CONTINUE",
    onCallFailurePolicyChange: (String) -> Unit = {}
) {
    val isWaCalling = selectedChannel == ChannelType.WHATSAPP_CALL || selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL
    val cardColor = if (isWaCalling) {
        if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        borderColor = cardColor.copy(alpha = 0.5f)
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
                        if (selectedRecipients.size > 1) "Scheduled Call Recipients (${selectedRecipients.size})" else "Scheduled Call Recipients",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (selectedRecipients.size > 1) "Helper will place calls sequentially to each number until all are completed" else "Helper will dial each scheduled number in sequence",
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

        if (isWaCalling && onSelectChannel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App:",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                GlassChip(
                    selected = selectedChannel == ChannelType.WHATSAPP_CALL,
                    onClick = { onSelectChannel(ChannelType.WHATSAPP_CALL) },
                    label = "WhatsApp (Personal)",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                GlassChip(
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
            if (isWaCalling && onOpenWhatsAppPicker != null) {
                GlassButton(
                    text = if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL) "Pick in W4B" else "Pick in WA",
                    onClick = onOpenWhatsAppPicker,
                    variant = GlassButtonVariant.PRIMARY,
                    icon = Icons.Default.Add,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
            if (isWaCalling && onOpenWhatsAppContacts != null) {
                GlassButton(
                    text = if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL) "W4B Contacts" else "WA Contacts",
                    onClick = onOpenWhatsAppContacts,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
            GlassButton(
                text = "From Pool",
                onClick = onOpenPoolPicker,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.Group,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = AppDesignTokens.ShapePill
            )
            GlassButton(
                text = "Device Contacts",
                onClick = onOpenContactsPicker,
                variant = GlassButtonVariant.PRIMARY,
                icon = Icons.Default.Contacts,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = AppDesignTokens.ShapePill
            )
        }

        // Duplicate Warning Banner
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

        // Selected Target Number Display
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

        // Manual Number Entry (Single or comma/newline-separated multiple numbers)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            GlassInputField(
                value = recipientsInput,
                onValueChange = onRecipientsInputChange,
                label = "Add Target Phone Number(s)",
                placeholder = "Enter number(s) e.g. +91 9876..., 91234...",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
                text = "Add Number",
                onClick = onAddManualRecipient,
                variant = GlassButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 14.dp),
                shape = AppDesignTokens.ShapeSmall,
                modifier = Modifier.height(46.dp)
            )
        }

        // Duration & Sequence Mode Controls
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sequence Mode",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCallSequenceModeChange("GIVEN") }
                    ) {
                        RadioButton(
                            selected = callSequenceMode == "GIVEN",
                            onClick = { onCallSequenceModeChange("GIVEN") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Given Order", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCallSequenceModeChange("RANDOM") }
                    ) {
                        RadioButton(
                            selected = callSequenceMode == "RANDOM",
                            onClick = { onCallSequenceModeChange("RANDOM") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Random Order", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            var durationInputText by remember(callDurationSeconds) {
                mutableStateOf(if (callDurationSeconds > 0) callDurationSeconds.toString() else "0")
            }
            GlassInputField(
                value = durationInputText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(5)
                    durationInputText = digits
                    onCallDurationChange(digits.toIntOrNull() ?: 0)
                },
                label = "Call Duration in Seconds (0 = Open / Untimed)",
                placeholder = "0",
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
                    Text("Include Dialing Time in Call Duration", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Count call duration from dialing start instead of waiting for connection", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            var delayInputText by remember(delayBetweenCallsSeconds) {
                mutableStateOf(delayBetweenCallsSeconds.toString())
            }
            GlassInputField(
                value = delayInputText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(4)
                    delayInputText = digits
                    onDelayBetweenCallsChange(digits.toIntOrNull() ?: 10)
                },
                label = "Sequential Inter-Call Delay in Seconds",
                placeholder = "10",
                leadingIcon = Icons.Default.HourglassBottom,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Pause between consecutive calls in multi-number sequence to let network and radio settle",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Failure Policy:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Halt remaining calls or continue dialing the next recipient if an individual call fails",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCallFailurePolicyChange("CONTINUE") }
                ) {
                    RadioButton(
                        selected = callFailurePolicy == "CONTINUE",
                        onClick = { onCallFailurePolicyChange("CONTINUE") },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Continue Next", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCallFailurePolicyChange("STOP_ON_FAILURE") }
                ) {
                    RadioButton(
                        selected = callFailurePolicy == "STOP_ON_FAILURE",
                        onClick = { onCallFailurePolicyChange("STOP_ON_FAILURE") },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop on Failure", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Note: No text message needed
        Surface(
            shape = AppDesignTokens.ShapeSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pure Phone Call mode: At the scheduled time, the call will be initiated to destination numbers in sequence.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

