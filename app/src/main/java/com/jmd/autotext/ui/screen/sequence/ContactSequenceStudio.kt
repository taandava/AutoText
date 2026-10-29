package com.jmd.autotext.ui.screen.sequence

import android.content.*
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.scheduler.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.model.*
import com.jmd.autotext.ui.screen.compose.*
import com.jmd.autotext.ui.theme.*

/**
 * Studio environment for creating and modifying multi-step drip communication sequences for specific contacts.
 *
 * @param sequenceId Identifier of existing sequence if editing; 0L for new sequence.
 * @param initialContactName Pre-filled contact recipient name.
 * @param initialContactPhone Pre-filled contact phone number.
 * @param initialChannel Pre-selected communication channel preset.
 * @param onDismiss Callback to exit sequence studio.
 * @param onSaved Callback invoked when sequence is successfully persisted.
 */
@Composable
fun contactSequenceStudio(
    sequenceId: Long = 0L,
    initialContactName: String = "",
    initialContactPhone: String = "",
    initialChannel: ChannelType? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }

    // Channel-first selection dialog state: ask first if new sequence without a preselected channel
    var showChannelSelectorDialog by remember(sequenceId, initialChannel) {
        mutableStateOf(sequenceId == 0L && initialChannel == null)
    }

    // Multi-source Contact Picker Sheets State
    var showPoolPicker by remember { mutableStateOf(false) }
    var showWhatsAppContactsPicker by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler {
        if (showPoolPicker) {
            showPoolPicker = false
        } else if (showWhatsAppContactsPicker) {
            showWhatsAppContactsPicker = false
        } else if (showChannelSelectorDialog) {
            showChannelSelectorDialog = false
            onDismiss()
        } else {
            onDismiss()
        }
    }

    // Sequence Header State
    var contactName by remember { mutableStateOf(initialContactName) }
    var contactPhone by remember { mutableStateOf(initialContactPhone) }
    var sequenceTitle by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(true) }
    var sequenceChannel by remember(initialChannel) { mutableStateOf(initialChannel ?: ChannelType.SMS) }

    // Start Time State
    var isStartNow by remember { mutableStateOf(true) }
    var scheduledStartTimestamp by remember { mutableLongStateOf(System.currentTimeMillis() + 300_000L) }
    var isSaving by remember { mutableStateOf(false) }

    // Steps list state - strictly locked to the sequence's single channel
    val steps = remember(initialChannel) {
        val ch = initialChannel ?: ChannelType.SMS
        mutableStateListOf(
            ContactSequenceStep(
                stepOrder = 1,
                channel = ch,
                messageText = "",
                delayMinutesFromPrevious = 0,
                simSlot = 0
            ),
            ContactSequenceStep(
                stepOrder = 2,
                channel = ch,
                messageText = "",
                delayMinutesFromPrevious = 120, // +2 hours
                simSlot = 0
            )
        )
    }

    // Load existing sequence if editing
    LaunchedEffect(sequenceId) {
        if (sequenceId > 0L) {
            val existing = repository.getContactSequenceById(sequenceId)
            if (existing != null) {
                contactName = existing.contactName
                contactPhone = existing.contactPhone
                sequenceTitle = existing.title
                isEnabled = existing.isEnabled
                if (existing.steps.isNotEmpty()) {
                    sequenceChannel = existing.steps.first().channel
                    steps.clear()
                    steps.addAll(existing.steps)
                }
            }
        }
    }

    // Contact Picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val contactId = if (idIndex >= 0) it.getString(idIndex) else null
                        val name = if (nameIndex >= 0) it.getString(nameIndex) else ""
                        if (!name.isNullOrBlank()) contactName = name

                        if (contactId != null) {
                            val pCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(contactId),
                                null
                            )
                            pCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numberIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numberIdx >= 0) {
                                        val number = pc.getString(numberIdx)
                                        if (!number.isNullOrBlank()) {
                                            contactPhone = number.replace("[^0-9+]".toRegex(), "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                context.showAppToast("Could not load contact: ${e.message}")
            }
        }
    }

    BackHandler(enabled = showPoolPicker || showWhatsAppContactsPicker) {
        showPoolPicker = false
        showWhatsAppContactsPicker = false
    }

    if (showPoolPicker) {
        val currentList = if (contactPhone.isNotBlank()) listOf(SelectedRecipient(contactName.ifBlank { contactPhone }, contactPhone)) else emptyList()
        contactPoolPickerSheet(
            repository = repository,
            selectedRecipients = currentList,
            onAddRecipient = { name, phone ->
                contactName = name
                contactPhone = phone
                showPoolPicker = false
                true
            },
            onCommitRecipients = { list ->
                list.lastOrNull()?.let {
                    contactName = it.name
                    contactPhone = it.phone
                }
                showPoolPicker = false
            },
            onDismiss = { showPoolPicker = false }
        )
    }

    if (showWhatsAppContactsPicker) {
        val currentList = if (contactPhone.isNotBlank()) listOf(SelectedRecipient(contactName.ifBlank { contactPhone }, contactPhone)) else emptyList()
        whatsAppContactsPickerSheet(
            repository = repository,
            selectedRecipients = currentList,
            selectedChannel = sequenceChannel,
            onAddRecipient = { name, phone ->
                contactName = name
                contactPhone = phone
                showWhatsAppContactsPicker = false
                true
            },
            onCommitRecipients = { list ->
                list.lastOrNull()?.let {
                    contactName = it.name
                    contactPhone = it.phone
                }
                showWhatsAppContactsPicker = false
            },
            onDismiss = { showWhatsAppContactsPicker = false }
        )
    }

    if (showChannelSelectorDialog) {
        Dialog(
            onDismissRequest = { showChannelSelectorDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = AppDesignTokens.ShapeLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .systemBarsPadding()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Choose Sequence Channel",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select messaging channel for this drip sequence",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showChannelSelectorDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    sequenceChannelCard(
                        title = "SMS Message Sequence",
                        description = "Step-by-step drip messages via device SIM card (Carrier Direct)",
                        badge = "SIM Direct",
                        emoji = "📩",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            sequenceChannel = ChannelType.SMS
                            steps.indices.forEach { i -> steps[i] = steps[i].copy(channel = ChannelType.SMS) }
                            showChannelSelectorDialog = false
                        }
                    )

                    sequenceChannelCard(
                        title = "WhatsApp Sequence",
                        description = "Multi-step scheduled messages via personal WhatsApp",
                        badge = "Personal WA",
                        emoji = "💬",
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            sequenceChannel = ChannelType.WHATSAPP
                            steps.indices.forEach { i -> steps[i] = steps[i].copy(channel = ChannelType.WHATSAPP) }
                            showChannelSelectorDialog = false
                        }
                    )

                    sequenceChannelCard(
                        title = "WhatsApp Business (W4B) Sequence",
                        description = "Client outreach and paced follow-up sequence via WhatsApp Business",
                        badge = "Business W4B",
                        emoji = "💼",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            sequenceChannel = ChannelType.WHATSAPP_BUSINESS
                            steps.indices.forEach { i -> steps[i] = steps[i].copy(channel = ChannelType.WHATSAPP_BUSINESS) }
                            showChannelSelectorDialog = false
                        }
                    )
                }
            }
        }
    }

    // Projected Trigger Timestamps preview calculation
    val projectedTimestamps = remember(steps.map { Triple(it.stepOrder, it.delayMinutesFromPrevious, it.specificTriggerTime) }, isStartNow, scheduledStartTimestamp) {
        var acc = if (isStartNow) System.currentTimeMillis() + 5000L else scheduledStartTimestamp
        steps.mapIndexed { idx, st ->
            if (st.specificTriggerTime > 0L) {
                st.specificTriggerTime
            } else {
                if (idx > 0) {
                    acc += st.delayMinutesFromPrevious * 60_000L
                }
                acc
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = if (sequenceId > 0) "Edit Contact Sequence" else "New Contact Task Sequence",
                subtitle = "Multi-step scheduled messaging per number",
                onBack = onDismiss,
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isEnabled) "Active" else "Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp)
                .padding(top = 2.dp)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Target Contact Information Card
            item {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👤", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Target Contact", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            glassButton(
                                text = "From Pool",
                                emoji = "👥",
                                onClick = { showPoolPicker = true },
                                variant = GlassButtonVariant.SECONDARY,
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                            glassButton(
                                text = "WA Contacts",
                                emoji = "💬",
                                onClick = { showWhatsAppContactsPicker = true },
                                variant = GlassButtonVariant.SECONDARY,
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                            glassButton(
                                text = "Contacts",
                                emoji = "👤",
                                onClick = { contactPickerLauncher.launch(null) },
                                variant = GlassButtonVariant.SECONDARY,
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        glassInputField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            label = "Contact Name",
                            placeholder = "e.g. John Doe, Client, Lead",
                            leadingIcon = Icons.Default.Badge,
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        glassInputField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' } },
                            label = "Phone Number",
                            placeholder = "e.g. +91 9876543210",
                            leadingIcon = Icons.Default.Phone,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    glassInputField(
                        value = sequenceTitle,
                        onValueChange = { sequenceTitle = it },
                        label = "Sequence Identifier (Optional)",
                        placeholder = if (contactName.isNotBlank()) "e.g. Follow-up for $contactName" else "e.g. 3-Step Lead Nurture",
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sequence Channel:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            val (badgeText, badgeColor, badgeEmoji) = when (sequenceChannel) {
                                ChannelType.WHATSAPP_BUSINESS -> Triple("WA Business (W4B)", MaterialTheme.colorScheme.primary, "💼")
                                ChannelType.WHATSAPP -> Triple("WhatsApp (Personal)", MaterialTheme.colorScheme.tertiary, "💬")
                                else -> Triple("SMS (Carrier Direct)", MaterialTheme.colorScheme.primary, "📩")
                            }
                            appBadge(text = badgeText, emoji = badgeEmoji, color = badgeColor)
                        }
                        Text(
                            text = "Change Channel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(AppDesignTokens.ShapePill)
                                .clickable { showChannelSelectorDialog = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Initial Start Timing Enterprise Card
            item {
                appCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sequence Start Timing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    glassSegmentedControl(
                        items = listOf("Start Now (Immediate)", "Schedule Date & Time"),
                        selectedIndex = if (isStartNow) 0 else 1,
                        onItemSelected = { isStartNow = (it == 0) },
                        activeColor = MaterialTheme.colorScheme.primary
                    )

                    if (!isStartNow) {
                        Spacer(modifier = Modifier.height(8.dp))
                        appDateTimePicker(
                            timestampMillis = scheduledStartTimestamp,
                            onTimestampChanged = { newTs: Long -> scheduledStartTimestamp = newTs },
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 3. Visual Pipeline / Sequence Timeline
            item {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sequence Pipeline Flow (${steps.size} Steps)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { idx, step ->
                            val chipColor = when (step.channel) {
                                ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary
                                ChannelType.WHATSAPP_BUSINESS -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val delayLabel = when {
                                idx == 0 && isStartNow -> "Now"
                                idx == 0 -> "At start"
                                step.delayMinutesFromPrevious < 60 -> "+${step.delayMinutesFromPrevious}m"
                                step.delayMinutesFromPrevious < 1440 -> "+${step.delayMinutesFromPrevious / 60}h"
                                else -> "+${step.delayMinutesFromPrevious / 1440}d"
                            }

                            Surface(
                                shape = AppDesignTokens.ShapePill,
                                color = chipColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("#${idx + 1} ${step.channel.name.take(3)}", color = chipColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text(delayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Sequence Steps Editor
            itemsIndexed(steps) { index, step ->
                stepEditorCard(
                    stepNumber = index + 1,
                    isFirst = index == 0,
                    step = step,
                    projectedTriggerTimestamp = projectedTimestamps.getOrElse(index) { 0L },
                    onStepChanged = { updated -> steps[index] = updated },
                    onDelete = {
                        if (steps.size > 1) {
                            steps.removeAt(index)
                        } else {
                            context.showAppToast("Add at least 1 step")
                        }
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val temp = steps[index]
                            steps[index] = steps[index - 1]
                            steps[index - 1] = temp
                        }
                    },
                    onMoveDown = {
                        if (index < steps.size - 1) {
                            val temp = steps[index]
                            steps[index] = steps[index + 1]
                            steps[index + 1] = temp
                        }
                    }
                )
            }

            // 5. Add Step Button
            item {
                glassButton(
                    text = "Add Next Message / Task Step",
                    emoji = "➕",
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val nextDelay = if (steps.isEmpty()) 0 else 120 // +2 hours default
                        steps.add(
                            ContactSequenceStep(
                                stepOrder = steps.size + 1,
                                channel = sequenceChannel,
                                messageText = "",
                                delayMinutesFromPrevious = nextDelay,
                                simSlot = 0
                            )
                        )
                    }
                )
            }

            // 6. Save Sequence Action
            item {
                Spacer(modifier = Modifier.height(4.dp))
                glassButton(
                    text = if (isSaving) "Saving Sequence..." else if (sequenceId > 0) "Update Sequence & Reschedule" else "Arm & Save Contact Sequence",
                    emoji = if (isSaving) "⏳" else "💾",
                    variant = GlassButtonVariant.PRIMARY,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isSaving) return@glassButton
                        val cleanPhone = contactPhone.replace("[^0-9+]".toRegex(), "")
                        if (cleanPhone.length < 5) {
                            context.showAppToast("Enter valid phone number")
                            return@glassButton
                        }
                        if (steps.any { it.messageText.isBlank() }) {
                            context.showAppToast("Fill message text for all steps")
                            return@glassButton
                        }

                        val startTs = if (isStartNow) {
                            System.currentTimeMillis() + 5000L
                        } else {
                            scheduledStartTimestamp
                        }

                        val sequenceToSave = ContactSequence(
                            id = sequenceId,
                            contactName = contactName.trim(),
                            contactPhone = cleanPhone,
                            title = sequenceTitle.ifBlank { "Sequence for ${contactName.ifBlank { cleanPhone }}" },
                            isEnabled = isEnabled,
                            createdAt = System.currentTimeMillis(),
                            steps = steps.toList()
                        )

                        isSaving = true
                        try {
                            repository.saveContactSequence(sequenceToSave, startTs)
                            context.showAppToast("Sequence saved (${steps.size} steps)")
                            onSaved()
                        } catch (e: Exception) {
                            Log.e("ContactSequenceStudio", "Failed saving sequence", e)
                            context.showAppToast("Failed to save sequence: ${e.message}")
                            isSaving = false
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun sequenceChannelCard(
    title: String,
    description: String,
    badge: String,
    emoji: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = AppDesignTokens.ShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppDesignTokens.ShapeMedium)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f))
                    .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = AppDesignTokens.ShapePill,
                        color = color.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// =========================================================================================
// STEP EDITOR COMPONENT
// =========================================================================================



/**
 * Step configuration card for adjusting individual action in a multi-step sequence (delay, channel, message).
 *
 * @param stepNumber Index of step in sequence pipeline.
 * @param isFirst True if this is the initial execution step.
 * @param step Contact sequence step data model.
 * @param projectedTriggerTimestamp Computed trigger epoch timestamp for preview.
 * @param onStepChanged Callback when step properties are modified.
 * @param onDelete Callback to remove this step.
 * @param onMoveUp Callback to swap step with preceding item.
 * @param onMoveDown Callback to swap step with succeeding item.
 */
@Composable
fun stepEditorCard(
    stepNumber: Int,
    isFirst: Boolean,
    step: ContactSequenceStep,
    projectedTriggerTimestamp: Long = 0L,
    onStepChanged: (ContactSequenceStep) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val channelColor = when (step.channel) {
        ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary
        ChannelType.WHATSAPP_BUSINESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = channelColor.copy(alpha = 0.5f)
    ) {
        // Step Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = channelColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, channelColor),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$stepNumber", color = channelColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Step $stepNumber Message", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step Channel Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dispatch Channel:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            appBadge(
                text = step.channel.displayName,
                color = if (step.channel == ChannelType.SMS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }

        if (step.channel == ChannelType.SMS) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Carrier SIM: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Default SIM", 1 to "SIM 1", 2 to "SIM 2").forEach { (sim, label) ->
                        val isSelected = step.simSlot == sim
                        Surface(
                            shape = AppDesignTokens.ShapePill,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(AppDesignTokens.ShapePill)
                                .clickable { onStepChanged(step.copy(simSlot = sim)) }
                        ) {
                            Text(
                                label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Step Timing: Relative Interval or Exact Target Date/Time
        Spacer(modifier = Modifier.height(8.dp))
        val isExactTargetTime = step.specificTriggerTime > 0L

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Step Dispatch Timing:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            val timingBadgeText = remember(step.specificTriggerTime, step.delayMinutesFromPrevious, isFirst) {
                if (step.specificTriggerTime > 0L) {
                    SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(Date(step.specificTriggerTime))
                } else if (isFirst && step.delayMinutesFromPrevious == 0) {
                    "Immediate upon start"
                } else if (step.delayMinutesFromPrevious < 60) {
                    "+${step.delayMinutesFromPrevious}m after prev"
                } else if (step.delayMinutesFromPrevious < 1440) {
                    "+${step.delayMinutesFromPrevious / 60}h after prev"
                } else {
                    "+${step.delayMinutesFromPrevious / 1440}d after prev"
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = AppDesignTokens.ShapePill,
                    color = channelColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, channelColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "⚡ $timingBadgeText",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = channelColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (projectedTriggerTimestamp > 0L && step.specificTriggerTime == 0L) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "≈ " + SimpleDateFormat("dd MMM, hh:mm a", androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)).format(Date(projectedTriggerTimestamp)),
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        glassSegmentedControl(
            items = listOf("Interval Delay", "Target Date & Time"),
            selectedIndex = if (isExactTargetTime) 1 else 0,
            onItemSelected = { selectedIdx ->
                if (selectedIdx == 0) {
                    onStepChanged(step.copy(specificTriggerTime = 0L))
                } else {
                    val defaultTime = if (step.specificTriggerTime > 0L) step.specificTriggerTime else System.currentTimeMillis() + (stepNumber * 3600_000L)
                    onStepChanged(step.copy(specificTriggerTime = defaultTime))
                }
            },
            activeColor = channelColor
        )

        if (!isExactTargetTime) {
            Spacer(modifier = Modifier.height(6.dp))
            val delayPresets = if (isFirst) {
                listOf(
                    0 to "Immediate",
                    15 to "+15m",
                    30 to "+30m",
                    60 to "+1h",
                    120 to "+2h",
                    1440 to "+1d"
                )
            } else {
                listOf(
                    15 to "+15m",
                    30 to "+30m",
                    60 to "+1h",
                    120 to "+2h",
                    360 to "+6h",
                    1440 to "+1d",
                    2880 to "+2d",
                    4320 to "+3d"
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                delayPresets.chunked(4).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowPresets.forEach { (minutes, label) ->
                            val isSel = step.delayMinutesFromPrevious == minutes
                            Surface(
                                shape = AppDesignTokens.ShapePill,
                                color = if (isSel) channelColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, if (isSel) channelColor else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(AppDesignTokens.ShapePill)
                                    .clickable { onStepChanged(step.copy(delayMinutesFromPrevious = minutes)) }
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) channelColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            appDateTimePicker(
                timestampMillis = step.specificTriggerTime,
                onTimestampChanged = { newTs: Long ->
                    onStepChanged(step.copy(specificTriggerTime = newTs))
                },
                accentColor = channelColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message text input
        glassInputField(
            value = step.messageText,
            onValueChange = { onStepChanged(step.copy(messageText = it)) },
            label = "Step $stepNumber Message Text",
            placeholder = "Type message for this stage...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            accentColor = channelColor
        )

        // Dynamic Variable Chips
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "{name}", "{first_name}", "{phone}",
                "{date}", "{short_date}", "{time}",
                "{day}", "{short_day}", "{day_num}"
            ).chunked(3).forEach { rowTags ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppDesignTokens.ShapePill)
                                .background(channelColor.copy(alpha = 0.12f))
                                .border(1.dp, channelColor.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                .clickable {
                                    onStepChanged(step.copy(messageText = step.messageText + " $tag"))
                                }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tag, fontSize = 10.sp, color = channelColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
